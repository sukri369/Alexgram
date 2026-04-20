package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class QuickSettingsController {

    private static final String PREF_NAME = "quick_settings_pref";
    private static final String KEY_ITEMS = "quick_settings_items";

    private static volatile QuickSettingsController Instance;

    public static QuickSettingsController getInstance() {
        QuickSettingsController localInstance = Instance;
        if (localInstance == null) {
            synchronized (QuickSettingsController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new QuickSettingsController();
                }
            }
        }
        return localInstance;
    }

    private final List<QuickSettingEntry> quickSettings = new ArrayList<>();
    private final Gson gson = new Gson();

    private QuickSettingsController() {
        load();
    }

    private void load() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = preferences.getString(KEY_ITEMS, null);
            if (json != null) {
                Type listType = new TypeToken<ArrayList<QuickSettingEntry>>() {}.getType();
                List<QuickSettingEntry> items = gson.fromJson(json, listType);
                if (items != null) {
                    quickSettings.clear();
                    quickSettings.addAll(items);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void save() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = gson.toJson(quickSettings);
            preferences.edit().putString(KEY_ITEMS, json).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public List<QuickSettingEntry> getQuickSettings() {
        return new ArrayList<>(quickSettings);
    }

    public boolean isAdded(String key) {
        for (QuickSettingEntry entry : quickSettings) {
            if (entry.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public void addQuickSetting(QuickSettingEntry entry) {
        if (!isAdded(entry.key)) {
            quickSettings.add(entry);
            save();
        }
    }

    public void removeQuickSetting(String key) {
        boolean removed = false;
        for (int i = 0; i < quickSettings.size(); i++) {
            if (quickSettings.get(i).key.equals(key)) {
                quickSettings.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            save();
        }
    }
}
