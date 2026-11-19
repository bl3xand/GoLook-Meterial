package local.materialui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Feedback follows the native camera request and player callbacks, with a bounded failure path. */
public final class PreviewBusy implements Runnable, View.OnLayoutChangeListener {
    private static final WeakHashMap<Activity, PreviewBusy> pending = new WeakHashMap<>();
    private static Toast lastError;
    private static int nextToken;
    private final int token = ++nextToken;
    private final Activity activity;
    private final View overlay;
    private final View video;
    private final ViewGroup content;
    private final View legacyStatus;
    private final float legacyAlpha;
    private final Map<View, Boolean> controls = new LinkedHashMap<>();
    private final Map<View, Float> opacity = new LinkedHashMap<>();
    private boolean awaitingPlayer;

    private PreviewBusy(Activity activity, View video, ViewGroup content) {
        this.activity = activity;
        this.video = video;
        this.content = content;
        legacyStatus = activity.findViewById(id("layoutEmrRecording"));
        legacyAlpha = legacyStatus == null ? 1 : legacyStatus.getAlpha();
        if (legacyStatus != null) legacyStatus.setAlpha(0);
        int layout = activity.getResources().getIdentifier("material_ui_camera_busy", "layout", activity.getPackageName());
        overlay = LayoutInflater.from(activity).inflate(layout, content, false);
        content.addView(overlay, new FrameLayout.LayoutParams(1, 1));
        video.addOnLayoutChangeListener(this);
        position();
        for (String name : new String[]{"imgCameraToggle", "imgCameraToggleLand", "ivCommand",
                "ivBrowseFile", "ivSettings", "lyBrowseFile", "lySettings"}) {
            View view = activity.findViewById(id(name));
            if (view != null) {
                controls.put(view, view.isEnabled());
                opacity.put(view, view.getAlpha());
                view.setEnabled(false);
            }
        }
        for (View view : controls.keySet()) {
            android.view.ViewParent parent = view.getParent();
            while (parent instanceof View && !controls.containsKey(parent)) parent = parent.getParent();
            if (!(parent instanceof View)) view.setAlpha(opacity.get(view) * 0.38f);
        }
        overlay.postDelayed(this, 12000);
    }
    private void position() {
        int[] origin = new int[2], location = new int[2];
        content.getLocationInWindow(origin);
        video.getLocationInWindow(location);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlay.getLayoutParams();
        params.width = video.getWidth();
        params.height = video.getHeight();
        params.leftMargin = location[0] - origin[0];
        params.topMargin = location[1] - origin[1];
        overlay.setLayoutParams(params);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0xB3000000);
        background.setCornerRadius(activity.getResources().getConfiguration().orientation == 1
                ? 24 * activity.getResources().getDisplayMetrics().density : 0);
        overlay.setBackground(background);
        overlay.setClipToOutline(true);
    }
    @Override public void onLayoutChange(View view, int l, int t, int r, int b,
            int oldL, int oldT, int oldR, int oldB) { position(); }
    private int id(String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }
    public static boolean begin(Activity activity) {
        if (pending.containsKey(activity) || activity.isFinishing() || activity.isDestroyed()) return false;
        clearError();
        int id = activity.getResources().getIdentifier("video_view_layout", "id", activity.getPackageName());
        View video = activity.findViewById(id);
        View content = activity.findViewById(android.R.id.content);
        if (video == null || !(content instanceof FrameLayout)) return false;
        pending.put(activity, new PreviewBusy(activity, video, (ViewGroup) content));
        return true;
    }
    public static int currentToken(Activity activity) {
        PreviewBusy state = pending.get(activity);
        return state == null ? -1 : state.token;
    }
    public static boolean isCurrent(Activity activity, int token) {
        return token >= 0 && currentToken(activity) == token;
    }
    public static void commandFailed(final Activity activity, final int token) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { if (isCurrent(activity, token)) failed(activity); }
        });
    }
    public static void ready(final Activity activity) { finish(activity, false); }
    public static void playerStarting(Activity activity) {
        PreviewBusy state = pending.get(activity);
        if (state != null) state.awaitingPlayer = true;
    }
    public static void playerReady(Activity activity) {
        PreviewBusy state = pending.get(activity);
        if (state != null && state.awaitingPlayer) ready(activity);
    }
    public static void playerFailed(Activity activity) {
        PreviewBusy state = pending.get(activity);
        if (state != null && state.awaitingPlayer) failed(activity);
    }
    public static void failed(final Activity activity) { finish(activity, true); }
    public static void cancel(final Activity activity) { clearError(); finish(activity, false); }
    private static void clearError() {
        if (lastError != null) { lastError.cancel(); lastError = null; }
    }
    private static void finish(final Activity activity, final boolean failed) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                PreviewBusy state = pending.remove(activity);
                if (state == null) return;
                state.overlay.removeCallbacks(state);
                state.video.removeOnLayoutChangeListener(state);
                if (state.legacyStatus != null) {
                    state.legacyStatus.setVisibility(View.INVISIBLE);
                    state.legacyStatus.setAlpha(state.legacyAlpha);
                }
                if (state.overlay.getParent() instanceof ViewGroup)
                    ((ViewGroup) state.overlay.getParent()).removeView(state.overlay);
                for (Map.Entry<View, Boolean> entry : state.controls.entrySet()) {
                    entry.getKey().setEnabled(entry.getValue());
                    entry.getKey().setAlpha(state.opacity.get(entry.getKey()));
                }
                if (failed && !activity.isFinishing() && !activity.isDestroyed()) {
                    int message = activity.getResources().getIdentifier("material_ui_camera_switch_failed", "string", activity.getPackageName());
                    clearError();
                    lastError = Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_SHORT);
                    lastError.show();
                }
            }
        });
    }
    @Override public void run() { failed(activity); }
}
