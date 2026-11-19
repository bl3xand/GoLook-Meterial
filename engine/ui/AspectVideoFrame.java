package local.materialui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** Reserves a stable video viewport independently of the media's loading state. */
public final class AspectVideoFrame extends FrameLayout {
    private float surfaceAspect;

    /** Native SurfaceView has no built-in fit mode; use the prepared media dimensions. */
    public void setVideoSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        surfaceAspect = (float) width / height;
        requestLayout();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (surfaceAspect <= 0) return;
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        int videoWidth = Math.min(width, Math.round(height * surfaceAspect));
        int videoHeight = Math.min(height, Math.round(videoWidth / surfaceAspect));
        int x = getPaddingLeft() + (width - videoWidth) / 2;
        int y = getPaddingTop() + (height - videoHeight) / 2;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            android.content.Context context = getContext();
            while (context instanceof android.content.ContextWrapper && !(context instanceof android.app.Activity))
                context = ((android.content.ContextWrapper) context).getBaseContext();
            if (context instanceof android.app.Activity) {
                android.util.DisplayMetrics display = new android.util.DisplayMetrics();
                ((android.app.Activity) context).getWindowManager().getDefaultDisplay().getRealMetrics(display);
                int[] position = new int[2]; getLocationOnScreen(position);
                int centered = Math.round(display.widthPixels / 2f - position[0] - videoWidth / 2f);
                x = Math.max(getPaddingLeft(), Math.min(centered,
                        getWidth() - getPaddingRight() - videoWidth));
            }
        }
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View child = getChildAt(i);
            if (child instanceof android.view.SurfaceView && child.getVisibility() != GONE)
                child.layout(x, y, x + videoWidth, y + videoHeight);
        }
    }
    public AspectVideoFrame(Context context) { super(context); }
    public AspectVideoFrame(Context context, AttributeSet attributes) { super(context, attributes); }
    public AspectVideoFrame(Context context, AttributeSet attributes, int style) { super(context, attributes, style); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (getLayoutParams() != null && getLayoutParams().height == -1
                && MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthSpec);
        int height = Math.round(width * 9f / 16f);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            height = Math.min(height, Math.round(180 * getResources().getDisplayMetrics().density));
        }
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
