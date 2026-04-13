package tw.nekomimi.nekogram.helpers;

import androidx.annotation.Keep;

@Keep
public class QuickSettingEntry {
    public static final int TYPE_SWITCH = 0;
    public static final int TYPE_DIALOG = 1;
    public static final int TYPE_NAVIGATE = 2;

    public String key;
    public String title;
    public String iconResName;
    public int iconColor;
    public int type;
    public String activityClass;

    public QuickSettingEntry() {
    }

    public QuickSettingEntry(String key, String title, String iconResName, int iconColor, int type, String activityClass) {
        this.key = key;
        this.title = title;
        this.iconResName = iconResName;
        this.iconColor = iconColor;
        this.type = type;
        this.activityClass = activityClass;
    }
}
