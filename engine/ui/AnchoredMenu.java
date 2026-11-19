package local.materialui;

import android.view.View;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.widget.PopupWindow;

/** Aligns anchored menus inside the same screen gutter as the surrounding content. */
public final class AnchoredMenu {
    private AnchoredMenu() { }
    public static void show(PopupWindow popup, View anchor, int ignoredX, int ignoredY) {
        show(popup, anchor);
    }
    public static void show(PopupWindow popup, View anchor) {
        float density = anchor.getResources().getDisplayMetrics().density;
        int gutter = Math.round(24 * density);
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int screen = visible.width();
        View content = popup.getContentView();
        content.measure(View.MeasureSpec.makeMeasureSpec(screen - 2 * gutter, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = content.getMeasuredWidth();
        int height = content.getMeasuredHeight();
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setWidth(width);
        // An explicit measured height prevents PopupWindow from shrinking the last row.
        popup.setHeight(height);
        popup.setElevation(6 * density);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int right = Math.min(visible.right - gutter, location[0] + anchor.getWidth());
        int left = Math.max(visible.left + gutter, right - width);
        int gap = Math.round(8 * density);
        int below = location[1] + anchor.getHeight() + gap;
        int top = below;
        if (below + height > visible.bottom - gutter)
            top = location[1] - gap - height;
        top = Math.max(visible.top + gutter, Math.min(top, visible.bottom - gutter - height));
        popup.showAsDropDown(anchor, left - location[0], top - location[1] - anchor.getHeight());
    }
}
