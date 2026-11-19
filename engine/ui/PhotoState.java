package local.materialui;

import android.app.Activity;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Preserve only the opened photo, avoiding a large gallery in saved state. */
public final class PhotoState {
    private static final String SELECTED = "local.materialui.openPhoto";
    private static final String EXPORT = "local.materialui.exportSource";
    private static final String GLOBAL = "com.hisilicon.cameralib.utils.GlobalData";
    private static final String ITEM = "com.hisilicon.cameralib.device.bean.FileItem";
    private static final String[] STRINGS = {"FileHttpPath", "FileLocalPath", "FileName",
            "StartTime", "EndTime", "ThmPath", "TranscodePath", "VideoGpsHttpPath",
            "VideoGpsLocalPath", "VideoGpsFileName", "VideoAdasHttpPath",
            "VideoAdasLocalPath", "VideoAdasFileName"};
    private static final String[] INTS = {"Duration", "Type", "CameraType"};
    private static final String[] LONGS = {"StartTimeStamp", "EndTimeStamp", "FileSize"};

    private PhotoState() {}

    private static List<?> files() throws ReflectiveOperationException {
        return (List<?>) Class.forName(GLOBAL).getMethod("getArrayPathlist").invoke(null);
    }

    public static void save(Activity activity, Bundle state) {
        String export = activity.getIntent().getStringExtra(EXPORT);
        if (export != null) state.putString(EXPORT, export);
        try {
            List<?> files = files();
            int index = activity.getIntent().getIntExtra("CurIndex", 0);
            if (files == null || index < 0 || index >= files.size()) return;
            Object item = files.get(index);
            Class<?> type = item.getClass();
            Bundle selected = new Bundle();
            for (String key : STRINGS)
                selected.putString(key, (String) type.getMethod("get" + key).invoke(item));
            for (String key : INTS)
                selected.putInt(key, ((Number) type.getMethod("get" + key).invoke(item)).intValue());
            for (String key : LONGS)
                selected.putLong(key, ((Number) type.getMethod("get" + key).invoke(item)).longValue());
            // Do not parcel the native item: its reader and writer have different fields.
            state.putBundle(SELECTED, selected);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { }
    }

    public static boolean restore(Activity activity, Bundle state) {
        try {
            if (state != null) {
                state.setClassLoader(activity.getClassLoader());
                String export = state.getString(EXPORT);
                if (export != null) activity.getIntent().putExtra(EXPORT, export);
            }
            List<?> files = files();
            int index = activity.getIntent().getIntExtra("CurIndex", 0);
            if (files != null && index >= 0 && index < files.size()) return true;
            Bundle selected = state == null ? null : state.getBundle(SELECTED);
            if (selected == null) return false;
            Class<?> type = Class.forName(ITEM);
            Object item = type.getConstructor().newInstance();
            for (String key : STRINGS)
                type.getMethod("set" + key, String.class).invoke(item, selected.getString(key));
            for (String key : INTS)
                type.getMethod("set" + key, int.class).invoke(item, selected.getInt(key));
            for (String key : LONGS)
                type.getMethod("set" + key, long.class).invoke(item, selected.getLong(key));
            ArrayList<Object> restored = new ArrayList<>();
            restored.add(item);
            Class.forName(GLOBAL).getMethod("setArrayPathlist", ArrayList.class).invoke(null, restored);
            activity.getIntent().putExtra("CurIndex", 0);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }
}
