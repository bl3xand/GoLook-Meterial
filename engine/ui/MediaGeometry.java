package local.materialui;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** Position the render surface itself, rather than translating a full-width wrapper. */
public final class MediaGeometry implements ViewTreeObserver.OnPreDrawListener {
    private final Activity owner;
    private final View root;
    private MediaGeometry(Activity owner, View root) { this.owner = owner; this.root = root; }
    public static void install(Activity owner) {
        View root = owner.findViewById(android.R.id.content);
        int key = owner.getResources().getIdentifier("material_ui_preview_layout_observer", "id", owner.getPackageName());
        if (root == null || root.getTag(key) instanceof MediaGeometry) return;
        MediaGeometry adapter = new MediaGeometry(owner, root);
        root.setTag(key, adapter);
        root.getViewTreeObserver().addOnPreDrawListener(adapter);
    }
    @Override public boolean onPreDraw() {
        if (!owner.isFinishing() && !owner.isDestroyed()) visit(root);
        return true;
    }
    private void visit(View view) {
        if (!view.isShown()) return;
        if (view instanceof android.view.SurfaceView || view instanceof android.view.TextureView) fit(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i));
        }
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private void fit(View view) {
        if (!(view.getParent() instanceof View)) return;
        try {
            Object helper = field(view, "mMeasureHelper");
            int width = (Integer) field(helper, "mVideoWidth");
            int height = (Integer) field(helper, "mVideoHeight");
            if (width <= 0 || height <= 0) return;
            int rotation = (Integer) field(helper, "mVideoRotationDegree");
            View parent = (View) view.getParent();
            Rect bounds = new Rect();
            if (!parent.getGlobalVisibleRect(bounds)) return;
            int[] origin = new int[2]; parent.getLocationOnScreen(origin);
            int[] size = VideoFit.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.EXACTLY), width, height,
                    (Integer) field(helper, "mVideoSarNum"), (Integer) field(helper, "mVideoSarDen"), rotation);
            android.util.DisplayMetrics display = new android.util.DisplayMetrics();
            owner.getWindowManager().getDefaultDisplay().getRealMetrics(display);
            boolean landscape = owner.getResources().getConfiguration().orientation == 2;
            float centerX = landscape ? display.widthPixels / 2f : bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            int visualWidth = Math.abs(rotation % 180) == 90 ? size[1] : size[0];
            int visualHeight = Math.abs(rotation % 180) == 90 ? size[0] : size[1];
            centerX = Math.max(bounds.left + visualWidth / 2f, Math.min(centerX, bounds.right - visualWidth / 2f));
            centerY = Math.max(bounds.top + visualHeight / 2f, Math.min(centerY, bounds.bottom - visualHeight / 2f));
            int x = Math.round(centerX - origin[0] - size[0] / 2f);
            int y = Math.round(centerY - origin[1] - size[1] / 2f);
            if (view.getMeasuredWidth() != size[0] || view.getMeasuredHeight() != size[1])
                view.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.EXACTLY));
            view.setTranslationX(0); view.setTranslationY(0);
            if (view.getLeft() != x || view.getTop() != y || view.getWidth() != size[0] || view.getHeight() != size[1])
                view.layout(x, y, x + size[0], y + size[1]);
        } catch (ReflectiveOperationException unsupportedRenderer) {
            // Non-IJK surfaces retain their own renderer-specific sizing.
        }
    }
}
