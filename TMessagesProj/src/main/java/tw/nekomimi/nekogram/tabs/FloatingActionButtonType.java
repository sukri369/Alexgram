package tw.nekomimi.nekogram.tabs;

import android.content.Context;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.RLottieImageView;

public enum FloatingActionButtonType {
    CREATE_CHAT(0),
    BOOKMARKS(1),
    ARCHIVE(2),
    CLOUD(3),
    MARK_ALL_READ(4),
    WALLET(5),
    CONTACTS(6),
    MUSIC(7),
    ALBUMS(8),
    CREATE_ALBUM(9),
    CREATE_STORY(10),
    MINI_APPS(11),
    AI_CHAT(12),
    TODO(13);

    private final int id;

    FloatingActionButtonType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static FloatingActionButtonType getDefault() {
        return CREATE_CHAT;
    }

    public String getTitle(Context context) {
        switch (this) {
            case CREATE_CHAT:
                return LocaleController.getString("folder_fab_settings_fab_create_chat", R.string.folder_fab_settings_fab_create_chat);
            case BOOKMARKS:
                return LocaleController.getString("bookmarks_title", R.string.bookmarks_title);
            case ARCHIVE:
                return LocaleController.getString("folder_fab_settings_fab_archive", R.string.folder_fab_settings_fab_archive);
            case CLOUD:
                return LocaleController.getString("folder_fab_settings_fab_cloud", R.string.folder_fab_settings_fab_cloud);
            case MARK_ALL_READ:
                return LocaleController.getString("folder_fab_settings_fab_mark_all_read", R.string.folder_fab_settings_fab_mark_all_read);
            case WALLET:
                return LocaleController.getString("folder_fab_settings_fab_wallet", R.string.folder_fab_settings_fab_wallet);
            case CONTACTS:
                return LocaleController.getString("folder_fab_settings_fab_contacts", R.string.folder_fab_settings_fab_contacts);
            case MUSIC:
                return LocaleController.getString("cloud_filter_music", R.string.cloud_filter_music);
            case ALBUMS:
                return LocaleController.getString("music_albums_tab", R.string.music_albums_tab);
            case CREATE_ALBUM:
                return LocaleController.getString("cloud_albums_intro_button", R.string.cloud_albums_intro_button);
            case CREATE_STORY:
                return LocaleController.getString("Story", R.string.Story);
            case MINI_APPS:
                return LocaleController.getString("drawer_mini_apps", R.string.drawer_mini_apps);
            case AI_CHAT:
                return LocaleController.getString("ai_chat_name", R.string.ai_chat_name);
            case TODO:
                return LocaleController.getString("drawer_todo_item_title", R.string.drawer_todo_item_title);
            default:
                return name();
        }
    }

    public void bindBig(RLottieImageView view) {
        if (view == null) return;
        view.clearAnimation();
        switch (this) {
            case CREATE_CHAT:
                view.setAnimation(R.raw.write_contacts_fab_icon, 52, 52);
                view.playAnimation();
                break;
            case BOOKMARKS:
                view.setImageResource(R.drawable.fork_drawer_bookmarks);
                break;
            case ARCHIVE:
                view.setImageResource(R.drawable.fork_fab_archive);
                break;
            case CLOUD:
                view.setImageResource(R.drawable.fork_fab_cloud);
                break;
            case MARK_ALL_READ:
                view.setImageResource(R.drawable.fork_fab_mark_all_read);
                break;
            case WALLET:
                view.setImageResource(R.drawable.fork_fab_wallet);
                break;
            case CONTACTS:
                view.setImageResource(R.drawable.fork_fab_contacts);
                break;
            case MUSIC:
                view.setImageResource(R.drawable.fork_fab_music);
                break;
            case ALBUMS:
                view.setImageResource(R.drawable.fork_fab_albums);
                break;
            case CREATE_ALBUM:
                view.setImageResource(R.drawable.msg_add);
                break;
            case CREATE_STORY:
                view.setAnimation(R.raw.write_contacts_fab_icon_camera, 56, 56);
                view.playAnimation();
                break;
            case MINI_APPS:
                view.setImageResource(R.drawable.fork_fab_miniapps_icon);
                break;
            case AI_CHAT:
                view.setImageResource(R.drawable.fork_fab_ai);
                break;
            case TODO:
                view.setImageResource(R.drawable.fork_fab_todo_icon);
                break;
        }
    }
}
