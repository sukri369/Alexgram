package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.database.AyuData;
import com.radolyn.ayugram.messages.AyuMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;

import kotlin.Unit;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.PhotoAlbumPickerActivity;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.config.cell.WithOnClick;
import tw.nekomimi.nekogram.filters.RegexFiltersSettingActivity;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoExperimentalSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private AnimatorSet animatorSet;
    private boolean sensitiveCanChange = false;
    private boolean sensitiveEnabled = false;

    private MessageObject convertingVideo;
    private NotificationCenter.NotificationCenterDelegate videoConvertDelegate;

    private final CellGroup cellGroup = new CellGroup(this);

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell backAnimationStyleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getBackAnimationStyle(),
            Build.VERSION.SDK_INT >= 34 ? new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
                    getString(R.string.BackAnimationPredictive),
            } : new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
            }, null));
    private final AbstractConfigCell springAnimationCrossfadeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSpringAnimationCrossfade()));
    private final AbstractConfigCell saveToChatSubfolderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveToChatSubfolder()));
    private final AbstractConfigCell localPremiumRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.localPremium));
    private final AbstractConfigCell unlimitedPinnedDialogsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedPinnedDialogs, getString(R.string.UnlimitedPinnedDialogsAbout)));
    private final AbstractConfigCell unlimitedFavedStickersRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedFavedStickers, getString(R.string.UnlimitedFavoredStickersAbout)));
    private final AbstractConfigCell liquidGlassUIRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.liquidGlassUI, "Enable glass effect for the UI", getString(R.string.liquidGlassUI)));
    private final AbstractConfigCell iosStyleInputBarRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getIosStyleInputBar(), "Use an input bar design similar to iOS", getString(R.string.IosStyleInputBar)));
    private final AbstractConfigCell searchEngineInSearchBarRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSearchEngineInSearchBar(), "Add a Search tab to the search bar to search the web with your chosen engine", "Search Engines in Search Bar"));
    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());

    // Connections
    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Connection)));
    private final AbstractConfigCell boostUploadRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.uploadBoost));
    private final AbstractConfigCell enhancedFileLoaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enhancedFileLoader));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerMedia = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MediaSettings)));
    private final AbstractConfigCell musicGraphRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMusicGraph(), "Visualize audio playback", getString(R.string.MusicGraph)));
    private final AbstractConfigCell playAudio3DRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getPlayAudio3D(), "Experience 3D spatial audio (Experimental)", "Play Audio in 3D"));
    private final AbstractConfigCell hideContactsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideContacts(), "Remove the contacts item from the side menu", getString(R.string.HideContacts)));
    private final AbstractConfigCell audioEnhanceRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance()));
    private final AbstractConfigCell sendMp4DocumentAsVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendMp4DocumentAsVideo()));
    private final AbstractConfigCell enhancedVideoBitrateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnhancedVideoBitrate()));
    private final AbstractConfigCell customAudioBitrateRow = cellGroup.appendCell(new ConfigCellCustom("customGroupVoipAudioBitrate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell waveformSeekBarRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getWaveformSeekBar(), "Enable water-like animated waveform on seek bars", "Waveform Seek Bar"));
    private final AbstractConfigCell playerDecoderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPlayerDecoder(), new String[]{
            getString(R.string.VideoPlayerDecoderHardware),
            getString(R.string.VideoPlayerDecoderPreferHW),
            getString(R.string.VideoPlayerDecoderPreferSW),
    }, null));
    private final AbstractConfigCell dividerMedia = cellGroup.appendCell(new ConfigCellDivider());

    // Ayu
    private final AbstractConfigCell headerAyuMoments = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AyuMoments)));
    private final AbstractConfigCell GhostModeRow = cellGroup.appendCell(new ConfigCellText("GhostMode", () -> presentFragment(new GhostModeActivity())));
    private final AbstractConfigCell regexFiltersEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getRegexFiltersEnabled(), getString(R.string.RegexFiltersNotice)));
    private final AbstractConfigCell saveLastSeenRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveLocalLastSeen()));
    private final AbstractConfigCell runInBackgroundRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getRunInBackground(), getString(R.string.RunInBackgroundInfo)));
    private final AbstractConfigCell enableSaveDeletedMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSaveDeletedMessages()));
    private final AbstractConfigCell enableSaveEditsHistoryRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSaveEditsHistory()));
    private final AbstractConfigCell messageSavingSaveMediaRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMessageSavingSaveMedia(), getString(R.string.MessageSavingSaveMediaHint)));
    private final AbstractConfigCell saveDeletedMessageForBotsUserRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveDeletedMessageForBotUser()));
    private final AbstractConfigCell saveDeletedMessageInBotChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveDeletedMessageForBot()));
    private final AbstractConfigCell translucentDeletedMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getTranslucentDeletedMessages()));
    private final AbstractConfigCell useDeletedIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUseDeletedIcon()));
    private final AbstractConfigCell hideTabsOnScrollRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideTabsOnScroll()));
    private final AbstractConfigCell hideTabsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideTabs()));
    private final AbstractConfigCell customDeletedMarkRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomDeletedMark(), "", null));
    private final AbstractConfigCell clearMessageDatabaseRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "ClearMessageDatabase", null, AyuData.totalSize > 0 ? AndroidUtilities.formatFileSize(AyuData.totalSize) : "...", R.drawable.msg_clear, false, () -> new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(getString(R.string.ClearMessageDatabase))
            .setMessage(getString(R.string.AreYouSure))
            .setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
                AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.setCanCancel(false);
                progressDialog.show();
                Utilities.globalQueue.postRunnable(() -> {
                    AyuMessagesController.getInstance().clean();
                    AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ClearMessageDatabaseNotification)).show();
                    });
                    AyuData.loadSizes(this);
                });
            })
            .setNegativeButton(getString(R.string.Cancel), (d, w) -> d.dismiss())
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show()));
    private final AbstractConfigCell dividerAyuMoments = cellGroup.appendCell(new ConfigCellDivider());

    // N-Config
    private final AbstractConfigCell headerNConfig = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.N_Config)));
    private final AbstractConfigCell showRPCErrorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowRPCError()));
    private final AbstractConfigCell disableChoosingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableChoosingSticker));
    private final AbstractConfigCell disableFilteringRow = cellGroup.appendCell(new ConfigCellCustom("SensitiveDisableFiltering", CellGroup.ITEM_TYPE_TEXT_CHECK, true));
    private final AbstractConfigCell devicePerformanceClassRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPerformanceClass(), new String[]{
            getString(R.string.QualityAuto) + " [" + SharedConfig.getPerformanceClassName(SharedConfig.measureDevicePerformanceClass()) + "]",
            getString(R.string.PerformanceClassHigh),
            getString(R.string.PerformanceClassAverage),
            getString(R.string.PerformanceClassLow),
    }, null));
    private final AbstractConfigCell customArtworkApiRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomArtworkApi(), "", null));
    private final AbstractConfigCell dividerNConfig = cellGroup.appendCell(new ConfigCellDivider());

    // Story
    private final AbstractConfigCell headerStory = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Story)));
    private final AbstractConfigCell disableStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableStories()));
    
    // Custom wrapper for Hide from Header to handle side-effects
    private final ConfigItem hideFromHeaderConfigWrapper = new ConfigItem(NaConfig.INSTANCE.getHideStoriesFromHeader().getKey(), ConfigItem.configTypeBool, false) {
        @Override
        public boolean Bool() {
            return NaConfig.INSTANCE.getHideStoriesFromHeader().Bool();
        }

        @Override
        public boolean toggleConfigBool() {
            boolean newValue = !Bool();
            setConfigBool(newValue);
            return newValue;
        }

        @Override
        public void setConfigBool(boolean v) {
            NaConfig.INSTANCE.getHideStoriesFromHeader().setConfigBool(v);
            // REQUIREMENT: Video Header works ONLY when Hide Stories is ON.
            // If user turns Hide Stories OFF (v == false) -> Stories become visible -> Video Header must turn OFF.
            if (!v && NekoConfig.videoHeaderEnabled.Bool()) {
                 NekoConfig.videoHeaderEnabled.setConfigBool(false);
                 BulletinFactory.of(NekoExperimentalSettingsActivity.this).createSimpleBulletin(R.raw.ic_delete, "Live Video Header disabled because stories are now visible").show();
            }
        }
    };
    private final AbstractConfigCell hideFromHeaderRow = cellGroup.appendCell(new ConfigCellTextCheck(hideFromHeaderConfigWrapper));
    private final AbstractConfigCell dividerStory = cellGroup.appendCell(new ConfigCellDivider());

    // Pangu
    private final AbstractConfigCell headerPangu = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Pangu)));
    private final AbstractConfigCell enablePanguOnSendingRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnablePanguOnSending(), getString(R.string.PanguInfo)));
    private final AbstractConfigCell dividerPangu = cellGroup.appendCell(new ConfigCellDivider());

    // AI Assistance Help & Setup Section
    // Removed AI Assistance Setup Guide row as requested

    // AI Reply Section
    private final AbstractConfigCell headerAIReply = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.enableAIReply)));
    // Info/help row for Model URL/API Key (use ConfigCellText for compatibility)
    private final AbstractConfigCell aiInfoRow = cellGroup.appendCell(new ConfigCellText("Model URL & API Key Info", "Enter your provider's endpoint and secret key. Tap for help.", () -> {
        AndroidUtilities.runOnUIThread(() -> {
            new AlertDialog.Builder(getParentActivity())
                .setTitle("Model URL & API Key Setup Guide")
                .setMessage("To use AI Assistance, you need a Model URL and API Key. Please avoid using public APIs.\n\n" +
                    "1. Sign up at an AI provider (e.g., OpenAI, DeepSeek, Groq, Gemini, etc.).\n" +
                    "2. Find the API Keys section in your provider's dashboard.\n" +
                    "3. Create a new secret key and copy it.\n" +
                    "4. Copy the Model URL from your provider's API docs.\n" +
                    "5. Paste both into the fields below.\n\n" +
                    "Need help? Tap 'Go to AI Reply Settings' to jump to the configuration section.")
                .setPositiveButton("Go to AI Reply Settings", (dialog, which) -> {
                    // Open external link as requested
                    Context ctx = getParentActivity();
                    if (ctx != null) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/alexsettings/experimental?r=aiModelUrl"));
                            ctx.startActivity(intent);
                        } catch (Exception e) {
                            // fallback: show a message
                            new AlertDialog.Builder(ctx)
                                .setTitle("Navigation Failed")
                                .setMessage("Please open https://t.me/alexsettings/experimental?r=aiModelUrl manually in your browser.")
                                .setPositiveButton("OK", null)
                                .show();
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
        });
    }));
    private final AbstractConfigCell enableAIReplyRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableAIReply(), "Suggest replies using AI", getString(R.string.enableAIReply)));
    private final AbstractConfigCell enableSummarizeChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSummarizeChat(), "Generate chat summaries using AI", getString(R.string.enableSummarizeChat)));
    private final AbstractConfigCell aiModelUrlRow = cellGroup.appendCell(new ConfigCellTextInput("Model URL 1", NaConfig.INSTANCE.getAiModelUrl(), "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash", null));
    private final AbstractConfigCell aiApiKeyRow = cellGroup.appendCell(new ConfigCellTextInput("API Key 1", NaConfig.INSTANCE.getAiApiKey(), "Api Key", null));
    // Add universal Test API 1 button
    private final AbstractConfigCell testApi1Row = cellGroup.appendCell(new ConfigCellText("Test API 1", () -> {
        String url = NaConfig.INSTANCE.getAiModelUrl().String();
        String key = NaConfig.INSTANCE.getAiApiKey().String();
        Context ctx = getParentActivity();
        if (ctx == null) return;
        AlertDialog progressDialog = new AlertDialog(ctx, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.setMessage("Testing API, please wait...");
        progressDialog.show();
        new Thread(() -> {
            String msg;
            boolean success = false;
            try {
                String urlLower = url.toLowerCase();
                if (urlLower.contains("generativelanguage.googleapis.com")) { // Gemini
                    // Gemini expects API key as query param, not Bearer header
                    String endpoint = url.endsWith(":generateContent") ? url : (url.endsWith("/") ? url.substring(0, url.length()-1) : url) + ":generateContent";
                    if (!endpoint.contains("?key=")) {
                        endpoint += (endpoint.contains("?") ? "&" : "?") + "key=" + key;
                    }
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                    org.json.JSONObject req = new org.json.JSONObject();
                    org.json.JSONArray contents = new org.json.JSONArray();
                    org.json.JSONObject contentObj = new org.json.JSONObject();
                    org.json.JSONArray parts = new org.json.JSONArray();
                    org.json.JSONObject textPart = new org.json.JSONObject();
                    textPart.put("text", "Test");
                    parts.put(textPart);
                    contentObj.put("parts", parts);
                    contents.put(contentObj);
                    req.put("contents", contents);
                    okhttp3.RequestBody body = okhttp3.RequestBody.create(req.toString(), tw.nekomimi.nekogram.utils.HttpClient.MEDIA_TYPE_JSON);
                    okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .build();
                    try (okhttp3.Response response = client.newCall(request).execute()) {
                        int code = response.code();
                        String resp = response.body().string();
                        if (code >= 200 && code < 300) {
                            msg = "Gemini API Test Success! Response: " + resp;
                            success = true;
                        } else {
                            msg = "Gemini API Test Failed: HTTP " + code + "\n" + resp;
                        }
                    }
                } else if (urlLower.contains("openai.com") || urlLower.contains("groq.com") || urlLower.contains("deepseek.com") || urlLower.contains("x.ai") || urlLower.contains("cerebras.ai") || urlLower.contains("openrouter.ai") || urlLower.contains("vercel.sh") || urlLower.contains("ollama.com")) {
                    // OpenAI-compatible: try fetchModels, fallback to chat completion
                    var res = tw.nekomimi.nekogram.llm.net.OpenAICompatClient.fetchModels(url, key);
                    if (res.isSuccess()) {
                        msg = "API 1 Test Success! Models: " + (res.data() != null ? res.data().toString() : "<none>");
                        success = true;
                    } else {
                        // Try chat completion with a test prompt
                        var chatRes = tw.nekomimi.nekogram.llm.net.OpenAICompatClient.testChatCompletions(url, key, "gpt-3.5-turbo");
                        if (chatRes.isSuccess()) {
                            msg = "API 1 Chat Test Success! Response: " + chatRes.data();
                            success = true;
                        } else {
                            msg = "API 1 Test Failed: " + res.error() + "\nChat Test: " + chatRes.error();
                        }
                    }
                } else {
                    // Unknown: try generic chat completion
                    var chatRes = tw.nekomimi.nekogram.llm.net.OpenAICompatClient.testChatCompletions(url, key, "gpt-3.5-turbo");
                    if (chatRes.isSuccess()) {
                        msg = "API 1 Generic Chat Test Success! Response: " + chatRes.data();
                        success = true;
                    } else {
                        msg = "API 1 Test Failed (Unknown Provider): " + chatRes.error();
                    }
                }
            } catch (Throwable e) {
                msg = "API 1 Test Exception: " + e.getMessage();
            }
            boolean finalSuccess = success;
            String finalMsg = msg;
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                String title = finalSuccess ? "Test API 1 Result (Success)" : "Test API 1 Result (Failed)";
                int color = finalSuccess ? android.graphics.Color.parseColor("#2ecc40") : android.graphics.Color.parseColor("#ff3b30");
                android.text.SpannableString sTitle = new android.text.SpannableString(title);
                sTitle.setSpan(new android.text.style.ForegroundColorSpan(color), 0, title.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                new AlertDialog.Builder(ctx)
                        .setTitle(sTitle)
                        .setMessage(finalMsg)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }).start();
    }));
    private final AbstractConfigCell aiModelUrl2Row = cellGroup.appendCell(new ConfigCellTextInput("Model URL 2 (Failover)", NaConfig.INSTANCE.getAiModelUrl2(), "https://api.openai.com/v1/", null));
    private final AbstractConfigCell aiApiKey2Row = cellGroup.appendCell(new ConfigCellTextInput("API Key 2 (Failover)", NaConfig.INSTANCE.getAiApiKey2(), "sk-...", null));
    // Add universal Test API 2 button
    private final AbstractConfigCell testApi2Row = cellGroup.appendCell(new ConfigCellText("Test API 2", () -> {
        String url = NaConfig.INSTANCE.getAiModelUrl2().String();
        String key = NaConfig.INSTANCE.getAiApiKey2().String();
        Context ctx = getParentActivity();
        if (ctx == null) return;
        AlertDialog progressDialog = new AlertDialog(ctx, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.setMessage("Testing API, please wait...");
        progressDialog.show();
        new Thread(() -> {
            String msg;
            boolean success = false;
            try {
                String urlLower = url.toLowerCase();
                if (urlLower.contains("generativelanguage.googleapis.com")) { // Gemini
                    String endpoint = url.endsWith(":generateContent") ? url : (url.endsWith("/") ? url.substring(0, url.length()-1) : url) + ":generateContent";
                    if (!endpoint.contains("?key=")) {
                        endpoint += (endpoint.contains("?") ? "&" : "?") + "key=" + key;
                    }
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                    org.json.JSONObject req = new org.json.JSONObject();
                    org.json.JSONArray contents = new org.json.JSONArray();
                    org.json.JSONObject contentObj = new org.json.JSONObject();
                    org.json.JSONArray parts = new org.json.JSONArray();
                    org.json.JSONObject textPart = new org.json.JSONObject();
                    textPart.put("text", "Test");
                    parts.put(textPart);
                    contentObj.put("parts", parts);
                    contents.put(contentObj);
                    req.put("contents", contents);
                    okhttp3.RequestBody body = okhttp3.RequestBody.create(req.toString(), tw.nekomimi.nekogram.utils.HttpClient.MEDIA_TYPE_JSON);
                    okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(endpoint)
                        .post(body)
                        .build();
                    try (okhttp3.Response response = client.newCall(request).execute()) {
                        int code = response.code();
                        String resp = response.body().string();
                        if (code >= 200 && code < 300) {
                            msg = "Gemini API Test Success! Response: " + resp;
                            success = true;
                        } else {
                            msg = "Gemini API Test Failed: HTTP " + code + "\n" + resp;
                        }
                    }
                } else if (urlLower.contains("openai.com") || urlLower.contains("groq.com") || urlLower.contains("deepseek.com") || urlLower.contains("x.ai") || urlLower.contains("cerebras.ai") || urlLower.contains("openrouter.ai") || urlLower.contains("vercel.sh") || urlLower.contains("ollama.com")) {
                    var res = tw.nekomimi.nekogram.llm.net.OpenAICompatClient.fetchModels(url, key);
                    if (res.isSuccess()) {
                        msg = "API 2 Test Success! Models: " + (res.data() != null ? res.data().toString() : "<none>");
                        success = true;
                    } else {
                        var chatRes = tw.nekomimi.nekogram.llm.net.OpenAICompatClient.testChatCompletions(url, key, "gpt-3.5-turbo");
                        if (chatRes.isSuccess()) {
                            msg = "API 2 Chat Test Success! Response: " + chatRes.data();
                            success = true;
                        } else {
                            msg = "API 2 Test Failed: " + res.error() + "\nChat Test: " + chatRes.error();
                        }
                    }
                } else {
                    var chatRes = tw.nekomimi.nekogram.llm.net.OpenAICompatClient.testChatCompletions(url, key, "gpt-3.5-turbo");
                    if (chatRes.isSuccess()) {
                        msg = "API 2 Generic Chat Test Success! Response: " + chatRes.data();
                        success = true;
                    } else {
                        msg = "API 2 Test Failed (Unknown Provider): " + chatRes.error();
                    }
                }
            } catch (Throwable e) {
                msg = "API 2 Test Exception: " + e.getMessage();
            }
            boolean finalSuccess = success;
            String finalMsg = msg;
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                String title = finalSuccess ? "Test API 2 Result (Success)" : "Test API 2 Result (Failed)";
                int color = finalSuccess ? android.graphics.Color.parseColor("#2ecc40") : android.graphics.Color.parseColor("#ff3b30");
                android.text.SpannableString sTitle = new android.text.SpannableString(title);
                sTitle.setSpan(new android.text.style.ForegroundColorSpan(color), 0, title.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                new AlertDialog.Builder(ctx)
                        .setTitle(sTitle)
                        .setMessage(finalMsg)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }).start();
    }));

    // Remove old help row (replaced by new setup guide and info row)

    // Helper: Scroll to AI Reply section
    private void scrollToAIRelaySection() {
        // Try to scroll to the AI Reply header row
        int index = cellGroup.rows.indexOf(headerAIReply);
        if (index >= 0 && listView != null) {
            listView.smoothScrollToPosition(index);
        }
    }

    public NekoExperimentalSettingsActivity() {
        if (NaConfig.INSTANCE.getUseDeletedIcon().Bool()) {
            cellGroup.rows.remove(customDeletedMarkRow);
        }
        if (!NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool()) {
            cellGroup.rows.remove(saveDeletedMessageInBotChatRow);
        }
        if (NaConfig.INSTANCE.getBackAnimationStyle().Int() != ActionBarLayout.BACK_ANIMATION_SPRING) {
            cellGroup.rows.remove(springAnimationCrossfadeRow);
        }
        checkStoriesRows();
        checkUseDeletedIconRows();
        checkSaveBotMsgRows();
        checkSaveDeletedRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        AyuData.loadSizes(this);

        return true;
    }

    @Override
    protected BlurredRecyclerView createListView(Context context) {
        return new BlurredRecyclerView(context) {
            @Override
            public Integer getSelectorColor(int position) {
                if (position == cellGroup.rows.indexOf(clearMessageDatabaseRow)) {
                    return Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .1f);
                }
                return getThemedColor(Theme.key_listSelector);
            }
        };
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);


        // Fragment: Set OnClick Callbacks
        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof ConfigCellTextCheck) {
                if (position == cellGroup.rows.indexOf(musicGraphRow)) {
                    if (!NaConfig.INSTANCE.getMusicGraph().Bool()) {
                         if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                             getParentActivity().requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
                             return;
                         }
                    }
                }
                if (position == cellGroup.rows.indexOf(regexFiltersEnabledRow) && (LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76)))) {
                    presentFragment(new RegexFiltersSettingActivity());
                    return;
                }
                if (position == cellGroup.rows.indexOf(messageSavingSaveMediaRow) && (LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76)))) {
                    showBottomSheet();
                    return;
                }
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
            } else if (a instanceof WithOnClick) {
                ((WithOnClick) a).onClick();
            } else if (a instanceof ConfigCellTextInput) {
                ((ConfigCellTextInput) a).onClick();
            } else if (a instanceof ConfigCellTextDetail) {
                RecyclerListView.OnItemClickListener o = ((ConfigCellTextDetail) a).onItemClickListener;
                if (o != null) {
                    try {
                        o.onItemClick(view, position);
                    } catch (Exception ignored) {
                    }
                }
            } else if (a instanceof ConfigCellCustom) { // Custom onclick
                if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                    sensitiveEnabled = !sensitiveEnabled;
                    TL_account.setContentSettings req = new TL_account.setContentSettings();
                    req.sensitive_enabled = sensitiveEnabled;
                    AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.show();
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        if (error == null) {
                            if (response instanceof TLRPC.TL_boolTrue && view instanceof TextCheckCell) {
                                ((TextCheckCell) view).setChecked(sensitiveEnabled);
                            }
                        } else {
                            AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
                        }
                    }));
                } else if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
                    PopupBuilder builder = new PopupBuilder(view);
                    builder.setItems(new String[]{
                            "32 (" + getString(R.string.Default) + ")",
                            "64",
                            "128",
                            "192",
                            "256",
                            "320"
                    }, (i, __) -> {
                        switch (i) {
                            case 0:
                                NekoConfig.customAudioBitrate.setConfigInt(32);
                                break;
                            case 1:
                                NekoConfig.customAudioBitrate.setConfigInt(64);
                                break;
                            case 2:
                                NekoConfig.customAudioBitrate.setConfigInt(128);
                                break;
                            case 3:
                                NekoConfig.customAudioBitrate.setConfigInt(192);
                                break;
                            case 4:
                                NekoConfig.customAudioBitrate.setConfigInt(256);
                                break;
                            case 5:
                                NekoConfig.customAudioBitrate.setConfigInt(320);
                                break;
                        }
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                    builder.show();
                }
            } else if (a instanceof ConfigCellTextCheckIcon) {
                ((ConfigCellTextCheckIcon) a).onClick();
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoExperimentalSettingsActivity.this, "experimental", position);
                return true;
            }
            return false;
        });

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getMusicGraph().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideContacts().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getRunInBackground().getKey())) {
                updateRunInBackground((Boolean) newValue);
            } else if (key.equals(NaConfig.INSTANCE.getEnableSaveDeletedMessages().getKey())) {
                checkSaveDeletedRows();
            } else if (key.equals(NaConfig.INSTANCE.getDisableStories().getKey())) {
                checkStoriesRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.localPremium.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NaConfig.INSTANCE.getUseDeletedIcon().getKey())) {
                checkUseDeletedIconRows();
            } else if (key.equals(NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().getKey())) {
                checkSaveBotMsgRows();
            } else if (key.equals(NaConfig.INSTANCE.getBackAnimationStyle().getKey())) {
                final int style = (int) newValue;
                if (style != ActionBarLayout.BACK_ANIMATION_SPRING) {
                    if (cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(springAnimationCrossfadeRow);
                        cellGroup.rows.remove(springAnimationCrossfadeRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(backAnimationStyleRow) + 1;
                        cellGroup.rows.add(index, springAnimationCrossfadeRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSpringAnimationCrossfade().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPerformanceClass().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPlayerDecoder().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideStoriesFromHeader().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideTabs().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSearchEngineInSearchBar().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPlayAudio3D().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        //Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        return superView;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            checkSensitive();
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public int getBaseGuid() {
        return 11000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_fave;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Experimental);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }

    private void checkSensitive() {
        TL_account.getContentSettings req = new TL_account.getContentSettings();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TL_account.contentSettings settings = (TL_account.contentSettings) response;
                sensitiveEnabled = settings.sensitive_enabled;
                sensitiveCanChange = settings.sensitive_can_change;
                int count = listView.getChildCount();
                ArrayList<Animator> animators = new ArrayList<>();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
                    int position = holder.getAdapterPosition();
                    if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                        TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                        checkCell.setChecked(sensitiveEnabled);
                        checkCell.setEnabled(sensitiveCanChange, animators);
                        if (sensitiveCanChange) {
                            if (!animators.isEmpty()) {
                                if (animatorSet != null) {
                                    animatorSet.cancel();
                                }
                                animatorSet = new AnimatorSet();
                                animatorSet.playTogether(animators);
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animator) {
                                        if (animator.equals(animatorSet)) {
                                            animatorSet = null;
                                        }
                                    }
                                });
                                animatorSet.setDuration(150);
                                animatorSet.start();
                            }
                        }
                    }
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
            }
        }));
    }

    //impl ListAdapter
    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return cellGroup.rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.isEnabled();
            }
            return true;
        }

        @Override
        public int getItemViewType(int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.getType();
            }
            return CellGroup.ITEM_TYPE_TEXT_DETAIL;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    // Custom binds
                    if (holder.itemView instanceof TextCheckCell textCheckCell) {
                        textCheckCell.setEnabled(true, null);
                        if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                            textCheckCell.setTextAndValueAndCheck(getString(R.string.SensitiveDisableFiltering), getString(R.string.SensitiveAbout), sensitiveEnabled, true, true);
                            textCheckCell.setEnabled(sensitiveCanChange, null);
                        }
                    } else if (holder.itemView instanceof TextSettingsCell textSettingsCell) {
                        textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
                            String value = NekoConfig.customAudioBitrate.Int() + "kbps";
                            if (NekoConfig.customAudioBitrate.Int() == 32)
                                value += " (" + getString(R.string.Default) + ")";
                            textSettingsCell.setTextAndValue(getString(R.string.customGroupVoipAudioBitrate), value, true);
                        }
                    }
                } else {
                    // Default binds
                    a.onBindViewHolder(holder);
                    if (a instanceof ConfigCellTextCheckIcon) {
                        if (holder.itemView instanceof TextCell textCell) {
                            if (position == cellGroup.rows.indexOf(clearMessageDatabaseRow)) {
                                textCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                            }
                        }
                    }
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case CellGroup.ITEM_TYPE_DIVIDER:
                    view = new ShadowSectionCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Color.TRANSPARENT);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Color.TRANSPARENT);
                    break;
                case CellGroup.ITEM_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Color.TRANSPARENT);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_DETAIL:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Color.TRANSPARENT);
                    break;
                case CellGroup.ITEM_TYPE_TEXT:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Color.TRANSPARENT);
                    break;
            }
            //noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private void showBottomSheet() {
        if (getParentActivity() == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
        builder.setApplyTopPadding(false);
        builder.setApplyBottomPadding(false);
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setCustomView(linearLayout);

        HeaderCell headerCell = new HeaderCell(getParentActivity(), Theme.key_dialogTextBlue2, 21, 15, false);
        headerCell.setText(getString(R.string.MessageSavingSaveMedia).toUpperCase());
        linearLayout.addView(headerCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckBoxCell[] cells = new TextCheckBoxCell[5];
        for (int a = 0; a < cells.length; a++) {
            TextCheckBoxCell checkBoxCell = cells[a] = new TextCheckBoxCell(getParentActivity(), true, false);
            if (a == 0) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPrivateChats), NaConfig.INSTANCE.getSaveMediaInPrivateChats().Bool(), true);
            } else if (a == 1) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPublicChannels), NaConfig.INSTANCE.getSaveMediaInPublicChannels().Bool(), true);
            } else if (a == 2) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPrivateChannels), NaConfig.INSTANCE.getSaveMediaInPrivateChannels().Bool(), true);
            } else if (a == 3) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPublicGroups), NaConfig.INSTANCE.getSaveMediaInPublicGroups().Bool(), true);
            } else { // a == 4
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPrivateGroups), NaConfig.INSTANCE.getSaveMediaInPrivateGroups().Bool(), true);
            }
            cells[a].setBackground(Theme.getSelectorDrawable(false));
            cells[a].setOnClickListener(v -> {
                if (!v.isEnabled()) {
                    return;
                }
                checkBoxCell.setChecked(!checkBoxCell.isChecked());
            });
            linearLayout.addView(cells[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50));
        }

        FrameLayout buttonsLayout = new FrameLayout(getParentActivity());
        buttonsLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        TextView textView = new TextView(getParentActivity());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setText(getString(R.string.Cancel).toUpperCase());
        textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));
        textView.setOnClickListener(v14 -> builder.getDismissRunnable().run());

        textView = new TextView(getParentActivity());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setText(getString(R.string.Save).toUpperCase());
        textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
        textView.setOnClickListener(v1 -> {
            NaConfig.INSTANCE.getSaveMediaInPrivateChats().setConfigBool(cells[0].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPublicChannels().setConfigBool(cells[1].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPrivateChannels().setConfigBool(cells[2].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPublicGroups().setConfigBool(cells[3].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPrivateGroups().setConfigBool(cells[4].isChecked());

            builder.getDismissRunnable().run();
        });
        showDialog(builder.create());
    }

    public void refreshAyuDataSize() {
        if (listAdapter != null) {
            ((ConfigCellTextCheckIcon) clearMessageDatabaseRow).setValue(AyuData.totalSize > 0 ? AndroidUtilities.formatFileSize(AyuData.totalSize) : "...");
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(clearMessageDatabaseRow));
        }
    }

    private void checkStoriesRows() {
        boolean disabled = NaConfig.INSTANCE.getDisableStories().Bool();
        if (listAdapter == null) {
            if (disabled) {
                cellGroup.rows.remove(hideFromHeaderRow);
            }
            return;
        }
        if (!disabled) {
            final int index = cellGroup.rows.indexOf(disableStoriesRow);
            if (!cellGroup.rows.contains(hideFromHeaderRow)) {
                cellGroup.rows.add(index + 1, hideFromHeaderRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(hideFromHeaderRow);
            if (index != -1) {
                cellGroup.rows.remove(hideFromHeaderRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
    }

    private void updateRunInBackground(boolean enabled) {
        MessagesController.getGlobalNotificationsSettings().edit()
            .putBoolean("pushService", enabled)
            .putBoolean("pushConnection", enabled)
            .apply();

        MessagesController.getGlobalMainSettings().edit()
            .putBoolean("keepAliveService", enabled)
            .putBoolean("backgroundConnection", enabled)
            .apply();

        for (int i = 0; i < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; i++) {
            SharedPreferences notificationsSettings = MessagesController.getNotificationsSettings(i);
            notificationsSettings.edit()
                .putBoolean("pushService", enabled)
                .putBoolean("pushConnection", enabled)
                .apply();

            SharedPreferences mainSettings = MessagesController.getMainSettings(i);
            mainSettings.edit()
                .putBoolean("keepAliveService", enabled)
                .putBoolean("backgroundConnection", enabled)
                .apply();

            MessagesController.getInstance(i).keepAliveService = enabled;
            MessagesController.getInstance(i).backgroundConnection = enabled;
            ConnectionsManager.getInstance(i).setPushConnectionEnabled(enabled);
        }

        Intent serviceIntent = new Intent(ApplicationLoader.applicationContext, org.telegram.messenger.NotificationsService.class);
        if (!enabled) {
            ApplicationLoader.applicationContext.stopService(serviceIntent);
        }
        ApplicationLoader.startPushService();

        // Request all necessary permissions and settings when enabling background run
        if (enabled && getParentActivity() != null) {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            if (!prefs.getBoolean("asked_background_permissions", false)) {
                AndroidUtilities.requestIgnoreBatteryOptimizations(getParentActivity());
                AndroidUtilities.requestNotificationPermission(getParentActivity());
                
                Intent autoStart = AndroidUtilities.getAutoStartIntent();
                if (autoStart != null) {
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle("Background Reliability")
                        .setMessage("To ensure the background service works perfectly, please enable Auto-Start or Background Activity in your device settings. Allow battery optimizations first if prompted.")
                        .setPositiveButton("Open Settings", (dialog, which) -> {
                            try { getParentActivity().startActivity(autoStart); } catch (Exception ignored) {}
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle("Background Reliability")
                        .setMessage("To ensure background features work reliably, please enable auto-start or background run for this app in your device settings.")
                        .setPositiveButton("OK", null)
                        .show();
                }
                prefs.edit().putBoolean("asked_background_permissions", true).apply();
            }
        }
    }

    private void checkSaveDeletedRows() {
        final boolean isSaveEnabled = NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool();
        final List<AbstractConfigCell> allManagedRows = Arrays.asList(
                messageSavingSaveMediaRow,
                saveDeletedMessageForBotsUserRow,
                saveDeletedMessageInBotChatRow,
                translucentDeletedMessagesRow,
                useDeletedIconRow,
                customDeletedMarkRow
        );
        if (listAdapter == null) {
            if (!isSaveEnabled) {
                cellGroup.rows.removeAll(allManagedRows);
            }
            return;
        }
        final int anchorIndex = cellGroup.rows.indexOf(enableSaveEditsHistoryRow);
        int firstManagedRowIndex = -1;
        int lastManagedRowIndex = -1;
        for (int i = anchorIndex + 1; i < cellGroup.rows.size(); i++) {
            if (allManagedRows.contains(cellGroup.rows.get(i))) {
                if (firstManagedRowIndex == -1) {
                    firstManagedRowIndex = i;
                }
                lastManagedRowIndex = i;
            }
        }
        if (firstManagedRowIndex != -1) {
            int count = lastManagedRowIndex - firstManagedRowIndex + 1;
            cellGroup.rows.subList(firstManagedRowIndex, lastManagedRowIndex + 1).clear();
            listAdapter.notifyItemRangeRemoved(firstManagedRowIndex, count);
        }
        if (isSaveEnabled) {
            final List<AbstractConfigCell> rowsToAdd = new ArrayList<>();
            rowsToAdd.add(messageSavingSaveMediaRow);
            rowsToAdd.add(saveDeletedMessageForBotsUserRow);
            if (NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool()) {
                rowsToAdd.add(saveDeletedMessageInBotChatRow);
            }
            rowsToAdd.add(translucentDeletedMessagesRow);
            rowsToAdd.add(useDeletedIconRow);
            if (!NaConfig.INSTANCE.getUseDeletedIcon().Bool()) {
                rowsToAdd.add(customDeletedMarkRow);
            }
            cellGroup.rows.addAll(anchorIndex + 1, rowsToAdd);
            listAdapter.notifyItemRangeInserted(anchorIndex + 1, rowsToAdd.size());
        }
        addRowsToMap(cellGroup);
    }

    private void checkSaveBotMsgRows() {
        boolean enabled = NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool();
        if (listAdapter == null) {
            if (!enabled) {
                cellGroup.rows.remove(saveDeletedMessageInBotChatRow);
            }
            return;
        }
        if (enabled) {
            final int index = cellGroup.rows.indexOf(saveDeletedMessageForBotsUserRow);
            if (!cellGroup.rows.contains(saveDeletedMessageInBotChatRow)) {
                cellGroup.rows.add(index + 1, saveDeletedMessageInBotChatRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(saveDeletedMessageInBotChatRow);
            if (index != -1) {
                cellGroup.rows.remove(saveDeletedMessageInBotChatRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkUseDeletedIconRows() {
        boolean enabled = NaConfig.INSTANCE.getUseDeletedIcon().Bool();
        if (listAdapter == null) {
            if (enabled) {
                cellGroup.rows.remove(customDeletedMarkRow);
            }
            return;
        }
        if (!enabled) {
            final int index = cellGroup.rows.indexOf(useDeletedIconRow);
            if (!cellGroup.rows.contains(customDeletedMarkRow)) {
                cellGroup.rows.add(index + 1, customDeletedMarkRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(customDeletedMarkRow);
            if (index != -1) {
                cellGroup.rows.remove(customDeletedMarkRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
        addRowsToMap(cellGroup);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 NaConfig.INSTANCE.getMusicGraph().setConfigBool(true);
                 if (listAdapter != null) {
                     listAdapter.notifyItemChanged(cellGroup.rows.indexOf(musicGraphRow));
                 }
                 try {
                     // Since tooltip might not be accessible or initialized as a field, we skip it or use a safer way if available.
                     // The config changed callback handles the tooltip usually, but since we are modifying directly, we might miss it.
                     // However, the main requirement is enabling it.
                 } catch (Exception e) {}
            } else {
                if (getParentActivity() != null && !getParentActivity().shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle("Permission Required")
                            .setMessage("Go to settings-Apps-Alexgram give permission to allow microphone")
                            .setPositiveButton("Settings", (dialog, which) -> {
                                try {
                                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(android.net.Uri.parse("package:" + org.telegram.messenger.ApplicationLoader.applicationContext.getPackageName()));
                                    getParentActivity().startActivity(intent);
                                } catch (Exception ignore) {
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        }
    }
}
