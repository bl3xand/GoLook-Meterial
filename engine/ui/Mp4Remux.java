package local.materialui;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

/** Container-only preparation: every video and audio track uses stream copy. */
public final class Mp4Remux {
    private Mp4Remux() {}
    public static boolean needed(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ts") || lower.endsWith(".mts") || lower.endsWith(".m2ts");
    }
    public static String name(String source) {
        return source.substring(0, source.lastIndexOf('.')) + ".mp4";
    }
    public static String[] command(String input, String output) {
        return new String[]{"ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "mpegts", "-i", input, "-map", "0:v", "-map", "0:a?", "-c", "copy",
                "-copyinkf", "-map_metadata", "0", "-movflags", "+faststart", "-f", "mp4", output};
    }
    public static boolean transport(File file) throws IOException {
        // Recognize TS, M2TS and FEC packet spacing before entering the native parser.
        try (RandomAccessFile stream = new RandomAccessFile(file, "r")) {
            byte[] bytes = new byte[(int) Math.min(1024, stream.length())];
            stream.readFully(bytes);
            for (int stride : new int[]{188, 192, 204}) {
                for (int start = 0; start < stride && start + stride * 2 < bytes.length; start++) {
                    if ((bytes[start] & 255) == 0x47 && (bytes[start + stride] & 255) == 0x47
                            && (bytes[start + stride * 2] & 255) == 0x47) return true;
                }
            }
        }
        return false;
    }
    public static boolean complete(File file) throws IOException {
        boolean type = false, movie = false, media = false;
        try (RandomAccessFile stream = new RandomAccessFile(file, "r")) {
            long length = stream.length(), offset = 0;
            while (offset < length) {
                if (length - offset < 8) return false;
                stream.seek(offset);
                long size = stream.readInt() & 0xffffffffL;
                int tag = stream.readInt(), header = 8;
                if (size == 1) {
                    if (length - offset < 16) return false;
                    size = stream.readLong(); header = 16;
                } else if (size == 0) size = length - offset;
                if (size < header || size > length - offset) return false;
                type |= tag == 0x66747970;
                movie |= tag == 0x6d6f6f76;
                media |= tag == 0x6d646174 && size > header;
                offset += size;
            }
        }
        return type && movie && media;
    }
}
