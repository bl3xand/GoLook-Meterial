package local.materialui;

import android.view.View;
import java.util.WeakHashMap;

/** Match media overlay fades without delaying native playback or retaining activities. */
public final class MediaChrome {
    private static final WeakHashMap<View, Integer> desired = new WeakHashMap<>();
    private MediaChrome() {}

    public static void visibility(final View view, final int visibility) {
        if (view == null) return;
        boolean visible = visibility == View.VISIBLE;
        desired.put(view, visibility);
        view.animate().cancel();
        if (visible) {
            if (view.getVisibility() != View.VISIBLE) view.setAlpha(0);
            view.setVisibility(View.VISIBLE);
        }
        view.animate().alpha(visible ? 1 : 0).setDuration(visible ? 220 : 180)
                .withEndAction(new Runnable() { @Override public void run() {
                    Integer target = desired.get(view);
                    if (target != null && target == visibility && visibility != View.VISIBLE)
                        view.setVisibility(visibility);
                }}).start();
    }
}
