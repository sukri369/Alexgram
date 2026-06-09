package org.telegram.ui.Templates;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class TemplatesManager {
    public enum SortingType {
        DATE,
        NAME,
        USAGE
    }

    private static final String PREF_PREFIX = "alexgram_templates_";
    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_SORTING = "sorting";

    private static volatile TemplatesManager[] Instance = new TemplatesManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static TemplatesManager getInstance(int account) {
        TemplatesManager local = Instance[account];
        if (local == null) {
            synchronized (TemplatesManager.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new TemplatesManager(account);
                }
            }
        }
        return local;
    }

    private final int currentAccount;
    private final SharedPreferences preferences;
    private final ArrayList<TemplateSettings> templates = new ArrayList<>();
    private boolean loaded;

    private TemplatesManager(int account) {
        currentAccount = account;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREF_PREFIX + account, Context.MODE_PRIVATE);
    }

    public ArrayList<TemplateSettings> getTemplates() {
        ensureLoaded();
        ArrayList<TemplateSettings> result = new ArrayList<>(templates);
        SortingType sortingType = getSortingType();
        if (sortingType == SortingType.NAME) {
            Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if (sortingType == SortingType.USAGE) {
            Collections.sort(result, (a, b) -> {
                int usage = Integer.compare(b.usageRating, a.usageRating);
                if (usage != 0) {
                    return usage;
                }
                return Long.compare(b.creationDate, a.creationDate);
            });
        } else {
            Collections.sort(result, Comparator.comparingLong(a -> -a.creationDate));
        }
        return result;
    }

    public SortingType getSortingType() {
        String value = preferences.getString(KEY_SORTING, SortingType.DATE.name());
        try {
            return SortingType.valueOf(value);
        } catch (Exception ignored) {
            return SortingType.DATE;
        }
    }

    public void setSortingType(SortingType sortingType) {
        if (sortingType == null) {
            sortingType = SortingType.DATE;
        }
        preferences.edit().putString(KEY_SORTING, sortingType.name()).apply();
        notifyUpdated();
    }

    public TemplateSettings addTemplate(String name, String text) {
        ensureLoaded();
        TemplateSettings template = new TemplateSettings(
                preferences.getLong(KEY_NEXT_ID, 1),
                normalizeName(name),
                text == null ? "" : text,
                System.currentTimeMillis(),
                0
        );
        templates.add(template);
        preferences.edit().putLong(KEY_NEXT_ID, template.id + 1).apply();
        save();
        notifyUpdated();
        return template;
    }

    public void updateTemplate(TemplateSettings template, String name, String text) {
        if (template == null) {
            return;
        }
        ensureLoaded();
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id == template.id) {
                templates.set(i, template.withValues(normalizeName(name), text));
                save();
                notifyUpdated();
                return;
            }
        }
    }

    public void deleteTemplate(long id) {
        ensureLoaded();
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id == id) {
                templates.remove(i);
                save();
                notifyUpdated();
                return;
            }
        }
    }

    public void incrementUsage(long id) {
        ensureLoaded();
        for (int i = 0; i < templates.size(); i++) {
            TemplateSettings template = templates.get(i);
            if (template.id == id) {
                templates.set(i, template.withUsageIncremented());
                save();
                notifyUpdated();
                return;
            }
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        templates.clear();
        String serialized = preferences.getString(KEY_TEMPLATES, "[]");
        try {
            JSONArray array = new JSONArray(serialized);
            for (int i = 0; i < array.length(); i++) {
                templates.add(TemplateSettings.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_TEMPLATES).apply();
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        for (TemplateSettings template : templates) {
            try {
                array.put(template.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY_TEMPLATES, array.toString()).apply();
    }

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return TextUtils.isEmpty(trimmed) ? "Template" : trimmed;
    }

    private void notifyUpdated() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.templatesSettingsUpdated));
    }
}
