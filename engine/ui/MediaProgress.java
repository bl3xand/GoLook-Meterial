package local.materialui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

/** A compact, smooth progress arc shared by native refresh headers and footers. */
public final class MediaProgress extends Drawable implements Animatable, Runnable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running;
    private final float density;
    public MediaProgress(android.content.Context context) {
        density = context.getResources().getDisplayMetrics().density;
        int id = context.getResources().getIdentifier("material_ui_primary", "color", context.getPackageName());
        paint.setColor(context.getColor(id));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.5f * density);
    }
    @Override public void draw(Canvas canvas) {
        RectF rect = new RectF(getBounds());
        rect.inset(3 * density, 3 * density);
        canvas.drawArc(rect, running ? (android.os.SystemClock.uptimeMillis() % 1200) * .3f - 90 : -90, 270, false, paint);
    }
    @Override public int getIntrinsicWidth() { return Math.round(24 * density); }
    @Override public int getIntrinsicHeight() { return getIntrinsicWidth(); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void start() { if (!running) { running = true; run(); } }
    @Override public void stop() { running = false; unscheduleSelf(this); }
    @Override public boolean isRunning() { return running; }
    @Override public void run() { if (running) { invalidateSelf(); scheduleSelf(this, android.os.SystemClock.uptimeMillis() + 16); } }
}
