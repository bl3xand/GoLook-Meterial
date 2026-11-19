package local.materialui;

import android.content.Context;
import android.net.wifi.ScanResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Apply one stable permutation to scan results and their corresponding credentials. */
public final class NetworkOrder {
    private NetworkOrder() {}

    public static void apply(Context context, List<ScanResult> networks, List<Object> credentials) {
        reorder(ScanConnection.currentSsid(context), networks, credentials);
    }

    public static void reorder(final String current, final List<ScanResult> networks, List<Object> credentials) {
        if (networks.size() != credentials.size()) return;
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < networks.size(); i++) order.add(i);
        Collections.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer left, Integer right) {
                ScanResult a = networks.get(left), b = networks.get(right);
                boolean activeA = !current.isEmpty() && current.equals(ScanConnection.normalize(a.SSID));
                boolean activeB = !current.isEmpty() && current.equals(ScanConnection.normalize(b.SSID));
                if (activeA != activeB) return activeA ? -1 : 1;
                return Integer.compare(b.level, a.level);
            }
        });
        List<ScanResult> original = new ArrayList<ScanResult>(networks);
        List<Object> passwords = new ArrayList<Object>(credentials);
        for (int i = 0; i < order.size(); i++) {
            int source = order.get(i);
            networks.set(i, original.get(source));
            credentials.set(i, passwords.get(source));
        }
    }
}
