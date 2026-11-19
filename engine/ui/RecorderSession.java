package local.materialui;

import android.app.Activity;

/** A verified recorder may use an arbitrary Wi-Fi name. */
public final class RecorderSession {
    private static String connectedSsid = "";

    private RecorderSession() {}

    public static void begin(Activity activity) {
        connectedSsid = ScanConnection.currentSsid(activity);
    }

    public static boolean sameWifi(String ssid) {
        return !connectedSsid.isEmpty() && connectedSsid.equals(ScanConnection.normalize(ssid));
    }
}
