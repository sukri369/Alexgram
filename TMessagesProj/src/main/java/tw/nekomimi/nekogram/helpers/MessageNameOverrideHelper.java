package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.NekoConfig;

public class MessageNameOverrideHelper {

    public static boolean isGroupChat(int currentAccount, long dialogId) {
        if (dialogId >= 0) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        if (chat == null) {
            return false;
        }
        return !org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(chat);
    }

    public static String getCustomName(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        if (!NekoConfig.enableChangeNameInGroups.Bool()) {
            return null;
        }
        int account = messageObject.currentAccount;
        long dialogId = messageObject.getDialogId();
        if (!isGroupChat(account, dialogId)) {
            return null;
        }

        SharedPreferences prefs = org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("name_overrides", Context.MODE_PRIVATE);

        // 1. Message-specific override first
        String msgKey = account + "_msg_" + dialogId + "_" + messageObject.getId();
        if (prefs.contains(msgKey)) {
            return prefs.getString(msgKey, null);
        }

        // 2. Sender-specific override second
        long senderId = messageObject.getFromChatId();
        if (senderId != 0) {
            String senderKey = account + "_sender_" + dialogId + "_" + senderId;
            if (prefs.contains(senderKey)) {
                return prefs.getString(senderKey, null);
            }
        }

        return null;
    }

    public static String getOriginalName(MessageObject messageObject) {
        if (messageObject == null) {
            return "";
        }
        int account = messageObject.currentAccount;
        long senderId = messageObject.getFromChatId();
        if (senderId > 0) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(senderId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (senderId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-senderId);
            if (chat != null) {
                return chat.title;
            }
        }
        return "";
    }

    public static void showChangeNameDialog(ChatActivity activity, MessageObject messageObject) {
        if (activity == null || messageObject == null) {
            return;
        }
        Context context = activity.getContext();
        if (context == null) {
            return;
        }

        int account = messageObject.currentAccount;
        long dialogId = messageObject.getDialogId();
        long senderId = messageObject.getFromChatId();

        String msgKey = account + "_msg_" + dialogId + "_" + messageObject.getId();
        String senderKey = account + "_sender_" + dialogId + "_" + senderId;

        SharedPreferences prefs = org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("name_overrides", Context.MODE_PRIVATE);
        String msgVal = prefs.getString(msgKey, null);
        String senderVal = prefs.getString(senderKey, null);

        int initialOption = 0;
        String initialText = "";

        if (msgVal != null) {
            initialOption = 0;
            initialText = msgVal;
        } else if (senderVal != null) {
            initialOption = 1;
            initialText = senderVal;
        }

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), AndroidUtilities.dp(10));

        String originalName = getOriginalName(messageObject);
        TextView originalNameView = new TextView(context);
        originalNameView.setText(LocaleController.formatString(R.string.ChangeSenderNameOriginal, originalName));
        originalNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        originalNameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, activity.getResourceProvider()));
        contentLayout.addView(originalNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView labelView = new TextView(context);
        labelView.setText(getString(R.string.ChangeSenderNameNew));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        labelView.setTypeface(AndroidUtilities.bold());
        labelView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, activity.getResourceProvider()));
        contentLayout.addView(labelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        FrameLayout container = new FrameLayout(context);
        boolean isDark = Theme.isCurrentThemeDark();
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        inputBg.setCornerRadius(AndroidUtilities.dp(8));
        inputBg.setColor(isDark ? 0x15FFFFFF : 0x0F000000);
        inputBg.setStroke(AndroidUtilities.dp(1), isDark ? 0x20FFFFFF : 0x1A000000);
        container.setBackground(inputBg);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, activity.getResourceProvider()));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray, activity.getResourceProvider()));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, activity.getResourceProvider()));
        editText.setFocusable(true);
        editText.setBackground(null);
        editText.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        editText.setHint(getString(R.string.ChangeSenderNameHint));
        editText.setText(initialText);
        editText.setSelection(editText.getText().length());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        final RadioColorCell[] optionCells = new RadioColorCell[3];
        final int[] selectedOpt = new int[]{initialOption};

        Runnable updateInputState = () -> {
            boolean enabled = selectedOpt[0] != 2;
            editText.setEnabled(enabled);
            editText.setAlpha(enabled ? 1.0f : 0.5f);
        };

        for (int i = 0; i < 3; i++) {
            RadioColorCell optionCell = new RadioColorCell(context, activity.getResourceProvider());
            optionCell.setPadding(0, 0, 0, 0);
            final int optIndex = i;
            String optionText = "";
            if (i == 0) {
                optionText = getString(R.string.ChangeSenderNameOptionMessage);
            } else if (i == 1) {
                optionText = getString(R.string.ChangeSenderNameOptionAll);
            } else if (i == 2) {
                optionText = getString(R.string.ChangeSenderNameOptionRestore);
            }

            optionCell.setTextAndValue(optionText, selectedOpt[0] == i);
            optionCells[i] = optionCell;
            contentLayout.addView(optionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            optionCell.setOnClickListener(v -> {
                selectedOpt[0] = optIndex;
                for (int j = 0; j < 3; j++) {
                    optionCells[j].setChecked(selectedOpt[0] == j, true);
                }
                updateInputState.run();
            });
        }
        updateInputState.run();

        AlertDialog dialog = new AlertDialog.Builder(context, activity.getResourceProvider())
            .setTitle(getString(R.string.ChangeSenderNameTitle))
            .setView(contentLayout)
            .setNegativeButton(getString(R.string.Cancel), null)
            .setPositiveButton(getString(R.string.ChangeSenderNameApply), null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            int opt = selectedOpt[0];
            String text = editText.getText().toString().trim();
            if (opt != 2 && TextUtils.isEmpty(text)) {
                tw.nekomimi.nekogram.utils.AndroidUtil.showInputError(editText);
                return;
            }

            SharedPreferences.Editor editor = prefs.edit();
            if (opt == 2) {
                editor.remove(msgKey);
                editor.remove(senderKey);
            } else if (opt == 0) {
                editor.putString(msgKey, text);
            } else if (opt == 1) {
                editor.putString(senderKey, text);
                editor.remove(msgKey);
            }
            editor.apply();

            activity.updateVisibleRows();
            dialog.dismiss();
        }));

        activity.showDialog(dialog);
    }
}
