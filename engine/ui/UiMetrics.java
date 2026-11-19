package local.materialui;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

/** Shared roles for legacy controls created without XML. */
public final class UiMetrics {
    private UiMetrics() {}
    private static final java.util.WeakHashMap<View, Boolean> feedbackRows = new java.util.WeakHashMap<>();

    public static void passiveLabel(View view) {
        view.setOnClickListener(null);
        view.setClickable(false);
    }

    public static void hideDiagnostic(View view) {
        passiveLabel(view);
        view.setVisibility(View.GONE);
    }

    /** Keep one standard ripple for the row even when native values are rebound. */
    public static void settingsFeedback(View row) {
        if (feedbackRows.containsKey(row)) return;
        int background = row.getResources().getIdentifier("material_ui_settings_row_bg", "drawable",
                row.getContext().getPackageName());
        row.setBackgroundResource(background);
        feedbackRows.put(row, Boolean.TRUE);
    }

    public static void bindChoice(android.preference.ListPreference preference, View row) {
        View candidate = row.findViewById(android.R.id.summary);
        if (candidate instanceof TextView) {
            CharSequence entry = preference.getEntry();
            TextView value = (TextView) candidate;
            value.setText(entry == null ? "—" : entry);
            value.setVisibility(View.VISIBLE);
        }
        int arrow = row.getResources().getIdentifier("setitem_more", "id", row.getContext().getPackageName());
        View action = row.findViewById(arrow);
        if (action != null) action.setVisibility(View.VISIBLE);
    }

    public static View preferenceChoice(Context context, ViewGroup parent) {
        int layout = context.getResources().getIdentifier("material_ui_preference_choice", "layout", context.getPackageName());
        return LayoutInflater.from(context).inflate(layout, parent, false);
    }


    private static int pixels(View view, int unit, float value) {
        return Math.round(TypedValue.applyDimension(unit, value,
                view.getResources().getDisplayMetrics()));
    }

    private static void text(TextView view, boolean secondary, boolean caption) {
        Context context = view.getContext();
        String appearance = caption ? "BodySmall" : secondary ? "BodyMedium" : "TitleMedium";
        int style = view.getResources().getIdentifier("TextAppearance.Material3." + appearance,
                "style", context.getPackageName());
        if (style != 0) view.setTextAppearance(context, style);
        view.setTextSize(caption ? 12 : secondary ? 14 : 16);
        int color = view.getResources().getIdentifier(
                secondary ? "material_ui_text_secondary" : "material_ui_text_primary",
                "color", context.getPackageName());
        if (color != 0) view.setTextColor(view.getResources().getColorStateList(color));
        int line = pixels(view, TypedValue.COMPLEX_UNIT_SP, caption ? 16 : secondary ? 20 : 24);
        view.setIncludeFontPadding(false);
        if (Build.VERSION.SDK_INT >= 28) view.setLineHeight(line);
        else view.setLineSpacing(Math.max(0, line - view.getPaint().getFontMetricsInt(null)), 1);
    }

    /** Caption models can outlive the activity that originally supplied their context. */
    public static Context localizedContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        android.content.res.Configuration configuration =
                new android.content.res.Configuration(app.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager manager = app.getSystemService(android.app.LocaleManager.class);
            if (manager != null && !manager.getApplicationLocales().isEmpty())
                configuration.setLocales(manager.getApplicationLocales());
        }
        return app.createConfigurationContext(configuration);
    }

    public static void fileLocation(TextView view, CharSequence value) {
        String text = value == null ? "" : value.toString();
        String privateRoot = view.getContext().getFilesDir().getAbsolutePath() + "/";
        boolean hidden = text.isEmpty() || text.contains(privateRoot);
        int scheme = text.indexOf("http://");
        if (scheme < 0) scheme = text.indexOf("https://");
        if (!hidden && scheme >= 0) {
            android.net.Uri uri = android.net.Uri.parse(text.substring(scheme).trim());
            String path = uri.getPath();
            if (uri.getQuery() == null && path != null && path.lastIndexOf('/') > 0)
                text = text.substring(0, scheme) + path.substring(0, path.lastIndexOf('/'));
        }
        view.setText(text);
        view.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    private static final java.util.WeakHashMap<View, Boolean> choiceWindows =
            new java.util.WeakHashMap<View, Boolean>();

    /** Native choice lists keep their adapter, checked state and click callbacks. */
    public static void dialogChoices(android.app.Dialog dialog) {
        if (dialog.getWindow() == null) return;
        final View root = dialog.getWindow().getDecorView();
        if (!choiceWindows.containsKey(root)) {
            choiceWindows.put(root, Boolean.TRUE);
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() { decorateChoices(root, false); }
            });
        }
        decorateChoices(root, false);
    }

    private static void decorateChoices(View view, boolean singleChoice) {
        if (view instanceof android.widget.ListView)
            singleChoice = ((android.widget.ListView) view).getChoiceMode()
                    == android.widget.ListView.CHOICE_MODE_SINGLE;
        if (singleChoice && view instanceof android.widget.CheckedTextView) {
            android.widget.CheckedTextView item = (android.widget.CheckedTextView) view;
            if (!(item.getCheckMarkDrawable() instanceof ChoiceMark)) {
                item.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                item.setCompoundDrawablePadding(0);
                int inset = pixels(item, TypedValue.COMPLEX_UNIT_DIP, 24);
                item.setPaddingRelative(inset, item.getPaddingTop(), inset, item.getPaddingBottom());
                item.setCheckMarkDrawable(new ChoiceMark(item));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                decorateChoices(group.getChildAt(i), singleChoice);
        }
    }

    private static final class ChoiceMark extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(3);
        private final float density;
        private final int active, inactive;
        private int alpha = 255;
        ChoiceMark(View view) {
            density = view.getResources().getDisplayMetrics().density;
            Context context = view.getContext();
            active = context.getColor(view.getResources().getIdentifier(
                    "material_ui_primary", "color", context.getPackageName()));
            inactive = context.getColor(view.getResources().getIdentifier(
                    "material_ui_on_surface_variant", "color", context.getPackageName()));
        }
        @Override public int getIntrinsicWidth() { return Math.round(24 * density); }
        @Override public int getIntrinsicHeight() { return Math.round(24 * density); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void draw(android.graphics.Canvas canvas) {
            boolean checked = false;
            for (int state : getState()) if (state == android.R.attr.state_checked) checked = true;
            float x = getBounds().exactCenterX(), y = getBounds().exactCenterY();
            paint.setColor(checked ? active : inactive);
            paint.setAlpha(alpha);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(2 * density);
            canvas.drawCircle(x, y, 9 * density, paint);
            if (checked) {
                paint.setStyle(android.graphics.Paint.Style.FILL);
                canvas.drawCircle(x, y, 5 * density, paint);
            }
        }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter); invalidateSelf();
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    public static void recoveryAction(com.google.android.material.button.MaterialButton button) {
        Context context = button.getContext();
        android.content.res.Resources resources = button.getResources();
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getColor(
                resources.getIdentifier("material_ui_primary", "color", context.getPackageName()))));
        button.setTextColor(context.getColor(resources.getIdentifier(
                "material_ui_on_primary", "color", context.getPackageName())));
        button.setMinHeight(pixels(button, TypedValue.COMPLEX_UNIT_DIP, 56));
        button.setAllCaps(false);
    }

    public static void primary(TextView view) { text(view, false, false); }
    public static void secondary(TextView view) { text(view, true, false); }
    public static void thumbnail(TextView view) {
        text(view, true, true);
        view.setTextColor(0xffffffff);
    }
    public static View legacyThumbnail(Context context) {
        int layout = context.getResources().getIdentifier("recyclerview_item", "layout", context.getPackageName());
        View view = LayoutInflater.from(context).inflate(layout, null, false);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                pixels(view, TypedValue.COMPLEX_UNIT_DIP, 72)));
        int caption = context.getResources().getIdentifier("videoTime", "id", context.getPackageName());
        int legacyCaption = context.getResources().getIdentifier("video_time_text_id", "id", context.getPackageName());
        view.findViewById(caption).setId(legacyCaption);
        for (String[] alias : new String[][] {
                {"imgSelect", "select_icon_img_id"}, {"lastClick", "select_bg_img_id"}}) {
            int current = context.getResources().getIdentifier(alias[0], "id", context.getPackageName());
            int legacy = context.getResources().getIdentifier(alias[1], "id", context.getPackageName());
            view.findViewById(current).setId(legacy);
        }
        return view;
    }
    public static void dividerHeight(View view, ViewGroup.LayoutParams params) {
        params.height = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 17);
    }

    public static void dialogContent(View view) {
        int horizontal = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 24);
        int vertical = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 16);
        view.setPaddingRelative(horizontal, vertical, horizontal, 0);
    }
    private static android.graphics.drawable.GradientDrawable sliderShape(View view, int color, int width, int height) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(view.getContext().getColor(color));
        shape.setSize(pixels(view, TypedValue.COMPLEX_UNIT_DIP, width), pixels(view, TypedValue.COMPLEX_UNIT_DIP, height));
        shape.setCornerRadius(pixels(view, TypedValue.COMPLEX_UNIT_DIP, Math.min(width, height) / 2f));
        return shape;
    }
    public static void fusion(android.graphics.Canvas canvas, View view, int progress) {
        float inset = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 16);
        float stroke = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 4);
        float end = view.getWidth() - inset;
        float y = view.getHeight() - inset;
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        int track = view.getResources().getIdentifier("material_ui_surface_container_high", "color", view.getContext().getPackageName());
        int accent = view.getResources().getIdentifier("material_ui_primary", "color", view.getContext().getPackageName());
        paint.setColor(view.getContext().getColor(track));
        canvas.drawLine(inset, y, end, y, paint);
        if (progress > 0) {
            paint.setColor(view.getContext().getColor(accent));
            float fraction = Math.max(0, Math.min(100, progress)) / 100f;
            canvas.drawLine(inset, y, inset + (end - inset) * fraction, y, paint);
        }
    }

    private static final java.util.WeakHashMap<android.app.Activity, Long> previewPreparation = new java.util.WeakHashMap<>();

    public static boolean canRetryPreview(android.app.Activity activity, boolean preparing) {
        if (activity.isFinishing() || !activity.hasWindowFocus()) return false;
        if (!preparing) {
            previewPreparation.remove(activity);
            return true;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        Long since = previewPreparation.get(activity);
        if (since == null) {
            previewPreparation.put(activity, now);
            return false;
        }
        if (now - since < 15000) return false;
        previewPreparation.remove(activity);
        return true;
    }

    public static boolean canOpenVideo(View view) {
        if (!view.isAttachedToWindow()) return false;
        Context context = view.getContext();
        for (int depth = 0; depth < 16; depth++) {
            if (context instanceof android.app.Activity) {
                android.app.Activity owner = (android.app.Activity) context;
                return !owner.isFinishing() && !owner.isDestroyed();
            }
            if (!(context instanceof android.content.ContextWrapper)) break;
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return true;
    }

    public static void restorePlaybackAfterLayout(final View view, final PlaybackRestoration owner) {
        // Prepared listeners run before the native view applies the video dimensions.
        // Let that layout finish before seeking a single frame in a paused decoder.
        view.postOnAnimation(new Runnable() {
            @Override public void run() {
                view.postOnAnimation(new Runnable() {
                    @Override public void run() {
                        if (canOpenVideo(view)) owner.materialUiRestorePlayback();
                    }
                });
            }
        });
    }

    public static void restorePlaybackSurface(View view) {
        if (!canOpenVideo(view)) return;
        Context context = view.getContext();
        for (int depth = 0; depth < 16; depth++) {
            if (context instanceof PlaybackRestoration) {
                ((PlaybackRestoration) context).materialUiRestorePlayback();
                return;
            }
            if (!(context instanceof android.content.ContextWrapper)) return;
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) return;
            context = base;
        }
    }

    public static boolean canShowPlaybackError(View view) {
        if (!canOpenVideo(view) || !view.isShown()
                || view.getWindowVisibility() != View.VISIBLE) return false;
        Context context = view.getContext();
        for (int depth = 0; depth < 16; depth++) {
            if (context instanceof android.app.Activity) {
                // A fast load failure may precede first window focus. Attachment,
                // visibility and the finishing check still reject obsolete screens.
                return true;
            }
            if (!(context instanceof android.content.ContextWrapper)) return false;
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) return false;
            context = base;
        }
        return false;
    }

    public static CharSequence playbackErrorMessage(Context context) {
        int message = context.getResources().getIdentifier(
                "material_ui_playback_error", "string", context.getPackageName());
        return message == 0 ? "Unable to play this video. Open it again to retry."
                : context.getString(message);
    }

    private static final java.util.WeakHashMap<android.app.Activity, Boolean> previewErrors = new java.util.WeakHashMap<>();

    public static void previewReady(android.app.Activity activity) {
        PreviewBusy.playerReady(activity);
        previewErrors.remove(activity);
        previewPreparation.remove(activity);
    }

    public static void previewError(android.app.Activity activity, String fallback) {
        if (activity.isFinishing() || !activity.hasWindowFocus() || previewErrors.containsKey(activity)) return;
        previewErrors.put(activity, Boolean.TRUE);
        int message = activity.getResources().getIdentifier("material_ui_video_unavailable", "string", activity.getPackageName());
        android.widget.Toast.makeText(activity, message == 0 ? fallback : activity.getString(message),
                android.widget.Toast.LENGTH_LONG).show();
    }

    public static void storageVisibility(View row, int visibility) {
        // A missing slot must not reserve a transient row between recorder updates.
        row.setVisibility(visibility == View.INVISIBLE ? View.GONE : visibility);
    }

    public static void deviceEntry(View row, boolean filtered) {
        Context context = row.getContext();
        int surface = row.getResources().getIdentifier("material_ui_surface_container_low", "color", context.getPackageName());
        int outline = row.getResources().getIdentifier("material_ui_outline_variant", "color", context.getPackageName());
        android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
        card.setColor(context.getColor(surface));
        card.setCornerRadius(pixels(row, TypedValue.COMPLEX_UNIT_DIP, 20));
        card.setStroke(Math.max(1, pixels(row, TypedValue.COMPLEX_UNIT_DIP, 1)), context.getColor(outline));
        row.setBackground(card);
        row.setAlpha(filtered ? 0.6f : 1f);
    }

    public static void compactSignal(View row) {
        int signal = row.getResources().getIdentifier("rssi", "id", row.getContext().getPackageName());
        View candidate = row.findViewById(signal);
        if (candidate instanceof TextView) {
            candidate.setVisibility(android.text.TextUtils.isEmpty(((TextView) candidate).getText()) ? View.GONE : View.VISIBLE);
        }
    }

    public static void formHint(TextView view) {
        int gap = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 8);
        view.setPadding(0, gap, 0, gap);
        view.setGravity(android.view.Gravity.START);
    }
    public static void slider(View view) {
        int vertical = pixels(view, TypedValue.COMPLEX_UNIT_DIP, 8);
        view.setPadding(0, vertical, 0, vertical);
        view.setMinimumHeight(pixels(view, TypedValue.COMPLEX_UNIT_DIP, 48));
        if (view instanceof android.widget.SeekBar) {
            android.widget.SeekBar slider = (android.widget.SeekBar) view;
            int primary = view.getResources().getIdentifier("material_ui_primary", "color", view.getContext().getPackageName());
            int surface = view.getResources().getIdentifier("material_ui_surface_container_high", "color", view.getContext().getPackageName());
            android.graphics.drawable.GradientDrawable track = sliderShape(view, surface, 16, 16);
            android.graphics.drawable.GradientDrawable active = sliderShape(view, primary, 16, 16);
            android.graphics.drawable.ClipDrawable progress = new android.graphics.drawable.ClipDrawable(
                    active, android.view.Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[] {track, progress});
            layers.setId(0, android.R.id.background);
            layers.setId(1, android.R.id.progress);
            if (Build.VERSION.SDK_INT >= 23) {
                for (int index = 0; index < 2; index++) {
                    layers.setLayerHeight(index, pixels(view, TypedValue.COMPLEX_UNIT_DIP, 16));
                    layers.setLayerGravity(index, android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.FILL_HORIZONTAL);
                }
            }
            slider.setProgressDrawable(layers);
            slider.setThumb(sliderShape(view, primary, 4, 32));
            slider.setThumbOffset(pixels(view, TypedValue.COMPLEX_UNIT_DIP, 2));
            slider.setSplitTrack(false);
        }
    }
    public static void brandImage(android.widget.ImageView view, int ignoredLegacyResource) {
        int drawable = view.getResources().getIdentifier("material_ui_brand_mark", "drawable", view.getContext().getPackageName());
        view.setImageResource(drawable);
        view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
    }

}
