package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.style.URLSpan;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import android.text.SpannableStringBuilder;
import java.util.ArrayList;

public class SpecialForwardActivity extends ChatActivity {

    private final SparseArray<MessageObject> originalMessagesMap = new SparseArray<>();
    private MessageObject selectedMessage = null;
    private int nextUniqueId = -100000;
    private boolean forwardAsFile = false;

    private final static int MENU_RESET_ALL = 1005;
    private final static int MENU_REPLACE_TEXTS = 1006;
    private final static int MENU_REPLACE_LINKS = 1002;
    private final static int MENU_DELETE_LINKS = 1001;
    private final static int MENU_DELETE_CAPTIONS = 1007;
    private final static int MENU_TOGGLE_FORWARD_FILE = 1008;
    private final static int MENU_SEND = 1004;

    public SpecialForwardActivity(ArrayList<MessageObject> sourceMessages) {
        super(new Bundle());
        this.chatMode = 102; // Custom multi-chat / special forward mode
        
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
                    resetObj.messageOwner.grouped_id = 0L;
                    resetObj.localSentGroupId = 0L;
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
                    clonedObj.messageOwner.grouped_id = 0L;
                    clonedObj.localSentGroupId = 0L;
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
            chatActivityEnterView.setVisibility(View.VISIBLE);
            chatActivityEnterView.setAllowStickersAndGifs(true, false, false, true);
            chatActivityEnterView.showEditDoneProgress(false, true);
        }
        
        if (avatarContainer != null) {
            avatarContainer.setTitle(LocaleController.getString("SpecialForwardTitle", R.string.SpecialForwardTitle));
            avatarContainer.setSubtitle(messages.size() + " messages");
        }
        
        if (messages.size() == 1) {
            startEditingMessage(messages.get(0));
        } else {
            updateBottomOverlay();
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
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(messageObject, null, false);
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
                        newCloned.messageOwner.grouped_id = 0L;
                        newCloned.localSentGroupId = 0L;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        
                        messages.set(idx, newCloned);
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
    }

    @Override
    protected void multiChatCreateMenuItems(org.telegram.ui.ActionBar.ActionBarMenu menu) {
        org.telegram.ui.ActionBar.ActionBarMenuItem editItem = menu.addItem(0, R.drawable.ic_ab_other);
        editItem.addSubItem(MENU_RESET_ALL, R.drawable.msg_repeat, LocaleController.getString("SpecialForwardResetAll", R.string.SpecialForwardResetAll));
        editItem.addSubItem(MENU_REPLACE_TEXTS, R.drawable.msg_edit, LocaleController.getString("SpecialForwardReplaceAllTexts", R.string.SpecialForwardReplaceAllTexts));
        editItem.addSubItem(MENU_REPLACE_LINKS, R.drawable.baseline_link_24, LocaleController.getString("SpecialForwardReplaceAllLinks", R.string.SpecialForwardReplaceAllLinks));
        editItem.addSubItem(MENU_DELETE_LINKS, R.drawable.msg_delete, LocaleController.getString("SpecialForwardDeleteAllLinks", R.string.SpecialForwardDeleteAllLinks));
        editItem.addSubItem(MENU_DELETE_CAPTIONS, R.drawable.msg_delete, LocaleController.getString("SpecialForwardDeleteAllCaptions", R.string.SpecialForwardDeleteAllCaptions));
        editItem.addSubItem(MENU_TOGGLE_FORWARD_FILE, R.drawable.ic_ab_other, LocaleController.getString("SpecialForwardAsFile", R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF"));
        
        menu.addItem(MENU_SEND, R.drawable.baseline_send_24);
    }

    @Override
    protected boolean multiChatOnMenuItemClicked(int itemId) {
        if (itemId == MENU_SEND) {
            if (selectedMessage != null && chatActivityEnterView != null) {
                multiChatOnMessageSend(chatActivityEnterView.getFieldText(), false, 0, 0, 0);
            }
            forwardMessages();
            return true;
        } else if (itemId == MENU_RESET_ALL) {
            resetAll();
            return true;
        } else if (itemId == MENU_REPLACE_TEXTS) {
            replaceAllTexts();
            return true;
        } else if (itemId == MENU_REPLACE_LINKS) {
            replaceAllLinks();
            return true;
        } else if (itemId == MENU_DELETE_LINKS) {
            deleteAllLinks();
            return true;
        } else if (itemId == MENU_DELETE_CAPTIONS) {
            deleteAllCaptions();
            return true;
        } else if (itemId == MENU_TOGGLE_FORWARD_FILE) {
            forwardAsFile = !forwardAsFile;
            Toast.makeText(getParentActivity(), LocaleController.getString("SpecialForwardAsFile", R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            if (actionBar != null && actionBar.createMenu() != null) {
                actionBar.createMenu().clearItems();
                multiChatCreateMenuItems(actionBar.createMenu());
            }
            return true;
        }
        return false;
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
                        newCloned.messageOwner.grouped_id = 0L;
                        newCloned.localSentGroupId = 0L;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        Toast.makeText(getParentActivity(), LocaleController.getString("SpecialForwardResetSuccess", R.string.SpecialForwardResetSuccess), Toast.LENGTH_SHORT).show();
    }

    private void replaceAllTexts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardReplaceAllTexts", R.string.SpecialForwardReplaceAllTexts));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(8), org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(8));
        
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
                        newCloned.messageOwner.grouped_id = 0L;
                        newCloned.localSentGroupId = 0L;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardReplaceAllLinks", R.string.SpecialForwardReplaceAllLinks));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(8), org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(8));
        
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
                        newCloned.messageOwner.grouped_id = 0L;
                        newCloned.localSentGroupId = 0L;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
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
        layout.setPadding(org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(8), org.telegram.messenger.AndroidUtilities.dp(24), org.telegram.messenger.AndroidUtilities.dp(8));
        
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
                        newCloned.messageOwner.grouped_id = 0L;
                        newCloned.localSentGroupId = 0L;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
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
                    newCloned.messageOwner.grouped_id = 0L;
                    newCloned.localSentGroupId = 0L;
                    newCloned.forceUpdate = true;
                    newCloned.checkLayout();
                    messages.set(i, newCloned);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
    }

    private void forwardMessages() {
        if (messages.isEmpty()) return;

        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity dialogsActivity = new DialogsActivity(args);
        dialogsActivity.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
            @Override
            public boolean didSelectDialogs(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, int scheduleRepeatPeriod, TopicsFragment topicsFragment) {
                if (dids == null || dids.isEmpty()) return false;
                 
                fragment.finishFragment();
                removeSelfFromStack(); 
                 
                for (int i = 0; i < dids.size(); i++) {
                    MessagesStorage.TopicKey key = dids.get(i);
                    long peer = key.dialogId; 
                    long topicId = key.topicId;
                     
                    for (int j = 0; j < messages.size(); j++) {
                        MessageObject msg = messages.get(j);
                        if (msg == null || msg.messageOwner == null) continue;
                     
                        String caption = msg.caption != null ? msg.caption.toString() : "";
                        String attachPath = msg.messageOwner.attachPath;
                        ArrayList<TLRPC.MessageEntity> entities = msg.messageOwner.entities;

                        if (!TextUtils.isEmpty(attachPath) && !attachPath.startsWith("http")) {
                            if (msg.isVideo() || msg.isRoundVideo()) {
                                SendMessagesHelper.prepareSendingVideo(getAccountInstance(), attachPath, msg.videoEditedInfo, null, null, peer, null, null, null, null, entities, 0, null, notify, scheduleDate, scheduleRepeatPeriod, false, false, caption, null, 0, 0, 0, topicId, null, false);
                            } else if (msg.isPhoto()) {
                                SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), attachPath, null, null, peer, null, null, null, null, entities, null, null, 0, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, false, caption, null, 0, 0, 0, topicId, null);
                            } else {
                                ArrayList<String> paths = new ArrayList<>();
                                paths.add(attachPath);
                                SendMessagesHelper.prepareSendingDocuments(getAccountInstance(), paths, paths, null, caption, entities, null, peer, null, null, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, null, 0, 0, false, 0, topicId, null, null, null, null, false);
                            }
                            continue;
                        }

                        SendMessageParams params = null;
                        if (msg.isPhoto()) {
                            TLRPC.TL_photo photo = null;
                            if (msg.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                photo = (TLRPC.TL_photo) msg.messageOwner.media.photo;
                            }
                            if (photo != null) {
                                params = SendMessageParams.of(photo, null, peer, null, null, caption, entities, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, null, false);
                            }
                        } else if (msg.isVideo() || msg.isDocument()) {
                            TLRPC.TL_document document = null;
                            if (msg.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                document = (TLRPC.TL_document) msg.messageOwner.media.document;
                            }
                            if (document != null) {
                                params = SendMessageParams.of(document, msg.videoEditedInfo, null, peer, null, null, caption, entities, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, null, null, false);
                            }
                        }
                        
                        if (params == null) {
                            if (msg.messageText != null && !TextUtils.isEmpty(msg.messageText)) {
                                params = SendMessageParams.of(msg.messageText.toString(), peer, null, null, null, true, entities, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, false);
                            } else {
                                params = SendMessageParams.of(msg);
                                params.peer = peer;
                                params.caption = caption;
                                params.entities = entities;
                            }
                        }

                        if (params != null) {
                            params.monoForumPeer = topicId;
                            getAccountInstance().getSendMessagesHelper().sendMessage(params);
                        }
                    }
                }
                return true;
            }
        });
        presentFragment(dialogsActivity);
    }
}
