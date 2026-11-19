package local.materialui;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.io.File;

/** Private, bound-only native worker; no encoder, network input or boot entry point. */
public final class ShareRemuxService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Job current;
    private static final class Job {
        String id; File input, output; Messenger reply;
    }
    private final Messenger endpoint = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if (message.what == Mp4Share.CANCEL) {
                if (current != null && current.id.equals(message.getData().getString("id"))) closeWorker();
            } else if (message.what == Mp4Share.START) start(message);
        }
    });
    @Override public IBinder onBind(Intent intent) {
        if (!Mp4Share.worker(this)) throw new IllegalStateException("Wrong remux process");
        return endpoint.getBinder();
    }
    private void start(Message message) {
        final Job job = new Job(); job.id = message.getData().getString("id"); job.reply = message.replyTo;
        try {
            job.input = new File(message.getData().getString("input")).getCanonicalFile();
            job.output = new File(message.getData().getString("output")).getCanonicalFile();
            File root = new File(getCacheDir(), "media-share").getCanonicalFile();
            File folder = job.input.getParentFile();
            if (job.id == null || job.reply == null || current != null || !job.input.isFile()
                    || !root.equals(folder.getParentFile()) || !folder.equals(job.output.getParentFile())
                    || job.input.equals(job.output) || !job.output.getName().endsWith(".mp4"))
                throw new IllegalArgumentException("Invalid remux request");
        } catch (Exception invalid) { reply(job, false); return; }
        current = job;
        new Thread(new Runnable() { @Override public void run() {
            boolean success = false;
            try {
                if (!Mp4Remux.transport(job.input)) throw new java.io.IOException("Invalid transport stream");
                Class<?> type = Class.forName("io.microshow.rxffmpeg.RxFFmpegInvoke");
                Object engine = type.getMethod("getInstance").invoke(null);
                int code = ((Number) type.getMethod("runFFmpegCmd", String[].class)
                        .invoke(engine, (Object) Mp4Remux.command(job.input.getPath(), job.output.getPath()))).intValue();
                success = code == 0 && Mp4Remux.complete(job.output);
            } catch (Throwable unavailable) { }
            final boolean complete = success;
            handler.post(new Runnable() { @Override public void run() {
                if (current != job) return;
                current = null; reply(job, complete);
            }});
        }}, "mp4-remux").start();
    }
    private void reply(Job job, boolean ok) {
        if (job.reply == null) return;
        Message result = Message.obtain(null, Mp4Share.RESULT);
        Bundle data = new Bundle(); data.putString("id", job.id); data.putBoolean("ok", ok); result.setData(data);
        try { job.reply.send(result); } catch (RemoteException gone) { }
    }
    private void closeWorker() {
        stopSelf();
        // Only this dedicated process can be stopped; the main app and trim engine are separate.
        if (Mp4Share.worker(this)) android.os.Process.killProcess(android.os.Process.myPid());
    }
    @Override public boolean onUnbind(Intent intent) { closeWorker(); return false; }
    @Override public void onDestroy() { super.onDestroy(); closeWorker(); }
}
