package local.materialui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/** Keep the native empty and last-recorder states in one scrolling card column. */
public final class HomeCards {
    private HomeCards() {}

    private static int id(Activity a, String type, String name) {
        return a.getResources().getIdentifier(name, type, a.getPackageName());
    }

    public static void refresh(final Activity a) {
        View card = a.findViewById(id(a, "id", "layout_dev_not_empty"));
        if (card == null || !(card.getParent() instanceof LinearLayout)) return;
        LinearLayout stack = (LinearLayout) card.getParent();
        View add = a.getWindow().getDecorView().findViewWithTag("material-ui-add-saved");
        if (add == null) {
            add = a.getLayoutInflater().inflate(id(a, "layout", "material_ui_home_action"), stack, false);
            add.setTag("material-ui-add-saved");
            ((TextView) add).setText(ScanConnection.text(a, "changhong_add_device"));
            add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(a.getColor(
                    id(a, "color", "material_ui_surface_container_high"))));
            ((TextView) add).setTextColor(a.getColor(id(a, "color", "material_ui_on_surface")));
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    a.startActivity(new Intent().setClassName(a,
                            a.getPackageName() + ".activitys.devmanager.DeviceScanActivity"));
                }
            });
            int inset = Math.round(24 * a.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(inset, Math.round(inset * 2f / 3), inset, 0);
            stack.addView(add, params);
        }
        boolean landscape = a.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        View settings = a.findViewById(id(a, "id", "ibsetting"));
        if (landscape && add.getParent() == stack && settings != null
                && settings.getParent() instanceof RelativeLayout) {
            stack.removeView(add);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(-2,
                    Math.round(48 * a.getResources().getDisplayMetrics().density));
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            params.setMarginEnd(Math.round(72 * a.getResources().getDisplayMetrics().density));
            ((ViewGroup) settings.getParent()).addView(add, params);
        }
        add.setVisibility(card.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }
}
