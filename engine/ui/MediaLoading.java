package local.materialui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import java.util.WeakHashMap;

/** Keep one visible loading presentation when a modal covers an album. */
public final class MediaLoading {
    private static final WeakHashMap<View, Integer> covers = new WeakHashMap<View, Integer>();
    private static final WeakHashMap<View, Boolean> dialogs = new WeakHashMap<View, Boolean>();
    private MediaLoading() {}
    public static void modal(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Context context = dialog.getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity))
            context = ((ContextWrapper) context).getBaseContext();
        Activity activity = context instanceof Activity ? (Activity) context : dialog.getOwnerActivity();
        if (activity == null) return;
        final Activity owner = activity;
        final boolean fileMenu = activity.getClass().getSimpleName().equals("PopupWinforPreviewActivity");
        int id = activity.getResources().getIdentifier("layoutEmrRecording", "id", activity.getPackageName());
        View candidate = activity.findViewById(id);
        if (fileMenu) candidate = activity.findViewById(android.R.id.content);
        if (candidate == null) {
            int progressId = activity.getResources().getIdentifier("ibPBarProgress", "id", activity.getPackageName());
            View progress = activity.findViewById(progressId);
            if (progress != null && progress.getVisibility() == View.VISIBLE && progress.getParent() instanceof View)
                candidate = (View) progress.getParent();
        }
        final View overlay = candidate;
        if (overlay == null || (overlay.getVisibility() != View.VISIBLE && !covers.containsKey(overlay))) return;
        final View decor = dialog.getWindow().getDecorView();
        if (dialogs.containsKey(decor)) return;
        dialogs.put(decor, Boolean.TRUE);
        Integer count = covers.get(overlay);
        covers.put(overlay, count == null ? 1 : count + 1);
        final android.graphics.drawable.Drawable background = owner.getWindow().getDecorView().getBackground();
        final float dim = owner.getWindow().getAttributes().dimAmount;
        if (fileMenu) {
            owner.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            owner.getWindow().setDimAmount(0);
        }
        overlay.setVisibility(View.INVISIBLE);
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                decor.removeOnAttachStateChangeListener(this); dialogs.remove(decor);
                Integer count = covers.get(overlay);
                if (count == null || count <= 1) {
                    covers.remove(overlay);
                    if (fileMenu) {
                        if (!owner.isFinishing() && !owner.isDestroyed()) {
                            overlay.setVisibility(View.VISIBLE);
                            owner.getWindow().setBackgroundDrawable(background);
                            owner.getWindow().setDimAmount(dim);
                        }
                        return;
                    }
                    if (overlay.getVisibility() == View.INVISIBLE) {
                        int progressId = overlay.getResources().getIdentifier("progressBar", "id", overlay.getContext().getPackageName());
                        View progress = overlay.findViewById(progressId);
                        if (progress == null) progress = overlay.findViewById(overlay.getResources()
                                .getIdentifier("ibPBarProgress", "id", overlay.getContext().getPackageName()));
                        overlay.setVisibility(progress != null && progress.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
                    }
                } else covers.put(overlay, count - 1);
            }
        });
    }
    public static void enforce(Activity activity) {
        int id = activity.getResources().getIdentifier("layoutEmrRecording", "id", activity.getPackageName());
        View overlay = activity.findViewById(id);
        if (overlay != null && covers.containsKey(overlay) && overlay.getVisibility() == View.VISIBLE)
            overlay.setVisibility(View.INVISIBLE);
    }
}
