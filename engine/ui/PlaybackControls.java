package local.materialui;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

/** Fits the native floating playback panel to the visible part of its anchor. */
public final class PlaybackControls {
    private PlaybackControls() {}

    public static void layout(WindowManager.LayoutParams params, View anchor, View decor) {
        params.windowAnimations = anchor == null ? 0 : anchor.getResources().getIdentifier(
                "Animation.MaterialUi.MediaChrome", "style", anchor.getContext().getPackageName());
        if (anchor == null || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) return;
        int[] origin = new int[2];
        anchor.getLocationOnScreen(origin);
        Rect visible = new Rect(origin[0], origin[1], origin[0] + anchor.getWidth(),
                origin[1] + anchor.getHeight());
        for (ViewParent parent = anchor.getParent(); parent instanceof View;
                parent = parent.getParent()) {
            View view = (View) parent;
            if (view instanceof ViewGroup && !((ViewGroup) view).getClipChildren()) continue;
            view.getLocationOnScreen(origin);
            if (!visible.intersect(origin[0], origin[1], origin[0] + view.getWidth(),
                    origin[1] + view.getHeight())) return;
        }
        Rect window = new Rect();
        anchor.getWindowVisibleDisplayFrame(window);
        if (!window.isEmpty() && !visible.intersect(window)) return;
        android.util.DisplayMetrics metrics = anchor.getResources().getDisplayMetrics();
        boolean landscape = anchor.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int contentHeight = Math.round((landscape ? 136 : 168) * metrics.density
                + 20 * metrics.scaledDensity);
        params.width = visible.width();
        params.height = Math.min(visible.height(), contentHeight);
        params.x = visible.left;
        params.y = visible.bottom - params.height;
        decor.measure(View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY));
    }
}
