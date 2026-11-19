package local.materialui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Keeps legacy icon-state callbacks separate from the button's touch background. */
public final class IconButton extends ImageView {
    private boolean initialized;

    public IconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialized = true;
        setClickable(true);
        setFocusable(true);
        setScaleType(ScaleType.CENTER_INSIDE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            setDefaultFocusHighlightEnabled(false);
            setTooltipText(getContentDescription());
        }
    }

    @Override public void setBackgroundResource(int resource) {
        if (initialized) setImageResource(resource);
        else super.setBackgroundResource(resource);
    }
}
