package tw.nekomimi.nekogram.tabs;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * SharedPreferences-backed settings manager for the "Tabs by Type" feature.
 * Mirrors iMe's DialogsSortingManager preference keys.
 */
public class TabsByTypeSettings {

    private static final String PREFS_NAME = "tabsByType";
    private static final String KEY_ENABLED_MAIN    = "tabsByType_enabled_main";
    private static final String KEY_ENABLED_ARCHIVE = "tabsByType_enabled_archive";
    private static final String KEY_HIDE_FOLDERS    = "tabsByType_hide_folders";

    private static TabsByTypeSettings instance;

    private final SharedPreferences prefs;

    private TabsByTypeSettings() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, 0);
    }

    public static synchronized TabsByTypeSettings getInstance() {
        if (instance == null) {
            instance = new TabsByTypeSettings();
        }
        return instance;
    }

    // ── Global enabled flags ───────────────────────────────────────────────────

    public boolean isEnabledInMain() {
        return prefs.getBoolean(KEY_ENABLED_MAIN, false);
    }

    public void setEnabledInMain(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED_MAIN, enabled).apply();
    }

    public boolean isEnabledInArchive() {
        return prefs.getBoolean(KEY_ENABLED_ARCHIVE, false);
    }

    public void setEnabledInArchive(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED_ARCHIVE, enabled).apply();
    }

    public boolean isEnabled(boolean archive) {
        return archive ? isEnabledInArchive() : isEnabledInMain();
    }

    public void setEnabled(boolean archive, boolean enabled) {
        if (archive) {
            setEnabledInArchive(enabled);
        } else {
            setEnabledInMain(enabled);
        }
    }

    public boolean isHideFolders() {
        return prefs.getBoolean(KEY_HIDE_FOLDERS, false);
    }

    public void setHideFolders(boolean hide) {
        prefs.edit().putBoolean(KEY_HIDE_FOLDERS, hide).apply();
    }

    // ── Per-tab enable/disable ─────────────────────────────────────────────────

    private String keyEnabled(TabsByTypeEntry tab, boolean archive) {
        return "tabsByType_" + tab.name() + (archive ? "_archive" : "_main") + "_enabled";
    }

    private String keyOrder(TabsByTypeEntry tab, boolean archive) {
        return "tabsByType_" + tab.name() + (archive ? "_archive" : "_main") + "_order";
    }

    public boolean isTabEnabled(TabsByTypeEntry tab, boolean archive) {
        return prefs.getBoolean(keyEnabled(tab, archive), tab.enabledByDefault);
    }

    public void setTabEnabled(TabsByTypeEntry tab, boolean archive, boolean enabled) {
        prefs.edit().putBoolean(keyEnabled(tab, archive), enabled).apply();
    }

    private String keyFabType(TabsByTypeEntry tab) {
        return "tabsByType_" + tab.name() + "_fabType";
    }

    public FloatingActionButtonType getTabFabType(TabsByTypeEntry tab) {
        FloatingActionButtonType defaultType = tab.isEditableFab ? FloatingActionButtonType.CREATE_CHAT : FloatingActionButtonType.MARK_ALL_READ;
        String saved = prefs.getString(keyFabType(tab), defaultType.name());
        try {
            return FloatingActionButtonType.valueOf(saved);
        } catch (Exception e) {
            return defaultType;
        }
    }

    public void setTabFabType(TabsByTypeEntry tab, FloatingActionButtonType fabType) {
        prefs.edit().putString(keyFabType(tab), fabType.name()).apply();
    }

    // ── Per-tab ordering ──────────────────────────────────────────────────────

    public int getTabOrder(TabsByTypeEntry tab, boolean archive) {
        return prefs.getInt(keyOrder(tab, archive), tab.ordinal());
    }

    public void setTabOrder(TabsByTypeEntry tab, boolean archive, int order) {
        prefs.edit().putInt(keyOrder(tab, archive), order).apply();
    }

    public void swapTabOrders(TabsByTypeEntry from, TabsByTypeEntry to, boolean archive) {
        int fromOrder = getTabOrder(from, archive);
        int toOrder   = getTabOrder(to, archive);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt(keyOrder(from, archive), toOrder);
        ed.putInt(keyOrder(to, archive), fromOrder);
        ed.apply();
    }

    // ── Sorted list helpers ────────────────────────────────────────────────────

    /** Returns all tabs (Main or Archive) sorted by their saved order. */
    public List<TabsByTypeEntry> getSortedTabs(boolean archive) {
        TabsByTypeEntry[] all = TabsByTypeEntry.values();
        List<TabsByTypeEntry> list = new ArrayList<>();
        for (TabsByTypeEntry e : all) {
            list.add(e);
        }
        list.sort((a, b) -> Integer.compare(getTabOrder(a, archive), getTabOrder(b, archive)));
        return list;
    }

    /** Returns only the enabled tabs sorted by order — used for actual tab rendering. */
    public List<TabsByTypeEntry> getEnabledTabs(boolean archive) {
        List<TabsByTypeEntry> sorted = getSortedTabs(archive);
        List<TabsByTypeEntry> enabled = new ArrayList<>();
        for (TabsByTypeEntry tab : sorted) {
            if (isTabEnabled(tab, archive)) {
                enabled.add(tab);
            }
        }
        return enabled;
    }
}
