package local.materialui;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.button.MaterialButton;

/** Retains the selected state from the recorder's existing OEM button callbacks. */
public final class SourceChoiceButton extends MaterialButton {
    public SourceChoiceButton(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override public void setBackgroundResource(int resource) {
        super.setBackgroundResource(resource);
        if (resource != 0) {
            String name = getResources().getResourceEntryName(resource);
            setSelected(name.contains("_selected") && !name.contains("_unselected"));
            requestLayout();
        }
    }
}
