package org.telegram.ui.Templates;

import org.json.JSONException;
import org.json.JSONObject;

public class TemplateSettings {
    public final long id;
    public final String name;
    public final String text;
    public final long creationDate;
    public final int usageRating;

    public TemplateSettings(long id, String name, String text, long creationDate, int usageRating) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.text = text == null ? "" : text;
        this.creationDate = creationDate;
        this.usageRating = usageRating;
    }

    public TemplateSettings withUsageIncremented() {
        return new TemplateSettings(id, name, text, creationDate, usageRating + 1);
    }

    public TemplateSettings withValues(String newName, String newText) {
        return new TemplateSettings(id, newName, newText, creationDate, usageRating);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("text", text);
        object.put("creationDate", creationDate);
        object.put("usageRating", usageRating);
        return object;
    }

    static TemplateSettings fromJson(JSONObject object) {
        return new TemplateSettings(
                object.optLong("id"),
                object.optString("name"),
                object.optString("text"),
                object.optLong("creationDate"),
                object.optInt("usageRating")
        );
    }
}
