package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineThemesActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private FrameLayout loadingView;
    private ArrayList<ThemeItem> themes = new ArrayList<>();
    private String jsonUrl = "https://raw.githubusercontent.com/alexandeer1/Alexgram-Theme/main/themes.json";

    public static class ThemeItem {
        public String name;
        public String url;
        public int previewBg;
        public int previewIn;
        public int previewOut;
        public boolean isSelected;

        public ThemeItem(JSONObject json) {
            try {
                name = json.optString("name", "Untitled");
                url = json.optString("url", "");
                previewBg = (int) Long.parseLong(json.optString("preview_bg", "0xffffffff").replace("0x", ""), 16);
                previewIn = (int) Long.parseLong(json.optString("preview_in", "0xffebeef4").replace("0x", ""), 16);
                previewOut = (int) Long.parseLong(json.optString("preview_out", "0xff7cb2fe").replace("0x", ""), 16);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        loadThemes();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Online Themes");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        // List View
        listView = new RecyclerListView(context);
        listView.setVisibility(View.GONE);
        listView.setAlpha(0);
        GridLayoutManager layoutManager = new GridLayoutManager(context, 3);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Loading View
        loadingView = new FrameLayout(context);
        loadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        
        RadialProgressView progressView = new RadialProgressView(context);
        progressView.setSize(AndroidUtilities.dp(48));
        progressView.setProgressColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        loadingView.addView(progressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER, 0, 0, 0, 40));
        
        TextView loadingText = new TextView(context);
        loadingText.setText("Wait.. Theme is loading..");
        loadingText.setTextSize(16);
        loadingText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        loadingText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        loadingView.addView(loadingText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 40, 0, 0));
        
        frameLayout.addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position < themes.size()) {
                ThemeItem item = themes.get(position);
                
                AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                progressDialog.setCanCancel(false);
                progressDialog.show();
                
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder().url(item.url).build();
                        try (Response response = client.newCall(request).execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                File themesDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "themes");
                                if (!themesDir.exists()) themesDir.mkdirs();
                                File themeFile = new File(themesDir, item.name + ".attheme");
                                
                                try (FileOutputStream out = new FileOutputStream(themeFile)) {
                                    out.write(response.body().bytes());
                                }
                                
                                AndroidUtilities.runOnUIThread(() -> {
                                    progressDialog.dismiss();
                                    Theme.ThemeInfo info = new Theme.ThemeInfo();
                                    info.name = item.name;
                                    info.pathToFile = themeFile.getAbsolutePath();
                                    info.previewBackgroundColor = item.previewBg;
                                    info.previewInColor = item.previewIn;
                                    info.previewOutColor = item.previewOut;
                                    
                                    // Apply the theme
                                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, info, false, null, -1);
                                    
                                    BulletinFactory.of(OnlineThemesActivity.this).createSimpleBulletin(R.raw.done, "Theme applied!").show();
                                    
                                    // Update visual selection
                                    for (ThemeItem t : themes) {
                                        t.isSelected = (t == item);
                                    }
                                    sortThemes();
                                    listAdapter.notifyDataSetChanged();
                                });
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();
                            BulletinFactory.of(OnlineThemesActivity.this).createSimpleBulletin(R.raw.error, "Failed to download theme").show();
                        });
                    }
                });
            }
        });

        return fragmentView;
    }

    private void loadThemes() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(jsonUrl).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().string();
                        JSONArray array = new JSONArray(content);
                        ArrayList<ThemeItem> newThemes = new ArrayList<>();
                        Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
                        
                        for (int i = 0; i < array.length(); i++) {
                            ThemeItem item = new ThemeItem(array.getJSONObject(i));
                            if (currentTheme != null && currentTheme.pathToFile != null) {
                                if (currentTheme.pathToFile.endsWith(item.name + ".attheme")) {
                                    item.isSelected = true;
                                }
                            }
                            newThemes.add(item);
                        }
                        
                        AndroidUtilities.runOnUIThread(() -> {
                            themes.clear();
                            themes.addAll(newThemes);
                            sortThemes();
                            showContent();
                        });
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }
    
    private void showContent() {
        if (loadingView != null && loadingView.getVisibility() == View.VISIBLE) {
            loadingView.animate().alpha(0).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loadingView.setVisibility(View.GONE);
                }
            }).start();
            
            listView.setVisibility(View.VISIBLE);
            listView.animate().alpha(1).setDuration(200).start();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void sortThemes() {
        Collections.sort(themes, (t1, t2) -> {
            if (t1.isSelected && !t2.isSelected) return -1;
            if (!t1.isSelected && t2.isSelected) return 1;
            return t1.name.compareToIgnoreCase(t2.name);
        });
    }

    private class ThemePreviewCell extends FrameLayout {
        private TextView nameTextView;
        private ThemeItem themeItem;
        private Drawable inDrawable;
        private Drawable outDrawable;
        private RectF rect = new RectF();
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ThemePreviewCell(Context context) {
            super(context);
            setWillNotDraw(false);
            
            inDrawable = context.getResources().getDrawable(R.drawable.minibubble_in).mutate();
            outDrawable = context.getResources().getDrawable(R.drawable.minibubble_out).mutate();
            
            selectionPaint.setStyle(Paint.Style.STROKE);
            selectionPaint.setStrokeWidth(AndroidUtilities.dp(3));
            selectionPaint.setColor(Theme.getColor(Theme.key_checkboxCheck));
            
            nameTextView = new TextView(context);
            nameTextView.setTextSize(13);
            nameTextView.setLines(1);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 5, 0, 5, 5));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = (int) (width * 1.35f);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        public void setThemeItem(ThemeItem item) {
            themeItem = item;
            nameTextView.setText(item.name);
            nameTextView.setTypeface(item.isSelected ? AndroidUtilities.getTypeface("fonts/rmedium.ttf") : null);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (themeItem == null) return;
            
            int x = AndroidUtilities.dp(10);
            int y = AndroidUtilities.dp(10);
            int w = getMeasuredWidth() - AndroidUtilities.dp(20);
            int h = getMeasuredHeight() - AndroidUtilities.dp(40);
            
            rect.set(x, y, x + w, y + h);
            
            // Draw background
            paint.setColor(themeItem.previewBg);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);
            
            // Draw selection border
            if (themeItem.isSelected) {
                selectionPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), selectionPaint);
            }
            
            // Bubbles
            Theme.setDrawableColor(inDrawable, themeItem.previewIn);
            inDrawable.setBounds(x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(12), x + AndroidUtilities.dp(6 + 40), y + AndroidUtilities.dp(12 + 15));
            inDrawable.draw(canvas);
            
            Theme.setDrawableColor(outDrawable, themeItem.previewOut);
            outDrawable.setBounds(x + w - AndroidUtilities.dp(6 + 40), y + AndroidUtilities.dp(35), x + w - AndroidUtilities.dp(6), y + AndroidUtilities.dp(35 + 15));
            outDrawable.draw(canvas);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return themes.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ThemePreviewCell cell = new ThemePreviewCell(mContext);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ThemePreviewCell view = (ThemePreviewCell) holder.itemView;
            view.setThemeItem(themes.get(position));
        }
    }
    
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(loadingView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        return themeDescriptions;
    }
}
