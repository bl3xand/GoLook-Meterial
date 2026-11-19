package local.materialui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/** Preserves the content inset when native callers hide the action row. */
public final class DialogSurface extends LinearLayout {
    private boolean messageSuppressed;
    public DialogSurface(Context context, AttributeSet attrs) { super(context, attrs); }

    private static boolean hasAction(View view) {
        if (view.getVisibility() != VISIBLE) return false;
        if (view instanceof Button && !(view instanceof android.widget.CompoundButton)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasAction(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        String pkg = getContext().getPackageName();
        android.widget.TextView title = findViewById(getResources().getIdentifier("tvDialogTitle", "id", pkg));
        android.widget.TextView message = findViewById(getResources().getIdentifier("tvDialogMessage", "id", pkg));
        if (title != null && message != null) {
            String first = title.getText().toString().replaceAll("[.\\s\\u2026]+$", "");
            String second = message.getText().toString().replaceAll("[.\\s\\u2026]+$", "");
            if (second.isEmpty() || (title.getVisibility() == VISIBLE
                    && !first.isEmpty() && first.equalsIgnoreCase(second))) {
                messageSuppressed = message.getVisibility() == VISIBLE || messageSuppressed;
                message.setVisibility(GONE);
            } else if (messageSuppressed) {
                message.setVisibility(VISIBLE); messageSuppressed = false;
            }
        }
        // Native progress callers use INVISIBLE; these optional slots must collapse.
        for (String name : new String[]{"tvDialogMessage", "pbDialogProgress", "download_progressbar"}) {
            View optional = findViewById(getResources().getIdentifier(name, "id", pkg));
            if (optional != null && optional.getVisibility() == INVISIBLE) optional.setVisibility(GONE);
        }
        int bottom = hasAction(this) ? 0
                : Math.round(24 * getResources().getDisplayMetrics().density);
        if (getPaddingBottom() != bottom) {
            setPaddingRelative(getPaddingStart(), getPaddingTop(), getPaddingEnd(), bottom);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
