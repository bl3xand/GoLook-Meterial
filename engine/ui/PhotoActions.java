package local.materialui;

import android.content.Context;
import android.util.AttributeSet;

/** Center compact landscape actions in separate halves of the media scrim. */
public final class PhotoActions extends DialogActions {
    public PhotoActions(Context context, AttributeSet attrs) { super(context, attrs); }
    private boolean landscape() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int limit = Math.round(560 * getResources().getDisplayMetrics().density);
        int width = landscape() ? MeasureSpec.getSize(widthSpec)
                : Math.min(limit, MeasureSpec.getSize(widthSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(widthSpec)), heightSpec);
        if (landscape() && getOrientation() == HORIZONTAL && getChildCount() == 2) {
            int gap = Math.round(24 * getResources().getDisplayMetrics().density);
            int available = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
            int buttonWidth = Math.min(limit / 2, Math.max(0, available / 2 - gap));
            for (int i = 0; i < 2; i++) {
                android.view.View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY));
            }
        }
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!landscape() || getOrientation() != HORIZONTAL || getChildCount() != 2) return;
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        for (int i = 0; i < 2; i++) {
            android.view.View child = getChildAt(i);
            int center = getPaddingLeft() + Math.round(available * (i == 0 ? 0.25f : 0.75f));
            int x = center - child.getMeasuredWidth() / 2;
            child.layout(x, child.getTop(), x + child.getMeasuredWidth(), child.getBottom());
        }
    }
}
