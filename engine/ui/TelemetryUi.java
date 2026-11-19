package local.materialui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.text.NumberFormat;

/** Material telemetry presentation; recorder values and native setters stay intact. */
public final class TelemetryUi {
    private TelemetryUi() { }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    private static int color(View view, String name) {
        int id = view.getResources().getIdentifier(name, "color", view.getContext().getPackageName());
        return view.getContext().getColor(id);
    }

    public static void altitude(ViewGroup group) {
        int padding = Math.round(dp(group, 12));
        group.setPadding(padding, padding, padding, padding);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(0xE61B1B20);
        surface.setCornerRadius(dp(group, 16));
        group.setBackground(surface);
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (!(child instanceof TextView)) continue;
            TextView text = (TextView) child;
            if (index % 2 == 0) UiMetrics.secondary(text);
            else UiMetrics.primary(text);
            text.setTextColor(index % 2 == 0 ? 0xFFCAC4D0 : Color.WHITE);
            text.setMaxWidth(Math.round(dp(group, 240)));
            text.setMaxLines(2);
            if (index == 2) {
                ViewGroup.MarginLayoutParams layout = (ViewGroup.MarginLayoutParams) text.getLayoutParams();
                layout.topMargin = Math.round(dp(group, 8));
                text.setLayoutParams(layout);
            }
        }
    }

    public static void gauge(Canvas canvas, View view, float value, float maximum,
            boolean metric, boolean overlay) {
        float width = view.getWidth(), height = view.getHeight();
        if (width <= 0 || height <= 0) return;
        if (Float.isNaN(value) || Float.isInfinite(value)) value = 0;
        if (maximum <= 0) maximum = metric ? 240 : 160;
        float padding = dp(view, overlay ? 6 : 16);
        float radius = Math.max(0, Math.min(width, height) / 2 - padding);
        float cx = width / 2, cy = height / 2;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (overlay) {
            paint.setColor(0xE61B1B20);
            canvas.drawCircle(cx, cy, radius + dp(view, 4), paint);
        }
        RectF bounds = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(view, overlay ? 3 : 6));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(overlay ? 0xFF49454F : color(view, "material_ui_surface_container_high"));
        canvas.drawArc(bounds, 135, 270, false, paint);
        paint.setColor(overlay ? 0xFFDCE1FF : color(view, "material_ui_primary"));
        float fraction = Math.max(0, Math.min(1, value / maximum));
        if (fraction > 0) canvas.drawArc(bounds, 135, fraction * 270, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float scale = view.getResources().getDisplayMetrics().scaledDensity;
        paint.setTextSize(Math.min((overlay ? 16 : 28) * scale, radius * 0.8f));
        paint.setColor(overlay ? Color.WHITE : color(view, "material_ui_on_surface"));
        float numberY = cy - dp(view, 2);
        canvas.drawText(NumberFormat.getIntegerInstance().format((long) Math.max(0, value)),
                cx, numberY, paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(Math.min((overlay ? 10 : 14) * scale, radius * 0.38f));
        paint.setColor(overlay ? 0xFFCAC4D0 : color(view, "material_ui_on_surface_variant"));
        canvas.drawText(metric ? "km/h" : "mph", cx, cy + dp(view, overlay ? 16 : 24), paint);
    }
}
