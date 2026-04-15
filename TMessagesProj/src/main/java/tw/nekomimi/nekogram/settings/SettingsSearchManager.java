package tw.nekomimi.nekogram.settings;

import org.telegram.messenger.R;
import org.telegram.ui.AIAssistanceSettingsActivity;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsSearchManager {

    public static class SearchItem {
        public String title;
        public String subtitle;
        public String key;
        public Class<? extends BaseFragment> fragmentClass;
        public String category;
        public int iconRes;

        public SearchItem(String title, String subtitle, String key, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
            this.title = title;
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
        
        // --- PRIVACY & SECURITY ---
        add("Ghost Mode", "Read messages without sending read receipts", "ghost_mode", NekoSettingsActivity.class, "Privacy", R.drawable.msg_secret);
        add("Hide Contacts", "Remove the contacts tab from the main drawer", "hide_contacts", NekoSettingsActivity.class, "Privacy", R.drawable.msg_contacts);
        add("Save Deleted Messages", "Automatically keep copies of deleted messages", "save_deleted", NekoSettingsActivity.class, "Privacy", R.drawable.msg_delete_solar);
        add("Hidden Chats", "Secure your private conversations with a passcode", "hidden_chats", NekoSettingsActivity.class, "Privacy", R.drawable.msg_permissions);
        add("Message History", "View edit and deletion history for messages", "ViewHistory", NekoChatSettingsActivity.class, "Privacy", R.drawable.menu_recent);

        // --- GENERAL & APPEARANCE ---
        add("Custom App Title", "Set a personalized text for the main screen header", "CustomTitle", NekoGeneralSettingsActivity.class, "General", R.drawable.msg_edit);
        add("Notification Icon", "Customize the status bar icon for the app", "NotificationIcon", NekoGeneralSettingsActivity.class, "General", R.drawable.msg_notifications);
        add("Font & Typeface", "Change the global font family of the application", "typeface", NekoGeneralSettingsActivity.class, "General", R.drawable.msg_photo_text_framed3);
        add("Blur Effects", "Toggle glassmorphism and blur in various UI sections", "forceBlurInChat", NekoGeneralSettingsActivity.class, "General", R.drawable.msg_info);
        add("Double Heart Like", "Enable double tap to like with a heart", "DoubleHeart", NekoGeneralSettingsActivity.class, "General", R.drawable.msg_fave);

        // --- TRANSLATOR ---
        add("Translate Button", "Show a quick translate button on each message", "showTranslate", NekoTranslatorSettingsActivity.class, "Translator", R.drawable.ic_translate);
        add("AI Translation (LLM)", "Use advanced AI models for context-aware translation", "LlmProviderPreset", NekoTranslatorSettingsActivity.class, "Translator", R.drawable.settings_chat);
        add("Telegram Auto Translate", "Enable native Telegram UI for automatic translation", "TelegramUIAutoTranslate", NekoTranslatorSettingsActivity.class, "Translator", R.drawable.msg_retry);
        add("Keep Markdown", "Maintain formatting while translating messages", "TranslatorKeepMarkdown", NekoTranslatorSettingsActivity.class, "Translator", R.drawable.msg_photo_text_framed3);
        add("Preferred Language", "Set the target language for all translations", "TranslateTo", NekoTranslatorSettingsActivity.class, "Translator", R.drawable.msg_language);

        // --- AI ASSISTANT ---
        add("Enable AI Assistant", "Activate the floating Alexgram AI companion", "assistant_enabled", AIAssistanceSettingsActivity.class, "AI", R.drawable.settings_chat);
        add("AI Character Skin", "Change the visual appearance of the AI assistant", "character_skin", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_theme);
        add("Assistant Persona", "Choose between different AI personalities", "persona_preset", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_contacts);
        add("AI Background Animation", "Toggle the animated background in AI settings", "background_animation", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_sticker);
        add("Particle Effects", "Show dynamic particles on AI interaction", "particle_effects", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_info);

        // --- CHAT SETTINGS ---
        add("Sticker Size", "Adjust the default size of stickers in chats", "StickerSize", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_sticker);
        add("Hide Keyboard on Scroll", "Dismiss the keyboard when swiping through messages", "HideKeyboardOnScroll", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_discussion);
        add("Message Menu Animations", "Staggered 1-by-1 animations for context menus", "MenuAnimations", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_sticker);
        add("Show Seconds", "Display precise time with seconds in messages", "showSeconds", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_calendar2);
        add("Show Message ID", "Toggle visibility of internal message IDs", "ShowMessageID", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_info);
        add("Use Edited Icon", "Show an icon instead of 'edited' text", "UseEditedIcon", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_edit);
        add("Online Status", "Show user online status in chat headers", "ShowOnlineStatus", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_openprofile);
        add("Double Tap Action", "Customize what happens on message double tap", "DoubleTapIncoming", NekoChatSettingsActivity.class, "Chats", R.drawable.menu_reply);
        add("Hide Bot Button", "Remove the bot command button from input field", "HideBotButtonInInputField", NekoChatSettingsActivity.class, "Chats", R.drawable.msg_bots);

        // --- MEDIA & CHANNELS ---
        add("Auto-Play Voice", "Automatically play sequential voice messages", "DontAutoPlayNextVoice", NekoChatSettingsActivity.class, "Media", R.drawable.msg_voice_micro);
        add("Show Small GIFs", "Minimize GIF size in the chat list", "ShowSmallGIF", NekoChatSettingsActivity.class, "Media", R.drawable.msg_gif);
        add("Disable Swipe to Next", "Prevent accidental swiping between channel posts", "disableSwipeToNext", NekoChatSettingsActivity.class, "Channels", R.drawable.msg_disable);
        add("Hide Share Button", "Remove the share button from channel messages", "HideShareButtonInChannel", NekoChatSettingsActivity.class, "Channels", R.drawable.msg_shareout);

        // --- EXPERIMENTAL ---
        add("Experimental Features", "Access beta and untested NagramX features", "experimental", NekoExperimentalSettingsActivity.class, "Experimental", R.drawable.msg_fave);
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
