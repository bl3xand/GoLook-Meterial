package local.materialui;

import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import java.util.WeakHashMap;

/** Keep transient loading cards compact in both orientations. */
public final class ModalLayout {
    private static final WeakHashMap<Window, Boolean> observed = new WeakHashMap<>();
    private ModalLayout() { }

    public static void apply(final Window window) {
        if (window == null) return;
        resize(window);
        if (observed.containsKey(window)) return;
        observed.put(window, Boolean.TRUE);
        window.getDecorView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int l, int t, int r, int b,
                    int oldL, int oldT, int oldR, int oldB) {
                if (l != oldL || r != oldR || t != oldT || b != oldB) resize(window);
            }
        });
    }

    private static void resize(Window window) {
        View loading = window.getDecorView().findViewWithTag("material-ui-loading");
        if (loading == null) loading = window.getDecorView().findViewWithTag("material-ui-progress");
        if (loading == null) return;
        android.util.DisplayMetrics metrics = window.getContext().getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - Math.round(48 * metrics.density),
                Math.round(360 * metrics.density));
        if (window.getAttributes().width != width)
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
