package local.materialui;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.TextView;

/** Keep native diagnostic values separate from their existing action labels. */
public final class DiagnosticValue extends TextView {
    public DiagnosticValue(Context context, AttributeSet attrs) {
        super(context, attrs);
        UiMetrics.secondary(this);
    }

    private String text(String name) {
        int id = getResources().getIdentifier(name, "string", getContext().getPackageName());
        return id == 0 ? "" : getContext().getString(id);
    }

    private String label(String name, String value) {
        return text(name).replaceAll("[\\s:：]+$", "") + ": " + value;
    }

    @Override public void setText(CharSequence value, BufferType type) {
        String name = "";
        if (getId() != NO_ID) {
            try { name = getResources().getResourceEntryName(getId()); }
            catch (android.content.res.Resources.NotFoundException ignored) { }
        }
        if ("devModel".equals(name)) {
            value = label("build_model", Build.MODEL) + "\n"
                    + label("build_manufacturer", Build.MANUFACTURER) + "\n"
                    + label("build_version_sdk_int", Build.VERSION.RELEASE);
        } else if (value != null) {
            String result = value.toString();
            String prefix = "";
            if ("tvConnectModelToggle".equals(name)) prefix = text("preview_model");
            if ("tvPlackBackModelToggle".equals(name)) prefix = text("back_play_model");
            if ("tvPreviewModelToggle".equals(name)) prefix = text("rtsp_model");
            if ("tvDecodeToggle".equals(name)) prefix = text("current_codec_model");
            if (!prefix.isEmpty() && result.startsWith(prefix)) result = result.substring(prefix.length());
            value = result.replaceFirst("^[\\s:：]+", "");
        }
        super.setText(value, type);
    }
}
