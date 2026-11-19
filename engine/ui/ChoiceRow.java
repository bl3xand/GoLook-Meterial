package local.materialui;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Presents native camera/storage choices as a labeled single-choice selector. */
public final class ChoiceRow extends LinearLayout implements View.OnClickListener,
        DialogInterface.OnClickListener {
    private View presentation;
    private View selector;
    private TextView heading;
    private TextView value;
    private TextView copy;
    private Button copySource;
    private ArrayList<Button> options = new ArrayList<Button>();
    private ArrayList<Button> openOptions;

    public ChoiceRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBaselineAligned(false);
        setOrientation(VERTICAL);
        setShowDividers(SHOW_DIVIDER_NONE);
    }

    private int id(String name, String type) {
        return getResources().getIdentifier(name, type, getContext().getPackageName());
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        presentation = inflate(getContext(), id("material_ui_source_selector", "layout"), null);
        selector = presentation.findViewById(id("material_ui_source_trigger", "id"));
        heading = (TextView) presentation.findViewById(id("material_ui_source_label", "id"));
        value = (TextView) presentation.findViewById(id("material_ui_source_value", "id"));
        copy = (TextView) presentation.findViewById(id("material_ui_source_copy", "id"));
        heading.setText(id(getId() == id("frontAfterToggleLayout", "id")
                ? "material_ui_camera_source" : "material_ui_storage_source", "string"));
        selector.setOnClickListener(this);
        copy.setOnClickListener(this);
        addView(presentation, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void collect(View child) {
        if (child.getVisibility() == GONE) return;
        if (child instanceof Button) {
            if (child.getId() == id("btnCopyToSd", "id")) copySource = (Button) child;
            else options.add((Button) child);
        } else if (child instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) child;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
        }
    }

    private int selected() {
        for (int i = 0; i < options.size(); i++) if (options.get(i).isSelected()) return i;
        return 0;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (presentation == null) { super.onMeasure(widthSpec, heightSpec); return; }
        options.clear();
        copySource = null;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == presentation) continue;
            collect(child);
            child.setFocusable(false);
            if (child instanceof ViewGroup) ((ViewGroup) child).setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            child.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                          MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
        }
        selector.setVisibility(options.size() > 1 ? VISIBLE : GONE);
        if (!options.isEmpty()) {
            CharSequence text = options.get(selected()).getText();
            if (!text.toString().contentEquals(value.getText())) value.setText(text);
            selector.setContentDescription(heading.getText() + ": " + text);
        }
        copy.setVisibility(copySource == null ? GONE : VISIBLE);
        if (copySource != null && !copySource.getText().toString().contentEquals(copy.getText()))
            copy.setText(copySource.getText());
        int width = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        presentation.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                             MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), presentation.getMeasuredHeight()
                             + getPaddingTop() + getPaddingBottom());
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (presentation == null) return;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != presentation) child.layout(0, 0, 0, 0);
        }
        presentation.layout(getPaddingLeft(), getPaddingTop(),
                right - left - getPaddingRight(), bottom - top - getPaddingBottom());
    }

    @Override public void onClick(View clicked) {
        if (clicked == copy) {
            if (copySource != null) copySource.performClick();
            return;
        }
        openOptions = new ArrayList<Button>(options);
        CharSequence[] labels = new CharSequence[openOptions.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = openOptions.get(i).getText();
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle(heading.getText());
        builder.setSingleChoiceItems(labels, selected(), this);
        UiBridge.show(builder);
    }

    @Override public void onClick(DialogInterface dialog, int which) {
        if (openOptions != null && which >= 0 && which < openOptions.size())
            openOptions.get(which).performClick();
        dialog.dismiss();
        openOptions = null;
        requestLayout();
    }
}
