package local.materialui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Shows association to the saved SSID, independently of recorder-session state. */
public final class WifiAssociationIcon extends ImageView {
    private boolean registered;
    private int displayedResource;
    private View hiddenHint;
    private int hintVisibility;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    public WifiAssociationIcon(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override public void setImageResource(int id) { displayedResource = 0; super.setImageResource(id); if (isAttachedToWindow()) postRefresh(); }
    private void postRefresh() { post(new Runnable() { @Override public void run() { refresh(); } }); }
    private void refresh() {
        View row = getParent() instanceof View ? (View) getParent() : null;
        int labelId = getResources().getIdentifier("tvWifiName", "id", getContext().getPackageName());
        while (row != null && row.findViewById(labelId) == null)
            row = row.getParent() instanceof View ? (View) row.getParent() : null;
        TextView label = row == null ? null : row.findViewById(labelId);
        if (label == null) return;
        String current = ScanConnection.currentSsid(getContext());
        boolean associated = !current.isEmpty() && current.equals(ScanConnection.normalize(label.getText().toString()));
        int hintId = getResources().getIdentifier("openWifiTips", "id", getContext().getPackageName());
        View scope = row;
        while (scope != null && scope.findViewById(hintId) == null)
            scope = scope.getParent() instanceof View ? (View) scope.getParent() : null;
        View hint = scope == null ? null : scope.findViewById(hintId);
        if (associated && hint != null) {
            if (hiddenHint != hint) { hiddenHint = hint; hintVisibility = hint.getVisibility(); }
            if (hint.getVisibility() != View.GONE) hint.setVisibility(View.GONE);
        } else if (hiddenHint != null) {
            hiddenHint.setVisibility(hintVisibility); hiddenHint = null;
        }
        int icon = getResources().getIdentifier(associated ? "top_icon_wifi_4" : "top_icon_wifi_0", "drawable", getContext().getPackageName());
        if (icon != 0 && displayedResource != icon) {
            displayedResource = icon;
            super.setImageResource(icon);
            setContentDescription(ScanConnection.text(getContext(), associated ? "connected" : "un_connect"));
        }
    }
    @Override protected void onDraw(android.graphics.Canvas canvas) { refresh(); super.onDraw(canvas); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); postRefresh(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && !registered) {
            IntentFilter filter = new IntentFilter("android.net.wifi.STATE_CHANGE");
            filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
            if (android.os.Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else getContext().registerReceiver(receiver, filter);
            registered = true;
            postRefresh();
        } else if (visibility != VISIBLE) unregister();
    }
    private void unregister() { if (registered) { getContext().unregisterReceiver(receiver); registered = false; } }
    @Override protected void onDetachedFromWindow() { unregister(); super.onDetachedFromWindow(); }
}
