package local.materialui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Original-file saving and lossless MP4 preparation for transport-stream sharing. */
public final class MediaFiles {
    private static final int EXPORT = 27481;
    private static final String PENDING = "local.materialui.exportSource";
    private static Toast feedback;
    private MediaFiles() {}
    private static final class Source {
        final String location, name;
        Source(String location) {
            this.location = location;
            String path = Uri.parse(location).getLastPathSegment();
            name = path == null || path.isEmpty() ? "photo.jpg" : path.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        }
        String mime() {
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            return mime == null ? "application/octet-stream" : mime;
        }
        boolean remote() { return location.startsWith("http://") || location.startsWith("https://"); }
        InputStream open() throws Exception {
            if (!remote()) return new FileInputStream(location);
            final HttpURLConnection connection = (HttpURLConnection) new URL(location).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status != 200) { connection.disconnect(); throw new java.io.IOException("Media response " + status); }
            return new java.io.FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws java.io.IOException {
                    try { super.close(); } finally { connection.disconnect(); }
                }
            };
        }
    }
    private static int id(Activity a, String name) { return a.getResources().getIdentifier(name, "id", a.getPackageName()); }
    private static String text(Activity a, String name) { return ScanConnection.text(a, name); }
    private static final class Transfer implements Mp4Share.Control {
        volatile boolean cancelled;
        private boolean finished;
        private boolean resumed = true;
        private Runnable pending;
        private Thread worker;
        private final android.app.Application.ActivityLifecycleCallbacks lifecycle;
        private final ExpressiveProgressDialog dialog;
        private final Activity owner;
        private final View menuContent;
        private boolean menuHidden;
        private float menuDim;
        private android.graphics.drawable.Drawable menuBackground;
        private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        private final Runnable reveal;
        Transfer(final Activity activity, final View action) {
            if (feedback != null) { feedback.cancel(); feedback = null; }
            owner = activity;
            lifecycle = new android.app.Application.ActivityLifecycleCallbacks() {
                public void onActivityDestroyed(Activity value) { if (value == owner && action != null) cancel(); }
                public void onActivityCreated(Activity value, android.os.Bundle state) {}
                public void onActivityStarted(Activity value) {}
                public void onActivityResumed(Activity value) {
                    if (value == owner) {
                        resumed = true; Runnable ready = pending; pending = null;
                        if (ready != null) ready.run();
                    }
                }
                public void onActivityPaused(Activity value) { if (value == owner) resumed = false; }
                public void onActivityStopped(Activity value) {}
                public void onActivitySaveInstanceState(Activity value, android.os.Bundle state) {}
            };
            activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
            menuContent = isMenu(activity) ? activity.findViewById(android.R.id.content) : null;
            dialog = new ExpressiveProgressDialog(activity);
            dialog.setMessage(text(activity, "loading"));
            dialog.setButton(android.content.DialogInterface.BUTTON_NEGATIVE, text(activity, "cancel"),
                    new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface value, int which) {
                    cancel(); if (action != null) action.setEnabled(true);
                }
            });
            dialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                @Override public void onCancel(android.content.DialogInterface value) {
                    cancel(); if (action != null) action.setEnabled(true);
                }
            });
            dialog.setCanceledOnTouchOutside(false);
            reveal = new Runnable() { @Override public void run() {
                if (!finished && !activity.isFinishing() && !activity.isDestroyed()) {
                    if (menuContent != null) {
                        menuDim = owner.getWindow().getAttributes().dimAmount;
                        menuBackground = owner.getWindow().getDecorView().getBackground();
                        owner.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        menuContent.setVisibility(View.INVISIBLE);
                        owner.getWindow().setDimAmount(0); menuHidden = true;
                    }
                    dialog.show();
                }
            }};
            // Fast local exports should not flash a loading window.
            handler.postDelayed(reveal, 250);
        }
        @Override public boolean cancelled() { return cancelled; }
        void cancel() {
            cancelled = true; if (worker != null) worker.interrupt();
            Runnable abandoned = pending; pending = null; finish();
            if (abandoned != null) abandoned.run();
        }
        void deliver(Runnable ready) {
            if (resumed || cancelled || owner.isFinishing() || owner.isDestroyed()) ready.run();
            else pending = ready;
        }
        void message(String key) { dialog.setMessage(text(owner, key)); }
        void start(Runnable work, String name) { worker = new Thread(work, name); worker.start(); }
        void finish() {
            if (finished) return;
            owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
            finished = true; handler.removeCallbacks(reveal);
            if (dialog.isShowing()) dialog.dismiss();
            if (menuHidden) {
                if (!owner.isFinishing() && !owner.isDestroyed()) {
                    menuContent.setVisibility(View.VISIBLE);
                    owner.getWindow().setBackgroundDrawable(menuBackground);
                    owner.getWindow().setDimAmount(menuDim);
                }
                menuHidden = false;
            }
        }
    }
    private static boolean isMenu(Activity a) { return a.findViewById(id(a, "ibSave")) != null; }
    private static Source current(Activity a) {
        try {
            List<?> files = (List<?>) Class.forName("com.hisilicon.cameralib.utils.GlobalData")
                    .getMethod("getArrayPathlist").invoke(null);
            Object item = files.get(a.getIntent().getIntExtra("CurIndex", 0));
            String local = (String) item.getClass().getMethod("getFileLocalPath").invoke(item);
            String remote = (String) item.getClass().getMethod("getFileHttpPath").invoke(item);
            View progress = a.findViewById(id(a, "ibPBarProgress"));
            boolean complete = progress == null || progress.getVisibility() != View.VISIBLE;
            return new Source(complete && local != null && new File(local).isFile() ? local : remote);
        } catch (Exception unavailable) { return null; }
    }
    public static void photo(final Activity a) {
        final Source source = current(a);
        bind(a, a.findViewById(id(a, "material_ui_photo_save")), source, false, true);
        bind(a, a.findViewById(id(a, "material_ui_photo_share")), source, true, true);
    }
    public static void menu(final Activity a) {
        a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        String path = a.getIntent().getStringExtra("OriFileName");
        if (path == null || path.isEmpty()) return;
        Source source = new Source(path);
        View save = a.findViewById(id(a, "ibSave")), share = a.findViewById(id(a, "ibshare"));
        if (!source.remote() && !new File(path).isFile()) return;
        View download = a.findViewById(id(a, "ibdownload"));
        boolean nativeDownload = source.remote() && download != null;
        if (download != null) {
            download.setVisibility(nativeDownload ? View.VISIBLE : View.GONE);
            if (nativeDownload) {
                actionText(download, text(a, "action_save_to_phone"));
                if (save != null && download.getParent() == save.getParent()
                        && download.getParent() instanceof android.view.ViewGroup) {
                    android.view.ViewGroup group = (android.view.ViewGroup) download.getParent();
                    int before = group.indexOfChild(save);
                    if (group.indexOfChild(download) > before) {
                        group.removeView(download); group.addView(download, before);
                    }
                }
            }
        }
        if (save != null) save.setVisibility(nativeDownload ? View.GONE : View.VISIBLE);
        if (share != null) share.setVisibility(View.VISIBLE);
        // Keep the native recorder download and its offline-library bookkeeping.
        if (!nativeDownload) bind(a, save, source, false, false);
        bind(a, share, source, true, false);
    }
    private static void actionText(View view, String label) {
        if (view instanceof TextView) ((TextView) view).setText(label);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) actionText(group.getChildAt(i), label);
        }
    }
    private static void bind(final Activity a, final View action, final Source selected, final boolean share, final boolean viewer) {
        if (action == null) return;
        action.setEnabled(selected != null);
        action.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                final Source source = viewer ? current(a) : selected;
                if (source == null || !action.isEnabled()) return;
                if (share) share(a, source, action);
                else {
                    a.getIntent().putExtra(PENDING, source.location);
                    Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    create.addCategory(Intent.CATEGORY_OPENABLE);
                    create.setType(source.mime());
                    create.putExtra(Intent.EXTRA_TITLE, source.name);
                    try { a.startActivityForResult(create, EXPORT); }
                    catch (android.content.ActivityNotFoundException missing) { a.getIntent().removeExtra(PENDING); failure(a); }
                }
            }
        });
    }
    private static void copy(Source source, OutputStream output, Transfer transfer) throws Exception {
        if (output == null) throw new java.io.IOException("No destination stream");
        try (OutputStream destination = output) {
            if (transfer.cancelled) throw new java.io.InterruptedIOException("Export cancelled");
            try (InputStream input = source.open()) {
                byte[] buffer = new byte[32768]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (transfer.cancelled) throw new java.io.InterruptedIOException("Export cancelled");
                    destination.write(buffer, 0, count);
                }
                if (transfer.cancelled) throw new java.io.InterruptedIOException("Export cancelled");
            }
        }
    }
    public static void result(final Activity a, int request, int result, Intent data) {
        if (request != EXPORT) return;
        String location = a.getIntent().getStringExtra(PENDING);
        a.getIntent().removeExtra(PENDING);
        final Source source = location == null ? null : new Source(location);
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || source == null) return;
        final Uri uri = data.getData();
        final Transfer transfer = new Transfer(a, null);
        transfer.start(new Runnable() { @Override public void run() {
            try {
                copy(source, a.getContentResolver().openOutputStream(uri, "w"), transfer);
                a.runOnUiThread(new Runnable() { @Override public void run() {
                    transfer.finish();
                    if (!a.isDestroyed() && !transfer.cancelled) {
                        feedback(a, "save_file_success", Toast.LENGTH_SHORT);
                        if (isMenu(a)) a.finish();
                    }
                }});
            } catch (Exception error) {
                a.runOnUiThread(new Runnable() { @Override public void run() {
                    transfer.finish(); if (!transfer.cancelled) failure(a);
                }});
            }
        }}, "media-export");
    }
    private static void share(final Activity a, final Source source, final View action) {
        action.setEnabled(false);
        final Transfer transfer = new Transfer(a, action);
        final boolean remux = Mp4Remux.needed(source.name);
        if (remux) transfer.message("material_ui_preparing_mp4");
        transfer.start(new Runnable() { @Override public void run() {
            File output = null, input = null, unique = null;
            boolean handedOff = false;
            try {
                File folder = new File(a.getCacheDir(), "media-share");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("No cache directory");
                // Keep only recent exports, preserving URI grants for shares still in use.
                File[] old = folder.listFiles();
                if (old != null) for (File f : old) {
                    if (System.currentTimeMillis() - f.lastModified() <= 86400000L) continue;
                    File[] children = f.listFiles();
                    if (children != null) for (File child : children) if (child.isFile()) child.delete();
                    f.delete();
                }
                unique = new File(folder, java.util.UUID.randomUUID().toString());
                if (!unique.mkdir()) throw new java.io.IOException("No share directory");
                final String sharedName = remux ? Mp4Remux.name(source.name) : source.name;
                output = new File(unique, sharedName);
                if (remux) {
                    input = new File(unique, "source.ts");
                    copy(source, new java.io.FileOutputStream(input), transfer);
                    Mp4Share.prepare(a, input, output, transfer);
                } else copy(source, new java.io.FileOutputStream(output), transfer);
                if (transfer.cancelled) throw new java.io.InterruptedIOException("Share cancelled");
                final Uri uri = (Uri) Class.forName("androidx.core.content.FileProvider")
                        .getMethod("getUriForFile", android.content.Context.class, String.class, File.class)
                        .invoke(null, a, a.getPackageName() + ".fileProvider", output);
                final File sharedFile = output, sharedFolder = unique;
                a.runOnUiThread(new Runnable() { @Override public void run() { transfer.deliver(new Runnable() {
                    @Override public void run() {
                    action.setEnabled(true);
                    transfer.finish();
                    if (a.isFinishing() || a.isDestroyed() || transfer.cancelled) {
                        sharedFile.delete(); sharedFolder.delete(); return;
                    }
                    Intent intent = new Intent(Intent.ACTION_SEND).setType(remux ? "video/mp4" : source.mime());
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setClipData(ClipData.newRawUri(sharedName, uri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        a.startActivity(Intent.createChooser(intent, text(a, "share")));
                        if (isMenu(a)) a.finish();
                    }
                    catch (android.content.ActivityNotFoundException missing) {
                        sharedFile.delete(); sharedFolder.delete(); failure(a);
                    }
                }}); }});
                handedOff = true;
            } catch (Exception error) {
                a.runOnUiThread(new Runnable() { @Override public void run() { transfer.deliver(new Runnable() {
                    @Override public void run() {
                    action.setEnabled(true); transfer.finish();
                    if (!transfer.cancelled) {
                        if (remux) mp4Failure(a); else failure(a);
                    }
                }}); }});
            } finally {
                if (input != null) input.delete();
                if (!handedOff) {
                    if (output != null) output.delete();
                    if (unique != null) unique.delete();
                }
            }
        }}, "media-share");
    }
    private static void mp4Failure(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(a);
        builder.setTitle("MP4"); builder.setMessage(text(a, "material_ui_mp4_failed"));
        builder.setPositiveButton(text(a, "close"), null); UiBridge.show(builder);
    }
    private static void failure(Activity a) {
        if (!a.isFinishing() && !a.isDestroyed()) feedback(a, "download_finish_failed", Toast.LENGTH_LONG);
    }
    private static void feedback(Activity a, String message, int duration) {
        if (feedback != null) feedback.cancel();
        feedback = Toast.makeText(a.getApplicationContext(), text(a, message), duration);
        feedback.show();
    }
}
