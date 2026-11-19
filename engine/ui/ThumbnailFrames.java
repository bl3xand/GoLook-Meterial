package local.materialui;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

/** Keep extracted video frames proportional before they enter the native cache. */
public final class ThumbnailFrames {
    private ThumbnailFrames() {}

    public static String cacheKey(String key) {
        return key == null ? null : "ui-thumb-aspect1-" + key;
    }

    public static Bitmap extract(String path, int width, int height) {
        if (path == null || path.isEmpty() || width <= 0 || height <= 0) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap frame = null;
        try {
            retriever.setDataSource(path);
            frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            float scale = Math.min(1f, Math.max((float) width / frame.getWidth(),
                    (float) height / frame.getHeight()));
            Bitmap result = Bitmap.createScaledBitmap(frame,
                    Math.max(1, Math.round(frame.getWidth() * scale)),
                    Math.max(1, Math.round(frame.getHeight() * scale)), true);
            if (result != frame) frame.recycle();
            frame = null;
            return result;
        } catch (RuntimeException unavailable) {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            return null;
        } finally {
            try { retriever.release(); }
            catch (java.io.IOException | RuntimeException ignored) { }
        }
    }
}
