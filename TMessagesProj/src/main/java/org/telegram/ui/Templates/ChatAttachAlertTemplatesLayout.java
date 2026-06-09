package org.telegram.ui.Templates;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.AlertsCreator;

import java.util.ArrayList;

public class ChatAttachAlertTemplatesLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {
    private final TemplatesManager manager;
    private final ArrayList<TemplateSettings> templates = new ArrayList<>();
    private final RecyclerListView listView;
    private final FillLastLinearLayoutManager layoutManager;
    private final ListAdapter listAdapter;
    private final EmptyTextProgressView emptyView;
    private TemplateSettings selectedTemplate;

    public ChatAttachAlertTemplatesLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        manager = TemplatesManager.getInstance(alert.currentAccount);
        occupyStatusBar = true;
        occupyNavigationBar = true;

        emptyView = new EmptyTextProgressView(context, null, resourcesProvider);
        emptyView.showTextView();
        emptyView.setText(LocaleController.getString(R.string.chat_templates_list_header));
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 52, 0, 0));

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= parentAlert.scrollOffsetY[0] + dp(30) + (!parentAlert.inBubbleMode ? AndroidUtilities.statusBarHeight : 0);
            }
        };
        listView.setClipToPadding(false);
        listView.setSections();
        listView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false, dp(9), listView) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller scroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        return super.calculateDyToMakeVisible(view, snapPreference) - (listView.getPaddingTop() - AndroidUtilities.statusBarHeight - dp(8));
                    }
                };
                scroller.setTargetPosition(position);
                startSmoothScroll(scroller);
            }
        });
        layoutManager.setBind(false);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            TemplateSettings template = getTemplateAt(position);
            if (template != null) {
                useTemplate(template, false);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            TemplateSettings template = getTemplateAt(position);
            if (template != null) {
                showTemplateOptions(template);
                return true;
            }
            return false;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertTemplatesLayout.this, true, dy);
                updateEmptyView();
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        reload();
        NotificationCenter.getInstance(alert.currentAccount).addObserver(this, NotificationCenter.templatesSettingsUpdated);
    }

    @Override
    public int getSelectedItemsCount() {
        return selectedTemplate == null ? 0 : 1;
    }

    @Override
    public boolean sendSelectedItems(boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia) {
        if (selectedTemplate == null) {
            return false;
        }
        TemplateSettings template = selectedTemplate;
        selectedTemplate = null;
        int messagesCount = Math.max(1, template.hasMessages() ? template.getMessageCount() : 1);
        parentAlert.updateCountButton(messagesCount);
        return AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), messagesCount, payStars -> sendTemplate(template, notify, scheduleDate, scheduleRepeatPeriod, effectId, payStars));
    }

    @Override
    public int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.statusBarHeight - dp(8);
        return (top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0) + dp(12);
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + dp(4);
    }

    @Override
    public int getListTopPadding() {
        return listView.getPaddingTop();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > dp(20)) {
            padding = dp(8);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = availableHeight / 5 * 2;
            }
            parentAlert.setAllowNestedScroll(true);
        }
        padding += AndroidUtilities.statusBarHeight;
        listView.setPaddingWithoutRequestLayout(0, padding, 0, listPaddingBottom);
    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        reload();
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.templatesSettingsUpdated) {
            reload();
        }
    }

    @Override
    public void onDestroy() {
        NotificationCenter.getInstance(parentAlert.currentAccount).removeObserver(this, NotificationCenter.templatesSettingsUpdated);
    }

    private TemplateSettings getTemplateAt(int position) {
        int row = listAdapter.getPositionInSectionForPosition(position);
        int section = listAdapter.getSectionForPosition(position);
        if (section != 1 || row < 0 || row >= templates.size()) {
            return null;
        }
        return templates.get(row);
    }

    private void useTemplate(TemplateSettings template, boolean send) {
        selectedTemplate = template;
        if (template.hasMessages()) {
            parentAlert.updateCountButton(Math.max(1, template.getMessageCount()));
            parentAlert.pressSendButton();
            return;
        }
        parentAlert.getCommentView().setText(template.text);
        parentAlert.getCommentView().setSelection(template.text.length(), template.text.length());
        if (send) {
            parentAlert.updateCountButton(1);
            parentAlert.pressSendButton();
        } else {
            manager.incrementUsage(template.id);
            parentAlert.dismiss();
        }
    }

    private void sendTemplate(TemplateSettings template, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, long payStars) {
        if (template == null || (!template.hasMessages() && TextUtils.isEmpty(template.text))) {
            return;
        }
        BaseFragment fragment = parentAlert.baseFragment;
        MessageObject replyTo = null;
        MessageObject replyToTop = null;
        ChatActivity.ReplyQuote quote = null;
        long monoForumPeer = 0;
        if (fragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) fragment;
            replyTo = chatActivity.getReplyMessage();
            replyToTop = chatActivity.getThreadMessage();
            quote = chatActivity.getReplyQuote();
            monoForumPeer = chatActivity.getSendMonoForumPeerId();
        }
        if (template.hasMessages()) {
            ArrayList<MessageObject> messages = template.toMessageObjects(parentAlert.currentAccount);
            if (messages.isEmpty()) {
                return;
            }
            int result = SendMessagesHelper.getInstance(parentAlert.currentAccount).sendMessage(messages, parentAlert.getDialogId(), true, false, notify, scheduleDate, scheduleRepeatPeriod, replyToTop, -1, payStars, monoForumPeer, fragment instanceof ChatActivity ? ((ChatActivity) fragment).getSendMessageSuggestionParams() : null);
            AlertsCreator.showSendMediaAlert(result, parentAlert.baseFragment, resourcesProvider);
            manager.incrementUsage(template.id);
            parentAlert.dismiss();
            return;
        }
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(template.text, parentAlert.getDialogId(), replyTo, replyToTop, null, true, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, false);
        params.replyQuote = quote;
        params.effect_id = effectId;
        params.payStars = payStars;
        params.monoForumPeer = monoForumPeer;
        if (fragment instanceof ChatActivity) {
            params.suggestionParams = ((ChatActivity) fragment).getSendMessageSuggestionParams();
        }
        SendMessagesHelper.getInstance(parentAlert.currentAccount).sendMessage(params);
        manager.incrementUsage(template.id);
        parentAlert.dismiss();
    }

    private void showTemplateOptions(TemplateSettings template) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(template.name);
        CharSequence[] items = new CharSequence[] {
                LocaleController.getString(R.string.Edit),
                LocaleController.getString(R.string.Delete)
        };
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                showTemplateEditor(template);
            } else if (which == 1) {
                manager.deleteTemplate(template.id);
            }
        });
        builder.show();
    }

    private void reload() {
        templates.clear();
        templates.addAll(manager.getTemplates());
        listAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        emptyView.setVisibility(templates.isEmpty() ? VISIBLE : GONE);
        View child = listView.getChildAt(0);
        if (child != null && emptyView.getVisibility() == VISIBLE) {
            emptyView.setTranslationY((emptyView.getMeasuredHeight() - getMeasuredHeight() + child.getTop()) / 2f);
        }
    }

    private void showTemplateEditor(TemplateSettings currentTemplate) {
        Context context = getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(currentTemplate == null ? R.string.create_chat_template : R.string.chat_template));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(4), dp(24), dp(8));

        EditTextBoldCursor nameField = new EditTextBoldCursor(context);
        nameField.setTextSize(18);
        nameField.setSingleLine(true);
        nameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameField.setHintText(LocaleController.getString(R.string.chat_template_name_hint));
        nameField.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        nameField.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        nameField.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        nameField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        container.addView(nameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        EditTextBoldCursor textField = new EditTextBoldCursor(context);
        textField.setTextSize(18);
        textField.setMinLines(3);
        textField.setMaxLines(6);
        textField.setGravity(Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        textField.setHintText(LocaleController.getString(R.string.chat_template_text_hint));
        textField.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textField.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        textField.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        textField.setPadding(0, dp(8), 0, dp(8));
        container.addView(textField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView infoView = new TextView(context);
        infoView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        infoView.setTextSize(14);
        infoView.setText(LocaleController.getString(R.string.chat_template_name_info));
        container.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        if (currentTemplate != null) {
            nameField.setText(currentTemplate.name);
            textField.setText(currentTemplate.text);
        } else {
            Editable existingText = parentAlert.getCommentView() == null ? null : parentAlert.getCommentView().getText();
            if (existingText != null && existingText.length() > 0) {
                textField.setText(existingText);
            }
        }

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialogInterface, which) -> {
            String name = nameField.getText().toString().trim();
            String text = textField.getText().toString();
            boolean needsText = currentTemplate == null || !currentTemplate.hasMessages();
            if (TextUtils.isEmpty(name) || (needsText && TextUtils.isEmpty(text.trim()))) {
                AndroidUtilities.shakeView(TextUtils.isEmpty(name) ? nameField : textField);
                return;
            }
            if (currentTemplate == null) {
                manager.addTemplate(name, text);
            } else {
                manager.updateTemplate(currentTemplate, name, text);
            }
            dialogInterface.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            nameField.requestFocus();
            AndroidUtilities.showKeyboard(nameField);
        });
        dialog.show();
    }

    private class ListAdapter extends RecyclerListView.SectionsAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getSectionCount() {
            return 3;
        }

        @Override
        public int getCountForSection(int section) {
            if (section == 0 || section == 2) {
                return 1;
            }
            return templates.size();
        }

        @Override
        public Object getItem(int section, int position) {
            if (section == 1 && position >= 0 && position < templates.size()) {
                return templates.get(position);
            }
            return null;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            return section == 1 && row >= 0 && row < templates.size();
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new HeaderView(context);
            } else if (viewType == 1) {
                view = new TemplateCell(context);
            } else {
                view = new View(context);
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 0) {
                ((HeaderView) holder.itemView).bind();
            } else if (holder.getItemViewType() == 1) {
                TemplateSettings template = templates.get(position);
                ((TemplateCell) holder.itemView).bind(template, position != templates.size() - 1, () -> useTemplate(template, true));
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section == 0) {
                return 0;
            }
            if (section == 1) {
                return 1;
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = 0;
            position[1] = 0;
        }
    }

    private class HeaderView extends LinearLayout {
        private final HeaderCell headerCell;
        private final TextView createButton;
        private final TextView sortButton;

        HeaderView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(0, 0, 0, dp(6));

            FrameLayout row = new FrameLayout(context);
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54));

            headerCell = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, false, resourcesProvider);
            row.addView(headerCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(actions, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 42, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            sortButton = new TextView(context);
            sortButton.setGravity(Gravity.CENTER);
            sortButton.setTextSize(14);
            sortButton.setTypeface(AndroidUtilities.bold());
            sortButton.setPadding(dp(12), 0, dp(12), 0);
            sortButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
            sortButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            actions.addView(sortButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));

            createButton = new TextView(context);
            createButton.setGravity(Gravity.CENTER);
            createButton.setTextSize(14);
            createButton.setTypeface(AndroidUtilities.bold());
            createButton.setPadding(dp(12), 0, dp(12), 0);
            createButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
            createButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            createButton.setText(LocaleController.getString(R.string.Create).toUpperCase());
            actions.addView(createButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 4, 0, 0, 0));
        }

        void bind() {
            headerCell.setText(LocaleController.getString(R.string.chat_templates));
            sortButton.setText(sortTitle(manager.getSortingType()));
            sortButton.setOnClickListener(v -> showSortDialog());
            createButton.setOnClickListener(v -> showTemplateEditor(null));
        }
    }

    private void showSortDialog() {
        TemplatesManager.SortingType[] values = TemplatesManager.SortingType.values();
        CharSequence[] items = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = sortTitle(values[i]);
        }
        new AlertDialog.Builder(getContext(), resourcesProvider)
                .setTitle(LocaleController.getString(R.string.SortBy))
                .setItems(items, (dialog, which) -> manager.setSortingType(values[which]))
                .show();
    }

    private String sortTitle(TemplatesManager.SortingType sortingType) {
        if (sortingType == TemplatesManager.SortingType.NAME) {
            return LocaleController.getString(R.string.dialogs_albums_sort_alphabetically);
        } else if (sortingType == TemplatesManager.SortingType.USAGE) {
            return LocaleController.getString(R.string.sort_by_usage);
        }
        return LocaleController.getString(R.string.dialogs_albums_sort_date);
    }
}
