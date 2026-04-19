/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.settings.AlexgramSettingsHeaderView;

public class AIAssistanceSettingsActivity extends BaseFragment {

    private SharedPreferences preferences;

    private FrameLayout rootFrame;
    private AlexgramSettingsHeaderView backgroundView;
    private SeekBar intensitySeekBar;
    private TextView skinValueView;
    private TextView personaValueView;

    private boolean isDark;
    private int cardBg;
    private int cardBorder;
    private int textTitle;
    private int textSub;
    private int sectionLabel;
    private int accentColor;
    private int dividerColor;

    @Override
    public boolean onFragmentCreate() {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        if (preferences == null) {
            preferences = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE);
        }
        setupColors();

        actionBar.setAddToContainer(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Alexgram AI Assistance");
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        int actionBarColor = isDark ? Color.WHITE : 0xFF1A1A2E;
        actionBar.setItemsColor(actionBarColor, false);
        actionBar.setTitleColor(actionBarColor);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // --- AI Assistance API Setup Card ---
        LinearLayout apiSetupCard = createGlassCard(context);
        TextView apiSetupTitle = new TextView(context);
        apiSetupTitle.setText("Setup API for AI Replies");
        apiSetupTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        apiSetupTitle.setTextColor(accentColor);
        apiSetupTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        apiSetupTitle.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(6));
        apiSetupCard.addView(apiSetupTitle);

        TextView apiSetupDesc = new TextView(context);
        apiSetupDesc.setText("To use AI Replies, you need to set up your Model URL and API Key. Tap below for a step-by-step guide and quick access to the configuration section.");
        apiSetupDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        apiSetupDesc.setTextColor(textSub);
        apiSetupDesc.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        apiSetupCard.addView(apiSetupDesc);

        TextView setupButton = new TextView(context);
        setupButton.setText("Setup API Now");
        setupButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        setupButton.setTextColor(Color.WHITE);
        setupButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        setupButton.setGravity(Gravity.CENTER);
        setupButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), accentColor, 0x22000000));
        setupButton.setPadding(AndroidUtilities.dp(0), AndroidUtilities.dp(12), AndroidUtilities.dp(0), AndroidUtilities.dp(12));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(18));
        setupButton.setLayoutParams(btnLp);
        setupButton.setOnClickListener(v -> {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            // Custom dialog layout
            LinearLayout dialogLayout = new LinearLayout(ctx);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
            // Glassy/rounded background
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(isDark ? 0xF21A2236 : 0xF2FFFFFF);
            dialogBg.setCornerRadius(AndroidUtilities.dp(24));
            dialogLayout.setBackground(dialogBg);

            // Icon
            TextView icon = new TextView(ctx);
            icon.setText("\uD83E\uDD16"); // robot face emoji as a placeholder for AI icon
            icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 44);
            icon.setGravity(Gravity.CENTER);
            icon.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            dialogLayout.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Title
            TextView title = new TextView(ctx);
            title.setText("AI Reply Setup Guide");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            title.setTextColor(accentColor);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, AndroidUtilities.dp(10));
            dialogLayout.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Steps
            int stepColor = isDark ? 0xFF5AB6FF : 0xFF2E93DE;
            String[] steps = new String[] {
                "Sign up at an AI provider (OpenAI, DeepSeek, Groq, Gemini, etc.)",
                "Get your API Key and Model URL from their dashboard",
                "Go to Experimental Settings > AI Reply section",
                "Paste your Model URL and API Key"
            };
            for (int i = 0; i < steps.length; i++) {
                LinearLayout stepRow = new LinearLayout(ctx);
                stepRow.setOrientation(LinearLayout.HORIZONTAL);
                stepRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView stepNum = new TextView(ctx);
                stepNum.setText("" + (i + 1));
                stepNum.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                stepNum.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                stepNum.setTextColor(stepColor);
                stepNum.setGravity(Gravity.CENTER);
                // Use a round background for the number
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(0x22FFFFFF);
                stepNum.setBackground(circle);
                LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(28), AndroidUtilities.dp(28));
                numLp.rightMargin = AndroidUtilities.dp(12);
                stepRow.addView(stepNum, numLp);
                TextView stepText = new TextView(ctx);
                stepText.setText(steps[i]);
                stepText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
                stepText.setTextColor(textTitle);
                stepText.setLineSpacing(0, 1.08f);
                stepRow.addView(stepText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                stepRow.setPadding(0, AndroidUtilities.dp(i == 0 ? 0 : 8), 0, 0);
                dialogLayout.addView(stepRow);
            }

            // Help text
            TextView help = new TextView(ctx);
            help.setText("Need help? Tap 'Go to AI Reply Settings' to jump there now.");
            help.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            help.setTextColor(textSub);
            help.setGravity(Gravity.CENTER);
            help.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(8));
            dialogLayout.addView(help);

            // Buttons
            LinearLayout buttonRow = new LinearLayout(ctx);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.END);
            buttonRow.setPadding(0, AndroidUtilities.dp(8), 0, 0);

            // We'll create the dialog first so we can reference it in listeners
            AlertDialog alert = new AlertDialog.Builder(ctx)
                .setView(dialogLayout)
                .create();

            TextView closeBtn = new TextView(ctx);
            closeBtn.setText("Close");
            closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            closeBtn.setTextColor(accentColor);
            closeBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            closeBtn.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setCornerRadius(AndroidUtilities.dp(8));
            closeBg.setColor(0x00000000);
            closeBtn.setBackground(closeBg);
            closeBtn.setOnClickListener(vv -> {
                alert.dismiss();
            });
            buttonRow.addView(closeBtn);

            TextView gotoBtn = new TextView(ctx);
            gotoBtn.setText("Go to AI Reply Settings");
            gotoBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            gotoBtn.setTextColor(Color.WHITE);
            gotoBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            gotoBtn.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
            GradientDrawable btnBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{accentColor, stepColor});
            btnBg.setCornerRadius(AndroidUtilities.dp(8));
            gotoBtn.setBackground(btnBg);
            gotoBtn.setOnClickListener(vv -> {
                try {
                    android.net.Uri uri = android.net.Uri.parse("https://t.me/alexsettings/experimental?r=aiModelUrl");
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
                    ctx.startActivity(intent);
                } catch (Exception e) {
                    new AlertDialog.Builder(ctx)
                        .setTitle("Navigation Failed")
                        .setMessage("Please open Experimental Settings manually and scroll to the AI Reply section.")
                        .setPositiveButton("OK", null)
                        .show();
                }
                alert.dismiss();
            });
            LinearLayout.LayoutParams gotoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gotoLp.leftMargin = AndroidUtilities.dp(12);
            buttonRow.addView(gotoBtn, gotoLp);

            dialogLayout.addView(buttonRow);

            alert.show();
        });
        apiSetupCard.addView(setupButton);

        rootFrame = new FrameLayout(context);

        backgroundView = new AlexgramSettingsHeaderView(context);
        boolean backgroundEnabled = preferences.getBoolean("background_animation", true);
        backgroundView.setVisibility(backgroundEnabled ? View.VISIBLE : View.GONE);
        rootFrame.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setClipToPadding(true);
        scrollView.setPadding(0, AndroidUtilities.dp(56) + AndroidUtilities.statusBarHeight, 0, 0);
        rootFrame.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        scrollView.addView(contentLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Add the API setup card at the top of the content layout
        contentLayout.addView(apiSetupCard);


        addGlassSection(contentLayout, context, "GENERAL");
        LinearLayout generalCard = createGlassCard(context);
        generalCard.addView(createSwitchRow(context, "Enable AI Assistance", "assistant_enabled", true, null));
        generalCard.addView(createCardDivider(context, 16, 16));
        generalCard.addView(createValueRow(context, "Character Skin", getSkinName(), "character_skin", () -> {
            int current = preferences.getInt("character_skin", 0);
            int next = (current + 1) % 3;
            preferences.edit().putInt("character_skin", next).apply();
            if (skinValueView != null) {
                skinValueView.setText(getSkinName());
            }
        }));
        generalCard.addView(createCardDivider(context, 16, 16));
        generalCard.addView(createValueRow(context, "Assistant Persona", getPersonaName(), "persona_preset", () -> {
            int current = preferences.getInt("persona_preset", 0);
            int next = (current + 1) % 3;
            preferences.edit().putInt("persona_preset", next).apply();
            if (personaValueView != null) {
                personaValueView.setText(getPersonaName());
            }
        }));
        contentLayout.addView(generalCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        addGlassSection(contentLayout, context, "APPEARANCE");
        LinearLayout appearanceCard = createGlassCard(context);
        appearanceCard.addView(createSwitchRow(context, "Background animation", "background_animation", true, enabled -> {
            applyBackgroundState(enabled);
        }));
        appearanceCard.addView(createCardDivider(context, 16, 16));
        appearanceCard.addView(createSliderRow(context));
        contentLayout.addView(appearanceCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        addGlassSection(contentLayout, context, "BEHAVIOR");
        LinearLayout behaviorCard = createGlassCard(context);
        behaviorCard.addView(createSwitchRow(context, "Hide on keyboard appearance", "keyboard_auto_hide", true, null));
        behaviorCard.addView(createCardDivider(context, 16, 16));
        behaviorCard.addView(createSwitchRow(context, "Auto-position near chat", "auto_follow", true, null));
        behaviorCard.addView(createCardDivider(context, 16, 16));
        behaviorCard.addView(createSwitchRow(context, "Use chat context for replies", "use_context", true, null));
        behaviorCard.addView(createCardDivider(context, 16, 16));
        behaviorCard.addView(createSwitchRow(context, "Only reply to tagged messages in groups", "group_tag_only", false, null));
        behaviorCard.addView(createCardDivider(context, 16, 16));
        behaviorCard.addView(createSwitchRow(context, "Particle effects on interaction", "particle_effects", true, null));
        behaviorCard.addView(createCardDivider(context, 16, 16));
        behaviorCard.addView(createSwitchRow(context, "Show reaction bubbles", "reaction_bubbles", true, null));
        contentLayout.addView(behaviorCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        rootFrame.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        applyBackgroundState(backgroundEnabled);

        fragmentView = rootFrame;
        return fragmentView;
    }

    private void setupColors() {
        isDark = Theme.getActiveTheme().isDark();
        if (isDark) {
            cardBg = 0x55FFFFFF;
            cardBorder = 0x22FFFFFF;
            textTitle = 0xFFFFFFFF;
            textSub = 0xFF9BB0C7;
            sectionLabel = 0xCC9BB0C7;
            accentColor = 0xFF5AB6FF;
            dividerColor = 0x1AFFFFFF;
        } else {
            cardBg = 0xB3FFFFFF;
            cardBorder = 0x22000000;
            textTitle = 0xFF1E2733;
            textSub = 0xFF5C6B7F;
            sectionLabel = 0xCC6B7E95;
            accentColor = 0xFF2E93DE;
            dividerColor = 0x14000000;
        }
    }

    private void addGlassSection(LinearLayout parent, Context context, String title) {
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(sectionLabel);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
        label.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        label.setLetterSpacing(0.08f);
        parent.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
    }

    private LinearLayout createGlassCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(AndroidUtilities.dp(24));
        bg.setStroke(AndroidUtilities.dp(1), cardBorder);
        card.setBackground(bg);
        card.setClipToPadding(false);
        card.setClipChildren(true);
        return card;
    }

    private View createCardDivider(Context context, int left, int right) {
        View divider = new View(context);
        divider.setBackgroundColor(dividerColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = AndroidUtilities.dp(left);
        lp.rightMargin = AndroidUtilities.dp(right);
        divider.setLayoutParams(lp);
        return divider;
    }

    private View createSwitchRow(Context context, String title, String prefKey, boolean defaultValue, OnToggleChanged toggleChanged) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        row.setBackground(Theme.getSelectorDrawable(false));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(textTitle);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(context);
        toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        toggle.setChecked(preferences.getBoolean(prefKey, defaultValue), false);
        row.addView(toggle, LayoutHelper.createLinear(44, 24));

        row.setOnClickListener(v -> {
            boolean newValue = !toggle.isChecked();
            toggle.setChecked(newValue, true);
            preferences.edit().putBoolean(prefKey, newValue).apply();
            if (toggleChanged != null) {
                toggleChanged.onChanged(newValue);
            }
        });
        row.setOnLongClickListener(v -> {
            showSettingLinkDialog(context, prefKey, String.valueOf(preferences.getBoolean(prefKey, defaultValue)));
            return true;
        });
        return row;
    }

    private View createValueRow(Context context, String title, String value, String prefKey, Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(15), AndroidUtilities.dp(20), AndroidUtilities.dp(15));
        row.setBackground(Theme.getSelectorDrawable(false));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(textTitle);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueView.setTextColor(accentColor);
        valueView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if ("Character Skin".equals(title)) {
            skinValueView = valueView;
        } else if ("Assistant Persona".equals(title)) {
            personaValueView = valueView;
        }

        row.setOnClickListener(v -> onClick.run());
        row.setOnLongClickListener(v -> {
            showSettingLinkDialog(context, prefKey, String.valueOf(preferences.getInt(prefKey, 0)));
            return true;
        });
        return row;
    }

    private View createSliderRow(Context context) {
        FrameLayout row = new FrameLayout(context);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        intensitySeekBar = new SeekBar(context);
        intensitySeekBar.setMax(100);
        intensitySeekBar.setProgress(preferences.getInt("animation_intensity", 70));
        intensitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                preferences.edit().putInt("animation_intensity", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        row.addView(intensitySeekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        row.setOnLongClickListener(v -> {
            showSettingLinkDialog(context, "animation_intensity", String.valueOf(preferences.getInt("animation_intensity", 70)));
            return true;
        });
        return row;
    }

    private void showSettingLinkDialog(Context context, String key, String value) {
        String prefix = "ai_assistance";
        String baseLink = String.format(Locale.getDefault(), "https://%s/alexsettings/%s?r=%s", getMessagesController().linkPrefix, prefix, key);
        String valueLink = String.format(Locale.getDefault(), "https://%s/alexsettings/%s?r=%s&v=%s", getMessagesController().linkPrefix, prefix, key, value);
        CharSequence[] items = new CharSequence[]{LocaleController.getString(R.string.CopyLink), LocaleController.getString(R.string.BackupSettings)};
        showDialog(new AlertDialog.Builder(context)
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    AndroidUtilities.addToClipboard(baseLink);
                } else {
                    AndroidUtilities.addToClipboard(valueLink);
                }
                BulletinFactory.of(this).createCopyLinkBulletin().show();
            }).create());
    }

    private void applyBackgroundState(boolean enabled) {
        if (backgroundView != null) {
            backgroundView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (rootFrame != null) {
            rootFrame.setBackgroundColor(enabled ? Color.TRANSPARENT : Theme.getColor(Theme.key_windowBackgroundGray));
        }
        if (intensitySeekBar != null) {
            intensitySeekBar.setEnabled(enabled);
            intensitySeekBar.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private String getSkinName() {
        int skinIndex = preferences.getInt("character_skin", 0);
        String[] skins = {"Sky Blue", "Mint Green", "Sunset"};
        return skins[Math.min(skinIndex, 2)];
    }

    private String getPersonaName() {
        int personaIndex = preferences.getInt("persona_preset", 0);
        String[] personas = {"Friendly Helper", "Playful Teaser", "Wise Mentor"};
        return personas[Math.min(personaIndex, 2)];
    }

    private interface OnToggleChanged {
        void onChanged(boolean enabled);
    }

    public void importToRow(String key, String value, Runnable unknown) {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE);
        }

        String normalized = normalizeKey(key);
        if (normalized == null || normalized.isEmpty()) {
            if (unknown != null) {
                unknown.run();
            }
            return;
        }

        boolean recognized;
        boolean applied = false;
        SharedPreferences.Editor editor = preferences.edit();

        switch (normalized) {
            case "assistantenabled":
            case "enableaiassistance":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("assistant_enabled", parseBoolean(value));
                    applied = true;
                }
                break;
            case "backgroundanimation":
                recognized = true;
                if (value != null) {
                    boolean enabled = parseBoolean(value);
                    editor.putBoolean("background_animation", enabled);
                    applied = true;
                    applyBackgroundState(enabled);
                }
                break;
            case "keyboardautohide":
            case "hideonkeyboardappearance":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("keyboard_auto_hide", parseBoolean(value));
                    applied = true;
                }
                break;
            case "autofollow":
            case "autopositionnearchat":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("auto_follow", parseBoolean(value));
                    applied = true;
                }
                break;
            case "usecontext":
            case "usechatcontextforreplies":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("use_context", parseBoolean(value));
                    applied = true;
                }
                break;
            case "grouptagonly":
            case "onlyreplytotaggedmessagesingroups":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("group_tag_only", parseBoolean(value));
                    applied = true;
                }
                break;
            case "particleeffects":
            case "particleeffectsoninteraction":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("particle_effects", parseBoolean(value));
                    applied = true;
                }
                break;
            case "reactionbubbles":
            case "showreactionbubbles":
                recognized = true;
                if (value != null) {
                    editor.putBoolean("reaction_bubbles", parseBoolean(value));
                    applied = true;
                }
                break;
            case "characterskin":
                recognized = true;
                if (value != null) {
                    int skin = clamp(parseIntSafe(value, preferences.getInt("character_skin", 0)), 0, 2);
                    editor.putInt("character_skin", skin);
                    applied = true;
                    if (skinValueView != null) {
                        skinValueView.setText(getSkinName());
                    }
                }
                break;
            case "personapreset":
            case "assistantpersona":
                recognized = true;
                if (value != null) {
                    int persona = clamp(parseIntSafe(value, preferences.getInt("persona_preset", 0)), 0, 2);
                    editor.putInt("persona_preset", persona);
                    applied = true;
                    if (personaValueView != null) {
                        personaValueView.setText(getPersonaName());
                    }
                }
                break;
            case "animationintensity":
                recognized = true;
                if (value != null) {
                    int intensity = clamp(parseIntSafe(value, preferences.getInt("animation_intensity", 70)), 0, 100);
                    editor.putInt("animation_intensity", intensity);
                    applied = true;
                    if (intensitySeekBar != null) {
                        intensitySeekBar.setProgress(intensity);
                    }
                }
                break;
            default:
                recognized = false;
                break;
        }

        if (!recognized) {
            if (unknown != null) {
                unknown.run();
            }
            return;
        }

        if (applied) {
            editor.apply();
        }
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = Character.toLowerCase(key.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean parseBoolean(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return "1".equals(v) || "true".equals(v) || "on".equals(v) || "yes".equals(v) || "enabled".equals(v);
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        descriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        descriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundGrayShadow));
        return descriptions;
    }
}
