package org.telegram.ui.Templates;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class TemplateSettings {
    public final long id;
    public final String name;
    public final String text;
    public final long creationDate;
    public final int usageRating;
    public final ArrayList<String> messagePayloads;

    public TemplateSettings(long id, String name, String text, long creationDate, int usageRating) {
        this(id, name, text, creationDate, usageRating, null);
    }

    public TemplateSettings(long id, String name, String text, long creationDate, int usageRating, ArrayList<String> messagePayloads) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.text = text == null ? "" : text;
        this.creationDate = creationDate;
        this.usageRating = usageRating;
        this.messagePayloads = messagePayloads == null ? new ArrayList<>() : new ArrayList<>(messagePayloads);
    }

    public TemplateSettings withUsageIncremented() {
        return new TemplateSettings(id, name, text, creationDate, usageRating + 1, messagePayloads);
    }

    public TemplateSettings withValues(String newName, String newText) {
        return new TemplateSettings(id, newName, newText, creationDate, usageRating, messagePayloads);
    }

    public boolean hasMessages() {
        return messagePayloads != null && !messagePayloads.isEmpty();
    }

    public int getMessageCount() {
        return messagePayloads == null ? 0 : messagePayloads.size();
    }

    public ArrayList<MessageObject> toMessageObjects(int account) {
        ArrayList<MessageObject> result = new ArrayList<>();
        if (messagePayloads == null) {
            return result;
        }
        for (int i = 0; i < messagePayloads.size(); i++) {
            TLRPC.Message message = deserializeMessage(messagePayloads.get(i));
            if (message != null) {
                result.add(new MessageObject(account, message, false, true));
            }
        }
        return result;
    }

    public static String serializeMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner instanceof TLRPC.TL_messageService) {
            return "";
        }
        SerializedData data = null;
        try {
            data = new SerializedData();
            messageObject.messageOwner.serializeToStream(data);
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static TLRPC.Message deserializeMessage(String payload) {
        if (payload == null || payload.length() == 0) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(Base64.decode(payload, Base64.DEFAULT));
            return TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("text", text);
        object.put("creationDate", creationDate);
        object.put("usageRating", usageRating);
        if (messagePayloads != null && !messagePayloads.isEmpty()) {
            JSONArray messages = new JSONArray();
            for (int i = 0; i < messagePayloads.size(); i++) {
                messages.put(messagePayloads.get(i));
            }
            object.put("messages", messages);
        }
        return object;
    }

    static TemplateSettings fromJson(JSONObject object) {
        ArrayList<String> messagePayloads = new ArrayList<>();
        JSONArray messages = object.optJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                String payload = messages.optString(i, "");
                if (payload.length() > 0) {
                    messagePayloads.add(payload);
                }
            }
        }
        return new TemplateSettings(
                object.optLong("id"),
                object.optString("name"),
                object.optString("text"),
                object.optLong("creationDate"),
                object.optInt("usageRating"),
                messagePayloads
        );
    }
}
