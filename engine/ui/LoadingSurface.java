package local.materialui;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Empty native messages must not move a lone indicator away from the center. */
public final class LoadingSurface extends LinearLayout {
    public LoadingSurface(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        watch(this);
    }

    private void watch(View view) {
        if (view instanceof TextView && view.getVisibility() == VISIBLE) {
            final TextView label = (TextView) view;
            label.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    label.setVisibility(TextUtils.isEmpty(s) ? GONE : VISIBLE);
                }
                @Override public void afterTextChanged(Editable text) { }
            });
            label.setVisibility(TextUtils.isEmpty(label.getText()) ? GONE : VISIBLE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) watch(group.getChildAt(index));
        }
    }
}
