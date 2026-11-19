package local.materialui;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.ContextWrapper;
import android.media.MediaPlayer;
import android.view.View;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.TextView;
import __TARGET_PACKAGE__.activitys.videoclip.VideoClipsActivity;
import com.spx.library.player.VideoPlayer;
import com.zoulequan.base.spx.videoclipeditviewtest.ClipContainer;
import java.util.Locale;
import java.util.WeakHashMap;

/** Presents the existing editor range and transport without replacing the media engine. */
public final class ClipLayout implements Runnable, View.OnClickListener, View.OnAttachStateChangeListener, View.OnTouchListener {
    private final VideoClipsActivity activity;
    private final ImageButton transport;
    private final TextView start;
    private final TextView end;
    private final TextView duration;
    private final TextView position;
    private final View timeline;
    private final View leftHandle;
    private final View rightHandle;
    private final View leftTouch;
    private final View rightTouch;
    private final View middleTouch;
    private float touchX;
    private float touchY;
    private float dragLeft;
    private float dragWidth;
    private long dragFirst;
    private long dragDuration;
    private boolean moving;
    private boolean dragWasPlaying;
    private boolean attached;
    private Boolean wasPlaying;
    private boolean resumed;
    private boolean rangeReady;
    private boolean playerReady;
    private boolean restoring;
    private Bundle pending;
    private Bundle suspended;
    private long exportFirst = -1;
    private long exportLast = -1;
    private static final String STATE = "material_ui_trim";
    private static final WeakHashMap<Activity, Bundle> restored = new WeakHashMap<>();

    private static ClipLayout controller(Activity owner) {
        int id = owner.getResources().getIdentifier("material_ui_clip_play", "id", owner.getPackageName());
        View button = owner.findViewById(id);
        return button != null && button.getTag() instanceof ClipLayout ? (ClipLayout) button.getTag() : null;
    }
    public static void restore(Activity owner, Bundle state) {
        if (state != null && state.getBundle(STATE) != null)
            restored.put(owner, new Bundle(state.getBundle(STATE)));
    }
    public static void save(Activity owner, Bundle out) {
        ClipLayout ui = controller(owner);
        Bundle state = ui == null ? restored.get(owner) : ui.snapshot();
        if (state != null) out.putBundle(STATE, state);
    }
    public static void pause(Activity owner) {
        ClipLayout ui = controller(owner);
        if (ui == null) return;
        ui.suspended = ui.snapshot();
        ui.pending = ui.suspended;
        ui.resumed = false;
        ui.transport.removeCallbacks(ui);
    }
    public static void resume(Activity owner) {
        apply(owner);
        ClipLayout ui = controller(owner);
        if (ui == null) return;
        ui.resumed = true;
        ui.transport.removeCallbacks(ui);
        ui.transport.post(ui);
    }
    public static void ready(Activity owner) {
        ClipLayout ui = controller(owner);
        if (ui == null) { apply(owner); ui = controller(owner); }
        if (ui == null) return;
        ui.rangeReady = true;
        if (ui.resumed) { ui.transport.removeCallbacks(ui); ui.transport.post(ui); }
    }
    public static boolean hasCurrentExport(Activity owner) {
        ClipLayout ui = controller(owner);
        return ui != null && ui.exportFirst >= 0
                && ui.exportFirst == ui.activity.getStartMillSec()
                && ui.exportLast == ui.activity.getEndMillSec();
    }
    public static void exportStarted(Activity owner) {
        ClipLayout ui = controller(owner);
        if (ui != null) {
            ui.exportFirst = ui.exportLast = -1;
            selectionChanged(owner);
        }
    }
    /** Mark only a completed native encoder job, on the UI thread. */
    public static void exportCompleted(final Activity owner) {
        owner.runOnUiThread(new Runnable() {
            @Override public void run() {
                ClipLayout ui = controller(owner);
                if (ui == null || owner.isFinishing() || owner.isDestroyed()) return;
                ui.exportFirst = ui.activity.getStartMillSec();
                ui.exportLast = ui.activity.getEndMillSec();
            }
        });
    }
    public static void selectionChanged(Activity owner) {
        ClipLayout ui = controller(owner);
        if (ui == null || ui.restoring) return;
        View share = owner.findViewById(ui.resource("tv_share", "id"));
        if (share != null && !hasCurrentExport(owner)) share.setVisibility(View.GONE);
    }
    private Bundle withExport(Bundle state) {
        if (state == null) return null;
        Bundle result = new Bundle(state);
        result.putLong("exportFirst", exportFirst);
        result.putLong("exportLast", exportLast);
        return result;
    }
    public static boolean hasPreparedPlayer(Activity owner) {
        ClipLayout ui = controller(owner);
        return ui != null && ui.playerReady;
    }
    public static boolean isPlayerReady(Activity owner) {
        ClipLayout ui = controller(owner);
        return ui != null && ui.resumed && ui.playerReady;
    }
    public static boolean isResumed(Activity owner) {
        ClipLayout ui = controller(owner);
        return ui != null && ui.resumed;
    }
    public static boolean mayStart(Activity owner) {
        ClipLayout ui = controller(owner);
        return ui != null && ui.resumed && ui.playerReady && ui.pending == null && !ui.restoring;
    }
    /** Delayed native preparation must not restart a paused or background editor. */
    public static void prepared(View surface, MediaPlayer player) {
        Context context = surface.getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        if (!(context instanceof VideoClipsActivity)) { player.start(); return; }
        ClipLayout ui = controller((Activity) context);
        if (ui == null) return;
        ui.playerReady = true;
        if (surface.getParent() instanceof AspectVideoFrame)
            ((AspectVideoFrame) surface.getParent()).setVideoSize(player.getVideoWidth(), player.getVideoHeight());
        if (mayStart(ui.activity)) player.start();
        if (ui.resumed) {
            ui.activity.materialUiResumeTimer();
            ui.activity.updatePlayPosition();
            ui.transport.removeCallbacks(ui);
            ui.transport.post(ui);
        }
    }
    private Bundle snapshot() {
        if (pending != null) return withExport(pending);
        if (!resumed && suspended != null) return withExport(suspended);
        ClipContainer range = activity.getClipContainer();
        VideoPlayer player = activity.getVideoPlayer();
        if (!rangeReady || !playerReady || range == null || range.getFrameWidth() <= 0 || player == null) return null;
        try {
            Bundle state = new Bundle();
            float left = leftHandle.getX() / range.getFrameWidth();
            float right = (rightHandle.getX() - leftHandle.getWidth()) / range.getFrameWidth();
            state.putFloat("left", left);
            state.putFloat("right", right);
            state.putFloat("scroll", (range.getStartMillSec() - left * range.getMillSecInFrame())
                    / Math.max(1, range.getMediaDutaion()));
            state.putLong("first", activity.getStartMillSec());
            state.putLong("last", activity.getEndMillSec());
            state.putLong("position", player.getPlayerCurrentPosition());
            state.putBoolean("playing", player.isPlaying());
            return withExport(state);
        } catch (IllegalStateException notPrepared) { return withExport(suspended); }
    }

    public static void apply(Activity owner) {
        if (!(owner instanceof VideoClipsActivity)) return;
        int id = owner.getResources().getIdentifier("material_ui_clip_play", "id", owner.getPackageName());
        View button = owner.findViewById(id);
        if (button == null || button.getTag() instanceof ClipLayout) return;
        new ClipLayout((VideoClipsActivity) owner, (ImageButton) button);
    }
    private ClipLayout(VideoClipsActivity owner, ImageButton button) {
        activity = owner;
        transport = button;
        transport.setBackgroundTintList(owner.getResources().getColorStateList(resource("material_ui_primary", "color")));
        transport.setImageTintList(owner.getResources().getColorStateList(resource("material_ui_on_primary", "color")));
        pending = restored.remove(owner);
        if (pending != null) {
            exportFirst = pending.getLong("exportFirst", -1);
            exportLast = pending.getLong("exportLast", -1);
        }
        start = view("material_ui_clip_start_value");
        end = view("material_ui_clip_end_value");
        duration = view("material_ui_clip_duration_value");
        position = view("material_ui_clip_position");
        timeline = owner.findViewById(resource("clipContainer", "id"));
        leftHandle = owner.findViewById(resource("frame_left", "id"));
        rightHandle = owner.findViewById(resource("frame_right", "id"));
        leftTouch = owner.findViewById(resource("material_ui_clip_left_touch", "id"));
        rightTouch = owner.findViewById(resource("material_ui_clip_right_touch", "id"));
        middleTouch = owner.findViewById(resource("material_ui_clip_middle_touch", "id"));
        leftTouch.setOnTouchListener(this);
        rightTouch.setOnTouchListener(this);
        middleTouch.setOnTouchListener(this);
        ClipTimeline.bind((android.view.ViewGroup) timeline, leftHandle, rightHandle);
        TextView title = view("title_content");
        title.setText(resource("material_ui_clip_title", "string"));
        transport.setTag(this);
        transport.setOnClickListener(this);
        transport.addOnAttachStateChangeListener(this);
        if (transport.isAttachedToWindow()) onViewAttachedToWindow(transport);
    }
    public static boolean isPlaying(Activity owner) {
        if (!(owner instanceof VideoClipsActivity)) return false;
        if (!isPlayerReady(owner)) return false;
        try {
            VideoPlayer player = ((VideoClipsActivity) owner).getVideoPlayer();
            return player != null && player.isPlaying();
        } catch (IllegalStateException notPrepared) { return false; }
    }
    private int resource(String name, String type) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }
    private TextView view(String name) { return activity.findViewById(resource(name, "id")); }
    private static String time(float milliseconds) {
        long tenths = Math.max(0, Math.round(milliseconds / 100));
        return String.format(Locale.getDefault(), "%02d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10);
    }
    private static void text(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }
    @Override public void run() {
        if (!attached || !resumed || activity.isFinishing()) return;
        try {
        if (transport.getWindowVisibility() == View.VISIBLE) {
            updateHandleTargets();
            ClipContainer range = activity.getClipContainer();
            VideoPlayer player = playerReady ? activity.getVideoPlayer() : null;
            if (pending != null && rangeReady && range != null && range.getFrameWidth() > 0
                    && player != null && player.getDuration() > 0) {
                Bundle state = pending;
                restoring = true;
                try {
                    range.materialUiRestoreRange(state.getFloat("left"), state.getFloat("right"), state.getFloat("scroll"));
                    // Export uses exact native boundaries, independent of pixel rounding.
                    activity.setStartMillSec(state.getLong("first"));
                    activity.setEndMillSec(state.getLong("last"));
                    activity.updatePlayPosition();
                    player.seekToPosition(state.getLong("position"));
                    if (state.getBoolean("playing")) player.startPlayer();
                    else if (player.isPlaying()) player.pausePlayer();
                    pending = null;
                    suspended = null;
                    activity.materialUiUpdateExport();
                } finally { restoring = false; }
            }
            if (range != null) {
                float first = activity.getStartMillSec();
                float last = activity.getEndMillSec();
                if (last <= first) last = Math.max(first, range.getMediaDutaion());
                text(start, time(first));
                text(end, time(last));
                text(duration, time(last - first));
                transport.setEnabled(player != null && player.getDuration() > 0);
                if (player != null && player.getDuration() > 0) text(position, time(player.getPlayerCurrentPosition()) + " / " + time(player.getDuration()));
            }
            boolean playing = player != null && player.isPlaying();
            if (wasPlaying == null || wasPlaying != playing) {
                transport.setImageResource(resource(playing ? "material_ui_ic_media_pause" : "material_ui_ic_media_play", "drawable"));
                transport.setContentDescription(activity.getString(resource(playing ? "material_ui_clip_pause" : "material_ui_clip_play", "string")));
                wasPlaying = playing;
            }
        }
        } catch (IllegalStateException notPrepared) {
            transport.setEnabled(false);
        }
        transport.postDelayed(this, 200);
    }
    @Override public void onClick(View view) {
        VideoPlayer player = activity.getVideoPlayer();
        if (!playerReady || player == null) return;
        if (player.isPlaying()) player.pausePlayer();
        else {
            ClipContainer range = activity.getClipContainer();
            if (range != null && (player.getPlayerCurrentPosition() < activity.getStartMillSec()
                    || player.getPlayerCurrentPosition() >= activity.getEndMillSec()))
                player.seekToPosition(activity.getStartMillSec());
            player.startPlayer();
        }
        activity.updatePlayPosition();
        transport.removeCallbacks(this);
        run();
    }
    private void updateHandleTargets() {
        positionTarget(leftHandle, leftTouch);
        positionTarget(rightHandle, rightTouch);
        float left = leftHandle.getX() + leftHandle.getWidth();
        float width = Math.max(0, rightHandle.getX() - left);
        android.view.ViewGroup.LayoutParams params = middleTouch.getLayoutParams();
        if (params.width != Math.round(width)) {
            params.width = Math.round(width);
            middleTouch.setLayoutParams(params);
        }
        middleTouch.setX(left);
    }
    private void positionTarget(View handle, View target) {
        if (target.getWidth() == 0 || timeline.getWidth() == 0) return;
        float x = handle.getX() - (target.getWidth() - handle.getWidth()) / 2f;
        x = Math.max(0, Math.min(timeline.getWidth() - target.getWidth(), x));
        if (Math.abs(target.getX() - x) > 0.5f) target.setX(x);
    }
    @Override public boolean onTouch(View target, MotionEvent event) {
        if (target == middleTouch) return moveSelection(event);
        View handle = target == leftTouch ? leftHandle : rightHandle;
        MotionEvent forwarded = MotionEvent.obtain(event);
        forwarded.offsetLocation(target.getX() - handle.getX(), target.getY() - handle.getY());
        handle.dispatchTouchEvent(forwarded);
        forwarded.recycle();
        updateHandleTargets();
        return true;
    }
    private boolean moveSelection(MotionEvent event) {
        ClipContainer range = activity.getClipContainer();
        VideoPlayer player = activity.getVideoPlayer();
        if (!rangeReady || !playerReady || range == null || player == null
                || range.getFrameWidth() <= 0 || range.getMillSecInFrame() <= 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getRawX();
                touchY = event.getRawY();
                dragLeft = leftHandle.getX() / range.getFrameWidth();
                dragWidth = (rightHandle.getX() - leftHandle.getX() - leftHandle.getWidth())
                        / range.getFrameWidth();
                dragFirst = activity.getStartMillSec();
                dragDuration = activity.getEndMillSec() - dragFirst;
                dragWasPlaying = player.isPlaying();
                moving = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - touchX;
                if (!moving) {
                    int slop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
                    if (Math.abs(dx) <= slop || Math.abs(dx) <= Math.abs(event.getRawY() - touchY)) return true;
                    moving = true;
                    timeline.getParent().requestDisallowInterceptTouchEvent(true);
                }
                long first = Math.max(0, Math.min(range.getMediaDutaion() - dragDuration,
                        dragFirst + Math.round(dx * range.getMillSecInFrame() / range.getFrameWidth())));
                float left = Math.max(0, Math.min(1 - dragWidth,
                        dragLeft + (first - dragFirst) / (float) range.getMillSecInFrame()));
                float scroll = (first - left * range.getMillSecInFrame()) / Math.max(1, range.getMediaDutaion());
                range.materialUiSetRange(left, left + dragWidth, scroll, false);
                // Keep the exported duration exact despite integer thumbnail scroll offsets.
                range.setStartMillSec(first);
                range.setEndMillSec(first + dragDuration);
                activity.setStartMillSec(first);
                activity.setEndMillSec(first + dragDuration);
                selectionChanged(activity);
                player.seekToPosition(first);
                activity.updatePlayPosition();
                updateHandleTargets();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (moving) {
                    // The native callback also updates its playback-range controller.
                    activity.onSelectionChang(range.getItemCount(), activity.getStartMillSec(),
                            activity.getEndMillSec(), true);
                    if (!dragWasPlaying || event.getActionMasked() == MotionEvent.ACTION_CANCEL) player.pausePlayer();
                    activity.updatePlayPosition();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    player.pausePlayer();
                    long at = dragFirst + Math.round(Math.max(0, Math.min(1,
                            event.getX() / Math.max(1, middleTouch.getWidth()))) * dragDuration);
                    player.seekToPosition(Math.min(activity.getEndMillSec() - 1, at));
                    activity.updatePlayPosition();
                    middleTouch.performClick();
                }
                timeline.getParent().requestDisallowInterceptTouchEvent(false);
                moving = false;
                return true;
            default: return true;
        }
    }
    @Override public void onViewAttachedToWindow(View view) {
        attached = true;
        transport.removeCallbacks(this);
        transport.post(this);
    }
    @Override public void onViewDetachedFromWindow(View view) {
        attached = false;
        transport.removeCallbacks(this);
    }
}
