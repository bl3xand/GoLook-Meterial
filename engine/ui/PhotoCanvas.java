package local.materialui;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Rounded image bounds follow the image matrix, rather than a stationary viewport. */
public final class PhotoCanvas extends FrameLayout {
    private boolean zoomed;
    private boolean controlsShown = true;
    private ImageView photo;
    private View header, actions;
    private java.lang.reflect.Method readScale;
    private final float[] previousMatrix = new float[9];
    private final float[] currentMatrix = new float[9];
    private android.animation.ValueAnimator backgroundAnimation;
    private int backgroundColor;
    private android.app.Activity activity;
    private View root;
    private int insetLeft, insetTop, insetRight, insetBottom;
    private final android.view.ViewTreeObserver.OnPreDrawListener zoomWatcher =
            new android.view.ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
            try {
                boolean next = ((Number) readScale.invoke(photo)).floatValue() > 1.025f;
                if (next != zoomed) { zoomed = next; zoomBackground(); showControls(!zoomed); }
                photo.getImageMatrix().getValues(currentMatrix);
                if (!java.util.Arrays.equals(previousMatrix, currentMatrix)) {
                    System.arraycopy(currentMatrix, 0, previousMatrix, 0, 9);
                    photo.invalidateOutline();
                }
            } catch (ReflectiveOperationException unavailable) { }
            return true;
        }
    };
    public PhotoCanvas(Context context, AttributeSet attrs) { super(context, attrs); setClipChildren(true); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ImageView)) return;
        photo = (ImageView) getChildAt(0);
        photo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) { imageOutline(view, outline); }
        });
        photo.setClipToOutline(true);
        backgroundColor = getContext().getColor(getResources().getIdentifier("material_ui_surface", "color", getContext().getPackageName()));
        root = (View) getParent();
        header = root.findViewById(getResources().getIdentifier("material_ui_photo_header", "id", getContext().getPackageName()));
        actions = root.findViewById(getResources().getIdentifier("material_ui_photo_actions", "id", getContext().getPackageName()));
        android.content.Context context = getContext();
        while (context instanceof android.content.ContextWrapper && !(context instanceof android.app.Activity))
            context = ((android.content.ContextWrapper) context).getBaseContext();
        if (context instanceof android.app.Activity) {
            activity = (android.app.Activity) context;
            android.view.Window window = activity.getWindow();
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.view.WindowManager.LayoutParams params = window.getAttributes();
                params.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(params);
            }
            updateSystemBars();
            root.setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
                @Override public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                    insetLeft = insets.getSystemWindowInsetLeft(); insetTop = insets.getSystemWindowInsetTop();
                    insetRight = insets.getSystemWindowInsetRight(); insetBottom = insets.getSystemWindowInsetBottom();
                    if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                        android.view.DisplayCutout cutout = insets.getDisplayCutout();
                        insetLeft = Math.max(insetLeft, cutout.getSafeInsetLeft());
                        insetRight = Math.max(insetRight, cutout.getSafeInsetRight());
                        insetTop = Math.max(insetTop, cutout.getSafeInsetTop());
                        insetBottom = Math.max(insetBottom, cutout.getSafeInsetBottom());
                    }
                    layoutPanels();
                    return insets.consumeSystemWindowInsets();
                }
            });
            root.requestApplyInsets();
        }
        try {
            readScale = photo.getClass().getMethod("getScale");
            getViewTreeObserver().addOnPreDrawListener(zoomWatcher);
        } catch (NoSuchMethodException unavailable) { }
        photo.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { if (zoomed) showControls(!controlsShown); }
        });
    }
    private void showControls(final boolean visible) {
        controlsShown = visible;
        boolean overlay = zoomed || getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        for (final View bar : new View[]{header, actions}) {
            if (bar == null) continue;
            bar.animate().cancel();
            panelBackground(bar, overlay);
            if (visible) bar.setVisibility(VISIBLE);
            float offset = 12 * getResources().getDisplayMetrics().density * (bar == header ? -1 : 1);
            bar.animate().alpha(visible ? 1 : 0).translationY(visible ? 0 : offset)
                    .setDuration(visible ? 220 : 180)
                    .withEndAction(new Runnable() { @Override public void run() {
                        if (!visible && !controlsShown) bar.setVisibility(INVISIBLE);
                    }}).start();
        }
    }
    private void zoomBackground() {
        final View background = (View) getParent();
        int surface = getContext().getColor(getResources().getIdentifier("material_ui_surface", "color", getContext().getPackageName()));
        if (backgroundAnimation != null) backgroundAnimation.cancel();
        backgroundAnimation = android.animation.ValueAnimator.ofArgb(backgroundColor, zoomed ? android.graphics.Color.BLACK : surface);
        backgroundAnimation.setDuration(200);
        backgroundAnimation.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                backgroundColor = (Integer) animation.getAnimatedValue();
                background.setBackgroundColor(backgroundColor);
            }
        });
        backgroundAnimation.start();
        updateSystemBars();
        tintHeader(header, zoomed || getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
    }
    private final java.util.HashMap<android.widget.TextView, android.content.res.ColorStateList> textColors = new java.util.HashMap<>();
    private final java.util.HashMap<ImageView, android.content.res.ColorStateList> imageColors = new java.util.HashMap<>();
    private void tintHeader(View view, boolean dark) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView label = (android.widget.TextView) view;
            if (!textColors.containsKey(label)) textColors.put(label, label.getTextColors());
            label.setTextColor(dark ? android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE) : textColors.get(label));
        }
        if (view instanceof ImageView) {
            ImageView icon = (ImageView) view;
            if (!imageColors.containsKey(icon)) imageColors.put(icon, icon.getImageTintList());
            icon.setImageTintList(dark ? android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE) : imageColors.get(icon));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintHeader(group.getChildAt(i), dark);
        }
    }
    @Override protected void onDetachedFromWindow() {
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(zoomWatcher);
        for (View bar : new View[]{header, actions}) if (bar != null) bar.animate().cancel();
        if (backgroundAnimation != null) backgroundAnimation.cancel();
        super.onDetachedFromWindow();
    }
    private boolean landscape() {
        return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }
    private void updateSystemBars() {
        if (activity == null) return;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean light = !zoomed && !landscape() && (getResources().getConfiguration().uiMode & 0x30) != 0x20;
        if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        View decor = activity.getWindow().getDecorView();
        if (decor.getSystemUiVisibility() != flags) decor.setSystemUiVisibility(flags);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void panelBackground(View bar, boolean overlay) {
        if (!overlay) { bar.setBackgroundColor(android.graphics.Color.TRANSPARENT); return; }
        // The scrim spans the screen; only its controls are inset from system gestures and cutouts.
        android.graphics.drawable.GradientDrawable.Orientation direction = bar == header
                ? android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                : android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP;
        bar.setBackground(new android.graphics.drawable.GradientDrawable(direction,
                new int[]{0xc0000000, 0x80000000, 0x00000000}));
    }
    private void layoutPanels() {
        if (header != null) header.setPadding(insetLeft, insetTop, insetRight, 0);
        if (actions != null) {
            int side = Math.max(insetLeft, insetRight) + dp(24);
            int left = side, right = side;
            if (actions.getPaddingLeft() != left || actions.getPaddingRight() != right
                    || actions.getPaddingBottom() != insetBottom + dp(16))
                actions.setPadding(left, dp(16), right, insetBottom + dp(16));
        }
    }
    @Override protected void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        layoutPanels();
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateSystemBars();
        boolean overlay = zoomed || landscape();
        tintHeader(header, overlay);
        for (View bar : new View[]{header, actions}) if (bar != null) panelBackground(bar, overlay);
    }
    private void imageOutline(View view, android.graphics.Outline outline) {
        ImageView image = (ImageView) view;
        if (image.getDrawable() == null) { outline.setEmpty(); return; }
        android.graphics.drawable.Drawable drawable = image.getDrawable();
        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        image.getImageMatrix().mapRect(rect);
        rect.offset(image.getPaddingLeft(), image.getPaddingTop());
        float zoom = 1;
        try { if (readScale != null) zoom = ((Number) readScale.invoke(photo)).floatValue(); }
        catch (ReflectiveOperationException unavailable) { }
        // Follow the pinch continuously: rounded at rest, square before the image
        // reaches the viewport edges. Panning must never expose magnified corners.
        float fraction = Math.max(0, Math.min(1, (zoom - 1) / .15f));
        float eased = fraction * fraction * (3 - 2 * fraction);
        outline.setRoundRect(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right),
                Math.round(rect.bottom), 24 * getResources().getDisplayMetrics().density * (1 - eased));
    }
}
