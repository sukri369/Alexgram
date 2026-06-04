package tw.nekomimi.nekogram.tabs;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages injecting virtual DialogFilter objects into MessagesController.dialogFilters
 * so the existing FilterTabsView shows "tabs by type" tabs in the chat list.
 *
 * Virtual filter IDs: large negative values (≤ VIRTUAL_ID_BASE) to avoid conflict
 * with real server filter IDs (which are positive integers starting at 2).
 *
 * Usage:
 *   - Call injectFiltersSync() directly from MessagesController when the real
 *     filter list has just been refreshed (already on UI thread, notification
 *     will follow naturally).
 *   - Call applyAndNotify() from TabsByTypeActivity when the user changes settings.
 */
public class TabsByTypeManager {

    /** Base ID for virtual tab-type filters — must never clash with real folder IDs */
    public static final int VIRTUAL_ID_BASE = -20000;
    public static final int VIRTUAL_ID_BASE_ARCHIVE = -30000;

    private static TabsByTypeManager[] instances = new TabsByTypeManager[UserConfig.MAX_ACCOUNT_COUNT];

    private final int account;
    private final TabsByTypeSettings settings;

    private TabsByTypeManager(int account) {
        this.account = account;
        this.settings = TabsByTypeSettings.getInstance();
    }

    public static synchronized TabsByTypeManager getInstance(int account) {
        if (instances[account] == null) {
            instances[account] = new TabsByTypeManager(account);
        }
        return instances[account];
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Call this synchronously on the UI thread right after MessagesController
     * has built / replaced its dialogFilters list.
     * Does NOT post dialogFiltersUpdated — the caller does that.
     */
    public void injectFiltersSync() {
        MessagesController mc = MessagesController.getInstance(account);
        removeVirtualFilters(mc.dialogFilters);
        if (settings.isEnabled(false)) {
            injectVirtualFilters(mc.dialogFilters, false);
        }
        if (settings.isEnabled(true)) {
            injectVirtualFilters(mc.dialogFilters, true);
        }
    }

    /**
     * Call from TabsByTypeActivity (or any settings toggle) to re-inject and
     * broadcast the change so DialogsActivity refreshes its tab strip.
     * Notifies ALL active accounts because TabsByTypeSettings is shared globally.
     */
    public void applyAndNotify() {
        AndroidUtilities.runOnUIThread(() -> {
            // [Alexgram: Tabs by Type] - apply to all active accounts so tabs show on every account
            for (int acc = 0; acc < UserConfig.MAX_ACCOUNT_COUNT; acc++) {
                if (UserConfig.getInstance(acc).isClientActivated()) {
                    TabsByTypeManager.getInstance(acc).injectFiltersSync();
                    NotificationCenter.getInstance(acc)
                            .postNotificationName(NotificationCenter.dialogFiltersUpdated);
                }
            }
        });
    }

    // ── Virtual filter helpers ─────────────────────────────────────────────────

    /** Remove all previously injected virtual tab-type filters from the list */
    public static void removeVirtualFilters(ArrayList<MessagesController.DialogFilter> filters) {
        for (int i = filters.size() - 1; i >= 0; i--) {
            if (isVirtualFilter(filters.get(i))) {
                filters.remove(i);
            }
        }
    }

    public static boolean isVirtualFilter(MessagesController.DialogFilter filter) {
        return filter != null && filter.id <= VIRTUAL_ID_BASE;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void injectVirtualFilters(
            ArrayList<MessagesController.DialogFilter> filters,
            boolean archive) {

        List<TabsByTypeEntry> enabled = settings.getEnabledTabs(archive);
        Context ctx = org.telegram.messenger.ApplicationLoader.applicationContext;

        for (TabsByTypeEntry tab : enabled) {
            MessagesController.DialogFilter vf = buildVirtualFilter(tab, ctx, archive);
            int insertAt = findInsertPosition(filters);
            filters.add(insertAt, vf);
        }
    }

    /** Insert after the last non-virtual real filter */
    private int findInsertPosition(ArrayList<MessagesController.DialogFilter> filters) {
        for (int i = filters.size() - 1; i >= 0; i--) {
            if (!isVirtualFilter(filters.get(i))) {
                return i + 1;
            }
        }
        return filters.size();
    }

    private MessagesController.DialogFilter buildVirtualFilter(
            TabsByTypeEntry tab,
            Context ctx,
            boolean archive) {

        MessagesController.DialogFilter f = new MessagesController.DialogFilter();
        // Unique negative ID per tab type — cannot be 0 (that is the "All Chats" default)
        f.id = (archive ? VIRTUAL_ID_BASE_ARCHIVE : VIRTUAL_ID_BASE) - tab.ordinal();
        f.name = tab.getTitle(ctx);
        f.emoticon = null;
        f.order = 10000 + tab.ordinal();
        f.flags = buildFlags(tab, archive);
        return f;
    }

    private int buildFlags(TabsByTypeEntry tab, boolean archive) {
        int flags = tab.dialogFilterFlags;
        if (archive) {
            flags = (flags & ~MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED)
                    | MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED;
        } else {
            flags = (flags & ~MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED)
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
        }
        return flags;
    }
}
