package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.style.URLSpan;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SendMessagesHelper.SendMessageParams;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import android.text.SpannableStringBuilder;
import java.util.ArrayList;

public class SpecialForwardActivity extends ChatActivity {

    private final SparseArray<MessageObject> originalMessagesMap = new SparseArray<>();
    private MessageObject selectedMessage = null;
    private int nextUniqueId = -100000;
    private boolean forwardAsFile = false;

    private final static int MENU_EDIT_OPTIONS = 1009;
    private final static int MENU_PREVIEW = 1010;

    private ImageView inlineResetView;
    private ImageView inlineReplaceLinkView;
    private ImageView inlineDeleteLinkView;

    public SpecialForwardActivity(ArrayList<MessageObject> sourceMessages) {
        super(new Bundle());
        this.chatMode = 102; // Custom multi-chat / special forward mode
        
        // Sort original messages so the newest (highest ID) comes first, matching Telegram's reversed messages layout
        java.util.Collections.sort(sourceMessages, (o1, o2) -> Integer.compare(o2.getId(), o1.getId()));
        
        // Deep copy messages and assign sequential negative unique IDs
        for (MessageObject msg : sourceMessages) {
            try {
                TLRPC.Message resetClone = cloneMessage(msg.messageOwner);
                if (resetClone != null) {
                    int id = nextUniqueId--;
                    resetClone.id = id;
                    
                    // Strip replies, reactions, and forwards metadata
                    resetClone.flags &= ~0x8D40;
                    resetClone.reply_to = null;
                    resetClone.reactions = null;
                    resetClone.reply_markup = null;
                    resetClone.fwd_from = null;
                    resetClone.edit_date = 0;
                    
                    MessageObject resetObj = new MessageObject(currentAccount, resetClone, false, true);
                    resetObj.forceUpdate = true;
                    resetObj.checkLayout();
                    
                    originalMessagesMap.put(id, resetObj);
                    
                    TLRPC.Message msgClone = cloneMessage(msg.messageOwner);
                    msgClone.id = id;
                    msgClone.flags &= ~0x8D40;
                    msgClone.reply_to = null;
                    msgClone.reactions = null;
                    msgClone.reply_markup = null;
                    msgClone.fwd_from = null;
                    msgClone.edit_date = 0;
                    
                    MessageObject clonedObj = new MessageObject(currentAccount, msgClone, false, true);
                    clonedObj.forceUpdate = true;
                    clonedObj.checkLayout();
                    
                    this.messages.add(clonedObj);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private TLRPC.Message cloneMessage(TLRPC.Message source) {
        if (source == null) return null;
        try {
            SerializedData data = new SerializedData(source.getObjectSize());
            source.serializeToStream(data);
            
            SerializedData readData = new SerializedData(data.toByteArray());
            int constructor = readData.readInt32(false);
            TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, constructor, false);
            
            data.cleanup();
            readData.cleanup();
            
            if (messageClone != null) {
                messageClone.dialog_id = source.dialog_id;
                messageClone.attachPath = source.attachPath;
                messageClone.params = source.params;
            }
            return messageClone;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @Override
    public boolean isMultiChat() {
        return true;
    }

    @Override
    public boolean isMultiChatWithInput() {
        return true;
    }

    @Override
    protected boolean multiChatOnFragmentCreate() {
        return !messages.isEmpty();
    }

    @Override
    protected void multiChatMessagesFirstLoad(int lastLoadIndex) {
        setLoaded();
        updateVisibleRows();
    }

    @Override
    protected void multiChatOnCreateView() {
        setLoaded();
        
        if (bottomChannelButtonsLayout != null) {
            bottomChannelButtonsLayout.setVisibility(View.GONE);
        }
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.forceShowSendButton = true;
            chatActivityEnterView.checkSendButton(false);
            chatActivityEnterView.setVisibility(View.VISIBLE);
            chatActivityEnterView.setAllowStickersAndGifs(true, false, false, true);
            chatActivityEnterView.showEditDoneProgress(false, true);
            
            View doneBtn = chatActivityEnterView.getDoneButton();
            if (doneBtn != null) {
                doneBtn.setOnClickListener(v -> {
                    CharSequence text = chatActivityEnterView.getFieldText();
                    if (selectedMessage != null) {
                        updateMessageText(selectedMessage, text);
                        int idx = messages.indexOf(selectedMessage);
                        if (idx >= 0) {
                            try {
                                TLRPC.Message messageClone = cloneMessage(selectedMessage.messageOwner);
                                if (messageClone != null) {
                                    messageClone.id = selectedMessage.getId();
                                    MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                                    newCloned.forceUpdate = true;
                                    newCloned.checkLayout();
                                    messages.set(idx, newCloned);
                                    rebuildGroupedMessages();
                                    if (chatAdapter != null) {
                                        chatAdapter.notifyItemChanged(idx);
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                    chatActivityEnterView.setEditingMessageObject(null, null, false);
                    hideFieldPanel(true);
                    selectedMessage = null;
                    editingMessageObject = null;
                    updateBottomOverlay();
                    updateVisibleRows();
                    updateSendBadge();
                });
            }
            if (chatActivityEnterView.sendButtonContainer != null) {
                for (int i = 0; i < chatActivityEnterView.sendButtonContainer.getChildCount(); i++) {
                    View child = chatActivityEnterView.sendButtonContainer.getChildAt(i);
                    if (child instanceof org.telegram.ui.Components.ChatActivityEnterView.SendButton) {
                        child.setOnClickListener(v -> forwardMessages());
                    }
                }
            }
        }
        
        if (replyCloseImageView != null) {
            replyCloseImageView.setOnClickListener(v -> {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setEditingMessageObject(null, null, false);
                }
                hideFieldPanel(true);
                selectedMessage = null;
                editingMessageObject = null;
                updateBottomOverlay();
                updateVisibleRows();
                updateSendBadge();
            });
        }
        
        if (avatarContainer != null) {
            avatarContainer.setTitle(LocaleController.getString("SpecialForwardTitle", R.string.SpecialForwardTitle));
            avatarContainer.setSubtitle(messages.size() + " messages");
            
            // Customize avatar to be circular blue forward button
            if (avatarContainer.avatarImageView != null) {
                avatarContainer.avatarImageView.setVisibility(View.GONE);
            }
            
            ImageView customAvatar = new ImageView(getParentActivity());
            customAvatar.setImageResource(R.drawable.baseline_forward_24);
            customAvatar.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            
            android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circleBg.setColor(0xff2196f3); // Telegraph blue
            customAvatar.setBackground(circleBg);
            
            customAvatar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            customAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            customAvatar.setOnClickListener(v -> forwardMessages());
            
            avatarContainer.addView(customAvatar, 0, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        }
        
        setupCustomEditPanel();
        updateSendBadge();
        
        rebuildGroupedMessages();
        
        if (messages.size() == 1) {
            startEditingMessage(messages.get(0));
        } else {
            updateBottomOverlay();
        }
    }

    private void setupCustomEditPanel() {
        if (chatActivityEnterTopView == null || getParentActivity() == null) return;
        
        Context context = getParentActivity();
        
        if (inlineResetView != null) chatActivityEnterTopView.removeView(inlineResetView);
        if (inlineReplaceLinkView != null) chatActivityEnterTopView.removeView(inlineReplaceLinkView);
        if (inlineDeleteLinkView != null) chatActivityEnterTopView.removeView(inlineDeleteLinkView);
        
        inlineDeleteLinkView = new ImageView(context);
        inlineDeleteLinkView.setImageResource(R.drawable.msg_delete);
        inlineDeleteLinkView.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), android.graphics.PorterDuff.Mode.SRC_IN));
        inlineDeleteLinkView.setScaleType(ImageView.ScaleType.CENTER);
        inlineDeleteLinkView.setBackground(Theme.getSelectorDrawable(true));
        inlineDeleteLinkView.setOnClickListener(v -> {
            if (selectedMessage != null) {
                deleteLinks(selectedMessage, 15);
                startEditingMessage(selectedMessage);
                Toast.makeText(context, "Deleted all links in message", Toast.LENGTH_SHORT).show();
            }
        });
        
        inlineReplaceLinkView = new ImageView(context);
        inlineReplaceLinkView.setImageResource(R.drawable.baseline_link_24);
        inlineReplaceLinkView.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), android.graphics.PorterDuff.Mode.SRC_IN));
        inlineReplaceLinkView.setScaleType(ImageView.ScaleType.CENTER);
        inlineReplaceLinkView.setBackground(Theme.getSelectorDrawable(true));
        inlineReplaceLinkView.setOnClickListener(v -> {
            if (selectedMessage != null) {
                replaceLinkForSelectedMessage();
            }
        });
        
        inlineResetView = new ImageView(context);
        inlineResetView.setImageResource(R.drawable.msg_repeat);
        inlineResetView.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), android.graphics.PorterDuff.Mode.SRC_IN));
        inlineResetView.setScaleType(ImageView.ScaleType.CENTER);
        inlineResetView.setBackground(Theme.getSelectorDrawable(true));
        inlineResetView.setOnClickListener(v -> {
            resetSelectedMessage();
        });
        
        chatActivityEnterTopView.addView(inlineDeleteLinkView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 52, 0));
        chatActivityEnterTopView.addView(inlineReplaceLinkView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 100, 0));
        chatActivityEnterTopView.addView(inlineResetView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 148, 0));
    }

    private void updateSendBadge() {
        if (chatActivityEnterView == null || getParentActivity() == null) return;
        
        View enterSendBtn = chatActivityEnterView.getSendButtonInternal();
        if (enterSendBtn instanceof org.telegram.ui.Components.ChatActivityEnterView.SendButton) {
            ((org.telegram.ui.Components.ChatActivityEnterView.SendButton) enterSendBtn).setCount(messages.size(), true);
        }
    }

    @Override
    protected boolean multiChatListOnItemClick(View view, int position) {
        if (position >= 0 && position < messages.size()) {
            startEditingMessage(messages.get(position));
            return true;
        }
        return false;
    }

    private void startEditingMessage(MessageObject messageObject) {
        if (messageObject == null) return;
        this.selectedMessage = messageObject;
        this.editingMessageObject = messageObject;
        
        showFieldPanelForEdit(true, messageObject);
        setupCustomEditPanel();
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(messageObject, null, false);
            View doneBtn = chatActivityEnterView.getDoneButton();
            if (doneBtn != null) {
                doneBtn.setOnClickListener(v -> {
                    CharSequence text = chatActivityEnterView.getFieldText();
                    if (selectedMessage != null) {
                        updateMessageText(selectedMessage, text);
                        int idx = messages.indexOf(selectedMessage);
                        if (idx >= 0) {
                            try {
                                TLRPC.Message messageClone = cloneMessage(selectedMessage.messageOwner);
                                if (messageClone != null) {
                                    messageClone.id = selectedMessage.getId();
                                    MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                                    newCloned.forceUpdate = true;
                                    newCloned.checkLayout();
                                    messages.set(idx, newCloned);
                                    rebuildGroupedMessages();
                                    if (chatAdapter != null) {
                                        chatAdapter.notifyItemChanged(idx);
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                    chatActivityEnterView.setEditingMessageObject(null, null, false);
                    hideFieldPanel(true);
                    selectedMessage = null;
                    editingMessageObject = null;
                    updateBottomOverlay();
                    updateVisibleRows();
                    updateSendBadge();
                });
            }
            chatActivityEnterView.setVisibility(View.VISIBLE);
            chatActivityEnterView.setFieldFocused();
        }
        
        updateBottomOverlay();
        updateVisibleRows();
    }

    @Override
    protected void multiChatOnMessageSend(CharSequence charSequence, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long topicId) {
        if (selectedMessage != null) {
            updateMessageText(selectedMessage, charSequence);
            
            int idx = messages.indexOf(selectedMessage);
            if (idx >= 0) {
                try {
                    TLRPC.Message messageClone = cloneMessage(selectedMessage.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = selectedMessage.getId();
                        MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        
                        messages.set(idx, newCloned);
                        rebuildGroupedMessages();
                        if (chatAdapter != null) {
                            chatAdapter.notifyItemChanged(idx);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(null, null, false);
        }
        hideFieldPanel(true);
        selectedMessage = null;
        editingMessageObject = null;
        
        updateBottomOverlay();
        updateVisibleRows();
        updateSendBadge();
    }

    @Override
    protected void multiChatCreateMenuItems(org.telegram.ui.ActionBar.ActionBarMenu menu) {
        menu.addItem(MENU_EDIT_OPTIONS, R.drawable.baseline_edit_24);
        menu.addItem(MENU_PREVIEW, R.drawable.msg_views_solar);
    }

    @Override
    protected boolean multiChatOnMenuItemClicked(int itemId) {
        if (itemId == MENU_EDIT_OPTIONS) {
            showEditOptionsMenu();
            return true;
        } else if (itemId == MENU_PREVIEW) {
            showPreviewDialog();
            return true;
        }
        return false;
    }

    private void showEditOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardTitle", R.string.SpecialForwardTitle) + " Options");
        
        CharSequence[] items = new CharSequence[]{
            LocaleController.getString("SpecialForwardResetAll", R.string.SpecialForwardResetAll),
            LocaleController.getString("SpecialForwardReplaceAllTexts", R.string.SpecialForwardReplaceAllTexts),
            LocaleController.getString("SpecialForwardReplaceAllLinks", R.string.SpecialForwardReplaceAllLinks),
            LocaleController.getString("SpecialForwardDeleteAllLinks", R.string.SpecialForwardDeleteAllLinks),
            LocaleController.getString("SpecialForwardDeleteAllCaptions", R.string.SpecialForwardDeleteAllCaptions),
            LocaleController.getString("SpecialForwardAsFile", R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF")
        };
        
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                resetAll();
            } else if (which == 1) {
                replaceAllTexts();
            } else if (which == 2) {
                replaceAllLinks();
            } else if (which == 3) {
                deleteAllLinks();
            } else if (which == 4) {
                deleteAllCaptions();
            } else if (which == 5) {
                forwardAsFile = !forwardAsFile;
                Toast.makeText(getParentActivity(), LocaleController.getString("SpecialForwardAsFile", R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showPreviewDialog() {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            CharSequence text = getMessageText(msg);
            if (text != null && text.length() > 0) {
                if (builder.length() > 0) {
                    builder.append("\n\n---\n\n");
                }
                builder.append("Message #").append(String.valueOf(i + 1)).append(":\n").append(text);
            }
        }
        
        if (builder.length() == 0) {
            Toast.makeText(getParentActivity(), "All messages are empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder alert = new AlertDialog.Builder(getParentActivity());
        alert.setTitle("Forward Messages Preview");
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        
        TextView textView = new TextView(getParentActivity());
        textView.setText(builder);
        textView.setTextSize(16);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        alert.setView(layout);
        alert.setPositiveButton("OK", null);
        alert.show();
    }

    private void updateMessageText(MessageObject msg, CharSequence charSequence) {
        if (msg == null) return;
        ArrayList<TLRPC.MessageEntity> entities = charSequence == null ? null : MediaDataController.getInstance(currentAccount).getEntities(new CharSequence[]{charSequence}, true);
        msg.messageOwner.message = charSequence == null ? "" : charSequence.toString();
        TLRPC.Message message = msg.messageOwner;
        if (entities == null) {
            entities = new ArrayList<>();
        }
        message.entities = entities;
        msg.messageOwner.send_state = 3;
        msg.forceUpdate = true;
        if (!msg.isMediaEmpty()) {
            msg.caption = null;
            msg.generateCaption();
        } else if (TextUtils.isEmpty(msg.messageOwner.message)) {
            msg.messageOwner.message = "Empty message";
        }
        msg.applyNewText();
        msg.checkLayout();
        msg.messageOwner.send_state = 0;
    }

    private CharSequence getMessageText(MessageObject msg) {
        if (msg == null) return "";
        return msg.isMediaEmpty() ? msg.messageText : msg.caption;
    }

    private void resetSelectedMessage() {
        if (selectedMessage == null || originalMessagesMap == null) return;
        MessageObject originalObj = originalMessagesMap.get(selectedMessage.getId());
        if (originalObj != null) {
            try {
                TLRPC.Message messageClone = cloneMessage(originalObj.messageOwner);
                if (messageClone != null) {
                    messageClone.id = selectedMessage.getId();
                    MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                    newCloned.forceUpdate = true;
                    newCloned.checkLayout();
                    
                    int idx = messages.indexOf(selectedMessage);
                    if (idx >= 0) {
                        messages.set(idx, newCloned);
                        rebuildGroupedMessages();
                        if (chatAdapter != null) {
                            chatAdapter.notifyItemChanged(idx);
                        }
                    }
                    
                    startEditingMessage(newCloned);
                    Toast.makeText(getParentActivity(), "Message reset to original", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void resetAll() {
        if (originalMessagesMap == null || originalMessagesMap.size() == 0) return;
        
        for (int i = 0; i < messages.size(); i++) {
            MessageObject workingObj = messages.get(i);
            MessageObject originalObj = originalMessagesMap.get(workingObj.getId());
            if (originalObj != null) {
                try {
                    TLRPC.Message messageClone = cloneMessage(originalObj.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = workingObj.getId();
                        MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        
        rebuildGroupedMessages();
        
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        
        if (selectedMessage != null) {
            startEditingMessage(selectedMessage);
        }
        updateSendBadge();
        Toast.makeText(getParentActivity(), LocaleController.getString("SpecialForwardResetSuccess", R.string.SpecialForwardResetSuccess), Toast.LENGTH_SHORT).show();
    }

    private void replaceAllTexts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardReplaceAllTexts", R.string.SpecialForwardReplaceAllTexts));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final org.telegram.ui.Cells.TextCheckCell checkPre = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
        checkPre.setTextAndCheck("Prepend (add at beginning)", false, true);
        
        final org.telegram.ui.Cells.TextCheckCell checkPost = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
        checkPost.setTextAndCheck("Append (add at end)", false, true);
        
        final EditText searchPhrase = new EditText(getParentActivity());
        searchPhrase.setHint("Phrase to search and replace (leave empty to replace all)");
        searchPhrase.setInputType(InputType.TYPE_CLASS_TEXT);
        
        final EditText input = new EditText(getParentActivity());
        input.setHint("Replacement / New text");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        
        final boolean[] prep = new boolean[]{false};
        final boolean[] app = new boolean[]{false};
        checkPre.setOnClickListener(v -> {
            prep[0] = !prep[0];
            checkPre.setChecked(prep[0]);
            if (prep[0]) {
                app[0] = false;
                checkPost.setChecked(false);
                searchPhrase.setVisibility(View.GONE);
            } else {
                searchPhrase.setVisibility(View.VISIBLE);
            }
        });
        checkPost.setOnClickListener(v -> {
            app[0] = !app[0];
            checkPost.setChecked(app[0]);
            if (app[0]) {
                prep[0] = false;
                checkPre.setChecked(false);
                searchPhrase.setVisibility(View.GONE);
            } else {
                searchPhrase.setVisibility(View.VISIBLE);
            }
        });
        
        layout.addView(checkPre, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(checkPost, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(searchPhrase, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 12));
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        builder.setView(layout);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newText = input.getText().toString();
            String searchVal = searchPhrase.getText().toString();
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                CharSequence oldText = getMessageText(msg);
                if (oldText == null) oldText = "";
                
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(oldText);
                boolean isTextEmpty = oldText.length() == 0;
                
                if (prep[0]) {
                    spannableStringBuilder.replace(0, 0, isTextEmpty ? newText : newText + "\n");
                } else if (app[0]) {
                    if (isTextEmpty) {
                        spannableStringBuilder.append(newText);
                    } else {
                        spannableStringBuilder.append("\n").append(newText);
                    }
                } else {
                    if (TextUtils.isEmpty(searchVal)) {
                        spannableStringBuilder = new SpannableStringBuilder(newText);
                    } else {
                        String searchStr = searchVal;
                        String fullStr = spannableStringBuilder.toString();
                        int index = fullStr.indexOf(searchStr);
                        while (index != -1) {
                            spannableStringBuilder.replace(index, index + searchStr.length(), newText);
                            fullStr = spannableStringBuilder.toString();
                            index = fullStr.indexOf(searchStr, index + newText.length());
                        }
                    }
                }
                
                updateMessageText(msg, spannableStringBuilder);
                
                try {
                    TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = msg.getId();
                        MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            rebuildGroupedMessages();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            if (selectedMessage != null) {
                startEditingMessage(selectedMessage);
            }
            updateSendBadge();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardReplaceAllLinks", R.string.SpecialForwardReplaceAllLinks));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final boolean[] checked = new boolean[]{true, true, true, true};
        String[] titles = new String[]{
            "Internal links (tg://, t.me)",
            "Mentions (@username)",
            "Hashtags (#tag)",
            "External links (http://, https://)"
        };
        
        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            final org.telegram.ui.Cells.TextCheckCell cell = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
            cell.setTextAndCheck(titles[i], checked[i], i < titles.length - 1);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx]);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        
        final EditText input = new EditText(getParentActivity());
        input.setHint("New link (e.g. https://t.me/...)");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        
        builder.setView(layout);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newLink = input.getText().toString();
            int typeMask = 0;
            if (checked[0]) typeMask |= 1;
            if (checked[1]) typeMask |= 2;
            if (checked[2]) typeMask |= 4;
            if (checked[3]) typeMask |= 8;
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                replaceLinks(msg, typeMask, newLink);
                try {
                    TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = msg.getId();
                        MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            rebuildGroupedMessages();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            if (selectedMessage != null) {
                startEditingMessage(selectedMessage);
            }
            updateSendBadge();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceLinkForSelectedMessage() {
        if (selectedMessage == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Replace Links in Selected Message");
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final boolean[] checked = new boolean[]{true, true, true, true};
        String[] titles = new String[]{
            "Internal links (tg://, t.me)",
            "Mentions (@username)",
            "Hashtags (#tag)",
            "External links (http://, https://)"
        };
        
        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            final org.telegram.ui.Cells.TextCheckCell cell = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
            cell.setTextAndCheck(titles[i], checked[i], i < titles.length - 1);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx]);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        
        final EditText input = new EditText(getParentActivity());
        input.setHint("New link (e.g. https://t.me/...)");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        
        builder.setView(layout);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newLink = input.getText().toString();
            int typeMask = 0;
            if (checked[0]) typeMask |= 1;
            if (checked[1]) typeMask |= 2;
            if (checked[2]) typeMask |= 4;
            if (checked[3]) typeMask |= 8;
            
            replaceLinks(selectedMessage, typeMask, newLink);
            startEditingMessage(selectedMessage);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceLinks(MessageObject msg, int typeMask, CharSequence newLink) {
        CharSequence text = getMessageText(msg);
        if (text == null) return;
        
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        Object[] spans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), Object.class);
        if (spans != null && spans.length > 0) {
            for (Object obj : spans) {
                if (obj instanceof URLSpan) {
                    String url = ((URLSpan) obj).getURL();
                    boolean shouldReplace = (org.telegram.messenger.browser.Browser.isInternalUrl(url, null) && (typeMask & 1) != 0)
                            || (url.startsWith("@") && (typeMask & 2) != 0)
                            || (url.startsWith("#") && (typeMask & 4) != 0)
                            || (!url.startsWith("@") && !url.startsWith("#") && !org.telegram.messenger.browser.Browser.isInternalUrl(url, null) && (typeMask & 8) != 0);
                    
                    if (shouldReplace) {
                        try {
                            int spanStart = spannableStringBuilder.getSpanStart(obj);
                            int spanEnd = spannableStringBuilder.getSpanEnd(obj);
                            if (spanStart >= 0 && spanEnd >= 0 && spanStart <= spannableStringBuilder.length() && spanEnd <= spannableStringBuilder.length()) {
                                spannableStringBuilder.replace(spanStart, spanEnd, newLink);
                                spannableStringBuilder.removeSpan(obj);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }
        }
        updateMessageText(msg, spannableStringBuilder);
    }

    private void deleteAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardDeleteAllLinks", R.string.SpecialForwardDeleteAllLinks));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final boolean[] checked = new boolean[]{true, true, true, true};
        String[] titles = new String[]{
            "Internal links (tg://, t.me)",
            "Mentions (@username)",
            "Hashtags (#tag)",
            "External links (http://, https://)"
        };
        
        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            final org.telegram.ui.Cells.TextCheckCell cell = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
            cell.setTextAndCheck(titles[i], checked[i], i < titles.length - 1);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx]);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        
        builder.setView(layout);
        builder.setPositiveButton("Delete", (dialog, which) -> {
            int typeMask = 0;
            if (checked[0]) typeMask |= 1;
            if (checked[1]) typeMask |= 2;
            if (checked[2]) typeMask |= 4;
            if (checked[3]) typeMask |= 8;
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                deleteLinks(msg, typeMask);
                try {
                    TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = msg.getId();
                        MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            rebuildGroupedMessages();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            if (selectedMessage != null) {
                startEditingMessage(selectedMessage);
            }
            updateSendBadge();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteLinks(MessageObject msg, int typeMask) {
        CharSequence text = getMessageText(msg);
        if (text == null) return;
        
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        Object[] spans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), Object.class);
        if (spans != null && spans.length > 0) {
            for (Object obj : spans) {
                if (obj instanceof URLSpan) {
                    String url = ((URLSpan) obj).getURL();
                    boolean shouldDelete = (org.telegram.messenger.browser.Browser.isInternalUrl(url, null) && (typeMask & 1) != 0)
                            || (url.startsWith("@") && (typeMask & 2) != 0)
                            || (url.startsWith("#") && (typeMask & 4) != 0)
                            || (!url.startsWith("@") && !url.startsWith("#") && !org.telegram.messenger.browser.Browser.isInternalUrl(url, null) && (typeMask & 8) != 0);
                    
                    if (shouldDelete) {
                        try {
                            int spanStart = spannableStringBuilder.getSpanStart(obj);
                            int spanEnd = spannableStringBuilder.getSpanEnd(obj);
                            if (spanStart >= 0 && spanEnd >= 0 && spanStart <= spannableStringBuilder.length() && spanEnd <= spannableStringBuilder.length()) {
                                spannableStringBuilder.delete(spanStart, spanEnd);
                                spannableStringBuilder.removeSpan(obj);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }
        }
        updateMessageText(msg, spannableStringBuilder);
    }

    private void deleteAllCaptions() {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            msg.caption = null;
            if (msg.isPhoto() || msg.isVideo() || msg.isDocument()) {
                msg.messageText = "";
                if (msg.messageOwner != null) msg.messageOwner.message = "";
            }
            updateMessageText(msg, "");
            try {
                TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                if (messageClone != null) {
                    messageClone.id = msg.getId();
                    MessageObject newCloned = new MessageObject(currentAccount, messageClone, false, true);
                    newCloned.forceUpdate = true;
                    newCloned.checkLayout();
                    messages.set(i, newCloned);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        
        rebuildGroupedMessages();
        
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        if (selectedMessage != null) {
            startEditingMessage(selectedMessage);
        }
        updateSendBadge();
    }

    private void rebuildGroupedMessages() {
        if (groupedMessagesMap == null) return;
        groupedMessagesMap.clear();
        for (MessageObject msgObj : messages) {
            if (msgObj.getGroupId() != 0) {
                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(msgObj.getGroupId());
                if (groupedMessages == null) {
                    groupedMessages = new MessageObject.GroupedMessages();
                    groupedMessages.groupId = msgObj.getGroupId();
                    groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                }
                groupedMessages.messages.add(msgObj);
                groupedMessages.reversed = reversed;
            }
        }
        for (int i = 0; i < groupedMessagesMap.size(); i++) {
            groupedMessagesMap.valueAt(i).calculate();
        }
    }

    private void forwardMessages() {
        if (messages.isEmpty() || getParentActivity() == null) return;
        
        SpecialForwardShareAlert shareAlert = new SpecialForwardShareAlert(
            getParentActivity(), 
            currentAccount, 
            messages, 
            forwardAsFile, 
            (dids, showQuoteState, keepCaptionState) -> {
                performForwardToSelectedChats(dids, showQuoteState, keepCaptionState);
            }
        );
        showDialog(shareAlert);
    }

    private void performForwardToSelectedChats(ArrayList<Long> dids, boolean showQuote, boolean keepCaption) {
        ArrayList<MessageObject> forwardList = new ArrayList<>(messages);
        java.util.Collections.sort(forwardList, (o1, o2) -> Integer.compare(o1.getId(), o2.getId()));

        for (long peer : dids) {
            if (forwardAsFile) {
                for (int j = 0; j < forwardList.size(); j++) {
                    MessageObject msg = forwardList.get(j);
                    if (msg == null || msg.messageOwner == null) continue;
                    
                    String caption = keepCaption && msg.caption != null ? msg.caption.toString() : "";
                    ArrayList<TLRPC.MessageEntity> entities = keepCaption ? msg.messageOwner.entities : new ArrayList<>();
                    String attachPath = msg.messageOwner.attachPath;
                    
                    if (!TextUtils.isEmpty(attachPath) && !attachPath.startsWith("http")) {
                        ArrayList<String> paths = new ArrayList<>();
                        paths.add(attachPath);
                        SendMessagesHelper.prepareSendingDocuments(getAccountInstance(), paths, paths, null, caption, entities, null, peer, null, null, null, null, null, false, 0, 0, null, null, 0, 0, false, 0, 0, null, null, null, null, false);
                    } else {
                        ArrayList<MessageObject> singleList = new ArrayList<>();
                        singleList.add(msg);
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(singleList, peer, !showQuote, !keepCaption, true, 0, 0);
                    }
                }
            } else {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(forwardList, peer, !showQuote, !keepCaption, true, 0, 0);
            }
        }
        finishFragment();
    }

    public static class SpecialForwardShareAlert extends BottomSheet {
        private final int currentAccount;
        private final ArrayList<MessageObject> messages;
        private final boolean forwardAsFile;
        private final ShareAlertCallback callback;
        
        private ArrayList<TLRPC.Dialog> allDialogs;
        private final ArrayList<TLRPC.Dialog> filteredDialogs = new ArrayList<>();
        private final LongSparseArray<TLRPC.Dialog> selectedDialogs = new LongSparseArray<>();
        
        private int activeCategory = 0; 
        private String searchQuery = "";
        
        private boolean showQuote = false; 
        private boolean keepCaption = true; 
        
        private RecyclerView gridView;
        private ShareAdapter adapter;
        private EditText searchEditText;
        private ImageView sendButton;
        private FrameLayout sendButtonContainer;
        private TextView sendBadge;
        
        public interface ShareAlertCallback {
            void onShareSelected(ArrayList<Long> dids, boolean showQuote, boolean keepCaption);
        }
        
        public SpecialForwardShareAlert(Context context, int account, ArrayList<MessageObject> messages, boolean asFile, ShareAlertCallback callback) {
            super(context, true);
            this.currentAccount = account;
            this.messages = messages;
            this.forwardAsFile = asFile;
            this.callback = callback;
            
            allDialogs = new ArrayList<>(org.telegram.messenger.MessagesController.getInstance(currentAccount).getAllDialogs());
            filterDialogs();
            
            LinearLayout rootLayout = new LinearLayout(context);
            rootLayout.setOrientation(LinearLayout.VERTICAL);
            rootLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(12));
            
            // 1. Bottom Sheet Top Slide Handle
            View handleView = new View(context);
            android.graphics.drawable.GradientDrawable handleBg = new android.graphics.drawable.GradientDrawable();
            int headerColor = Theme.getColor(Theme.key_divider);
            handleBg.setColor(headerColor != 0 ? headerColor : 0x22888888);
            handleBg.setCornerRadius(AndroidUtilities.dp(2));
            handleView.setBackground(handleBg);
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.bottomMargin = AndroidUtilities.dp(8);
            rootLayout.addView(handleView, handleLp);
            
            // 2. Search Box and Switches Top Row
            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            
            LinearLayout searchContainer = new LinearLayout(context);
            searchContainer.setOrientation(LinearLayout.HORIZONTAL);
            searchContainer.setGravity(Gravity.CENTER_VERTICAL);
            searchContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_dialogBackgroundGray)));
            searchContainer.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            
            ImageView searchIcon = new ImageView(context);
            searchIcon.setImageResource(R.drawable.baseline_search_24);
            searchIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(0xff888888, android.graphics.PorterDuff.Mode.SRC_IN));
            searchContainer.addView(searchIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
            
            searchEditText = new EditText(context);
            searchEditText.setHint("Send to...");
            searchEditText.setHintTextColor(0x88888888);
            searchEditText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            searchEditText.setTextSize(14);
            searchEditText.setBackground(null);
            searchEditText.setSingleLine(true);
            searchEditText.setInputType(InputType.TYPE_CLASS_TEXT);
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(android.text.Editable s) {
                    searchQuery = s.toString().trim().toLowerCase();
                    filterDialogs();
                }
            });
            searchContainer.addView(searchEditText, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));
            
            topRow.addView(searchContainer, LayoutHelper.createLinear(0, 36, 1.0f, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            // QUOTE Switch
            LinearLayout quoteLayout = new LinearLayout(context);
            quoteLayout.setOrientation(LinearLayout.VERTICAL);
            quoteLayout.setGravity(Gravity.CENTER);
            
            TextView quoteText = new TextView(context);
            quoteText.setText("QUOTE");
            quoteText.setTextSize(9);
            quoteText.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
            quoteText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            Switch quoteSwitch = new Switch(context);
            quoteSwitch.setChecked(showQuote, false);
            quoteSwitch.setOnCheckedChangeListener((view, checked) -> showQuote = checked);
            
            quoteLayout.addView(quoteText);
            quoteLayout.addView(quoteSwitch, LayoutHelper.createLinear(37, 20, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            topRow.addView(quoteLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            // CAP Switch
            LinearLayout capLayout = new LinearLayout(context);
            capLayout.setOrientation(LinearLayout.VERTICAL);
            capLayout.setGravity(Gravity.CENTER);
            
            TextView capText = new TextView(context);
            capText.setText("CAP");
            capText.setTextSize(9);
            capText.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
            capText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            Switch capSwitch = new Switch(context);
            capSwitch.setChecked(keepCaption, false);
            capSwitch.setOnCheckedChangeListener((view, checked) -> keepCaption = checked);
            
            capLayout.addView(capText);
            capLayout.addView(capSwitch, LayoutHelper.createLinear(37, 20, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            topRow.addView(capLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            ImageView threeDots = new ImageView(context);
            threeDots.setImageResource(R.drawable.ic_ab_other);
            threeDots.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), android.graphics.PorterDuff.Mode.SRC_IN));
            topRow.addView(threeDots, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            rootLayout.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            
            // 3. Category tabs
            HorizontalScrollView scrollCategories = new HorizontalScrollView(context);
            scrollCategories.setHorizontalScrollBarEnabled(false);
            
            LinearLayout categoriesRow = new LinearLayout(context);
            categoriesRow.setOrientation(LinearLayout.HORIZONTAL);
            categoriesRow.setGravity(Gravity.CENTER_VERTICAL);
            
            int[] icons = new int[]{
                R.drawable.filter_all_solar,        
                R.drawable.filter_private_solar,   
                R.drawable.filter_groups_solar,        
                R.drawable.filter_favorite_solar,          
                R.drawable.msg_contacts_solar,       
                R.drawable.msg_groups_solar,       
                R.drawable.filter_channel_solar,            
                R.drawable.filter_bots_solar            
            };
            
            int selectedColor = Theme.getColor(Theme.key_dialogTextBlue);
            int unselectedColor = Theme.getColor(Theme.key_dialogIcon);
            int selectedBgColor = (selectedColor & 0x00ffffff) | 0x1a000000;
            
            final View[] categoryTabs = new View[icons.length];
            for (int i = 0; i < icons.length; i++) {
                final int idx = i;
                ImageView tabIcon = new ImageView(context);
                tabIcon.setImageResource(icons[i]);
                tabIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(i == activeCategory ? selectedColor : unselectedColor, android.graphics.PorterDuff.Mode.SRC_IN));
                tabIcon.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                
                android.graphics.drawable.GradientDrawable tabBg = new android.graphics.drawable.GradientDrawable();
                tabBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                tabBg.setColor(i == activeCategory ? selectedBgColor : Color.TRANSPARENT);
                tabIcon.setBackground(tabBg);
                
                tabIcon.setOnClickListener(v -> {
                    activeCategory = idx;
                    for (int j = 0; j < categoryTabs.length; j++) {
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        bg.setColor(j == activeCategory ? selectedBgColor : Color.TRANSPARENT);
                        categoryTabs[j].setBackground(bg);
                        if (j == activeCategory) {
                            ((ImageView) categoryTabs[j]).setColorFilter(new android.graphics.PorterDuffColorFilter(selectedColor, android.graphics.PorterDuff.Mode.SRC_IN));
                        } else {
                            ((ImageView) categoryTabs[j]).setColorFilter(new android.graphics.PorterDuffColorFilter(unselectedColor, android.graphics.PorterDuff.Mode.SRC_IN));
                        }
                    }
                    filterDialogs();
                });
                
                categoryTabs[i] = tabIcon;
                categoriesRow.addView(tabIcon, LayoutHelper.createLinear(40, 40, 0, 4, 0, 4, 0));
            }
            scrollCategories.addView(categoriesRow);
            rootLayout.addView(scrollCategories, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            
            // 4. Grid view and floating submit send button
            FrameLayout mainContainer = new FrameLayout(context);
            
            gridView = new RecyclerView(context);
            gridView.setLayoutManager(new GridLayoutManager(context, 4));
            adapter = new ShareAdapter(context);
            gridView.setAdapter(adapter);
            mainContainer.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 260));
            
            sendButtonContainer = new FrameLayout(context);
            sendButtonContainer.setVisibility(View.GONE);
            
            sendButton = new ImageView(context);
            sendButton.setImageResource(R.drawable.baseline_forward_24);
            sendButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            
            android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
            btnBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            btnBg.setColor(0xff2196f3);
            sendButton.setBackground(btnBg);
            sendButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            sendButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            sendButton.setOnClickListener(v -> {
                if (selectedDialogs.size() > 0) {
                    dismiss();
                    ArrayList<Long> dids = new ArrayList<>();
                    for (int i = 0; i < selectedDialogs.size(); i++) {
                        dids.add(selectedDialogs.keyAt(i));
                    }
                    callback.onShareSelected(dids, showQuote, keepCaption);
                }
            });
            sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
            
            sendBadge = new TextView(context);
            sendBadge.setTextColor(Color.WHITE);
            sendBadge.setTextSize(9);
            sendBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            sendBadge.setGravity(Gravity.CENTER);
            
            android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
            badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            badgeBg.setColor(0xff2196f3);
            badgeBg.setStroke(AndroidUtilities.dp(1.2f), 0xffffffff);
            sendBadge.setBackground(badgeBg);
            
            FrameLayout.LayoutParams lpBadge = new FrameLayout.LayoutParams(
                AndroidUtilities.dp(16), 
                AndroidUtilities.dp(16), 
                Gravity.RIGHT | Gravity.TOP
            );
            sendButtonContainer.addView(sendBadge, lpBadge);
            
            mainContainer.addView(sendButtonContainer, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 16, 16));
            
            rootLayout.addView(mainContainer);
            containerView = rootLayout;
        }
        
        private void filterDialogs() {
            filteredDialogs.clear();
            
            // If Contacts category is active and search query is empty, show all contacts
            if (activeCategory == 4 && TextUtils.isEmpty(searchQuery)) {
                ArrayList<TLRPC.TL_contact> contactsList = org.telegram.messenger.ContactsController.getInstance(currentAccount).contacts;
                for (TLRPC.TL_contact contact : contactsList) {
                    TLRPC.Dialog fakeDialog = new TLRPC.TL_dialog();
                    fakeDialog.id = contact.user_id;
                    filteredDialogs.add(fakeDialog);
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                return;
            }
            
            for (TLRPC.Dialog dialog : allDialogs) {
                long dialogId = dialog.id;
                
                if (!TextUtils.isEmpty(searchQuery)) {
                    String title = "";
                    if (DialogObject.isUserDialog(dialogId)) {
                        TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
                        if (user != null) {
                            title = (user.first_name + " " + user.last_name).toLowerCase();
                        }
                    } else {
                        TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        if (chat != null) {
                            title = chat.title.toLowerCase();
                        }
                    }
                    if (!title.contains(searchQuery)) {
                        continue;
                    }
                }
                
                if (activeCategory == 1) { // PMs
                    if (!DialogObject.isUserDialog(dialogId)) continue;
                    TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user == null || user.bot) continue;
                } else if (activeCategory == 2) { // Groups
                    if (!DialogObject.isChatDialog(dialogId)) continue;
                    TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat == null || ChatObject.isChannel(chat)) continue;
                } else if (activeCategory == 3) { // Favorites (Pinned)
                    if (!dialog.pinned) continue;
                } else if (activeCategory == 4) { // Contacts
                    if (!DialogObject.isUserDialog(dialogId)) continue;
                    if (!org.telegram.messenger.ContactsController.getInstance(currentAccount).isContact(dialogId)) continue;
                } else if (activeCategory == 5) { // Supergroups
                    if (!DialogObject.isChatDialog(dialogId)) continue;
                    TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat == null || !ChatObject.isChannel(chat) || !chat.megagroup) continue;
                } else if (activeCategory == 6) { // Channels
                    if (!DialogObject.isChatDialog(dialogId)) continue;
                    TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat == null || !ChatObject.isChannel(chat) || chat.megagroup) continue;
                } else if (activeCategory == 7) { // Bots
                    if (!DialogObject.isUserDialog(dialogId)) continue;
                    TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user == null || !user.bot) continue;
                }
                
                filteredDialogs.add(dialog);
            }
            
            // Also search in contacts if searching
            if (!TextUtils.isEmpty(searchQuery)) {
                ArrayList<TLRPC.TL_contact> contactsList = org.telegram.messenger.ContactsController.getInstance(currentAccount).contacts;
                for (TLRPC.TL_contact contact : contactsList) {
                    TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (user != null) {
                        String title = (user.first_name + " " + user.last_name).toLowerCase();
                        if (title.contains(searchQuery)) {
                            // Check if already in filteredDialogs
                            boolean exists = false;
                            for (TLRPC.Dialog d : filteredDialogs) {
                                    if (d.id == user.id) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                TLRPC.Dialog fakeDialog = new TLRPC.TL_dialog();
                                fakeDialog.id = user.id;
                                filteredDialogs.add(fakeDialog);
                            }
                        }
                    }
                }
            }
            
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
        
        private class ShareAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            private final Context context;
            
            public ShareAdapter(Context context) {
                this.context = context;
            }
            
            @Override
            public int getItemCount() {
                return filteredDialogs.size();
            }
            
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                org.telegram.ui.Cells.ShareDialogCell cell = new org.telegram.ui.Cells.ShareDialogCell(context, org.telegram.ui.Cells.ShareDialogCell.TYPE_SHARE, null);
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(92)));
                return new Holder(cell);
            }
            
            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                org.telegram.ui.Cells.ShareDialogCell cell = (org.telegram.ui.Cells.ShareDialogCell) holder.itemView;
                TLRPC.Dialog dialog = filteredDialogs.get(position);
                boolean isSelected = selectedDialogs.indexOfKey(dialog.id) >= 0;
                
                cell.setDialog(dialog.id, isSelected, null);
                
                // Reposition the CheckBox2 view to the top-left of the avatar
                for (int i = 0; i < cell.getChildCount(); i++) {
                    View child = cell.getChildAt(i);
                    if (child instanceof org.telegram.ui.Components.CheckBox2) {
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
                        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                        lp.leftMargin = -AndroidUtilities.dp(20);
                        lp.rightMargin = 0;
                        lp.topMargin = AndroidUtilities.dp(2);
                        child.setLayoutParams(lp);
                        break;
                    }
                }
                
                cell.setOnClickListener(v -> {
                    if (selectedDialogs.indexOfKey(dialog.id) >= 0) {
                        selectedDialogs.remove(dialog.id);
                    } else {
                        selectedDialogs.put(dialog.id, dialog);
                    }
                    cell.setChecked(selectedDialogs.indexOfKey(dialog.id) >= 0, true);
                    
                    if (selectedDialogs.size() > 0) {
                        sendButtonContainer.setVisibility(View.VISIBLE);
                        sendBadge.setText(String.valueOf(selectedDialogs.size()));
                    } else {
                        sendButtonContainer.setVisibility(View.GONE);
                    }
                });
            }
        }
        
        private static class Holder extends RecyclerView.ViewHolder {
            public Holder(View itemView) {
                super(itemView);
            }
        }
    }
}
