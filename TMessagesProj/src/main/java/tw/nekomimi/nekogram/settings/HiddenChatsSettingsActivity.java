package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.BulletinFactory;

import tw.nekomimi.nekogram.helpers.HiddenChatsController;
import tw.nekomimi.nekogram.ui.HiddenChatsActivity;
import tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity;

public class HiddenChatsSettingsActivity extends BaseFragment {
    
    private int cardBg;
    private int cardBorder;
    private int dividerColor;
    private boolean isDark;

    @Override
    public View createView(Context context) {
        setupColors();
        
        isDark = Theme.getActiveTheme().isDark();
        
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Hidden Chats Settings");
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        int actionBarColor = isDark ? Color.WHITE : 0xFF1A1A2E;
        actionBar.setItemsColor(actionBarColor, false);
        actionBar.setTitleColor(actionBarColor);
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout parentFrame = new FrameLayout(context);
        
        // Add animated background
        AlexgramSettingsHeaderView backgroundView = new AlexgramSettingsHeaderView(context);
        backgroundView.setVisibility(View.VISIBLE);
        parentFrame.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        
        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, AndroidUtilities.dp(56) + AndroidUtilities.statusBarHeight, 0, 0);
        parentFrame.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        parentFrame.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        scrollView.addView(contentLayout);
        fragmentView = parentFrame;

        // Options section
        addGlassSection(contentLayout, context, "OPTIONS");
        LinearLayout optionsCard = createGlassCard(context);

        // Change Passcode
        optionsCard.addView(createSettingItem(context, "Change Passcode", "Update your 4-digit PIN", R.drawable.msg_permissions_solar, 0xFFE91E63, v -> {
            showChangePasscodeDialog(context);
        }, false));

        boolean hasFingerprint = false;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            try {
                org.telegram.messenger.support.fingerprint.FingerprintManagerCompat fingerprintManager = org.telegram.messenger.support.fingerprint.FingerprintManagerCompat.from(org.telegram.messenger.ApplicationLoader.applicationContext);
                hasFingerprint = fingerprintManager.isHardwareDetected();
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }

        if (hasFingerprint) {
            optionsCard.addView(createGlassDivider(context));
            optionsCard.addView(createSwitchItem(context, "Unlock with Fingerprint", "Use biometric scanning as secondary decryption.", R.drawable.fingerprint, 0xFF009688,
                    HiddenChatsController.getInstance().isBiometricEnabled(), isChecked -> {
                        HiddenChatsController.getInstance().setBiometricEnabled(isChecked);
                    }));
        } else {
            optionsCard.addView(createGlassDivider(context));
            optionsCard.addView(createSettingItem(context, "Fingerprint Scanning", "TERMINAL_STATUS: MODULE_OFFLINE", R.drawable.fingerprint, 0xFF607D8B, v -> {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_ban, "BIOMETRIC MODULE NOT FOUND", "System scan failed. Hardware sensors are not detected on this terminal.").show();
            }, false));
        }

        optionsCard.addView(createGlassDivider(context));

        // Open Hidden Chats
        optionsCard.addView(createSettingItem(context, "Open Hidden Chats", "Access your hidden chats now", R.drawable.msg_folders_private_solar, 0xFFE91E63, v -> {
            HiddenChatsController controller = HiddenChatsController.getInstance();
            if (controller.hasPasscode()) {
                if (controller.isLocked()) {
                    presentFragment(new HiddenChatsPasscodeActivity(HiddenChatsPasscodeActivity.MODE_UNLOCK_CHATS));
                } else {
                    presentFragment(new HiddenChatsActivity(new android.os.Bundle()));
                }
            } else {
                presentFragment(new HiddenChatsPasscodeActivity(HiddenChatsPasscodeActivity.MODE_SETUP_PASSCODE));
            }
        }, false));

        optionsCard.addView(createGlassDivider(context));

        // Reset
        optionsCard.addView(createSettingItem(context, "Reset Hidden Chats", "Clear all hidden chats and reset passcode", R.drawable.msg_delete, 0xFFE91E63, v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Reset Hidden Chats");
            builder.setMessage("This will unhide all chats and remove the passcode. Are you sure?");
            builder.setPositiveButton("Reset", (d, w) -> {
                HiddenChatsController.getInstance().reset();
                finishFragment();
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }, false));

        optionsCard.addView(createGlassDivider(context));

        // How to Use
        optionsCard.addView(createSettingItem(context, "How to Use", "Learn how to manage hidden chats", R.drawable.msg_info, 0xFFE91E63, v -> {
            org.telegram.ui.ActionBar.BottomSheet.Builder builder = new org.telegram.ui.ActionBar.BottomSheet.Builder(context);
            builder.setApplyBottomPadding(false);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(0, AndroidUtilities.dp(16), 0, 0);

            TextView titleView = new TextView(context);
            titleView.setText("How to Use Hidden Chats");
            titleView.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1A1A2E);
            titleView.setTextSize(20);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER_HORIZONTAL);
            container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

            container.addView(createGuideItem(context, R.drawable.msg_folders_private_solar, 0xFF00bcd4, "Hide Chats", "Long-press any chat in the chat list, open the 3-dot menu and select 'Add to Hidden Chats'."));
            container.addView(createGuideItem(context, R.drawable.msg_search, 0xFFf06292, "Access Hidden Chats", "Long-press on the Nagram header on the main screen to quickly access your Hidden Chats."));
            container.addView(createGuideItem(context, R.drawable.msg_permissions_solar, 0xFFba68c8, "Privacy", "Chats added to Hidden Chats are automatically muted to ensure maximum privacy."));
            container.addView(createGuideItem(context, R.drawable.outline_header_lock_24, 0xFF4db6ac, "Security", "Your hidden chats are strictly protected by a 4-digit passcode, and optionally your fingerprint."));

            TextView btn = new TextView(context);
            btn.setText("Got It");
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(14);
            btn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            btn.setGravity(Gravity.CENTER);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(AndroidUtilities.dp(8));
            btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            btn.setBackground(btnBg);
            btn.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            btn.setOnClickListener(b -> builder.getDismissRunnable().run());
            
            container.addView(btn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 16, 16, 16, 16));

            builder.setCustomView(container);
            showDialog(builder.create());
        }, true));

        contentLayout.addView(optionsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 12));

        return fragmentView;
    }

    private void setupColors() {
        isDark = Theme.getActiveTheme().isDark();
        if (isDark) {
            cardBg = 0x30FFFFFF;
            cardBorder = 0x22FFFFFF;
            dividerColor = 0x15FFFFFF;
        } else {
            cardBg = 0x60FFFFFF;
            cardBorder = 0x30000000;
            dividerColor = 0x18000000;
        }
    }

    private void addGlassSection(LinearLayout parent, Context ctx, String title) {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextSize(11);
        tv.setTextColor(isDark ? 0xCC8899AA : 0xCC5C6B7F);
        tv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tv.setLetterSpacing(0.1f);
        parent.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 8));
    }

    private LinearLayout createGlassCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setStroke(AndroidUtilities.dp(1), cardBorder);
        card.setBackground(bg);
        card.setClipChildren(true);
        return card;
    }

    private View createGlassDivider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(dividerColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = AndroidUtilities.dp(56);
        v.setLayoutParams(lp);
        return v;
    }

    private View createSettingItem(Context context, String title, String subtitle, int iconRes, int iconColor, View.OnClickListener onClick, boolean isLast) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(13), AndroidUtilities.dp(14), AndroidUtilities.dp(13));
        row.setClickable(true);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(onClick);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(Color.WHITE);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(10));
        iconBg.setColor(iconColor);
        iconView.setBackground(iconBg);
        row.addView(iconView, LayoutHelper.createLinear(32, 32));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1A1A2E);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(isDark ? 0xFF8899AA : 0xFF5C6B7F);
        subtitleView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        row.addView(texts, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 14, 0));

        return row;
    }

    private View createGuideItem(Context ctx, int iconRes, int iconColor, String title, String text) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        ImageView iconView = new ImageView(ctx);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(Color.WHITE);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(iconColor);
        iconView.setBackground(iconBg);
        row.addView(iconView, LayoutHelper.createLinear(40, 40));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1A1A2E);
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subView = new TextView(ctx);
        subView.setText(text);
        subView.setTextColor(isDark ? 0xFF8899AA : 0xFF5C6B7F);
        subView.setTextSize(13);
        subView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(texts, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 0, 0));

        return row;
    }

    interface OnSettingSwitchListener {
        void onSwitch(boolean isChecked);
    }

    private View createSwitchItem(Context ctx, String title, String subtitle, int iconRes, int iconColor, boolean checked, OnSettingSwitchListener onChange) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(13), AndroidUtilities.dp(14), AndroidUtilities.dp(13));
        row.setBackground(Theme.getSelectorDrawable(false));

        ImageView iconView = new ImageView(ctx);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(Color.WHITE);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(10));
        iconBg.setColor(iconColor);
        iconView.setBackground(iconBg);
        row.addView(iconView, LayoutHelper.createLinear(32, 32));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextColor(isDark ? 0xFFFFFFFF : 0xFF1A1A2E);
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subView = new TextView(ctx);
        subView.setText(subtitle);
        subView.setTextColor(isDark ? 0xFF8899AA : 0xFF5C6B7F);
        subView.setTextSize(12);
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));

        org.telegram.ui.Components.Switch sw = new org.telegram.ui.Components.Switch(ctx);
        sw.setChecked(checked, false);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        row.addView(sw, LayoutHelper.createLinear(44, 24));

        row.setOnClickListener(v -> {
            boolean isChecked = !sw.isChecked();
            sw.setChecked(isChecked, true);
            onChange.onSwitch(isChecked);
        });

        return row;
    }
    
    private void showChangePasscodeDialog(Context context) {
        presentFragment(new HiddenChatsPasscodeActivity(HiddenChatsPasscodeActivity.MODE_CHANGE_PASSCODE));
    }
}
