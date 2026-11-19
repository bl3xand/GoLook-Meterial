package local.materialui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/** Keeps the map SDK's tab callbacks while presenting a consistent underline tab. */
public final class TabLabel extends TextView {
    private boolean initialized;
    private final Paint indicator = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TabLabel(Context context, AttributeSet attributes) {
        super(context, attributes);
        initialized = true;
        setFocusable(true);
        setClickable(true);
        setIncludeFontPadding(false);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setTextSize(14);
        float line = 20 * getResources().getDisplayMetrics().scaledDensity;
        setLineSpacing(line - getPaint().getFontSpacing(), 1);
        if (android.os.Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false);
        setBackgroundResource(attributes.getAttributeResourceValue(
                "http://schemas.android.com/apk/res/android", "background", 0));
    }

    private int color(String name) {
        return getContext().getColor(getResources().getIdentifier(name, "color", getContext().getPackageName()));
    }

    @Override public void setBackgroundResource(int resource) {
        if (!initialized) { super.setBackgroundResource(resource); return; }
        String name = resource == 0 ? "" : getResources().getResourceEntryName(resource);
        setSelected(name.endsWith("_pressed"));
        super.setBackgroundResource(getResources().getIdentifier(
                "material_ui_settings_row_bg", "drawable", getContext().getPackageName()));
        setTextColor(color(isSelected() ? "material_ui_primary" : "material_ui_on_surface_variant"));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isSelected()) return;
        float density = getResources().getDisplayMetrics().density;
        indicator.setColor(color("material_ui_primary"));
        canvas.drawRoundRect(new RectF(12 * density, getHeight() - 3 * density,
                getWidth() - 12 * density, getHeight()), 2 * density, 2 * density, indicator);
    }
}
