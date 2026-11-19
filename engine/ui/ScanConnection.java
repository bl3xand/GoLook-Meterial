package local.materialui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;

/** Wi-Fi association is separate from successful recorder protocol discovery. */
public final class ScanConnection {
    private ScanConnection() {}
    public interface Host { void onWifiReady(); void onWifiCancelled(); }

    public static String currentSsid(Context context) {
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (manager == null || !manager.isWifiEnabled()) return "";
            WifiInfo info = manager.getConnectionInfo();
            return info != null && info.getSupplicantState() == SupplicantState.COMPLETED
                    ? normalize(info.getSSID()) : "";
        } catch (SecurityException unavailable) { return ""; }
    }

    public static String normalize(String ssid) {
        if (ssid == null || "<unknown ssid>".equals(ssid)) return "";
        return ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")
                ? ssid.substring(1, ssid.length() - 1) : ssid;
    }

    public static boolean accepts(String actual, String requested, boolean knownName) {
        String target = normalize(requested), current = normalize(actual);
        return !current.isEmpty() && (target.isEmpty() ? knownName : target.equals(current));
    }

    public static boolean requiresPassword(String capabilities) {
        return capabilities == null || capabilities.contains("WEP") || capabilities.contains("PSK")
                || capabilities.contains("SAE") || capabilities.contains("EAP") || capabilities.contains("WAPI");
    }

    public static String text(Context context, String name) {
        return context.getString(context.getResources().getIdentifier(name, "string", context.getPackageName()));
    }

    public static String guidance(Context context, String ssid, String password) {
        String message = context.getString(context.getResources().getIdentifier(
                "material_ui_wifi_manual", "string", context.getPackageName()), normalize(ssid));
        return WifiPasswordClipboard.prepare(context, password, message);
    }

    public static void cancelManual(Activity activity) {
        if (activity instanceof Host) ((Host) activity).onWifiCancelled();
    }

    public static boolean debounce(android.view.View control) {
        int key = control.getResources().getIdentifier("btnConnect", "id", control.getContext().getPackageName());
        Object previous = control.getTag(key);
        long now = android.os.SystemClock.uptimeMillis();
        if (previous instanceof Long && now - (Long) previous < 1000) return true;
        control.setTag(key, Long.valueOf(now));
        return false;
    }

    public static void cancelWatch(Activity activity) {
        android.view.View hint = activity.findViewById(activity.getResources().getIdentifier("tvPermissionTips", "id", activity.getPackageName()));
        if (hint == null) return;
        int key = activity.getResources().getIdentifier("gvDeviceList", "id", activity.getPackageName());
        Object pending = hint.getTag(key);
        if (pending instanceof Runnable) hint.removeCallbacks((Runnable) pending);
        hint.setTag(key, null);
    }

    public static void watch(Activity activity, Runnable timeout) {
        cancelWatch(activity);
        android.view.View hint = activity.findViewById(activity.getResources().getIdentifier("tvPermissionTips", "id", activity.getPackageName()));
        if (hint == null) return;
        int key = activity.getResources().getIdentifier("gvDeviceList", "id", activity.getPackageName());
        hint.setTag(key, timeout);
        hint.postDelayed(timeout, 20000);
    }

    public static void failed(final Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);
        builder.setTitle(text(activity, "connect_fail_no_camera_detected"));
        builder.setMessage(text(activity, "material_ui_wifi_no_recorder"));
        builder.setNegativeButton(text(activity, "cancel"), null);
        builder.setPositiveButton(text(activity, "material_ui_system_wifi_action"),
                new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                });
        UiBridge.show(builder);
    }
}
