package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.AIAssistanceSettingsActivity;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.LaunchActivity;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.HiddenChatsController;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity;
import tw.nekomimi.nekogram.utils.AlertUtil;
import xyz.nextalone.nagram.NaConfig;

public class NekoSettingsActivity extends BaseNekoSettingsActivity {

    private boolean isDark;
    private int cardBg;
    private int cardBorder;
    private int dividerColor;

    private int headerRow;
    private int quickSettingsHeaderRow;
    private int hideContactsRow;
    private int ghostModeRow;
    private int musicGraphRow;
    private int saveDeletedRow;
    private int privacyHeaderRow;
    private int hiddenChatsRow;
    private int coreHeaderRow;
    private int coreSettingsRow;
    private int advancedHeaderRow;
    private int advancedSettingsRow;
    private int actionsRow;
    private int footerRow;

    @Override
    protected void updateRows() {
        rowCount = 0;
        headerRow = addRow();
        
        quickSettingsHeaderRow = addRow();
        hideContactsRow = addRow();
        ghostModeRow = addRow();
        musicGraphRow = addRow();
        saveDeletedRow = addRow();
        
        privacyHeaderRow = addRow();
        hiddenChatsRow = addRow();
        
        coreHeaderRow = addRow();
        coreSettingsRow = addRow();
        
        advancedHeaderRow = addRow();
        advancedSettingsRow = addRow();
        
        actionsRow = addRow();
        footerRow = addRow();
    }

    @Override
    public View createView(Context context) {
        setupColors();
        
        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Color.TRANSPARENT);

        // Add animated background
        AlexgramSettingsHeaderView backgroundView = new AlexgramSettingsHeaderView(context);
        frameLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("A-Settings");
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        
        int color = isDark ? Color.WHITE : 0xFF1A1A2E;
        actionBar.setItemsColor(color, false);
        actionBar.setTitleColor(color);
        
        final int search_id = 1;
        final int cloud_id = 2;
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        org.telegram.ui.ActionBar.ActionBarMenuItem searchItem = menu.addItem(search_id, R.drawable.ic_ab_search_solar);
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                // Handle search expansion
            }
            @Override
            public void onSearchCollapse() {
                // Handle search collapse
            }
            @Override
            public void onTextChanged(android.widget.EditText editText) {
                // Handle search text change
            }
        });
        searchItem.setSearchFieldHint("Search settings...");
        
        org.telegram.ui.ActionBar.ActionBarMenuItem cloudItem = menu.addItem(cloud_id, R.drawable.cloud_sync);
        if (searchItem != null) searchItem.setIconColor(color);
        if (cloudItem != null) cloudItem.setIconColor(color);
        
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == cloud_id) {
                    CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this);
                }
            }
        });

        actionBar.getTitleTextView().setAlpha(0.0f);
        
        listView = new org.telegram.ui.Components.BlurredRecyclerView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(40) + (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight), 0, AndroidUtilities.dp(40));
        listView.setClipToPadding(false);
        listView.setAdapter(listAdapter = createAdapter(context));
        listView.setOnItemClickListener(this::onItemClick);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int scrollY = recyclerView.computeVerticalScrollOffset();
                float alpha = Math.max(0, Math.min(1.0f, (scrollY - AndroidUtilities.dp(100)) / (float) AndroidUtilities.dp(60)));
                actionBar.getTitleTextView().setAlpha(alpha);
                actionBar.setBackgroundColor(ColorUtils.setAlphaComponent(isDark ? 0xFF1E2732 : Color.WHITE, (int) (alpha * 255)));
            }
        });
        
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private void setupColors() {
        isDark = Theme.getActiveTheme().isDark();
        if (isDark) {
            cardBg = 0x221E2732;
            cardBorder = 0x15FFFFFF;
            dividerColor = 0x10FFFFFF;
        } else {
            cardBg = 0x40FFFFFF;
            cardBorder = 0x20000000;
            dividerColor = 0x15000000;
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return "A-Settings";
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: view = new BrandingHeaderView(mContext); break;
                case 1: view = new SectionHeaderCell(mContext); break;
                case 2: view = new CardSwitchCell(mContext); break;
                case 3: view = new CardItemCell(mContext); break;
                case 4: view = new ActionsCell(mContext); break;
                case 5: view = new FooterCell(mContext); break;
                default: view = new View(mContext); break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            if (viewType == 0) {
                ((BrandingHeaderView) holder.itemView).update();
            } else if (viewType == 1) {
                SectionHeaderCell cell = (SectionHeaderCell) holder.itemView;
                if (position == quickSettingsHeaderRow) cell.setText("QUICK SETTINGS");
                else if (position == privacyHeaderRow) cell.setText("PRIVACY");
                else if (position == coreHeaderRow) cell.setText("CORE SETTINGS");
                else if (position == advancedHeaderRow) cell.setText("ADVANCED");
            } else if (viewType == 2) {
                CardSwitchCell cell = (CardSwitchCell) holder.itemView;
                if (position == hideContactsRow) {
                    cell.setData("Hide Contacts", "Hide contacts tab", R.drawable.msg_contacts, NaConfig.INSTANCE.getHideContacts().Bool(), isChecked -> NaConfig.INSTANCE.getHideContacts().setConfigBool(isChecked), true, false);
                } else if (position == ghostModeRow) {
                    cell.setData("Ghost Mode", "Read silently", R.drawable.msg_secret, NekoConfig.isGhostModeActive(), isChecked -> NekoConfig.setGhostMode(isChecked), false, false);
                } else if (position == musicGraphRow) {
                    cell.setData("Music Graph", "Visualizer in player", R.drawable.baseline_music_note_24, NaConfig.INSTANCE.getMusicGraph().Bool(), isChecked -> NaConfig.INSTANCE.getMusicGraph().setConfigBool(isChecked), false, false);
                } else if (position == saveDeletedRow) {
                    cell.setData("Save Deleted", "Save deleted messages", R.drawable.msg_delete_solar, NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool(), isChecked -> NaConfig.INSTANCE.getEnableSaveDeletedMessages().setConfigBool(isChecked), false, true);
                }
            } else if (viewType == 3) {
                CardItemCell cell = (CardItemCell) holder.itemView;
                if (position == hiddenChatsRow) {
                    cell.setData("Hidden Chats", "Secure vault for private chats", R.drawable.msg_folders_private_solar, 0xFFE91E63, v -> {
                        if (HiddenChatsController.getInstance().hasPasscode()) {
                            presentFragment(new HiddenChatsPasscodeActivity(HiddenChatsPasscodeActivity.MODE_UNLOCK_SETTINGS));
                        } else {
                            presentFragment(new HiddenChatsPasscodeActivity(HiddenChatsPasscodeActivity.MODE_SETUP_PASSCODE));
                        }
                    }, true, true);
                } else if (position == coreSettingsRow) {
                    cell.setMultiData(new CoreItem[]{
                            new CoreItem("General", "Appearance, Language, Behavior", R.drawable.msg_settings, 0xFF2196F3, v -> presentFragment(new NekoGeneralSettingsActivity())),
                            new CoreItem("Translator", "Messages, Languages, Engine", R.drawable.ic_translate, 0xFF9C27B0, v -> presentFragment(new NekoTranslatorSettingsActivity())),
                            new CoreItem("AI Assistance", "Alexgram assistant behavior & animations", R.drawable.settings_chat, 0xFF8E44AD, v -> presentFragment(new AIAssistanceSettingsActivity())),
                            new CoreItem("Chats", "UI, Privacy, Media", R.drawable.msg_discussion, 0xFF4CAF50, v -> presentFragment(new NekoChatSettingsActivity())),
                            new CoreItem("Passcode", "Security & Fingerprint", R.drawable.msg_permissions, 0xFFF44336, v -> presentFragment(new NekoPasscodeSettingsActivity()))
                    });
                } else if (position == advancedSettingsRow) {
                    cell.setMultiData(new CoreItem[]{
                            new CoreItem("Cloud Settings", "Sync, backup, and restore", R.drawable.cloud_sync, 0xFF00BCD4, v -> CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this)),
                            new CoreItem("Experimental", "Beta Tools & Features", R.drawable.msg_fave, 0xFF673AB7, v -> presentFragment(new NekoExperimentalSettingsActivity()))
                    });
                }
            } else if (viewType == 4) {
                ((ActionsCell) holder.itemView).update();
            } else if (viewType == 5) {
                ((FooterCell) holder.itemView).update();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            if (position == quickSettingsHeaderRow || position == privacyHeaderRow || position == coreHeaderRow || position == advancedHeaderRow) return 1;
            if (position == hideContactsRow || position == ghostModeRow || position == musicGraphRow || position == saveDeletedRow) return 2;
            if (position == hiddenChatsRow || position == coreSettingsRow || position == advancedSettingsRow) return 3;
            if (position == actionsRow) return 4;
            if (position == footerRow) return 5;
            return -1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            return viewType == 2 || viewType == 3 || viewType == 4;
        }
    }

    private class BrandingHeaderView extends FrameLayout {
        private final ImageView logoView;
        private final TextView nameView;
        private final TextView versionView;

        public BrandingHeaderView(Context context) {
            super(context);
            setPadding(0, dp(24), 0, dp(24));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            FrameLayout logoContainer = new FrameLayout(context);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                logoContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                logoContainer.setClipToOutline(true);
            }
            GradientDrawable logoBg = new GradientDrawable();
            logoBg.setShape(GradientDrawable.OVAL);
            logoBg.setColor(isDark ? 0x1AFFFFFF : 0x1A000000);
            logoContainer.setBackground(logoBg);
            addView(logoContainer, LayoutHelper.createFrame(80, 80, Gravity.CENTER_HORIZONTAL));

            logoView = new ImageView(context);
            logoView.setImageResource(R.drawable.ic_launcher_alexgram_white);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoContainer.addView(logoView, LayoutHelper.createFrame(56, 56, Gravity.CENTER));

            nameView = new TextView(context);
            nameView.setText("Alexgram");
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            nameView.setGravity(Gravity.CENTER);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 88, 0, 0));

            versionView = new TextView(context);
            versionView.setText("v" + BuildVars.BUILD_VERSION_STRING);
            versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            versionView.setAlpha(0.6f);
            versionView.setTextColor(isDark ? Color.WHITE : 0xFF5C6B7F);
            versionView.setGravity(Gravity.CENTER);
            addView(versionView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 118, 0, 0));
        }
        public void update() {
            nameView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            versionView.setTextColor(isDark ? 0xAAFFFFFF : 0xAA5C6B7F);
        }
    }

    private class SectionHeaderCell extends FrameLayout {
        private final TextView textView;
        public SectionHeaderCell(Context context) {
            super(context);
            setPadding(dp(22), dp(16), dp(16), dp(8));
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setLetterSpacing(0.05f);
            textView.setTextColor(isDark ? 0x88FFFFFF : 0x885C6B7F);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        public void setText(String text) { textView.setText(text); }
    }

    private class CardSwitchCell extends FrameLayout {
        private final LinearLayout container;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final Switch switchView;
        private final View divider;

        public CardSwitchCell(Context context) {
            super(context);
            setPadding(dp(16), 0, dp(16), 0);
            container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding(dp(14), dp(12), dp(14), dp(12));
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            container.addView(iconView, LayoutHelper.createLinear(32, 32));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            texts.addView(titleView);
            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            texts.addView(subtitleView);
            container.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            switchView = new Switch(context);
            container.addView(switchView, LayoutHelper.createLinear(38, 20, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            addView(container);
            divider = new View(context);
            addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 54, 0, 14, 0));
        }

        public void setData(String title, String subtitle, int iconRes, boolean checked, OnSwitchListener listener, boolean first, boolean last) {
            titleView.setText(title);
            subtitleView.setText(subtitle);
            iconView.setImageResource(iconRes);
            switchView.setChecked(checked, false);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBg);
            float r = dp(16);
            bg.setCornerRadii(new float[]{first ? r : 0, first ? r : 0, first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0});
            container.setBackground(bg);
            titleView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            subtitleView.setTextColor(isDark ? 0xAAFFFFFF : 0xAA5C6B7F);
            iconView.setColorFilter(new PorterDuffColorFilter(checked ? 0xFF2196F3 : (isDark ? 0xFFAAAAAA : 0xFF777777), PorterDuff.Mode.SRC_IN));
            
            if (iconRes == R.drawable.msg_delete_solar) {
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconView.setPadding(dp(2), dp(2), dp(2), dp(2));
            } else {
                iconView.setScaleType(ImageView.ScaleType.CENTER);
                iconView.setPadding(0, 0, 0, 0);
            }
            
            divider.setBackgroundColor(dividerColor);
            divider.setVisibility(last ? GONE : VISIBLE);
            setOnClickListener(v -> {
                boolean target = !switchView.isChecked();
                switchView.setChecked(target, true);
                listener.onSwitch(target);
                iconView.setColorFilter(new PorterDuffColorFilter(target ? 0xFF2196F3 : (isDark ? 0xFFAAAAAA : 0xFF777777), PorterDuff.Mode.SRC_IN));
            });
        }
    }

    private interface OnSwitchListener { void onSwitch(boolean isChecked); }

    private class CardItemCell extends LinearLayout {
        public CardItemCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(16), 0, dp(16), 8);
        }
        public void setData(String title, String subtitle, int iconRes, int iconColor, View.OnClickListener listener, boolean first, boolean last) {
            removeAllViews();
            addView(createItem(getContext(), title, subtitle, iconRes, iconColor, listener, first, last));
        }
        public void setMultiData(CoreItem[] items) {
            removeAllViews();
            for (int i = 0; i < items.length; i++) {
                CoreItem item = items[i];
                addView(createItem(getContext(), item.title, item.subtitle, item.iconRes, item.iconColor, item.listener, i == 0, i == items.length - 1));
                if (i < items.length - 1) {
                    View d = new View(getContext());
                    d.setBackgroundColor(dividerColor);
                    addView(d, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 56, 0, 16, 0));
                }
            }
        }
        private View createItem(Context ctx, String title, String subtitle, int iconRes, int iconColor, View.OnClickListener listener, boolean first, boolean last) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setClickable(true);
            row.setOnClickListener(listener);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBg);
            float r = dp(16);
            bg.setCornerRadii(new float[]{first ? r : 0, first ? r : 0, first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0});
            row.setBackground(bg);
            ImageView iconView = new ImageView(ctx);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(Color.WHITE);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(dp(10));
            iconBg.setColor(iconColor);
            iconView.setBackground(iconBg);
            row.addView(iconView, LayoutHelper.createLinear(32, 32));
            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            TextView titleView = new TextView(ctx);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            texts.addView(titleView);
            TextView subView = new TextView(ctx);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            subView.setTextColor(isDark ? 0xAAFFFFFF : 0xAA5C6B7F);
            texts.addView(subView);
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            ImageView arrow = new ImageView(ctx);
            arrow.setImageResource(R.drawable.msg_arrowright);
            arrow.setColorFilter(new PorterDuffColorFilter(isDark ? 0x44FFFFFF : 0x22000000, PorterDuff.Mode.SRC_IN));
            row.addView(arrow, LayoutHelper.createLinear(20, 20));
            return row;
        }
    }

    private static class CoreItem {
        String title, subtitle;
        int iconRes, iconColor;
        View.OnClickListener listener;
        CoreItem(String t, String s, int i, int c, View.OnClickListener l) { title = t; subtitle = s; iconRes = i; iconColor = c; listener = l; }
    }

    private class ActionsCell extends LinearLayout {
        public ActionsCell(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(dp(16), dp(8), dp(16), dp(8));
        }
        public void update() {
            removeAllViews();
            int color = isDark ? Color.WHITE : 0xFF1A1A2E;
            addView(createAction(getContext(), "Export", R.drawable.msg_share, color, v -> SettingsBackupHelper.backupSettings(getParentActivity(), resourcesProvider)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            addView(new View(getContext()), LayoutHelper.createLinear(dp(12), 1));
            addView(createAction(getContext(), "Reset", R.drawable.msg_reset, color, v -> resetSettings()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            addView(new View(getContext()), LayoutHelper.createLinear(dp(12), 1));
            addView(createAction(getContext(), "Restart", R.drawable.msg_retry, color, v -> AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class))), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            addView(new View(getContext()), LayoutHelper.createLinear(dp(12), 1));
            addView(createAction(getContext(), "About", R.drawable.msg_info, color, v -> presentFragment(new NekoAboutActivity())), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        }
        private View createAction(Context ctx, String text, int iconRes, int textColor, View.OnClickListener listener) {
            LinearLayout btn = new LinearLayout(ctx);
            btn.setOrientation(VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(14), 0, dp(14));
            btn.setClickable(true);
            btn.setOnClickListener(listener);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBg);
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), cardBorder);
            btn.setBackground(bg);
            ImageView icon = new ImageView(ctx);
            icon.setImageResource(iconRes);
            icon.setColorFilter(new PorterDuffColorFilter(0xFF2196F3, PorterDuff.Mode.SRC_IN));
            btn.addView(icon, LayoutHelper.createLinear(26, 26));
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(textColor);
            btn.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            return btn;
        }
    }

    private class FooterCell extends FrameLayout {
        private final TextView textView;
        public FooterCell(Context context) {
            super(context);
            setPadding(0, dp(32), 0, dp(32));
            textView = new TextView(context);
            textView.setTextSize(11);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(isDark ? 0x80FFFFFF : 0x805C6B7F);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
        public void update() { 
            textView.setText("Alexgram v" + BuildVars.BUILD_VERSION_STRING); 
            textView.setGravity(Gravity.CENTER);
        }
    }

    private void resetSettings() {
        AlertUtil.showConfirm(getParentActivity(),
                getString(R.string.ResetSettingsAlert),
                R.drawable.msg_reset,
                getString(R.string.Reset),
                true,
                () -> {
                    ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                    ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                    NekoConfig.getPreferences().edit().clear().commit();
                    AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                });
    }
}
