package tw.nekomimi.nekogram.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.telegram.messenger.R;
import org.telegram.ui.AIAssistanceSettingsActivity;
import org.telegram.ui.ActionBar.BaseFragment;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCheckBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck2;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;

public class SettingsSearchManager {

    public static class SearchItem {
        public String title;
        public String subtitle;
        public String key;
        public Class<? extends BaseFragment> fragmentClass;
        public String category;
        public int iconRes;

        public SearchItem(String title, String subtitle, String key, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
            this.title = title == null ? "" : title;
            this.subtitle = subtitle;
            this.key = key;
            this.fragmentClass = fragmentClass;
            this.category = category;
            this.iconRes = iconRes;
        }
    }

    private static SettingsSearchManager instance;
    private final List<SearchItem> index = new ArrayList<>();

    public static SettingsSearchManager getInstance() {
        if (instance == null) {
            instance = new SettingsSearchManager();
        }
        return instance;
    }

    private SettingsSearchManager() {
        loadIndex();
    }

    private void loadIndex() {
        index.clear();
        
        // --- AUTOMATIC INDEXING (NAGRAM SETTINGS) ---
        registerActivity(NekoGeneralSettingsActivity.class, "General", R.drawable.msg_edit);
        registerActivity(NekoChatSettingsActivity.class, "Chats", R.drawable.drawer_discussion);
        registerActivity(NekoTranslatorSettingsActivity.class, "Translator", R.drawable.ic_translate);
        registerActivity(NekoExperimentalSettingsActivity.class, "Experimental", R.drawable.msg_fave);

        // --- MANUAL INDEXING (Standard UI & AI) ---
        add("Ghost Mode", "Read messages without sending read receipts", "ghost_mode", NekoSettingsActivity.class, "Privacy", R.drawable.msg_secret);
        add("Hide Contacts", "Remove the contacts tab from the main drawer", "hide_contacts", NekoSettingsActivity.class, "Privacy", R.drawable.msg_contacts);
        add("Save Deleted Messages", "Automatically keep copies of deleted messages", "save_deleted", NekoSettingsActivity.class, "Privacy", R.drawable.msg_delete_solar);
        add("Hidden Chats", "Secure your private conversations with a passcode", "hidden_chats", NekoSettingsActivity.class, "Privacy", R.drawable.msg_permissions);
        add("Enable AI Assistant", "Activate the floating Alexgram AI companion", "assistant_enabled", AIAssistanceSettingsActivity.class, "AI", R.drawable.settings_chat);
        add("AI Character Skin", "Change the visual appearance of the AI assistant", "character_skin", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_theme);
        add("Assistant Persona", "Choose between different AI personalities", "persona_preset", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_contacts);
        add("AI Background Animation", "Toggle the animated background in AI settings", "background_animation", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_sticker);
        add("Particle Effects", "Show dynamic particles on AI interaction", "particle_effects", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_info);
    }

    private void registerActivity(Class<? extends BaseNekoXSettingsActivity> clazz, String category, int defaultIcon) {
        try {
            BaseNekoXSettingsActivity activity = clazz.getConstructor().newInstance();
            // In Nekogram, updateRows() populates the cellGroup.
            // Some activities use static initialization, others use updateRows.
            activity.onFragmentCreate(); 
            
            CellGroup cellGroup = activity.getCellGroup();
            if (cellGroup != null && cellGroup.rows != null) {
                for (AbstractConfigCell cell : cellGroup.rows) {
                    processCell(cell, clazz, category, defaultIcon);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void processCell(AbstractConfigCell cell, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
        if (cell == null) return;
        
        String title = null;
        String subtitle = null;
        String key = null;

        if (cell instanceof ConfigCellTextCheck c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
        } else if (cell instanceof ConfigCellSelectBox c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
        } else if (cell instanceof ConfigCellTextInput c) {
            title = c.getKey(); // In TextInput, the key string usually serves as the label
            key = c.getKey();
        } else if (cell instanceof ConfigCellTextDetail c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
        } else if (cell instanceof ConfigCellTextCheckIcon c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
            if (c.getResId() != 0) iconRes = c.getResId();
        } else if (cell instanceof ConfigCellTextCheck2 c) {
            title = c.getTitle();
            key = c.getKey();
            add(title, null, key, fragmentClass, category, iconRes);
            if (c.getCheckBox() != null) {
                for (ConfigCellCheckBox cb : c.getCheckBox()) {
                    processCell(cb, fragmentClass, category, iconRes);
                }
            }
            return;
        } else if (cell instanceof ConfigCellCheckBox c) {
            title = c.getTitle();
            key = c.getKey();
        }

        if (title != null && key != null && !title.isEmpty() && !title.equals("null") && !title.equals("Divider")) {
            add(title, subtitle, key, fragmentClass, category, iconRes);
        }
    }

    private void add(String title, String subtitle, String key, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
        index.add(new SearchItem(title, subtitle, key, fragmentClass, category, iconRes));
    }

    public List<SearchItem> search(String query) {
        List<SearchItem> results = new ArrayList<>();
        if (query == null || query.isEmpty()) return results;
        
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (SearchItem item : index) {
            if (item.title.toLowerCase(Locale.ROOT).contains(lowerQuery) || 
                (item.subtitle != null && item.subtitle.toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                item.category.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                results.add(item);
            }
        }
        return results;
    }
}
