/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.settings.AlexgramSettingsHeaderView;

public class AIAssistanceSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private SharedPreferences preferences;

    // Section headers and dividers
    private static final int ROW_SPACE_TOP = 0;
    private static final int ROW_GENERAL_SECTION = 1;
    private static final int ROW_GENERAL_HEADER = 2;
    private static final int ROW_ENABLED = 3;
    private static final int ROW_DIVIDER_1 = 4;
    private static final int ROW_SKIN = 5;
    private static final int ROW_DIVIDER_2 = 6;
    private static final int ROW_PERSONA = 7;
    private static final int ROW_GENERAL_FOOTER = 8;
    
    private static final int ROW_APPEARANCE_SECTION = 9;
    private static final int ROW_APPEARANCE_HEADER = 10;
    private static final int ROW_BACKGROUND_ANIMATION = 11;
    private static final int ROW_DIVIDER_3 = 12;
    private static final int ROW_ANIMATION_INTENSITY_SLIDER = 13;
    private static final int ROW_APPEARANCE_FOOTER = 14;
    
    private static final int ROW_BEHAVIOR_SECTION = 15;
    private static final int ROW_BEHAVIOR_HEADER = 16;
    private static final int ROW_KEYBOARD_AUTO_HIDE = 17;
    private static final int ROW_DIVIDER_4 = 18;
    private static final int ROW_AUTO_FOLLOW = 19;
    private static final int ROW_DIVIDER_5 = 20;
    private static final int ROW_CHAT_CONTEXT = 21;
    private static final int ROW_DIVIDER_6 = 22;
    private static final int ROW_PARTICLE_EFFECTS = 23;
    private static final int ROW_DIVIDER_7 = 24;
    private static final int ROW_REACTION_BUBBLES = 25;
    private static final int ROW_BEHAVIOR_FOOTER = 26;
    private static final int ROW_SPACE_BOTTOM = 27;
    
    private static final int ROW_COUNT = 28;

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
        boolean isDark = Theme.getActiveTheme().isDark();

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

        FrameLayout parentFrame = new FrameLayout(context);

        AlexgramSettingsHeaderView backgroundView = new AlexgramSettingsHeaderView(context);
        boolean backgroundEnabled = preferences.getBoolean("background_animation", true);
        backgroundView.setVisibility(backgroundEnabled ? View.VISIBLE : View.GONE);
        parentFrame.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (!backgroundEnabled) {
            parentFrame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(56) + AndroidUtilities.statusBarHeight, 0, 0);
        adapter = new ListAdapter(context);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);
        listView.setAdapter(adapter);
        parentFrame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        parentFrame.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = parentFrame;
        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return ROW_COUNT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ROW_SPACE_TOP || position == ROW_SPACE_BOTTOM) return 5;
            if (position == ROW_GENERAL_SECTION || position == ROW_APPEARANCE_SECTION || position == ROW_BEHAVIOR_SECTION) return 6;
            if (position == ROW_GENERAL_HEADER || position == ROW_APPEARANCE_HEADER || position == ROW_BEHAVIOR_HEADER) return 4;
            if (position == ROW_GENERAL_FOOTER || position == ROW_APPEARANCE_FOOTER || position == ROW_BEHAVIOR_FOOTER) return 7;
            if (position == ROW_DIVIDER_1 || position == ROW_DIVIDER_2 || position == ROW_DIVIDER_3 || 
                position == ROW_DIVIDER_4 || position == ROW_DIVIDER_5 || position == ROW_DIVIDER_6 || position == ROW_DIVIDER_7) return 8;
            if (position == ROW_ANIMATION_INTENSITY_SLIDER) return 2;
            if (position == ROW_SKIN || position == ROW_PERSONA) return 3;
            return 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 2: // Slider
                    view = new FrameLayout(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
                        }
                    };
                    SeekBar seekBar = new SeekBar(context);
                    seekBar.setMax(100);
                    seekBar.setProgress(preferences.getInt("animation_intensity", 70));
                    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            preferences.edit().putInt("animation_intensity", progress).apply();
                        }
                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}
                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });
                    ((FrameLayout) view).addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 16, 8, 16, 8));
                    break;
                case 3: // TextSettingsCell
                    view = new TextSettingsCell(context, resourceProvider);
                    break;
                case 4: // HeaderCell
                    view = new HeaderCell(context, resourceProvider);
                    break;
                case 5: // Space
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(8), MeasureSpec.EXACTLY));
                        }
                    };
                    view.setBackgroundColor(Color.TRANSPARENT);
                    break;
                case 6: // Section label
                    view = new TextView(context);
                    TextView sectionLabel = (TextView) view;
                    sectionLabel.setTextSize(11);
                    sectionLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    sectionLabel.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    sectionLabel.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
                    break;
                case 7: // Card footer (ShadowSectionCell)
                    view = new ShadowSectionCell(context);
                    break;
                case 8: // Divider
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY));
                        }
                    };
                    int dividerColor = isDarkTheme() ? 0x15FFFFFF : 0x18000000;
                    view.setBackgroundColor(dividerColor);
                    break;
                default: // TextCheckCell
                    view = new TextCheckCell(context, resourceProvider);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (getItemViewType(position)) {
                case 0: // TextCheckCell
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    boolean checked = false;
                    String title = "";
                    String key = "";

                    switch (position) {
                        case ROW_ENABLED:
                            title = "Enable AI Assistance";
                            key = "assistant_enabled";
                            break;
                        case ROW_BACKGROUND_ANIMATION:
                            title = "Background animation";
                            key = "background_animation";
                            break;
                        case ROW_KEYBOARD_AUTO_HIDE:
                            title = "Hide on keyboard appearance";
                            key = "keyboard_auto_hide";
                            break;
                        case ROW_AUTO_FOLLOW:
                            title = "Auto-position near chat";
                            key = "auto_follow";
                            break;
                        case ROW_CHAT_CONTEXT:
                            title = "Use chat context for replies";
                            key = "use_context";
                            break;
                        case ROW_PARTICLE_EFFECTS:
                            title = "Particle effects on interaction";
                            key = "particle_effects";
                            break;
                        case ROW_REACTION_BUBBLES:
                            title = "Show reaction bubbles";
                            key = "reaction_bubbles";
                            break;
                    }

                    if (TextUtils.isEmpty(key)) {
                        cell.setOnClickListener(null);
                        cell.setTextAndCheck("", false, true);
                        break;
                    }

                    checked = preferences.getBoolean(key, true);
                    final String finalKey = key;
                    cell.setTextAndCheck(title, checked, isLastInCard(position));
                    cell.setOnClickListener(v -> {
                        boolean newValue = !preferences.getBoolean(finalKey, true);
                        preferences.edit().putBoolean(finalKey, newValue).apply();
                        int adapterPosition = holder.getAdapterPosition();
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            adapter.notifyItemChanged(adapterPosition);
                            if (position == ROW_BACKGROUND_ANIMATION) {
                                adapter.notifyItemChanged(ROW_ANIMATION_INTENSITY_SLIDER);
                            }
                        }
                    });
                    break;

                case 2: // Slider
                    FrameLayout sliderContainer = (FrameLayout) holder.itemView;
                    SeekBar slider = null;
                    if (sliderContainer.getChildCount() > 0 && sliderContainer.getChildAt(0) instanceof SeekBar) {
                        slider = (SeekBar) sliderContainer.getChildAt(0);
                    }
                    if (slider != null) {
                        boolean animationEnabled = preferences.getBoolean("background_animation", true);
                        slider.setProgress(preferences.getInt("animation_intensity", 70));
                        slider.setEnabled(animationEnabled);
                        slider.setAlpha(animationEnabled ? 1f : 0.45f);
                    }
                    break;

                case 3: // TextSettingsCell
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    if (position == ROW_SKIN) {
                        int skinIndex = preferences.getInt("character_skin", 0);
                        String[] skins = {"Sky Blue", "Mint Green", "Sunset"};
                        settingsCell.setTextAndValue("Character Skin", skins[Math.min(skinIndex, 2)], !isLastInCard(position));
                        settingsCell.setOnClickListener(v -> {
                            int current = preferences.getInt("character_skin", 0);
                            int next = (current + 1) % 3;
                            preferences.edit().putInt("character_skin", next).apply();
                            int adapterPosition = holder.getAdapterPosition();
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                adapter.notifyItemChanged(adapterPosition);
                            }
                        });
                    } else if (position == ROW_PERSONA) {
                        int personaIndex = preferences.getInt("persona_preset", 0);
                        String[] personas = {"Friendly Helper", "Playful Teaser", "Wise Mentor"};
                        settingsCell.setTextAndValue("Assistant Persona", personas[Math.min(personaIndex, 2)], isLastInCard(position));
                        settingsCell.setOnClickListener(v -> {
                            int current = preferences.getInt("persona_preset", 0);
                            int next = (current + 1) % 3;
                            preferences.edit().putInt("persona_preset", next).apply();
                            int adapterPosition = holder.getAdapterPosition();
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                adapter.notifyItemChanged(adapterPosition);
                            }
                        });
                    }
                    break;

                case 4: // HeaderCell
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == ROW_GENERAL_HEADER) {
                        headerCell.setText("General");
                    } else if (position == ROW_APPEARANCE_HEADER) {
                        headerCell.setText("Appearance");
                    } else if (position == ROW_BEHAVIOR_HEADER) {
                        headerCell.setText("Behavior");
                    }
                    break;

                case 6: // Section label
                    TextView sectionLabel = (TextView) holder.itemView;
                    if (position == ROW_GENERAL_SECTION) {
                        sectionLabel.setText("GENERAL");
                    } else if (position == ROW_APPEARANCE_SECTION) {
                        sectionLabel.setText("APPEARANCE");
                    } else if (position == ROW_BEHAVIOR_SECTION) {
                        sectionLabel.setText("BEHAVIOR");
                    }
                    break;
            }
        }

        private boolean isLastInCard(int position) {
            return (position == ROW_PERSONA) || (position == ROW_REACTION_BUBBLES) || (position == ROW_ANIMATION_INTENSITY_SLIDER);
        }

        private boolean isDarkTheme() {
            return Theme.getActiveTheme().isDark();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = getItemViewType(holder.getAdapterPosition());
            return viewType == 0 || viewType == 3;
        }
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
