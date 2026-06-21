package org.telegram.ui.Templates;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.NotificationsController;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class TemplatesManager {
    public enum SortingType {
        DATE,
        NAME,
        USAGE
    }

    public enum PanelType {
        OVAL,
        ATTACH,
        OFF
    }

    private static final String PREF_PREFIX = "alexgram_templates_";
    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_SORTING = "sorting";
    private static final String KEY_PANEL_TYPE = "panel_type";

    private static volatile TemplatesManager[] Instance = new TemplatesManager[UserConfig.MAX_ACCOUNT_COUNT];

    public static TemplatesManager getInstance(int account) {
        TemplatesManager local = Instance[account];
        if (local == null) {
            synchronized (TemplatesManager.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new TemplatesManager(account);
                }
            }
        }
        return local;
    }

    private final int currentAccount;
    private final SharedPreferences preferences;
    private final ArrayList<TemplateSettings> templates = new ArrayList<>();
    private boolean loaded;

    private TemplatesManager(int account) {
        currentAccount = account;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREF_PREFIX + account, Context.MODE_PRIVATE);
    }

    public ArrayList<TemplateSettings> getTemplates() {
        ensureLoaded();
        ArrayList<TemplateSettings> result = new ArrayList<>(templates);
        SortingType sortingType = getSortingType();
        if (sortingType == SortingType.NAME) {
            Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if (sortingType == SortingType.USAGE) {
            Collections.sort(result, (a, b) -> {
                int usage = Integer.compare(b.usageRating, a.usageRating);
                if (usage != 0) {
                    return usage;
                }
                return Long.compare(b.creationDate, a.creationDate);
            });
        } else {
            Collections.sort(result, Comparator.comparingLong(a -> -a.creationDate));
        }
        return result;
    }

    public SortingType getSortingType() {
        String value = preferences.getString(KEY_SORTING, SortingType.DATE.name());
        try {
            return SortingType.valueOf(value);
        } catch (Exception ignored) {
            return SortingType.DATE;
        }
    }

    public void setSortingType(SortingType sortingType) {
        if (sortingType == null) {
            sortingType = SortingType.DATE;
        }
        preferences.edit().putString(KEY_SORTING, sortingType.name()).apply();
        notifyUpdated();
    }

    public PanelType getPanelType() {
        String value = preferences.getString(KEY_PANEL_TYPE, PanelType.ATTACH.name());
        try {
            return PanelType.valueOf(value);
        } catch (Exception ignored) {
            return PanelType.ATTACH;
        }
    }

    public void setPanelType(PanelType panelType) {
        if (panelType == null) {
            panelType = PanelType.ATTACH;
        }
        preferences.edit().putString(KEY_PANEL_TYPE, panelType.name()).apply();
        notifyUpdated();
    }

    public TemplateSettings addTemplate(String name, String text) {
        return addTemplate(name, text, null);
    }

    public TemplateSettings addTemplate(String name, String text, ArrayList<String> messagePayloads) {
        ensureLoaded();
        TemplateSettings template = new TemplateSettings(
                preferences.getLong(KEY_NEXT_ID, 1),
                normalizeName(name),
                text == null ? "" : text,
                System.currentTimeMillis(),
                0,
                messagePayloads
        );
        templates.add(template);
        preferences.edit().putLong(KEY_NEXT_ID, template.id + 1).apply();
        save();
        notifyUpdated();

        // Cloud sync to templates private channel
        getOrCreateTemplatesChannel(channelId -> {
            if (channelId != 0) {
                if (messagePayloads != null && !messagePayloads.isEmpty()) {
                    ArrayList<MessageObject> messageObjects = template.toMessageObjects(currentAccount);
                    if (!messageObjects.isEmpty()) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(messageObjects, channelId, true, false, true, 0, 0, null, -1, 0, 0, null);
                    }
                } else if (!TextUtils.isEmpty(text)) {
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(
                            text, channelId, null, null, null, true, null, null, null, true, 0, 0, null, false
                    ));
                }
            }
        });

        return template;
    }

    public void updateTemplate(TemplateSettings template, String name, String text) {
        if (template == null) {
            return;
        }
        ensureLoaded();
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id == template.id) {
                templates.set(i, template.withValues(normalizeName(name), text));
                save();
                notifyUpdated();
                return;
            }
        }
    }

    public void deleteTemplate(long id) {
        ensureLoaded();
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id == id) {
                templates.remove(i);
                save();
                notifyUpdated();
                return;
            }
        }
    }

    public void incrementUsage(long id) {
        ensureLoaded();
        for (int i = 0; i < templates.size(); i++) {
            TemplateSettings template = templates.get(i);
            if (template.id == id) {
                templates.set(i, template.withUsageIncremented());
                save();
                notifyUpdated();
                return;
            }
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        templates.clear();
        String serialized = preferences.getString(KEY_TEMPLATES, "[]");
        try {
            JSONArray array = new JSONArray(serialized);
            for (int i = 0; i < array.length(); i++) {
                templates.add(TemplateSettings.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_TEMPLATES).apply();
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        for (TemplateSettings template : templates) {
            try {
                array.put(template.toJson());
            } catch (JSONException ignored) {
            }
        }
        preferences.edit().putString(KEY_TEMPLATES, array.toString()).apply();
    }

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return TextUtils.isEmpty(trimmed) ? "Template" : trimmed;
    }

    private void notifyUpdated() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.templatesSettingsUpdated));
    }

    private void muteChannel(long dialogId) {
        SharedPreferences notifPrefs = MessagesController.getNotificationsSettings(currentAccount);
        if (!notifPrefs.contains("notify2_" + dialogId)) {
            notifPrefs.edit().putInt("notify2_" + dialogId, 2).apply();
            NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialogId, 0, true);
        }
    }

    private boolean creatingChannel = false;
    private final ArrayList<Utilities.Callback<Long>> pendingCallbacks = new ArrayList<>();

    public void getOrCreateTemplatesChannel(Utilities.Callback<Long> callback) {
        long channelId = preferences.getLong("templates_channel_id", 0);
        if (channelId != 0) {
            callback.run(channelId);
            return;
        }
        synchronized (pendingCallbacks) {
            pendingCallbacks.add(callback);
            if (creatingChannel) {
                return;
            }
            creatingChannel = true;
        }

        TLRPC.TL_channels_createChannel req = new TLRPC.TL_channels_createChannel();
        req.title = "Alexgram Templates";
        req.about = "Private channel for Alexgram templates storage.";
        req.broadcast = true;
        
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            long resultChannelId = 0;
            if (error == null) {
                TLRPC.Updates updates = (TLRPC.Updates) response;
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                if (updates.chats != null && !updates.chats.isEmpty()) {
                    TLRPC.Chat chat = updates.chats.get(0);
                    resultChannelId = -chat.id;
                    preferences.edit().putLong("templates_channel_id", resultChannelId).apply();
                    muteChannel(resultChannelId);
                    uploadChannelAvatar(resultChannelId);
                }
            }
            final long finalChannelId = resultChannelId;
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<Utilities.Callback<Long>> callbacks;
                synchronized (pendingCallbacks) {
                    creatingChannel = false;
                    callbacks = new ArrayList<>(pendingCallbacks);
                    pendingCallbacks.clear();
                }
                for (Utilities.Callback<Long> cb : callbacks) {
                    cb.run(finalChannelId);
                }
            });
        });
    }

    private void uploadChannelAvatar(final long channelId) {
        try {
            android.graphics.drawable.Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(org.telegram.messenger.R.drawable.fork_templates);
            android.graphics.Bitmap bitmap;
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            } else {
                bitmap = android.graphics.Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            if (bitmap != null) {
                final TLRPC.PhotoSize bigPhoto = org.telegram.messenger.ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
                final TLRPC.PhotoSize smallPhoto = org.telegram.messenger.ImageLoader.scaleAndSaveImage(bitmap, 150, 150, 80, false, 150, 150);
                if (bigPhoto != null && smallPhoto != null) {
                    final String uploadingImage = org.telegram.messenger.FileLoader.getDirectory(org.telegram.messenger.FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
                    NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
                        @Override
                        public void didReceivedNotification(int id, int account, Object... args) {
                            if (id == NotificationCenter.fileUploaded) {
                                String location = (String) args[0];
                                if (location.equals(uploadingImage)) {
                                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
                                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
                                    TLRPC.InputFile uploadedPhoto = (TLRPC.InputFile) args[1];
                                    MessagesController.getInstance(currentAccount).changeChatAvatar(-channelId, null, uploadedPhoto, null, null, 0, null, smallPhoto.location, bigPhoto.location, null);
                                }
                            } else if (id == NotificationCenter.fileUploadFailed) {
                                String location = (String) args[0];
                                if (location.equals(uploadingImage)) {
                                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
                                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
                                }
                            }
                        }
                    };
                    NotificationCenter.getInstance(currentAccount).addObserver(observer, NotificationCenter.fileUploaded);
                    NotificationCenter.getInstance(currentAccount).addObserver(observer, NotificationCenter.fileUploadFailed);
                    org.telegram.messenger.FileLoader.getInstance(currentAccount).uploadFile(uploadingImage, false, true, ConnectionsManager.FileTypePhoto);
                }
            }
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    public long getTemplatesChannelId() {
        return preferences.getLong("templates_channel_id", 0);
    }
}
