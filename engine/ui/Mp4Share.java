package local.materialui;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** An on-demand worker process keeps native remux cancellation separate from trimming. */
public final class Mp4Share {
    static final int START = 1, CANCEL = 2, RESULT = 3;
    public interface Control { boolean cancelled(); }
    private Mp4Share() {}
    public static boolean worker(Context context) {
        String name = null;
        if (Build.VERSION.SDK_INT >= 28) name = Application.getProcessName();
        else try (InputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] bytes = new byte[256]; int count = input.read(bytes), end = 0;
            while (end < count && bytes[end] != 0) end++;
            name = new String(bytes, 0, end, "UTF-8");
        } catch (IOException unavailable) { }
        return (context.getPackageName() + ":material_share").equals(name);
    }
    public static void prepare(Context owner, final File input, final File output, Control control) throws Exception {
        final Context context = owner.getApplicationContext();
        final String id = UUID.randomUUID().toString();
        final CountDownLatch reply = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        final Messenger[] endpoint = new Messenger[1];
        final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message message) {
                if (message.what == RESULT && id.equals(message.getData().getString("id"))) {
                    result.compareAndSet(0, message.getData().getBoolean("ok") ? 1 : -1);
                    reply.countDown();
                }
            }
        });
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                synchronized (endpoint) {
                    if (closed.get()) return;
                    Messenger service = new Messenger(binder);
                    endpoint[0] = service;
                    Message request = Message.obtain(null, START);
                    Bundle data = new Bundle(); data.putString("id", id);
                    data.putString("input", input.getAbsolutePath()); data.putString("output", output.getAbsolutePath());
                    request.setData(data); request.replyTo = receiver;
                    try { service.send(request); }
                    catch (RemoteException unavailable) { failed(); }
                }
            }
            private void failed() { result.compareAndSet(0, -1); reply.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { if (!closed.get()) failed(); }
            @Override public void onBindingDied(ComponentName name) { failed(); }
            @Override public void onNullBinding(ComponentName name) { failed(); }
        };
        boolean bound = false;
        try {
            if (control.cancelled()) throw new InterruptedIOException("Share cancelled");
            bound = context.bindService(new Intent(context, ShareRemuxService.class), connection, Context.BIND_AUTO_CREATE);
            if (!bound) throw new IOException("Remux worker unavailable");
            long deadline = SystemClock.elapsedRealtime() + 600000;
            while (!reply.await(100, TimeUnit.MILLISECONDS)) {
                if (control.cancelled() || SystemClock.elapsedRealtime() > deadline)
                    throw new InterruptedIOException("Share cancelled or timed out");
            }
            if (control.cancelled()) throw new InterruptedIOException("Share cancelled");
            if (result.get() != 1 || !Mp4Remux.complete(output)) throw new IOException("Lossless MP4 preparation failed");
        } finally {
            closed.set(true);
            synchronized (endpoint) {
                if (endpoint[0] != null && result.get() != 1) {
                    Message cancel = Message.obtain(null, CANCEL); Bundle data = new Bundle(); data.putString("id", id); cancel.setData(data);
                    try { endpoint[0].send(cancel); } catch (RemoteException stopped) { }
                }
            }
            if (bound) context.unbindService(connection);
        }
    }
}
