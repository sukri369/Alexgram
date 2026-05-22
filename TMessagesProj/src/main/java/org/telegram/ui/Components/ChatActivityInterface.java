package org.telegram.ui.Components;

import android.app.Activity;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import tw.nekomimi.nekogram.helpers.MessageHelper;
public interface ChatActivityInterface {

    default ChatObject.Call getGroupCall() {
        return null;
    }

    default TLRPC.Chat getCurrentChat() {
        return null;
    }

    default TLRPC.User getCurrentUser() {
        return null;
    }

    long getDialogId();

    default void scrollToMessageId(int id, int i, boolean b, int i1, boolean b1, int i2) {

    }

    default boolean shouldShowImport() {
        return false;
    }

    default boolean openedWithLivestream() {
        return false;
    }

    default long getMergeDialogId() {
        return 0;
    }

    default long getTopicId() {
        return 0;
    }

    default boolean isRightFragment() {
        return false;
    }

    ChatAvatarContainer getAvatarContainer();

    default void checkAndUpdateAvatar() {

    }

    SizeNotifierFrameLayout getContentView();

    ActionBar getActionBar();

    Theme.ResourcesProvider getResourceProvider();

    default int getCurrentAccount() {
        return 0;
    }

    default NotificationCenter getNotificationCenter() {
        return null;
    }

    default MessagesController getMessagesController() {
        return null;
    }

    default Activity getParentActivity() {
        return null;
    }

    default MessageObject getMessageForTranslate() {
        return null;
    }

    default MessageObject.GroupedMessages getSelectedObjectGroup() {
        return null;
    }

    default java.util.ArrayList<MessageObject> getChatAdapterMessages() {
        return null;
    }

    default MessageHelper getMessageHelper() {
        return null;
    }
}
