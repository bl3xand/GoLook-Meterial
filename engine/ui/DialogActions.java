package local.materialui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps dialog labels intact when translations or larger fonts need more room. */
public class DialogActions extends LinearLayout {
    public DialogActions(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int count = 0;
        int required = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            count++;
            if (child instanceof TextView) {
                TextView label = (TextView) child;
                CharSequence text = label.getText();
                if (label.getTransformationMethod() != null)
                    text = label.getTransformationMethod().getTransformation(text, label);
                required = Math.max(required, (int) Math.ceil(label.getPaint().measureText(text.toString()))
                        + label.getCompoundPaddingLeft() + label.getCompoundPaddingRight());
            }
        }
        int gap = getDividerDrawable() == null ? 0 : getDividerDrawable().getIntrinsicWidth();
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight()
                - Math.max(0, count - 1) * gap;
        boolean stacked = count > 1 && required * count > available;
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            int width = stacked ? LayoutParams.MATCH_PARENT : 0;
            float weight = stacked ? 0 : 1;
            if (params.width != width || params.weight != weight) {
                params.width = width;
                params.weight = weight;
                child.setLayoutParams(params);
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
