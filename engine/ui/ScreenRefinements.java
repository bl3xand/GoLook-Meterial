package local.materialui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared recovery actions and read-only document navigation. */
public final class ScreenRefinements {
    private ScreenRefinements() {}

    private static int id(Activity activity, String type, String name) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }

    private static String text(Activity activity, String name) {
        return activity.getString(id(activity, "string", name));
    }

    public static String confirmationMessage(Activity activity, int action, String fallback) {
        return action == 3 ? text(activity, "material_ui_reset_question") : fallback;
    }

    public static String confirmationAction(Activity activity, int action, String fallback) {
        if (action == 2) return text(activity, "material_ui_action_format");
        if (action == 3) return text(activity, "material_ui_action_reset");
        return fallback;
    }

    public static void documents(final Activity activity) {
        final androidx.appcompat.app.AppCompatDialog dialog =
                new androidx.appcompat.app.AppCompatDialog(activity);
        View content = activity.getLayoutInflater().inflate(id(activity, "layout", "dialog_agreement"), null);
        dialog.setContentView(content);
        content.findViewById(id(activity, "id", "tvDialogTitle")).setVisibility(View.GONE);
        android.widget.Button close = content.findViewById(id(activity, "id", "leftBtn"));
        close.setText(text(activity, "close"));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        content.findViewById(id(activity, "id", "rightBtn")).setVisibility(View.GONE);
        int[] controls = {id(activity, "id", "tvUserAgreement"), id(activity, "id", "tvPrivacyAgreement")};
        for (int index = 0; index < controls.length; index++) {
            final int document = index == 0 ? 2 : 1;
            content.findViewById(controls[index]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                Intent intent = new Intent();
                intent.setClassName(activity, activity.getPackageName() + ".activitys.splash.AgreenActivity");
                intent.putExtra("agreement", document);
                dialog.dismiss();
                activity.startActivity(intent);
                }
            });
        }
        int top = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(content.getPaddingLeft(), top, content.getPaddingRight(), content.getPaddingBottom());
        dialog.show();
        UiBridge.apply(dialog);
    }

    public static void permissionState(Activity activity, boolean granted, String[] permissions) {
        ScanState.apply(activity, granted, permissions);
    }

    public static void mapLoading(final Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0
                || !(content.getChildAt(0) instanceof LinearLayout)) return;
        final LinearLayout parent = (LinearLayout) content.getChildAt(0);
        final LinearLayout status = new LinearLayout(activity);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setTag("material-ui-map-status");
        int inset = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        status.setPadding(inset, inset / 2, inset, inset / 2);
        final TextView message = new TextView(activity);
        UiMetrics.secondary(message);
        message.setText(text(activity, "loading"));
        status.addView(message);
        parent.addView(status, 1, new LinearLayout.LayoutParams(-1, -2));
        status.postDelayed(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || !status.isAttachedToWindow()
                        || status.getVisibility() != View.VISIBLE) return;
                message.setText(text(activity, "loading_failed") + "\n"
                        + text(activity, "offline_map_tips"));
                View map = activity.findViewById(id(activity, "id", "map"));
                if (map != null) map.setVisibility(View.INVISIBLE);
                com.google.android.material.button.MaterialButton settings =
                        new com.google.android.material.button.MaterialButton(activity, null);
                UiMetrics.recoveryAction(settings);
                settings.setText(text(activity, "material_ui_system_wifi_action"));
                settings.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = Math.round(12 * activity.getResources().getDisplayMetrics().density);
                status.addView(settings, params);
            }
        }, 10000);
    }

    public static void mapLoaded(Activity activity) {
        View map = activity.findViewById(id(activity, "id", "map"));
        if (map != null) map.setVisibility(View.VISIBLE);
        View status = activity.findViewById(android.R.id.content).findViewWithTag("material-ui-map-status");
        if (status != null) status.setVisibility(View.GONE);
    }

    private static boolean visibleText(View view, String value) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (visibleText(group.getChildAt(i), value)) return true;
            }
        }
        return false;
    }

    public static void albumResult(Activity activity, android.os.Message message) {
        if (message.what != 7 && message.what != 13) return;
        View root = activity.findViewById(android.R.id.content);
        if (root != null) root.setTag(id(activity, "id", "ibPreviewMenu"), message.arg2 < 0);
    }

    private static void albumHint(View view, String from, String to) {
        if (view instanceof TextView && from.contentEquals(((TextView) view).getText())) {
            TextView hint = (TextView) view;
            hint.setGravity(android.view.Gravity.CENTER);
            hint.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            hint.setText(to);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++)
                albumHint(group.getChildAt(index), from, to);
        }
    }

    public static void album(final Activity activity) {
        final View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                MediaLoading.enforce(activity);
                refreshAppearance(root);
                View menu = activity.findViewById(id(activity, "id", "ibPreviewMenu"));
                if (menu == null) return;
                String noFiles = text(activity, "no_photos_or_videos");
                View indicator = activity.findViewById(id(activity, "id", "progressBar"));
                View overlay = activity.findViewById(id(activity, "id", "layoutEmrRecording"));
                boolean loading = indicator != null && indicator.getVisibility() == View.VISIBLE
                        && overlay != null && overlay.getVisibility() != View.GONE;
                View grid = activity.findViewById(id(activity, "id", "ibImgGridView"));
                int loadingKey = id(activity, "id", "progressBar");
                if (grid != null) {
                    if (loading && grid.getVisibility() == View.VISIBLE) {
                        grid.setTag(loadingKey, Boolean.TRUE); grid.setVisibility(View.INVISIBLE);
                    } else if (!loading && Boolean.TRUE.equals(grid.getTag(loadingKey))) {
                        grid.setTag(loadingKey, null); grid.setVisibility(View.VISIBLE);
                    }
                }
                String failure = text(activity, "loading_failed") + "\n" + text(activity, "notwork_isok");
                boolean failed = Boolean.TRUE.equals(root.getTag(id(activity, "id", "ibPreviewMenu")));
                albumHint(root, failed ? noFiles : failure, failed ? failure : noFiles);
                boolean empty = visibleText(root, noFiles) || visibleText(root, failure);
                int visibility = empty || loading ? View.INVISIBLE : View.VISIBLE;
                if (menu.getVisibility() != visibility) menu.setVisibility(visibility);
            }
        });
    }

    private static void refreshAppearance(View view) {
        String type = view.getClass().getName();
        if ((type.endsWith(".ClassicsHeader") || type.endsWith(".ClassicsFooter"))
                && !"material-ui-refresh".equals(view.getTag())) {
            try {
                if (type.endsWith(".ClassicsFooter")) {
                    int surface = view.getResources().getIdentifier("material_ui_surface", "color",
                            view.getContext().getPackageName());
                    view.setBackgroundColor(view.getContext().getColor(surface));
                }
                view.getClass().getMethod("setProgressDrawable", android.graphics.drawable.Drawable.class)
                        .invoke(view, new MediaProgress(view.getContext()));
                view.getClass().getMethod("setTextSizeTitle", float.class).invoke(view, 14f);
                if (type.endsWith(".ClassicsHeader"))
                    view.getClass().getMethod("setEnableLastTime", boolean.class).invoke(view, false);
                view.setTag("material-ui-refresh");
            } catch (ReflectiveOperationException unsupported) {
                android.util.Log.w("MediaUi", "Unsupported refresh presentation", unsupported);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) refreshAppearance(group.getChildAt(i));
        }
    }

    public static void document(Activity activity, android.webkit.WebView view, String url) {
        View heading = activity.findViewById(id(activity, "id", "title_content"));
        if (heading instanceof TextView) {
            TextView title = (TextView) heading;
            title.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
            title.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL);
        }
        String prefix = "file:///android_asset/";
        if (!url.startsWith(prefix)) { view.loadUrl(url); return; }
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = activity.getAssets().open(
                    Uri.decode(url.substring(prefix.length())))) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            int surface = activity.getColor(id(activity, "color", "material_ui_surface"));
            int foreground = activity.getColor(id(activity, "color", "material_ui_on_surface"));
            String css = "<style>html,body{margin:0!important;background:#"
                    + String.format(java.util.Locale.ROOT, "%06x", surface & 0xffffff)
                    + "!important;color:#" + String.format(java.util.Locale.ROOT, "%06x", foreground & 0xffffff)
                    + "!important}body{padding:16px 24px!important;box-sizing:border-box}"
                    + "body,body *{font-family:sans-serif!important;font-size:16px!important;"
                    + "line-height:1.5!important;color:inherit!important;background-color:transparent!important;"
                    + "overflow-wrap:anywhere}p{margin:0 0 16px!important;text-indent:0!important}"
                    + "h1,h2,h3{font-size:20px!important;line-height:1.4!important;margin:16px 0!important}"
                    + "div{margin:0!important;padding:0!important}"
                    + "table{max-width:100%!important}img{max-width:100%;height:auto}</style>";
            String html = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            // Presentation only: retain the original document text and links.
            int head = html.toLowerCase(java.util.Locale.ROOT).lastIndexOf("</head>");
            html = head >= 0 ? html.substring(0, head) + css + html.substring(head) : css + html;
            view.setBackgroundColor(surface);
            view.getSettings().setTextZoom(Math.round(100 * activity.getResources().getConfiguration().fontScale));
            view.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null);
        } catch (java.io.IOException error) {
            view.loadUrl(url);
        }
    }

    public static void permissionRecovery(final Activity activity) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);
        builder.setTitle(text(activity, "missing_permissions"));
        builder.setMessage(text(activity, "rationale_ask_again"));
        builder.setPositiveButton(text(activity, "settings"), new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface dialog, int which) {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
            }
        });
        builder.setNegativeButton(text(activity, "cancel"), null);
        UiBridge.show(builder);
    }

}
