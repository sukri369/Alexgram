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

public class AIAssistanceSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private SharedPreferences preferences;

    private static final int ROW_ENABLED = 0;
    private static final int ROW_SKIN = 1;
    private static final int ROW_ANIMATION_INTENSITY = 2;
    private static final int ROW_ANIMATION_INTENSITY_SLIDER = 3;
    private static final int ROW_PERSONA = 4;
    private static final int ROW_KEYBOARD_AUTO_HIDE = 5;
    private static final int ROW_AUTO_FOLLOW = 6;
    private static final int ROW_CHAT_CONTEXT = 7;
    private static final int ROW_PARTICLE_EFFECTS = 8;
    private static final int ROW_REACTION_BUBBLES = 9;
    private static final int ROW_COUNT = 10;

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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Alexgram AI Assistance");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        adapter = new ListAdapter(context);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);
        listView.setAdapter(adapter);
        fragmentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return ROW_COUNT + 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 || position == ROW_COUNT + 1) {
                return 1;
            }
            int row = position - 1;
            if (row == ROW_ANIMATION_INTENSITY_SLIDER) {
                return 2;
            }
            if (row == ROW_SKIN || row == ROW_PERSONA) {
                return 3;
            }
            return 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new ShadowSectionCell(context);
                    break;
                case 2:
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
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
                    ((FrameLayout) view).addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 16, 8, 16, 8));
                    break;
                case 3:
                    view = new TextSettingsCell(context, resourceProvider);
                    break;
                default:
                    view = new TextCheckCell(context, resourceProvider);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int row = position - 1;
            switch (getItemViewType(position)) {
                case 0:
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    boolean checked = false;
                    String title = "";
                    String key = "";

                    if (row == ROW_ENABLED) {
                        title = "Enable AI Assistance";
                        key = "assistant_enabled";
                        checked = preferences.getBoolean(key, true);
                    } else if (row == ROW_KEYBOARD_AUTO_HIDE) {
                        title = "Hide on keyboard appearance";
                        key = "keyboard_auto_hide";
                        checked = preferences.getBoolean(key, true);
                    } else if (row == ROW_AUTO_FOLLOW) {
                        title = "Auto-position near chat";
                        key = "auto_follow";
                        checked = preferences.getBoolean(key, true);
                    } else if (row == ROW_CHAT_CONTEXT) {
                        title = "Use chat context for replies";
                        key = "use_context";
                        checked = preferences.getBoolean(key, true);
                    } else if (row == ROW_PARTICLE_EFFECTS) {
                        title = "Particle effects on interaction";
                        key = "particle_effects";
                        checked = preferences.getBoolean(key, true);
                    } else if (row == ROW_REACTION_BUBBLES) {
                        title = "Show reaction bubbles";
                        key = "reaction_bubbles";
                        checked = preferences.getBoolean(key, true);
                    }

                    if (TextUtils.isEmpty(key)) {
                        cell.setOnClickListener(null);
                        cell.setTextAndCheck("", false, true);
                        break;
                    }

                    final String finalKey = key;
                    cell.setTextAndCheck(title, checked, true);
                    cell.setOnClickListener(v -> {
                        boolean newValue = !preferences.getBoolean(finalKey, true);
                        preferences.edit().putBoolean(finalKey, newValue).apply();
                        int adapterPosition = holder.getAdapterPosition();
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            adapter.notifyItemChanged(adapterPosition);
                        }
                    });
                    break;
                case 2:
                    break;
                case 3:
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    if (row == ROW_SKIN) {
                        int skinIndex = preferences.getInt("character_skin", 0);
                        String[] skins = {"Sky Blue", "Mint Green", "Sunset"};
                        settingsCell.setTextAndValue("Character Skin", skins[Math.min(skinIndex, 2)], true);
                        settingsCell.setOnClickListener(v -> {
                            int current = preferences.getInt("character_skin", 0);
                            int next = (current + 1) % 3;
                            preferences.edit().putInt("character_skin", next).apply();
                            int adapterPosition = holder.getAdapterPosition();
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                adapter.notifyItemChanged(adapterPosition);
                            }
                        });
                    } else if (row == ROW_PERSONA) {
                        int personaIndex = preferences.getInt("persona_preset", 0);
                        String[] personas = {"Friendly Helper", "Playful Teaser", "Wise Mentor"};
                        settingsCell.setTextAndValue("Assistant Persona", personas[Math.min(personaIndex, 2)], true);
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
            }
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
