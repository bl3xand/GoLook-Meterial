package local.materialui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.TextView;

/** One selected SSID per native adapter; passwords and connection listeners remain native. */
public final class NetworkSelection {
    private final BaseAdapter adapter;
    private String selected;

    public NetworkSelection(BaseAdapter adapter) { this.adapter = adapter; }

    public void bind(final View row, String capabilities) {
        final TextView title = (TextView) find(row, "tvWifiName");
        final View fields = find(row, "material_ui_device_entry_fields");
        final View connect = find(row, "btnConnect");
        final View header = find(row, "material_ui_network_header");
        final android.widget.ImageView arrow = (android.widget.ImageView) find(row, "material_ui_network_chevron");
        if (title == null || fields == null || connect == null || header == null || arrow == null) return;
        final String ssid = title.getText().toString();
        boolean expanded = ssid.equals(selected);
        boolean active = ssid.equals(ScanConnection.currentSsid(row.getContext()));
        accent(row, title, active);
        boolean secured = ScanConnection.requiresPassword(capabilities);
        fields.setVisibility(expanded && secured && !active ? View.VISIBLE : View.GONE);
        if (!secured) {
            android.widget.EditText password = (android.widget.EditText) find(row, "etPasswd");
            if (password != null && password.length() != 0) password.setText("");
        }
        TextView status = (TextView) find(row, "rssi");
        if (status != null) {
            status.setTextColor(color(row, active ? "material_ui_on_primary_container" : "material_ui_on_surface_variant"));
            status.setText(active ? ScanConnection.text(row.getContext(), "material_ui_wifi_active") : "");
            status.setVisibility(active ? View.VISIBLE : View.GONE);
        }
        ((TextView) connect).setText(ScanConnection.text(row.getContext(), active
                ? "material_ui_check_recorder" : "btn_connect"));
        connect.setVisibility(expanded ? View.VISIBLE : View.GONE);
        title.setCompoundDrawablesRelative(null, null, null, null);
        title.setMinimumHeight(0);
        title.setClickable(false);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (status != null) status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        arrow.setImageDrawable(new Chevron(dp(row, 24), title.getCurrentTextColor(), expanded));
        header.setContentDescription(ssid + (active ? ", " + status.getText() : ""));
        header.setFocusable(true);
        header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        int ripple = row.getResources().getIdentifier("material_ui_menu_item_bg", "drawable", row.getContext().getPackageName());
        if (!header.isClickable()) header.setBackgroundResource(ripple);
        header.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View clicked) {
                View focused = row.getRootView().findFocus();
                if (focused != null) {
                    InputMethodManager keyboard = (InputMethodManager) row.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                    focused.clearFocus();
                }
                selected = ssid.equals(selected) ? null : ssid;
                adapter.notifyDataSetChanged();
            }
        });
        header.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.addAction(ssid.equals(selected)
                        ? android.view.accessibility.AccessibilityNodeInfo.ACTION_COLLAPSE
                        : android.view.accessibility.AccessibilityNodeInfo.ACTION_EXPAND);
            }
            @Override public boolean performAccessibilityAction(View host, int action, android.os.Bundle arguments) {
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_EXPAND
                        || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_COLLAPSE)
                    return host.performClick();
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
    }

    private static int color(View row, String name) {
        return row.getContext().getColor(row.getResources().getIdentifier(name, "color", row.getContext().getPackageName()));
    }

    private static void accent(View row, TextView title, boolean active) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color(row, active ? "material_ui_primary_container" : "material_ui_surface_container_low"));
        background.setCornerRadius(dp(row, 20));
        background.setStroke(dp(row, 1), color(row, active ? "material_ui_primary" : "material_ui_outline_variant"));
        row.setBackground(background);
        if (active) row.setAlpha(1f);
        title.setTextColor(color(row, active ? "material_ui_on_primary_container" : "material_ui_text_primary"));
    }

    private static View find(View row, String name) {
        return row.findViewById(row.getResources().getIdentifier(name, "id", row.getContext().getPackageName()));
    }
    private static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }

    private static final class Chevron extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private final boolean expanded;
        Chevron(int size, int color, boolean expanded) {
            this.size = size; this.expanded = expanded;
            paint.setColor(color); paint.setStrokeWidth(size / 12f); paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            float x = getBounds().left, y = getBounds().top;
            float a = expanded ? .6f : .4f, b = expanded ? .4f : .6f;
            canvas.drawLine(x + size * .28f, y + size * a, x + size * .5f, y + size * b, paint);
            canvas.drawLine(x + size * .5f, y + size * b, x + size * .72f, y + size * a, paint);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
