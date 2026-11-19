package local.materialui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/** Back navigation for legacy service screens without a native toolbar callback. */
public final class BackButton extends ImageView implements View.OnClickListener {
    public BackButton(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOnClickListener(this);
        setFocusable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false);
    }

    @Override public void onClick(View view) {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                long time = android.os.SystemClock.uptimeMillis();
                activity.dispatchKeyEvent(new android.view.KeyEvent(time, time,
                        android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0));
                activity.dispatchKeyEvent(new android.view.KeyEvent(time, time,
                        android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0));
                return;
            }
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return;
            context = next;
        }
    }
}
