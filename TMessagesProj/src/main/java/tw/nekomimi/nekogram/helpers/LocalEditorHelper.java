package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;


public class LocalEditorHelper {

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("local_editor_plus_edits", Context.MODE_PRIVATE);
    }

    public static void saveEdit(long dialogId, int messageId, String text, int date) {
        String key = dialogId + "_" + messageId;
        getPreferences().edit()
                .putString(key + "_text", text)
                .putInt(key + "_date", date)
                .apply();
    }

    public static String getEditText(long dialogId, int messageId) {
        String key = dialogId + "_" + messageId;
        return getPreferences().getString(key + "_text", null);
    }

    public static int getEditDate(long dialogId, int messageId) {
        String key = dialogId + "_" + messageId;
        return getPreferences().getInt(key + "_date", 0);
    }

    public static boolean hasEdit(long dialogId, int messageId) {
        String key = dialogId + "_" + messageId;
        return getPreferences().contains(key + "_text");
    }

    public static void applyLocalEdit(long dialogId, TLRPC.Message message) {
        if (message == null) return;
        String key = dialogId + "_" + message.id;
        SharedPreferences prefs = getPreferences();
        if (prefs.contains(key + "_text")) {
            String text = prefs.getString(key + "_text", null);
            int date = prefs.getInt(key + "_date", 0);
            if (text != null) {
                message.message = text;
            }
            if (date != 0) {
                message.date = date;
            }
        }
    }
}
