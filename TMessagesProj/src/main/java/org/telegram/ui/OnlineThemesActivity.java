package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
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
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ThemeSmallPreviewView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineThemesActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ArrayList<ThemeItem> themes = new ArrayList<>();
    private String jsonUrl = "https://raw.githubusercontent.com/alexandeer1/Alexgram-Theme/main/themes.json";

    public static class ThemeItem {
        public String name;
        public String url;
        public int previewBg;
        public int previewIn;
        public int previewOut;

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

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 3));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
                        for (int i = 0; i < array.length(); i++) {
                            newThemes.add(new ThemeItem(array.getJSONObject(i)));
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            themes.clear();
                            themes.addAll(newThemes);
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
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
            View view = new ThemeSmallPreviewView(mContext, null, null, ThemeSmallPreviewView.TYPE_GRID);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(120)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ThemeSmallPreviewView view = (ThemeSmallPreviewView) holder.itemView;
            ThemeItem item = themes.get(position);
            
            // We need a ChatThemeItem to use ThemeSmallPreviewView easily, 
            // but we can also manually set colors if we modify ThemeSmallPreviewView or use a mock.
            // For now, let's create a minimal ThemeInfo for the preview.
            Theme.ThemeInfo info = new Theme.ThemeInfo();
            info.name = item.name;
            info.previewBackgroundColor = item.previewBg;
            info.previewInColor = item.previewIn;
            info.previewOutColor = item.previewOut;
            
            // To make ThemeSmallPreviewView work without a full ChatThemeItem, 
            // we might need to adjust it. But let's see if we can just set colors.
            // In Telegram's ThemeSmallPreviewView, it uses ThemeInfo or ChatThemeItem.
            view.setThemeInfo(info);
            view.setItemName(item.name);
        }
    }
    
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ThemeSmallPreviewView.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        return themeDescriptions;
    }
}
