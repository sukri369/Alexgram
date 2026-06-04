package tw.nekomimi.nekogram.tabs;

import android.content.Context;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

/**
 * Mirrors iMe's DialogsSortingTabType — defines each "Tabs by Type" chat category.
 */
public enum TabsByTypeEntry {

    UNREAD(
            R.drawable.fork_filter_icon_bubble_point,
            MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            false,  // not pencil-editable (view/read-only FAB)
            true    // enabled by default
    ),
    PERSONAL(
            R.drawable.fork_filter_icon_user,
            MessagesController.DIALOG_FILTER_FLAG_CONTACTS
                    | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    GROUPS(
            R.drawable.fork_filter_icon_users,
            MessagesController.DIALOG_FILTER_FLAG_GROUPS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    CHANNELS(
            R.drawable.fork_filter_icon_channel,
            MessagesController.DIALOG_FILTER_FLAG_CHANNELS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    BOTS(
            R.drawable.fork_filter_icon_bot,
            MessagesController.DIALOG_FILTER_FLAG_BOTS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    ADMIN(
            R.drawable.fork_filter_icon_chat_admin,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x40000000,
            true,
            true
    ),
    OWNER(
            R.drawable.fork_filter_icon_owner,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x80000000,
            true,
            true
    ),
    PRIVATE_GROUPS(
            R.drawable.fork_filter_icon_private_groups,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x10000000,
            true,
            false
    ),
    PUBLIC_GROUPS(
            R.drawable.fork_filter_icon_public_groups,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x08000000,
            true,
            false
    ),
    MENTIONED_CHATS(
            R.drawable.fork_filter_icon_mentionbutton,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x01000000,
            false,
            false
    ),
    LIVE_CHATS(
            R.drawable.fork_filter_icon_voicechat,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x00800000,
            false,
            false
    ),
    DELETED_USERS(
            R.drawable.fork_ic_ghost_26,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x02000000,
            false,
            false
    ),
    SECRET_CHATS(
            R.drawable.fork_filter_icon_lock,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x20000000,
            true,
            false
    ),
    ALBUMS(
            R.drawable.fork_filter_icon_albums,
            MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED | 0x04000000,
            false,
            false
    );

    public final int iconResId;
    public final int dialogFilterFlags;
    public final boolean isEditableFab;    // true = pencil, false = eye
    public final boolean enabledByDefault;

    TabsByTypeEntry(int iconResId, int dialogFilterFlags, boolean isEditableFab, boolean enabledByDefault) {
        this.iconResId = iconResId;
        this.dialogFilterFlags = dialogFilterFlags;
        this.isEditableFab = isEditableFab;
        this.enabledByDefault = enabledByDefault;
    }

    public String getTitle(Context context) {
        switch (this) {
            case UNREAD:          return context.getString(R.string.TabsByTypeUnread);
            case PERSONAL:        return context.getString(R.string.TabsByTypePersonal);
            case GROUPS:          return context.getString(R.string.TabsByTypeGroups);
            case CHANNELS:        return context.getString(R.string.TabsByTypeChannels);
            case BOTS:            return context.getString(R.string.TabsByTypeBots);
            case ADMIN:           return context.getString(R.string.TabsByTypeAdmin);
            case OWNER:           return context.getString(R.string.TabsByTypeOwner);
            case PRIVATE_GROUPS:  return context.getString(R.string.TabsByTypePrivateGroups);
            case PUBLIC_GROUPS:   return context.getString(R.string.TabsByTypePublicGroups);
            case MENTIONED_CHATS: return context.getString(R.string.TabsByTypeMentioned);
            case LIVE_CHATS:      return context.getString(R.string.TabsByTypeLive);
            case DELETED_USERS:   return context.getString(R.string.TabsByTypeDeleted);
            case SECRET_CHATS:    return context.getString(R.string.TabsByTypeSecret);
            case ALBUMS:          return context.getString(R.string.TabsByTypeAlbums);
            default:              return name();
        }
    }
}
