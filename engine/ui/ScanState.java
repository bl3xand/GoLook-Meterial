package local.materialui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

/** Presents actual scan prerequisites without replacing the native scan or adapter. */
public final class ScanState implements Runnable, View.OnAttachStateChangeListener,
        ViewTreeObserver.OnWindowFocusChangeListener {
    private final Activity activity;
    private final TextView hint;
    private final MaterialButton recovery;
    private final View results;
    private final GridView grid;
    private long availableSince = SystemClock.elapsedRealtime();
    private String observedSsid;
    private boolean granted;
    private boolean attached;
    private int state = -1;
    private String[] permissions;

    public static void permissionResult(Activity activity, String[] permissions, int[] results) {
        if (Build.VERSION.SDK_INT < 23 || permissions == null || results == null) return;
        SharedPreferences.Editor history = activity.getSharedPreferences(
                "material_ui_permission_history", Context.MODE_PRIVATE).edit();
        for (int index = 0; index < Math.min(permissions.length, results.length); index++) {
            String permission = permissions[index];
            history.putBoolean(permission, results[index] != PackageManager.PERMISSION_GRANTED
                    && !activity.shouldShowRequestPermissionRationale(permission));
        }
        history.apply();
        View hint = activity.findViewById(id(activity, "tvPermissionTips"));
        Object prompt = hint == null ? null : hint.getTag(id(activity, "tvPermissionTips"));
        if (prompt instanceof androidx.appcompat.app.AlertDialog) {
            ((androidx.appcompat.app.AlertDialog) prompt).dismiss();
        }
    }

    public static void showRequest(final Activity activity, final String[] permissions) {
        final View hint = activity.findViewById(id(activity, "tvPermissionTips"));
        final int key = id(activity, "tvPermissionTips");
        Object current = hint == null ? null : hint.getTag(key);
        if (current instanceof androidx.appcompat.app.AlertDialog
                && ((androidx.appcompat.app.AlertDialog) current).isShowing()) return;
        final boolean blocked = permanentlyDenied(activity, permissions);
        androidx.appcompat.app.AlertDialog.Builder dialog = new androidx.appcompat.app.AlertDialog.Builder(activity);
        dialog.setTitle(text(activity, "material_ui_permission_title"));
        dialog.setMessage(text(activity, permissionMessageKey(activity, blocked)));
        dialog.setNegativeButton(text(activity, "cancel"), null);
        dialog.setPositiveButton(text(activity, blocked ? "settings" : "material_ui_permission_retry"),
                new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface dialog, int which) {
                request(activity, permissions);
            }
        });
        final androidx.appcompat.app.AlertDialog shown = UiBridge.show(dialog);
        if (hint != null) {
            hint.setTag(key, shown);
            shown.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override public void onDismiss(android.content.DialogInterface dialog) {
                    if (hint.getTag(key) == shown) hint.setTag(key, null);
                }
            });
        }
    }

    private static String permissionMessageKey(Activity activity, boolean blocked) {
        if (Build.VERSION.SDK_INT >= 31
                && activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED)
            return "material_ui_permission_precise_body";
        return blocked ? "rationale_ask_again" : Build.VERSION.SDK_INT >= 33
                ? "material_ui_permission_scan_body" : "location_permission_des";
    }

    private static void request(Activity activity, String[] permissions) {
        if (permanentlyDenied(activity, permissions)) {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())));
        } else if (Build.VERSION.SDK_INT >= 23) {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            for (String permission : permissions) {
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
            }
            if (!missing.isEmpty()) activity.requestPermissions(missing.toArray(new String[0]), 1);
        }
    }

    public static void apply(Activity activity, boolean granted, String[] permissions) {
        if (Build.VERSION.SDK_INT >= 23) {
            SharedPreferences history = activity.getSharedPreferences(
                    "material_ui_permission_history", Context.MODE_PRIVATE);
            SharedPreferences.Editor recovered = null;
            for (String permission : permissions) {
                if (history.contains(permission)
                        && activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                    if (recovered == null) recovered = history.edit();
                    recovered.remove(permission);
                }
            }
            if (recovered != null) recovered.apply();
        }
        View candidate = activity.findViewById(id(activity, "tvPermissionTips"));
        if (!(candidate instanceof TextView) || !(candidate.getParent() instanceof LinearLayout)) return;
        TextView hint = (TextView) candidate;
        ScanState controller = hint.getTag() instanceof ScanState
                ? (ScanState) hint.getTag() : new ScanState(activity, hint);
        controller.permissions = permissions.clone();
        controller.granted = granted;
        hint.removeCallbacks(controller);
        hint.post(controller);
    }

    private ScanState(Activity activity, TextView hint) {
        this.activity = activity;
        this.hint = hint;
        results = activity.findViewById(id(activity, "srf_layout"));
        grid = activity.findViewById(id(activity, "gvDeviceList"));
        recovery = new MaterialButton(activity, null);
        UiMetrics.recoveryAction(recovery);
        recovery.setVisibility(View.GONE);
        recovery.setMinHeight(dp(56));
        recovery.setAllCaps(false);
        recovery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (state == 0) {
                    request(activity, permissions);
                } else {
                    activity.startActivity(new Intent(state == 2
                            ? Settings.ACTION_LOCATION_SOURCE_SETTINGS : Settings.ACTION_WIFI_SETTINGS));
                }
            }
        });
        LinearLayout parent = (LinearLayout) hint.getParent();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(24), 0, dp(24), dp(16));
        parent.addView(recovery, parent.indexOfChild(hint) + 1, params);
        View settings = activity.findViewById(id(activity, "layout_ibsetting"));
        if (settings != null) settings.setContentDescription(text("material_ui_system_wifi_action"));
        hint.setTag(this);
        hint.addOnAttachStateChangeListener(this);
        attached = hint.isAttachedToWindow();
        if (attached) hint.getViewTreeObserver().addOnWindowFocusChangeListener(this);
    }

    private static int id(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private String text(String name) {
        return text(activity, name);
    }

    private static String text(Activity activity, String name) {
        return activity.getString(activity.getResources().getIdentifier(name, "string", activity.getPackageName()));
    }

    private boolean locationEnabled() {
        LocationManager location = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (location == null || Build.VERSION.SDK_INT < 23) return true;
        if (Build.VERSION.SDK_INT >= 28) return location.isLocationEnabled();
        return location.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private boolean permanentlyDenied() {
        return permanentlyDenied(activity, permissions);
    }

    private static boolean permanentlyDenied(Activity activity, String[] permissions) {
        if (Build.VERSION.SDK_INT < 23) return false;
        SharedPreferences history = activity.getSharedPreferences(
                "material_ui_permission_history", Context.MODE_PRIVATE);
        for (String permission : permissions) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
                    && history.getBoolean(permission, false)) return true;
        }
        return false;
    }

    @Override public void run() {
        if (!attached || activity.isFinishing() || activity.isDestroyed()
                || hint.getWindowVisibility() != View.VISIBLE) return;
        if (hint.getWindowVisibility() == View.VISIBLE) {
            WifiManager wifi = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            boolean permissionsGranted = true;
            if (Build.VERSION.SDK_INT >= 23) for (String permission : permissions) {
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) permissionsGranted = false;
            }
            granted = permissionsGranted;
            String ssid = ScanConnection.currentSsid(activity);
            if (!ssid.equals(observedSsid)) {
                observedSsid = ssid;
                if (grid != null && grid.getAdapter() instanceof android.widget.BaseAdapter)
                    ((android.widget.BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
            }
            if (granted && !ssid.isEmpty() && activity.hasWindowFocus()
                    && activity instanceof ScanConnection.Host)
                ((ScanConnection.Host) activity).onWifiReady();
            int previous = state;
            int prerequisite = !granted ? 0 : wifi == null || !wifi.isWifiEnabled() ? 1
                    : !locationEnabled() ? 2 : 3;
            if (previous < 3 && prerequisite == 3) availableSince = SystemClock.elapsedRealtime();
            state = prerequisite < 3 ? prerequisite : grid == null || grid.getAdapter() == null
                    || grid.getAdapter().getCount() == 0
                    ? (SystemClock.elapsedRealtime() - availableSince < 5000 ? 3 : 4) : 5;
            if (state < 3 && state != previous) {
                View focused = activity.getCurrentFocus();
                if (focused != null) focused.clearFocus();
                android.view.inputmethod.InputMethodManager keyboard =
                        (android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(hint.getWindowToken(), 0);
            }
            String message = text(new String[] {permissionMessageKey(activity, permanentlyDenied()),
                    "material_ui_scan_wifi_off",
                    "material_ui_scan_location_off", "material_ui_scan_searching",
                    "material_ui_scan_empty", "no_camera_tips"}[state]);
            if (!message.contentEquals(hint.getText())) hint.setText(message);
            boolean obscuredPermission = state == 0 && !activity.hasWindowFocus();
            hint.setVisibility(obscuredPermission ? View.INVISIBLE : View.VISIBLE);
            int visibility = state < 3 ? View.GONE : View.VISIBLE;
            if (results != null && results.getVisibility() != visibility) results.setVisibility(visibility);
            boolean action = !obscuredPermission && (state < 3 || state == 4);
            String label = text(state == 0 ? (permanentlyDenied() ? "settings" : "material_ui_permission_retry")
                    : state == 2 ? "settings" : "material_ui_system_wifi_action");
            if (!label.contentEquals(recovery.getText())) recovery.setText(label);
            visibility = action ? View.VISIBLE : View.GONE;
            if (recovery.getVisibility() != visibility) recovery.setVisibility(visibility);
        }
        if (activity.hasWindowFocus()) hint.postDelayed(this, 1000);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        hint.removeCallbacks(this);
        if (attached) hint.post(this);
    }

    @Override public void onViewAttachedToWindow(View view) {
        attached = true;
        hint.getViewTreeObserver().addOnWindowFocusChangeListener(this);
        hint.removeCallbacks(this);
        hint.post(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        attached = false;
        if (hint.getViewTreeObserver().isAlive()) {
            hint.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
        }
        hint.removeCallbacks(this);
    }
}
