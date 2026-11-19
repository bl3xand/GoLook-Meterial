package local.materialui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.View;

/** Bound feedback for a native saved-recorder attempt that otherwise fails silently. */
public final class ConnectionButton extends com.google.android.material.button.MaterialButton {
    private CharSequence idleText;
    private Runnable timeout;

    public ConnectionButton(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override public boolean performClick() {
        if (timeout != null) return true;
        boolean handled = super.performClick();
        if (!handled || !isShown()) return handled;
        idleText = getText();
        setText(ScanConnection.text(getContext(), "changhong_connecting"));
        setEnabled(false);
        timeout = new Runnable() {
            @Override public void run() {
                boolean foreground = isShown() && hasWindowFocus();
                restore();
                Activity owner = activity();
                if (foreground && owner != null && !owner.isFinishing() && !owner.isDestroyed())
                    ScanConnection.failed(owner);
            }
        };
        postDelayed(timeout, 20000);
        return handled;
    }

    private Activity activity() {
        Context context = getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private void restore() {
        if (timeout != null) {
            removeCallbacks(timeout);
            timeout = null;
            setText(idleText);
            idleText = null;
            setEnabled(true);
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) restore();
    }

    @Override protected void onDetachedFromWindow() {
        restore();
        super.onDetachedFromWindow();
    }
}
