package local.materialui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

/** Copy only the selected connection credential when manual Wi-Fi guidance opens. */
public final class WifiPasswordClipboard {
    private WifiPasswordClipboard() {}

    public static String prepare(Context context, String password, String message) {
        if (password == null || password.isEmpty()) return message;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return message;
        try {
            ClipData clip = ClipData.newPlainText("Wi-Fi", password);
            if (Build.VERSION.SDK_INT >= 24) {
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                clip.getDescription().setExtras(extras);
            }
            clipboard.setPrimaryClip(clip);
        } catch (SecurityException unavailable) {
            return message;
        }
        int toastId = context.getResources().getIdentifier("material_ui_password_copied_toast", "string", context.getPackageName());
        android.widget.Toast.makeText(context, context.getString(toastId), android.widget.Toast.LENGTH_SHORT).show();
        int id = context.getResources().getIdentifier("material_ui_wifi_password_copied", "string", context.getPackageName());
        return (message == null || message.trim().isEmpty() ? "" : message.trim() + "\n\n")
                + context.getString(id);
    }
}
