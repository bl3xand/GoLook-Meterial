package local.materialui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.text.NumberFormat;
import androidx.appcompat.app.AppCompatDialog;

/** Keeps legacy progress callbacks while using the shared Material dialog. */
public final class ExpressiveProgressDialog extends AppCompatDialog implements View.OnClickListener {
    private CharSequence heading;
    private CharSequence message;
    private CharSequence buttonText;
    private DialogInterface.OnClickListener buttonListener;
    private int buttonWhich = DialogInterface.BUTTON_NEGATIVE;
    private int progressStyle;
    private int maximum = 100;
    private int progress;
    private View content;
    private TextView titleView;
    private TextView messageView;
    private ProgressBar circular;
    private ProgressBar linear;
    private TextView percentage;
    private Button button;
    private View actions;

    public ExpressiveProgressDialog(Context context) { super(context); }
    public ExpressiveProgressDialog(Context context, int legacyTheme) { super(context); }

    private int id(String name, String type) {
        return getContext().getResources().getIdentifier(name, type, getContext().getPackageName());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = LayoutInflater.from(getContext()).inflate(id("dialog_progress", "layout"), null);
        setContentView(content);
        titleView = content.findViewById(id("tvDialogTitle", "id"));
        messageView = content.findViewById(id("tvDialogMessage", "id"));
        circular = content.findViewById(id("pbDialogProgress", "id"));
        linear = content.findViewById(id("download_progressbar", "id"));
        percentage = new TextView(getContext());
        percentage.setTextSize(14);
        percentage.setTextColor(getContext().getResources().getColor(id("material_ui_on_surface_variant", "color")));
        percentage.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        LinearLayout.LayoutParams percentageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentageParams.topMargin = dp(8);
        ((ViewGroup) linear.getParent()).addView(percentage, percentageParams);
        button = content.findViewById(id("btnDialogCancel", "id"));
        actions = (View) button.getParent();
        button.setOnClickListener(this);
        for (View view : new View[]{messageView, circular, linear}) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.topMargin = 0;
            params.bottomMargin = view == messageView ? dp(16) : 0;
            if (view == linear) params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(params);
        }
        update();
    }

    @Override protected void onStart() {
        super.onStart();
        UiBridge.apply(this);
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    @Override public void setTitle(CharSequence value) {
        super.setTitle(value);
        heading = value;
        update();
    }

    public void setMessage(CharSequence value) { message = value; update(); }
    public void setProgressStyle(int value) { progressStyle = value; update(); }
    public void setMax(int value) { maximum = Math.max(1, value); update(); }
    public void setProgress(int value) { progress = value; update(); }

    // Legacy callers supplied their spinner appearance, without binding its views.
    public void setView(View legacySpinner) { }

    public void setButton(int which, CharSequence text, DialogInterface.OnClickListener listener) {
        buttonWhich = which;
        buttonText = text;
        buttonListener = listener;
        update();
    }

    private void update() {
        if (titleView == null) return;
        titleView.setText(heading);
        titleView.setVisibility(TextUtils.isEmpty(heading) ? View.GONE : View.VISIBLE);
        messageView.setText(message);
        String first = heading == null ? "" : heading.toString().replaceAll("[.\\s\\u2026]+$", "");
        String second = message == null ? "" : message.toString().replaceAll("[.\\s\\u2026]+$", "");
        messageView.setVisibility(TextUtils.isEmpty(message) || (!first.isEmpty()
                && first.equalsIgnoreCase(second)) ? View.GONE : View.VISIBLE);
        boolean horizontal = progressStyle == 1;
        content.setTag(horizontal ? "material-ui-progress" : "material-ui-loading");
        if (messageView.getParent() == circular.getParent() && messageView.getParent() instanceof LinearLayout) {
            LinearLayout body = (LinearLayout) messageView.getParent();
            int index = body.indexOfChild(messageView);
            if ((horizontal && index != 0) || (!horizontal && index != body.indexOfChild(circular) + 1)) {
                body.removeView(messageView);
                body.addView(messageView, horizontal ? 0 : body.indexOfChild(circular) + 1);
            }
            messageView.setGravity(horizontal ? android.view.Gravity.START : android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = (LinearLayout.LayoutParams) messageView.getLayoutParams();
            labelParams.topMargin = horizontal ? 0 : dp(12);
            labelParams.bottomMargin = horizontal ? dp(16) : 0;
            messageView.setLayoutParams(labelParams);
            ViewGroup.LayoutParams ringParams = circular.getLayoutParams();
            ringParams.width = ringParams.height = dp(32);
            circular.setLayoutParams(ringParams);
        }
        circular.setVisibility(horizontal ? View.GONE : View.VISIBLE);
        linear.setVisibility(horizontal ? View.VISIBLE : View.GONE);
        linear.setMax(maximum);
        linear.setProgress(progress);
        percentage.setVisibility(horizontal ? View.VISIBLE : View.GONE);
        percentage.setText(NumberFormat.getPercentInstance(getContext().getResources().getConfiguration().locale)
                .format((double) progress / maximum));
        button.setText(buttonText);
        boolean hasAction = !TextUtils.isEmpty(buttonText);
        actions.setVisibility(hasAction ? View.VISIBLE : View.GONE);
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), hasAction ? 0 : dp(24));
    }

    @Override public void onClick(View view) {
        if (buttonListener != null) buttonListener.onClick(this, buttonWhich);
        dismiss();
    }
}
