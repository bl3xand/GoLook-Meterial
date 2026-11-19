package local.materialui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/** One continuous thumbnail strip and one rounded selection, sharing native range geometry. */
public final class ClipTimeline {
    private final ViewGroup timeline;
    private final View left;
    private final View right;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final Rect sample = new Rect();
    private final Path shade = new Path();
    private final float density;
    private final int primary;
    private final int onPrimary;

    private ClipTimeline(ViewGroup timeline, View left, View right) {
        this.timeline = timeline;
        this.left = left;
        this.right = right;
        density = timeline.getResources().getDisplayMetrics().density;
        primary = timeline.getContext().getColor(id("material_ui_primary", "color"));
        onPrimary = timeline.getContext().getColor(id("material_ui_on_primary", "color"));
        timeline.findViewById(id("frame_left_iv", "id")).setVisibility(View.INVISIBLE);
        timeline.findViewById(id("frame_right_iv", "id")).setVisibility(View.INVISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            timeline.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View view, int l, int t, int r, int b,
                        int oldL, int oldT, int oldR, int oldB) {
                    protectHandles(view);
                }
            });
            protectHandles(timeline);
        }
    }
    private static void protectHandles(View view) {
        if (android.os.Build.VERSION.SDK_INT >= 29)
            view.setSystemGestureExclusionRects(java.util.Collections.singletonList(
                    new Rect(0, 0, view.getWidth(), view.getHeight())));
    }
    private int id(String name, String type) {
        return timeline.getResources().getIdentifier(name, type, timeline.getContext().getPackageName());
    }
    public static void bind(ViewGroup timeline, View left, View right) {
        int key = timeline.getResources().getIdentifier("material_ui_clip_middle_touch", "id",
                timeline.getContext().getPackageName());
        timeline.setTag(key, new ClipTimeline(timeline, left, right));
    }
    public static void draw(ViewGroup timeline, Canvas canvas, boolean foreground) {
        int key = timeline.getResources().getIdentifier("material_ui_clip_middle_touch", "id",
                timeline.getContext().getPackageName());
        Object tag = timeline.getTag(key);
        if (!(tag instanceof ClipTimeline)) return;
        ClipTimeline strip = (ClipTimeline) tag;
        if (foreground) strip.selection(canvas);
        else strip.edges(canvas);
    }
    private void edges(Canvas canvas) {
        ViewGroup frames = timeline.findViewById(id("recyclerview", "id"));
        if (frames == null || frames.getChildCount() == 0) return;
        View first = frames.getChildAt(0);
        View last = frames.getChildAt(frames.getChildCount() - 1);
        float start = frames.getX() + first.getX();
        float end = frames.getX() + last.getX() + last.getWidth();
        // Bleed the edge pixels beneath the handles and the outside rounded mask.
        if (start > 0) edge(canvas, first, 0, start, true);
        if (end < timeline.getWidth()) edge(canvas, last, end, timeline.getWidth(), false);
    }
    private void edge(Canvas canvas, View child, float from, float to, boolean first) {
        ImageView image = child.findViewById(id("image", "id"));
        if (image == null || !(image.getDrawable() instanceof BitmapDrawable)) return;
        Bitmap bitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;
        int x = first ? 0 : bitmap.getWidth() - 1;
        sample.set(x, 0, x + 1, bitmap.getHeight());
        bounds.set(from, 0, to, timeline.getHeight());
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, sample, bounds, paint);
    }
    private void selection(Canvas canvas) {
        float start = Math.max(0, left.getX());
        float end = Math.min(timeline.getWidth(), right.getX() + right.getWidth());
        if (end <= start) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        shade.reset();
        shade.setFillType(Path.FillType.EVEN_ODD);
        shade.addRect(0, 0, timeline.getWidth(), timeline.getHeight(), Path.Direction.CW);
        bounds.set(start, 0, end, timeline.getHeight());
        shade.addRoundRect(bounds, 16 * density, 16 * density, Path.Direction.CW);
        canvas.drawPath(shade, paint);
        float stroke = 3 * density;
        bounds.set(start + stroke / 2, stroke / 2, end - stroke / 2, timeline.getHeight() - stroke / 2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(primary);
        canvas.drawRoundRect(bounds, 16 * density - stroke / 2, 16 * density - stroke / 2, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(primary);
        float width = Math.min(10 * density, (end - start) / 3);
        float top = timeline.getHeight() / 2f - 16 * density;
        bounds.set(start, top, start + width, top + 32 * density);
        canvas.drawRoundRect(bounds, 3 * density, 3 * density, paint);
        bounds.set(end - width, top, end, top + 32 * density);
        canvas.drawRoundRect(bounds, 3 * density, 3 * density, paint);
        paint.setColor(onPrimary);
        bounds.set(start + width / 2 - density, top + 6 * density,
                start + width / 2 + density, top + 26 * density);
        canvas.drawRoundRect(bounds, density, density, paint);
        bounds.offset(end - start - width, 0);
        canvas.drawRoundRect(bounds, density, density, paint);
    }
}
