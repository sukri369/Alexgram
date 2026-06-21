package org.telegram.ui.Templates;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;

public class TemplatesPreviewActivity extends ChatActivity {
    private static final long TEMPLATE_AUTHOR_ID = 777000;

    private final TemplateSettings template;
    private int nextMessageId = -500000;

    public TemplatesPreviewActivity(TemplateSettings template) {
        super(new Bundle());
        this.template = template;
        buildPreviewMessages();
    }

    @Override
    public boolean isMultiChat() {
        return true;
    }

    @Override
    protected boolean multiChatOnFragmentCreate() {
        return template != null && !messages.isEmpty();
    }

    @Override
    protected void multiChatMessagesFirstLoad(int lastLoadIndex) {
        setLoaded();
        updateVisibleRows();
    }

    @Override
    protected void multiChatOnCreateView() {
        setLoaded();
        rebuildGroupedMessages();
        if (bottomChannelButtonsLayout != null) {
            bottomChannelButtonsLayout.setVisibility(View.GONE);
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setOverrideHint(LocaleController.getString(R.string.chat_template));
            chatActivityEnterView.setVisibility(View.INVISIBLE);
        }
        if (avatarContainer != null) {
            avatarContainer.setTitle(LocaleController.getString(R.string.chat_templates));
            avatarContainer.setSubtitle(template.name);
            if (avatarContainer.avatarImageView != null) {
                GradientDrawable background = new GradientDrawable();
                background.setShape(GradientDrawable.OVAL);
                background.setColor(Theme.getColor(Theme.key_chat_messagePanelSend, getResourceProvider()));
                avatarContainer.avatarImageView.setBackground(background);
                avatarContainer.avatarImageView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                avatarContainer.avatarImageView.setImageResource(R.drawable.fork_templates_filled);
                avatarContainer.avatarImageView.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
                avatarContainer.avatarImageView.setVisibility(View.VISIBLE);
            }
        }
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        updateVisibleRows();
    }

    private void buildPreviewMessages() {
        messages.clear();
        if (template == null) {
            return;
        }
        ArrayList<MessageObject> templateMessages = template.toMessageObjects(currentAccount);
        long previewGroupId = System.currentTimeMillis();
        if (!templateMessages.isEmpty()) {
            for (int i = templateMessages.size() - 1; i >= 0; i--) {
                MessageObject object = templateMessages.get(i);
                if (object == null || object.messageOwner == null) {
                    continue;
                }
                preparePreviewMessage(object.messageOwner, previewGroupId);
                MessageObject preview = createPreviewMessageObject(object.messageOwner);
                preview.localGroupId = object.getGroupId() != 0 ? previewGroupId : 0;
                preview.localSentGroupId = preview.localGroupId;
                messages.add(preview);
            }
        }
        if (messages.isEmpty() && !TextUtils.isEmpty(template.text)) {
            messages.add(createPreviewMessageObject(createTextMessage(template.text)));
        }
        Collections.sort(messages, (a, b) -> Integer.compare(b.getId(), a.getId()));
    }

    private void preparePreviewMessage(TLRPC.Message message, long previewGroupId) {
        message.id = nextMessageId--;
        message.date = getTemplateDate();
        message.unread = false;
        message.out = false;
        message.dialog_id = 0;
        if (message.peer_id == null) {
            message.peer_id = peerUser(UserConfig.getInstance(currentAccount).getClientUserId());
        }
        if (message.from_id == null) {
            message.from_id = peerUser(TEMPLATE_AUTHOR_ID);
        }
        if (message.grouped_id != 0) {
            message.grouped_id = previewGroupId;
            message.flags |= 1 << 17;
        }
        if (message.fwd_from == null) {
            addTemplateForwardName(message);
        }
    }

    private TLRPC.Message createTextMessage(String text) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = nextMessageId--;
        message.date = getTemplateDate();
        message.message = text == null ? "" : text;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.peer_id = peerUser(UserConfig.getInstance(currentAccount).getClientUserId());
        message.from_id = peerUser(TEMPLATE_AUTHOR_ID);
        message.unread = false;
        message.out = false;
        CharSequence[] textForEntities = new CharSequence[]{message.message};
        message.entities = MediaDataController.getInstance(currentAccount).getEntities(textForEntities, true);
        if (message.entities != null && !message.entities.isEmpty()) {
            message.flags |= 128;
        }
        addTemplateForwardName(message);
        return message;
    }

    private MessageObject createPreviewMessageObject(TLRPC.Message message) {
        MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
        messageObject.previewForward = true;
        messageObject.stableId = ChatActivity.lastStableId++;
        messageObject.forceUpdate = true;
        try {
            messageObject.checkLayout();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return messageObject;
    }

    private void addTemplateForwardName(TLRPC.Message message) {
        if (TextUtils.isEmpty(template.name)) {
            return;
        }
        TLRPC.TL_messageFwdHeader header = new TLRPC.TL_messageFwdHeader();
        header.flags |= 1 << 5;
        header.from_name = template.name;
        header.date = message.date;
        message.fwd_from = header;
        message.flags |= TLRPC.MESSAGE_FLAG_FWD;
    }

    private TLRPC.Peer peerUser(long userId) {
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = userId;
        return peer;
    }

    private int getTemplateDate() {
        long date = template.creationDate > 0 ? template.creationDate : System.currentTimeMillis();
        return (int) (date / 1000);
    }

    private void rebuildGroupedMessages() {
        if (groupedMessagesMap == null) {
            return;
        }
        groupedMessagesMap.clear();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject.getGroupId() == 0) {
                continue;
            }
            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
            if (groupedMessages == null) {
                groupedMessages = new MessageObject.GroupedMessages();
                groupedMessages.groupId = messageObject.getGroupId();
                groupedMessages.reversed = reversed;
                groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
            }
            groupedMessages.messages.add(messageObject);
        }
        for (int i = 0; i < groupedMessagesMap.size(); i++) {
            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.valueAt(i);
            Collections.sort(groupedMessages.messages, (a, b) -> Integer.compare(a.getId(), b.getId()));
            groupedMessages.calculate();
        }
    }
}
