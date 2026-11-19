package local.materialui;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

/** Places the camera and its overlay anchors on the same content grid. */
public final class PreviewLayout implements Runnable, View.OnLayoutChangeListener {
    private final Activity activity;
    private boolean watching;
    private final FullscreenControls fullscreen;
    private View fullscreenHeader;
    private int headerVisibility;
    private View watchedVideo;

    private PreviewLayout(Activity activity) {
        this.activity = activity;
        this.fullscreen = new FullscreenControls(activity);
    }

    public static void apply(Activity activity) {
        int infoId = activity.getResources().getIdentifier("layoutInfoBar", "id", activity.getPackageName());
        int tagId = activity.getResources().getIdentifier("material_ui_preview_layout_observer", "id", activity.getPackageName());
        View info = activity.findViewById(infoId);
        PreviewLayout adapter = null;
        if (info != null && tagId != 0 && info.getTag(tagId) instanceof PreviewLayout) {
            adapter = (PreviewLayout) info.getTag(tagId);
        }
        if (adapter == null) {
            adapter = new PreviewLayout(activity);
            if (info != null && tagId != 0) info.setTag(tagId, adapter);
        }
        // Finish the native size update in the same UI turn, before its traversal.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) adapter.run();
        else activity.getWindow().getDecorView().post(adapter);
    }

    private View find(String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void cameraAction() {
        View camera = find("imgCameraToggle");
        if (camera == null) return;
        int label = activity.getResources().getIdentifier(
                "material_ui_camera_switch", "string", activity.getPackageName());
        camera.setContentDescription(activity.getString(label));
        if (Build.VERSION.SDK_INT >= 26) camera.setTooltipText(camera.getContentDescription());
        ViewGroup container = camera.getParent() instanceof ViewGroup
                ? (ViewGroup) camera.getParent() : null;
        if (container == null) return;
        if (!(container instanceof RelativeLayout)
                && container.getParent() instanceof RelativeLayout) {
            ViewGroup toolbar = (ViewGroup) container.getParent();
            container.removeView(camera);
            toolbar.addView(camera, new RelativeLayout.LayoutParams(dp(48), dp(48)));
            container = toolbar;
        }
        if (!(container instanceof RelativeLayout)) return;
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) camera.getLayoutParams();
        for (int rule : new int[]{RelativeLayout.CENTER_IN_PARENT, RelativeLayout.CENTER_HORIZONTAL,
                RelativeLayout.CENTER_VERTICAL, RelativeLayout.ALIGN_PARENT_START,
                RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.ALIGN_PARENT_RIGHT,
                RelativeLayout.BELOW, RelativeLayout.ABOVE}) params.removeRule(rule);
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.width = params.height = dp(48);
        params.setMargins(0, dp(8), 0, 0);
        params.setMarginStart(0);
        params.setMarginEnd(dp(12));
        camera.setLayoutParams(params);
        int ripple = activity.getResources().getIdentifier(
                "material_ui_icon_ripple", "drawable", activity.getPackageName());
        camera.setBackground(activity.getDrawable(ripple));
        int tint = activity.getResources().getIdentifier(
                "material_ui_on_surface_variant", "color", activity.getPackageName());
        tintAction(camera, activity.getColor(tint));
    }

    private void tintAction(View view, int color) {
        if (view instanceof android.widget.ImageView)
            ((android.widget.ImageView) view).setImageTintList(
                    android.content.res.ColorStateList.valueOf(color));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintAction(group.getChildAt(i), color);
        }
    }

    private void actionColumn(String name) {
        final View action = find(name);
        if (action == null || !(action.getParent() instanceof android.widget.LinearLayout)) return;
        final ViewGroup column = (ViewGroup) action.getParent();
        int columnId = activity.getResources().getIdentifier(
                "ivSettings".equals(name) ? "lySettings" : "lyBrowseFile", "id", activity.getPackageName());
        if (((android.widget.LinearLayout) column).getOrientation()
                != android.widget.LinearLayout.VERTICAL || column.getId() != columnId) return;
        int key = activity.getResources().getIdentifier(
                "material_ui_preview_layout_observer", "id", activity.getPackageName());
        if (key == 0 || column.getTag(key) == action) return;
        column.setTag(key, action);
        column.setContentDescription(action.getContentDescription());
        column.setFocusable(true);
        column.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        column.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (action.isEnabled() && action.getVisibility() == View.VISIBLE) action.performClick();
            }
        });
        column.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.setEnabled(action.isEnabled());
            }
        });
        for (int i = 0; i < column.getChildCount(); i++)
            column.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (view == activity.getWindow().getDecorView()) view.removeOnLayoutChangeListener(this);
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) view.post(this);
    }

    @Override public void run() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View video = find("video_view_layout");
        if (video == null || !(video.getParent() instanceof View)) return;
        View parent = (View) video.getParent();
        if (watchedVideo != video) {
            if (watchedVideo != null) watchedVideo.removeOnLayoutChangeListener(this);
            watchedVideo = video;
            video.addOnLayoutChangeListener(this);
        }
        boolean portrait = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        // The fullscreen video includes the cutout/navigation inset background.
        int backgroundId = activity.getResources().getIdentifier("app_background", "color", activity.getPackageName());
        int backgroundColor = portrait ? activity.getColor(backgroundId) : Color.BLACK;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0)
            ((ViewGroup) content).getChildAt(0).setBackgroundColor(backgroundColor);
        View title = find("title_content");
        if (title != null && title.getParent() instanceof View && content instanceof ViewGroup) {
            View header = (View) title.getParent();
            ViewGroup frame = (ViewGroup) content;
            if (frame.getChildCount() > 0 && header != frame.getChildAt(0)) {
                if (fullscreenHeader != header) {
                    fullscreenHeader = header;
                    headerVisibility = header.getVisibility();
                }
                header.setVisibility(portrait ? headerVisibility : View.GONE);
            }
        }
        activity.getWindow().setStatusBarColor(backgroundColor);
        activity.getWindow().setNavigationBarColor(backgroundColor);
        activity.getWindow().setBackgroundDrawableResource(portrait ? backgroundId : android.R.color.black);
        if (Build.VERSION.SDK_INT >= 29) activity.getWindow().setNavigationBarContrastEnforced(false);
        View decor = activity.getWindow().getDecorView();
        int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) lightBars |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int flags = decor.getSystemUiVisibility() & ~lightBars;
        if (portrait && (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES) flags |= lightBars;
        decor.setSystemUiVisibility(flags);
        View[] anchors = {video, find("video_view_layout_frag"), find("video_view_layout_bottom")};
        if (!portrait) {
            for (View anchor : anchors) {
                if (anchor == null || !(anchor.getLayoutParams() instanceof RelativeLayout.LayoutParams)) continue;
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) anchor.getLayoutParams();
                params.setMargins(0, 0, 0, 0);
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.addRule(RelativeLayout.CENTER_IN_PARENT);
                anchor.setLayoutParams(params);
                anchor.setClipToOutline(false);
            }
            fullscreen.apply();
            video.setBackgroundColor(Color.BLACK);
            centerFullscreen(video, parent, anchors);
            return;
        }
        for (View anchor : anchors) if (anchor != null) anchor.setTranslationX(0);
        fullscreen.restore();
        cameraAction();
        actionColumn("ivBrowseFile");
        actionColumn("ivSettings");
        View capture = find("ivCommand");
        if (capture instanceof android.widget.ImageView) {
            int primary = activity.getResources().getIdentifier("material_ui_primary", "color", activity.getPackageName());
            int foreground = activity.getResources().getIdentifier("material_ui_on_primary", "color", activity.getPackageName());
            capture.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(primary)));
            ((android.widget.ImageView) capture).setImageTintList(
                    android.content.res.ColorStateList.valueOf(activity.getColor(foreground)));
        }

        View info = find("layoutInfoBar");
        if (info == null) return;
        if (!watching) {
            info.addOnLayoutChangeListener(this);
            watching = true;
        }
        for (String name : new String[]{"sd_info_layout", "emmc_info_layout"}) {
            View storage = find(name);
            if (storage != null && storage.getVisibility() == View.INVISIBLE) storage.setVisibility(View.GONE);
        }
        if (parent.getWidth() == 0) {
            activity.getWindow().getDecorView().addOnLayoutChangeListener(this);
            return;
        }
        ViewGroup.LayoutParams old = video.getLayoutParams();
        int originalWidth = old.width > 0 ? old.width : parent.getWidth();
        float ratio = old.height > 0 ? (float) old.height / originalWidth : 9f / 16f;
        int width = parent.getWidth() - dp(48);
        int height = Math.round(width * ratio);
        int[] infoPosition = new int[2];
        int[] parentPosition = new int[2];
        info.getLocationInWindow(infoPosition);
        parent.getLocationInWindow(parentPosition);
        int top = infoPosition[1] + info.getHeight() + dp(8) - parentPosition[1];
        int available = parent.getHeight() - top - dp(160);
        if (available > dp(160) && height > available) {
            height = available;
            width = Math.round(height / ratio);
        }
        int[] videoPosition = {parentPosition[0] + (parent.getWidth() - width) / 2,
                               parentPosition[1] + top};
        for (View anchor : anchors) {
            if (anchor == null || !(anchor.getParent() instanceof View)
                    || !(anchor.getLayoutParams() instanceof RelativeLayout.LayoutParams)) continue;
            View anchorParent = (View) anchor.getParent();
            int[] origin = new int[2];
            anchorParent.getLocationInWindow(origin);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) anchor.getLayoutParams();
            params.removeRule(RelativeLayout.CENTER_IN_PARENT);
            params.removeRule(RelativeLayout.CENTER_VERTICAL);
            params.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            params.removeRule(RelativeLayout.BELOW);
            params.removeRule(RelativeLayout.ABOVE);
            params.removeRule(RelativeLayout.ALIGN_PARENT_START);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.width = width;
            params.height = height;
            params.setMargins(videoPosition[0] - origin[0], videoPosition[1] - origin[1], 0, 0);
            anchor.setLayoutParams(params);
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setCornerRadius(dp(24));
        video.setBackground(background);
        video.setClipToOutline(true);
        UiBridge.updateAdasBounds(activity, width, height);
    }

    private void centerFullscreen(View video, View parent, View[] anchors) {
        if (video.getWidth() == 0 || parent.getWidth() == 0) return;
        android.util.DisplayMetrics display = new android.util.DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(display);
        int[] position = new int[2]; parent.getLocationOnScreen(position);
        float desired = display.widthPixels / 2f - position[0]
                - (video.getLeft() + video.getWidth() / 2f);
        // Use available letterboxing; never move the video into a clipped edge.
        float offset = Math.max(-video.getLeft(), Math.min(desired,
                parent.getWidth() - video.getRight()));
        for (View anchor : anchors) if (anchor != null) anchor.setTranslationX(offset);
    }
}
