
package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.style.URLSpan;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import java.io.File;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.SendMessagesHelper.SendMessageParams;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.messenger.Utilities;
import android.text.SpannableStringBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PhotoPickerActivity;
import org.telegram.messenger.MediaController;

public class SpecialForwardActivity extends BaseFragment {

    private ArrayList<MessageObject> messages;
    private ArrayList<MessageObject> originalMessages;
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private org.telegram.ui.Components.EditTextCaption commentView;
    private FrameLayout bottomView;
    private ImageView sendButton;
    private MessageObject selectedMessage;
    private int selectedPosition = -1;
    private boolean forwardAsFile = false;

    private FrameLayout mediaPreviewContainer;
    private BackupImageView mediaPreviewImage;
    private TextView mediaPreviewText;
    private ImageView mediaPreviewClose;

    private final static int edit_item = 1;

    private ChatMessageCell.ChatMessageCellDelegate chatMessageCellDelegate;

    private PhotoViewer.PhotoViewerProvider photoViewerProvider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (mediaPreviewImage == null || selectedMessage == null || !selectedMessage.equals(messageObject)) return null;
            int[] coords = new int[2];
            mediaPreviewImage.getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1];
            object.parentView = mediaPreviewImage;
            object.imageReceiver = mediaPreviewImage.getImageReceiver();
            object.thumb = object.imageReceiver.getBitmapSafe();
            object.radius = mediaPreviewImage.getImageReceiver().getRoundRadius();
            return object;
        }
    };

    public SpecialForwardActivity(ArrayList<MessageObject> sourceMessages) {
        this.messages = new ArrayList<>();
        this.originalMessages = new ArrayList<>();
        
        // Deep copy messages to avoid editing the actual chat messages
        for (MessageObject msg : sourceMessages) {
            try {
                // Create copy for working list
                SerializedData data = new SerializedData(msg.messageOwner.getObjectSize());
                msg.messageOwner.serializeToStream(data);
                
                SerializedData readData = new SerializedData(data.toByteArray());
                TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, readData.readInt32(false), false);
                messageClone.dialog_id = msg.getDialogId();
                MessageObject newObj = new MessageObject(UserConfig.selectedAccount, messageClone, false, false);
                newObj.messageText = msg.messageText; 
                newObj.caption = msg.caption;
                this.messages.add(newObj);
                
                data.cleanup();
                readData.cleanup();
                
                // Create SEPARATE copy for restore point
                SerializedData data2 = new SerializedData(msg.messageOwner.getObjectSize());
                msg.messageOwner.serializeToStream(data2);
                
                SerializedData readData2 = new SerializedData(data2.toByteArray());
                TLRPC.Message messageClone2 = TLRPC.Message.TLdeserialize(readData2, readData2.readInt32(false), false);
                messageClone2.dialog_id = msg.getDialogId();
                MessageObject originalObj = new MessageObject(UserConfig.selectedAccount, messageClone2, false, false);
                originalObj.messageText = msg.messageText; 
                originalObj.caption = msg.caption;
                this.originalMessages.add(originalObj);
                
                data2.cleanup();
                readData2.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public View createView(Context context) {
        if (chatMessageCellDelegate == null) {
            chatMessageCellDelegate = new ChatMessageCell.ChatMessageCellDelegate() {
                 @Override public boolean canPerformActions() { return false; }
                 @Override public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {}
                 @Override public void didQuickShareStart(ChatMessageCell cell, float x, float y) {}
                 // @Override public boolean isChatAdminCell(int uid) { return false; } // Not in interface
            };
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.SpecialForwardTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == edit_item) {
                     showEditOptions();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        // Ensure icon exists or use standard
        menu.addItem(edit_item, R.drawable.ic_ab_other);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(64));
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < messages.size()) {
                 selectedMessage = messages.get(position);
                 selectedPosition = position;
                 if (commentView != null) {
                     CharSequence textToEdit = (selectedMessage.isPhoto() || selectedMessage.isVideo() || selectedMessage.isDocument()) ? selectedMessage.messageOwner.message : selectedMessage.messageText;
                     commentView.setText(org.telegram.ui.Components.ChatActivityEnterView.applyMessageEntities(selectedMessage.messageOwner.entities, textToEdit != null ? textToEdit : "", commentView.getPaint().getFontMetricsInt()));
                     if (commentView.getText().length() > 0) {
                        commentView.setSelection(commentView.getText().length());
                     }
                     
                     // Show media preview if exists
                     if (selectedMessage.isPhoto() || selectedMessage.isVideo() || selectedMessage.isDocument()) {
                         mediaPreviewContainer.setVisibility(View.VISIBLE);
                         mediaPreviewText.setText(selectedMessage.getFileName() != null ? selectedMessage.getFileName() : "Media");
                         if (selectedMessage.isPhoto() || selectedMessage.isVideo()) {
                             TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(selectedMessage.photoThumbs, AndroidUtilities.dp(50));
                             mediaPreviewImage.setImage(ImageLocation.getForObject(photoSize, selectedMessage.photoThumbsObject), "50_50", (Drawable) null, selectedMessage);
                         } else {
                             mediaPreviewImage.setImageResource(R.drawable.baseline_insert_drive_file_24);
                         }
                     } else {
                         mediaPreviewContainer.setVisibility(View.GONE);
                     }
                     
                     AndroidUtilities.showKeyboard(commentView);
                 }
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        // Bottom Edit/Send View (Floating Input Bar style to match image 2)
        bottomView = new FrameLayout(context);
        bottomView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(12));

        // Media Preview (Above input bar)
        mediaPreviewContainer = new FrameLayout(context);
        mediaPreviewContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_chat_messagePanelBackground)));
        mediaPreviewContainer.setVisibility(View.GONE);
        bottomView.addView(mediaPreviewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM, 0, 0, 0, 52));

        mediaPreviewImage = new BackupImageView(context);
        mediaPreviewImage.setRoundRadius(AndroidUtilities.dp(4));
        mediaPreviewImage.setOnClickListener(v -> {
            if (selectedMessage != null) {
                PhotoViewer.getInstance().setParentActivity(SpecialForwardActivity.this);
                PhotoViewer.getInstance().openPhoto(selectedMessage, 0, 0, 0, photoViewerProvider, true);
            }
        });
        mediaPreviewContainer.addView(mediaPreviewImage, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.LEFT, 5, 0, 0, 0));

        mediaPreviewText = new TextView(context);
        mediaPreviewText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        mediaPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        mediaPreviewText.setSingleLine(true);
        mediaPreviewText.setEllipsize(TextUtils.TruncateAt.END);
        mediaPreviewContainer.addView(mediaPreviewText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 50, 0, 40, 0));

        mediaPreviewClose = new ImageView(context);
        mediaPreviewClose.setImageResource(R.drawable.msg_panel_clear);
        mediaPreviewClose.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        mediaPreviewClose.setScaleType(ImageView.ScaleType.CENTER);
        mediaPreviewClose.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        mediaPreviewClose.setOnClickListener(v -> mediaPreviewContainer.setVisibility(View.GONE));
        mediaPreviewContainer.addView(mediaPreviewClose, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 4, 0));

        ImageView mediaReplace = new ImageView(context);
        mediaReplace.setImageResource(R.drawable.input_attach_solar);
        mediaReplace.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        mediaReplace.setScaleType(ImageView.ScaleType.CENTER);
        mediaReplace.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        mediaReplace.setOnClickListener(v -> openPhotoPicker());
        mediaPreviewContainer.addView(mediaReplace, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 44, 0));
        
        FrameLayout panelContainer = new FrameLayout(context);
        panelContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(24), Theme.getColor(Theme.key_chat_messagePanelBackground)));
        bottomView.addView(panelContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        // Emoji Button (Left) - Using input_smile_solar found in resources
        ImageView emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.input_smile_solar); 
        emojiButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        emojiButton.setScaleType(ImageView.ScaleType.CENTER);
        emojiButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        panelContainer.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT));
        
        commentView = new org.telegram.ui.Components.EditTextCaption(context, null) {
            @Override
             protected void extendActionMode(ActionMode actionMode, Menu menu) {
                if (menu.findItem(R.id.menu_bold) != null) return;
                menu.add(Menu.NONE, R.id.menu_bold, Menu.NONE, LocaleController.getString("Bold", R.string.Bold)).setIcon(R.drawable.baseline_format_bold_24);
                menu.add(Menu.NONE, R.id.menu_italic, Menu.NONE, LocaleController.getString("Italic", R.string.Italic)).setIcon(R.drawable.baseline_format_italic_24);
                menu.add(Menu.NONE, R.id.menu_mono, Menu.NONE, LocaleController.getString("Mono", R.string.Mono)).setIcon(R.drawable.baseline_code_24);
                menu.add(Menu.NONE, R.id.menu_strike, Menu.NONE, LocaleController.getString("Strike", R.string.Strike)).setIcon(R.drawable.baseline_strikethrough_s_24);
                menu.add(Menu.NONE, R.id.menu_underline, Menu.NONE, LocaleController.getString("Underline", R.string.Underline)).setIcon(R.drawable.baseline_format_underlined_24);
                menu.add(Menu.NONE, R.id.menu_spoiler, Menu.NONE, LocaleController.getString("Spoiler", R.string.Spoiler)).setIcon(R.drawable.msg_secret_solar);
                menu.add(Menu.NONE, R.id.menu_quote, Menu.NONE, LocaleController.getString("Quote", R.string.Quote)).setIcon(R.drawable.msg_share_quote_solar);
                menu.add(Menu.NONE, R.id.menu_link, Menu.NONE, LocaleController.getString("CreateLink", R.string.CreateLink)).setIcon(R.drawable.msg_link2_solar);
                menu.add(Menu.NONE, R.id.menu_regular, Menu.NONE, LocaleController.getString("Regular", R.string.Regular));
                if (menu.findItem(R.id.menu_change_font) == null) {
                    menu.add(Menu.NONE, R.id.menu_change_font, Menu.NONE, LocaleController.getString("ChangeFont", R.string.ChangeFont)).setIcon(R.drawable.msg_edit);
                }
            }
        };
        commentView.setWindowView(getParentActivity().getWindow().getDecorView());
        commentView.quoteColor = Theme.getColor(Theme.key_chat_inQuote);
        commentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        commentView.setHint(LocaleController.getString(R.string.SpecialForwardEditHint));
        commentView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        commentView.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        commentView.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        commentView.setBackgroundDrawable(null);
        commentView.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        commentView.setMaxLines(4);
        commentView.setGravity(Gravity.BOTTOM);
        commentView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        panelContainer.addView(commentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 48, 0, 88, 0));

        // Save Button (Right 1)
        ImageView saveButton = new ImageView(context);
        saveButton.setImageResource(R.drawable.baseline_check_24); 
        saveButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend), PorterDuff.Mode.MULTIPLY));
        saveButton.setScaleType(ImageView.ScaleType.CENTER);
        saveButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        saveButton.setOnClickListener(v -> {
             saveCurrentEdit();
        });
        panelContainer.addView(saveButton, LayoutHelper.createFrame(44, 48, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 44, 0));

        // Menu Button (Right 2)
        ImageView menuButton = new ImageView(context);
        menuButton.setImageResource(R.drawable.ic_ab_other); 
        menuButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        menuButton.setScaleType(ImageView.ScaleType.CENTER);
        menuButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        menuButton.setOnClickListener(v -> showEditOptions());
        panelContainer.addView(menuButton, LayoutHelper.createFrame(44, 48, Gravity.BOTTOM | Gravity.RIGHT));

        frameLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 84, 0));

        // Floating Action Button
        sendButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        sendButton.setBackgroundDrawable(drawable);
        sendButton.setImageResource(R.drawable.baseline_send_24);
        sendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        sendButton.setScaleType(ImageView.ScaleType.CENTER);
        sendButton.setOnClickListener(v -> {
             // Auto-save any pending edit before sending
             if (selectedMessage != null && commentView != null && commentView.getText().length() > 0) {
                 saveCurrentEdit();
             }
             forwardMessages();
        });
        frameLayout.addView(sendButton, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 16, 16)); 

        return fragmentView;
    }

    private MessageObject recreateMessageObject(MessageObject source) {
        if (source == null) return null;
        try {
            SerializedData data = new SerializedData(source.messageOwner.getObjectSize());
            source.messageOwner.serializeToStream(data);
            
            byte[] byteArray = data.toByteArray();
            data.cleanup();
            
            SerializedData readData = new SerializedData(byteArray);
            int constructor = readData.readInt32(false);
            TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, constructor, false);
            readData.cleanup();
            
            if (messageClone == null) return source;

            messageClone.dialog_id = source.getDialogId();
            messageClone.attachPath = source.messageOwner.attachPath;
            messageClone.params = source.messageOwner.params;
            
            MessageObject newObj = new MessageObject(UserConfig.selectedAccount, messageClone, false, false);
            newObj.messageText = source.messageText; 
            newObj.caption = source.caption;
            newObj.videoEditedInfo = source.videoEditedInfo;
            newObj.type = source.type;
            return newObj;
        } catch (Exception e) {
            FileLog.e(e);
            return source;
        }
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

    private void saveCurrentEdit() {
        if (selectedMessage != null && commentView != null) {
            updateMessageText(selectedMessage, commentView.getText());
            
            MessageObject newObj = recreateMessageObject(selectedMessage);
            if (newObj != null) {
                messages.set(selectedPosition, newObj);
                selectedMessage = newObj; 
            }

            if (selectedPosition != -1) {
                listAdapter.notifyItemChanged(selectedPosition);
            }
            commentView.setText("");
            mediaPreviewContainer.setVisibility(View.GONE);
            selectedMessage = null;
            selectedPosition = -1;
            AndroidUtilities.hideKeyboard(commentView);
        }
    }

    private void showEditOptions() {
        ActionBarMenuItem editItem = actionBar.createMenu().getItem(edit_item);
        if (editItem == null) return;
        
        ItemOptions itemOptions = ItemOptions.makeOptions(this, editItem);
        itemOptions.add(R.drawable.msg_repeat, LocaleController.getString(R.string.SpecialForwardResetAll), this::resetAll);
        itemOptions.add(R.drawable.msg_edit, LocaleController.getString(R.string.SpecialForwardReplaceAllTexts), this::replaceAllTexts);
        itemOptions.add(R.drawable.baseline_link_24, LocaleController.getString(R.string.SpecialForwardReplaceAllLinks), this::replaceAllLinks);
        itemOptions.add(R.drawable.msg_delete, LocaleController.getString(R.string.SpecialForwardDeleteAllLinks), this::deleteAllLinks);
        itemOptions.add(R.drawable.msg_delete, LocaleController.getString(R.string.SpecialForwardDeleteAllCaptions), this::deleteAllCaptions);
        itemOptions.add(forwardAsFile ? R.drawable.baseline_check_24 : R.drawable.ic_ab_other, LocaleController.getString(R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF"), () -> {
            forwardAsFile = !forwardAsFile;
            Toast.makeText(getParentActivity(), LocaleController.getString(R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        });
        itemOptions.show();
    }

    private void resetAll() {
        if (originalMessages == null || originalMessages.isEmpty()) return;
        
        messages.clear();
        for (MessageObject msg : originalMessages) {
             try {
                SerializedData data = new SerializedData(msg.messageOwner.getObjectSize());
                msg.messageOwner.serializeToStream(data);
                
                SerializedData readData = new SerializedData(data.toByteArray());
                TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, readData.readInt32(false), false);
                messageClone.dialog_id = msg.getDialogId();
                MessageObject newObj = new MessageObject(UserConfig.selectedAccount, messageClone, false, false);
                newObj.messageText = msg.messageText; 
                newObj.caption = msg.caption;
                this.messages.add(newObj);
                
                data.cleanup();
                readData.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        listAdapter.notifyDataSetChanged();
        Toast.makeText(getParentActivity(), LocaleController.getString(R.string.SpecialForwardResetSuccess), Toast.LENGTH_SHORT).show();
    }

    private void replaceAllTexts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.SpecialForwardReplaceAllTexts));
        
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
                messages.set(i, recreateMessageObject(msg));
            }
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.SpecialForwardReplaceAllLinks));
        
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
                messages.set(i, recreateMessageObject(msg));
            }
            listAdapter.notifyDataSetChanged();
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
        builder.setTitle(LocaleController.getString(R.string.SpecialForwardDeleteAllLinks));
        
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
                messages.set(i, recreateMessageObject(msg));
            }
            listAdapter.notifyDataSetChanged();
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
            messages.set(i, recreateMessageObject(msg));
        }
        listAdapter.notifyDataSetChanged();
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

                         // 1. Handle Replaced Media (Local Path present)
                         if (!TextUtils.isEmpty(attachPath) && !attachPath.startsWith("http")) {
                             if (msg.isVideo() || msg.isRoundVideo()) {
                                 SendMessagesHelper.prepareSendingVideo(getAccountInstance(), attachPath, msg.videoEditedInfo, null, null, peer, null, null, null, null, entities, 0, null, notify, scheduleDate, scheduleRepeatPeriod, false, false, caption, null, 0, 0, 0, topicId, null, false);
                             } else if (msg.isPhoto()) {
                                 SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), attachPath, null, null, peer, null, null, null, null, entities, null, null, 0, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, false, caption, null, 0, 0, 0, topicId, null);
                             } else {
                                 // Document or other file
                                 ArrayList<String> paths = new ArrayList<>();
                                 paths.add(attachPath);
                                 SendMessagesHelper.prepareSendingDocuments(getAccountInstance(), paths, paths, null, caption, entities, null, peer, null, null, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, null, 0, 0, false, 0, topicId, null, null, null, null, false);
                             }
                             continue;
                         }

                         // 2. Handle Forwarding/Editing Original Media
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
                                 // Fallback: Resend as is
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;
        private ChatMessageCell.ChatMessageCellDelegate delegate;

        public ListAdapter(Context context) { 
            mContext = context; 
            delegate = new ChatMessageCell.ChatMessageCellDelegate() {
                 @Override public boolean canPerformActions() { return false; }
                 @Override public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {}
                 @Override public void didQuickShareStart(ChatMessageCell cell, float x, float y) {}
            };
        }
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override
        public int getItemCount() { return messages.size(); }
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(mContext, UserConfig.selectedAccount);
            cell.setDelegate(delegate);
            return new RecyclerListView.Holder(cell);
        }
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = messages.get(position);
            // Ensure message has a minimal valid setup for display
            cell.setMessageObject(message, null, false, false, false, false);
            cell.setBackgroundColor(selectedPosition == position ? Theme.getColor(Theme.key_chat_messagePanelBackground) : Color.TRANSPARENT);
        }
        @Override
        public int getItemViewType(int position) { return 0; }
    }
    private void openPhotoPicker() {
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(PhotoAlbumPickerActivity.SELECT_TYPE_ALL, true, true, null);
        fragment.setMaxSelectedPhotos(1, false);
        fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                if (photos != null && !photos.isEmpty()) {
                    onMediaReplaced(photos.get(0));
                }
            }

            @Override
            public void startPhotoSelectActivity() {
            }
        });
        presentFragment(fragment);
    }

    private void onMediaReplaced(SendMessagesHelper.SendingMediaInfo info) {
        if (selectedMessage == null) return;
        
        mediaPreviewImage.setImage(ImageLocation.getForPath(info.path), "50_50", (Drawable) null, selectedMessage);
        mediaPreviewText.setText(new File(info.path).getName());
        
        selectedMessage.messageOwner.attachPath = info.path;
        selectedMessage.videoEditedInfo = info.videoEditedInfo;
        if (info.isVideo) {
            selectedMessage.type = MessageObject.TYPE_VIDEO;
            if (selectedMessage.messageOwner.media == null || !(selectedMessage.messageOwner.media instanceof TLRPC.TL_messageMediaDocument)) {
                selectedMessage.messageOwner.media = new TLRPC.TL_messageMediaDocument();
                selectedMessage.messageOwner.media.document = new TLRPC.TL_document();
            }
        } else {
            selectedMessage.type = MessageObject.TYPE_PHOTO;
            if (selectedMessage.messageOwner.media == null || !(selectedMessage.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto)) {
                selectedMessage.messageOwner.media = new TLRPC.TL_messageMediaPhoto();
                selectedMessage.messageOwner.media.photo = new TLRPC.TL_photo();
            }
        }
        
        if (selectedPosition != -1) {
            listAdapter.notifyItemChanged(selectedPosition);
        }
        
        Toast.makeText(getParentActivity(), "Media replaced!", Toast.LENGTH_SHORT).show();
    }
}
