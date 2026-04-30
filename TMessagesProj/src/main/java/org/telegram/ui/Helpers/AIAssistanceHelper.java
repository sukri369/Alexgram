package org.telegram.ui.Helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatAnimeAssistantView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import xyz.nextalone.nagram.NaConfig;

public class AIAssistanceHelper {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                okhttp3.Response response = chain.proceed(request);
                int tryCount = 0;
                while (!response.isSuccessful() && response.code() >= 502 && response.code() <= 504 && tryCount < 3) {
                    tryCount++;
                    response.close();
                    try { Thread.sleep(2000); } catch (Exception ignored) {}
                    response = chain.proceed(request);
                }
                return response;
            })
            .build();

    public static class HistoryItem {
        public final String text;
        public final boolean isUser;
        public HistoryItem(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }

    public interface AiGenerationCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void requestReply(int currentAccount, String prompt, String chatContext, ChatAnimeAssistantView.AssistantRequestCallback callback) {
        requestReply(currentAccount, prompt, chatContext, false, null, null, callback);
    }

    public static void requestReply(int currentAccount, String prompt, String chatContext, boolean isSummarize, File imageFile, ChatAnimeAssistantView.AssistantRequestCallback callback) {
        requestReply(currentAccount, prompt, chatContext, isSummarize, imageFile, null, callback);
    }

    public static void requestReply(int currentAccount, String prompt, String chatContext, boolean isSummarize, File imageFile, List<HistoryItem> history, ChatAnimeAssistantView.AssistantRequestCallback callback) {
        final String decoratedPrompt = prompt;

        if (NaConfig.INSTANCE.getUsePollinationsAi().Bool()) {
            callAiApi("https://text.pollinations.ai/v1/chat/completions", null, "openai", decoratedPrompt, chatContext, isSummarize, imageFile, history, new AiGenerationCallback() {
                @Override
                public void onSuccess(String result) {
                    AndroidUtilities.runOnUIThread(() -> callback.onSuccess(result));
                }

                @Override
                public void onError(String error) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                }
            });
            return;
        }

        final String url1;
        final String key1;

        try {
            url1 = NaConfig.INSTANCE.getAiModelUrl().String();
            key1 = NaConfig.INSTANCE.getAiApiKey().String();
        } catch (Throwable e) {
            AndroidUtilities.runOnUIThread(() -> callback.onError("Config loading failed: " + e.getMessage()));
            return;
        }

        callAiApi(url1, key1, "gpt-4o", decoratedPrompt, chatContext, isSummarize, imageFile, history, new AiGenerationCallback() {
            @Override
            public void onSuccess(String result) {
                AndroidUtilities.runOnUIThread(() -> callback.onSuccess(result));
            }

            @Override
            public void onError(String error) {
                try {
                    String url2 = NaConfig.INSTANCE.getAiModelUrl2().String();
                    String key2 = NaConfig.INSTANCE.getAiApiKey2().String();
                    if (TextUtils.isEmpty(url2)) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                        return;
                    }

                    callAiApi(url2, key2, "gpt-4o", decoratedPrompt, chatContext, isSummarize, imageFile, history, new AiGenerationCallback() {
                        @Override
                        public void onSuccess(String result) {
                            AndroidUtilities.runOnUIThread(() -> callback.onSuccess(result));
                        }

                        @Override
                        public void onError(String fallbackError) {
                            AndroidUtilities.runOnUIThread(() -> callback.onError(fallbackError));
                        }
                    });
                } catch (Throwable failoverCrash) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError(error + "\n(Failover crash: " + failoverCrash.getMessage() + ")"));
                }
            }
        });
    }

    public static void callAiApi(String apiUrl, String apiKey, String model, String userPrompt, String originalMessageText, boolean isSummarize, File imageFile, List<HistoryItem> history, AiGenerationCallback callback) {
        if (TextUtils.isEmpty(apiUrl)) {
            callback.onError("API URL is not configured.");
            return;
        }

        final boolean isGeminiNative = (apiUrl.contains("generativelanguage.googleapis.com") || apiUrl.contains("googleapis.com")) && !apiUrl.contains("openai");
        String finalUrl = apiUrl;
        
        if (isGeminiNative && !apiUrl.contains(":generateContent")) {
            if (apiUrl.endsWith("/")) {
                finalUrl = apiUrl.substring(0, apiUrl.length() - 1) + ":generateContent";
            } else {
                finalUrl = apiUrl + ":generateContent";
            }
        }

        try {
            JSONObject jsonBody = new JSONObject();

            String systemInstruction;
            if (isSummarize) {
                systemInstruction = "You are the Advanced AI Content Analyst for Alexgram. Your mission is to provide professional, executive-level summaries of messages and visual content. Be concise, highlight key points accurately, and maintain a formal, professional tone.";
            } else if (originalMessageText != null && originalMessageText.contains("Active Discussion Topic Summary:")) {
                systemInstruction = "You are Alexgram's Advanced AI Assistant in 'Deep Discussion' mode. Act as an expert on the provided topic summary. Provide deep insights and answer follow-up questions with precision and professional clarity. Use Markdown to structure your analysis.";
            } else {
                systemInstruction = "You are Alexgram's Advanced AI Assistant. You are a highly professional, intelligent, and helpful companion. Your goal is to provide insightful, accurate, and concise information while maintaining a sophisticated yet approachable tone. Use Markdown (bold, italic, code blocks, quotes) and appropriate Emojis to structure your responses elegantly. Identity rule: when asked about 'my name' or 'who am I', refer to the account owner from the provided context.\n\n" +
                        "FORMATTING RULE: Avoid using Markdown tables. Instead, use bullet points, numbered lists, and bold headers to present structured data, as tables are difficult to read in a mobile chat interface.\n\n" +
                        "IMAGE GENERATION: If the user asks to 'create', 'generate', 'draw', or 'make' an image or picture, you must generate a detailed descriptive prompt for it and return ONLY the following format: [GEN_IMAGE: your_detailed_description]. Do not add any other conversational text if you are generating an image.";
            }

            if (isGeminiNative) {
                JSONObject systemInstructionObj = new JSONObject();
                JSONArray siParts = new JSONArray();
                JSONObject siTextPart = new JSONObject();
                siTextPart.put("text", systemInstruction);
                siParts.put(siTextPart);
                systemInstructionObj.put("parts", siParts);
                jsonBody.put("system_instruction", systemInstructionObj);

                JSONArray contents = new JSONArray();

                // Add history
                if (history != null) {
                    for (HistoryItem item : history) {
                        JSONObject contentObj = new JSONObject();
                        contentObj.put("role", item.isUser ? "user" : "model");
                        JSONArray parts = new JSONArray();
                        JSONObject textPart = new JSONObject();
                        textPart.put("text", item.text);
                        parts.put(textPart);
                        contentObj.put("parts", parts);
                        contents.put(contentObj);
                    }
                }

                // Add current prompt with context
                JSONObject currentContentObj = new JSONObject();
                currentContentObj.put("role", "user");
                JSONArray currentParts = new JSONArray();

                JSONObject contextPart = new JSONObject();
                String contextText = "Context Data:\n---\n" + (originalMessageText != null ? originalMessageText : "No specific context.") + "\n---\n\nUser Request: " + userPrompt;
                contextPart.put("text", contextText);
                currentParts.put(contextPart);

                if (imageFile != null) {
                    try {
                        byte[] fileBytes = readBytes(imageFile);
                        if (fileBytes != null && fileBytes.length > 0) {
                            String base64Image = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
                            JSONObject imagePart = new JSONObject();
                            JSONObject inlineData = new JSONObject();
                            inlineData.put("mime_type", "image/jpeg");
                            inlineData.put("data", base64Image);
                            imagePart.put("inline_data", inlineData);
                            currentParts.put(imagePart);
                        }
                    } catch (Throwable e) { /* ignore image error */ }
                }

                currentContentObj.put("parts", currentParts);
                contents.put(currentContentObj);
                jsonBody.put("contents", contents);

            } else {
                jsonBody.put("model", model != null ? model : "gpt-4o"); 

                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemInstruction);
                messages.put(systemMsg);

                if (history != null) {
                    for (HistoryItem item : history) {
                        JSONObject msg = new JSONObject();
                        msg.put("role", item.isUser ? "user" : "assistant");
                        msg.put("content", item.text);
                        messages.put(msg);
                    }
                }

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");

                JSONArray contentArray = new JSONArray();

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                String content = "Context Data:\n---\n" + (originalMessageText != null ? originalMessageText : "No specific context.") + "\n---\n\nUser Request: " + userPrompt;
                textPart.put("text", content);
                contentArray.put(textPart);

                if (imageFile != null) {
                    try {
                        byte[] fileBytes = readBytes(imageFile);
                        if (fileBytes != null && fileBytes.length > 0) {
                            String base64Image = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP);
                            
                            JSONObject imagePart = new JSONObject();
                            imagePart.put("type", "image_url");
                            JSONObject imageUrl = new JSONObject();
                            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
                            imagePart.put("image_url", imageUrl);
                            contentArray.put(imagePart);
                        }
                    } catch (Throwable e) { /* ignore image error */ }
                }

                userMsg.put("content", contentArray);
                messages.put(userMsg);

                jsonBody.put("messages", messages);
            }

            Request.Builder requestBuilder = new Request.Builder()
                    .url(finalUrl)
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBody.toString()));

            if (!TextUtils.isEmpty(apiKey)) {
                if (isGeminiNative) {
                    requestBuilder.addHeader("x-goog-api-key", apiKey);
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
                }
            }

            client.newCall(requestBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (response.body() == null) {
                            callback.onError("Empty response body");
                            return;
                        }
                        String responseBody = response.body().string();
                        if (!response.isSuccessful()) {
                            // Professional auto-retry for transient server errors (502, 503, 504)
                            if (response.code() >= 502 && response.code() <= 504) {
                                // We can't easily retry from inside here without recursion or a retry interceptor
                                // But we can at least explain it better or tell the user it's a temporary server overload.
                            }
                            callback.onError("HTTP " + response.code() + ": " + responseBody);
                            return;
                        }

                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        if (isGeminiNative) {
                             if (jsonResponse.has("candidates")) {
                                  JSONArray candidates = jsonResponse.getJSONArray("candidates");
                                  if (candidates.length() > 0) {
                                      JSONObject candidate = candidates.getJSONObject(0);
                                      if (candidate.has("content")) {
                                          JSONObject content = candidate.getJSONObject("content");
                                          JSONArray parts = content.getJSONArray("parts");
                                          if (parts.length() > 0) {
                                              String reply = parts.getJSONObject(0).getString("text");
                                              callback.onSuccess(reply);
                                              return;
                                          }
                                      }
                                  }
                             }
                             callback.onError("No text generated: " + responseBody);
                        } else {
                            if (jsonResponse.has("choices")) {
                                 JSONArray choices = jsonResponse.getJSONArray("choices");
                                 if (choices.length() > 0) {
                                     JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                                     String reply = message.getString("content");
                                     callback.onSuccess(reply);
                                     return;
                                 } else {
                                     callback.onError("No choices in response");
                                 }
                            } else {
                                callback.onError("Unexpected JSON format: " + responseBody);
                            }
                        }
                    } catch (Throwable e) {
                        callback.onError("Parse/Response crash: " + e.getMessage());
                    }
                }
            });

        } catch (Throwable e) {
            callback.onError("Build Request crash: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static byte[] readBytes(File file) {
        FileInputStream fis = null;
        try {
            if (file.length() > 10 * 1024 * 1024) return null; 
            fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            return buffer;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fis != null) fis.close();
            } catch (Throwable e) { /* ignore */ }
        }
    }

    public static String buildContext(@Nullable BaseFragment fragment, int currentAccount, long dialogId, @Nullable ArrayList<MessageObject> messages, boolean isSummarize) {
        StringBuilder sb = new StringBuilder(512);
        if (!isSummarize) {
            sb.append("You are replying inside Alexgram assistant.\n");
            long ownerId = UserConfig.getInstance(currentAccount).getClientUserId();
            TLRPC.User ownerUser = MessagesController.getInstance(currentAccount).getUser(ownerId);
            if (ownerUser != null) {
                sb.append("Account owner (this is the user you assist): ")
                        .append(UserObject.getForcedFirstName(ownerUser));
                if (!TextUtils.isEmpty(ownerUser.username)) {
                    sb.append(" (@").append(ownerUser.username).append(")");
                }
                sb.append("\n");
            }

            if (dialogId != 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (user != null) {
                    sb.append("Dialog with user: ").append(UserObject.getForcedFirstName(user)).append("\n");
                } else if (chat != null) {
                    sb.append("Dialog in chat: ").append(chat.title).append("\n");
                }
            } else {
                sb.append("Current Location: App Home Screen (Chat List)\n");
            }
        }

        if (messages != null && !messages.isEmpty()) {
            sb.append("Recent interactions:\n");
            // If there's only one message, it's likely a "Summarize" or specific "Reply" action
            boolean isSingleMessage = messages.size() == 1;
            int limit = isSingleMessage ? 1 : 3;
            int added = 0;
            for (int i = 0; i < messages.size() && added < limit; i++) {
                MessageObject messageObject = messages.get(i);
                if (messageObject == null) {
                    continue;
                }
                String content = getMessageContent(messageObject);
                if (!TextUtils.isEmpty(content)) {
                    // For single message (Summarize), don't truncate or truncate much later
                    if (!isSingleMessage && content.length() > 300) {
                        content = content.substring(0, 300) + "...";
                    }
                    sb.append("- ").append(content.replace('\n', ' ')).append("\n");
                    added++;
                }
            }
        }

        return sb.toString();
    }

    public static String getMessageContent(MessageObject messageObject) {
        return getMessageContent(messageObject, 0);
    }

    private static String getMessageContent(MessageObject messageObject, int depth) {
        if (messageObject == null || depth > 1) return "";
        StringBuilder sb = new StringBuilder();

        // 0. Forward Info (Helpful for AI context)
        if (messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null) {
            sb.append("[Forwarded Content]\n");
        }

        // 1. Text or Caption
        CharSequence text = messageObject.messageText;
        if (TextUtils.isEmpty(text)) {
            if (!TextUtils.isEmpty(messageObject.caption)) {
                text = messageObject.caption;
            } else if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                text = messageObject.messageOwner.message;
            }
        }

        if (!TextUtils.isEmpty(text)) {
            sb.append(text);
        }

        // 1.5. Custom Emojis (Premium)
        if (messageObject.messageOwner != null && messageObject.messageOwner.entities != null) {
            boolean hasCustom = false;
            for (TLRPC.MessageEntity entity : messageObject.messageOwner.entities) {
                if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                    hasCustom = true;
                    break;
                }
            }
            if (hasCustom) {
                if (sb.length() > 0 && !sb.toString().trim().isEmpty()) {
                    sb.append(" [Premium Emoji]");
                } else {
                    sb.append("[Premium Emoji]");
                }
            }
        }

        // 2. Polls
        if (messageObject.isPoll() && messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
            if (sb.length() > 0) sb.append("\n");
            sb.append("Poll: ").append(poll.question.text);
            if (poll.answers != null) {
                for (TLRPC.PollAnswer answer : poll.answers) {
                    sb.append("\n- ").append(answer.text.text);
                }
            }
        }

        // 3. Link Description
        if (messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            TLRPC.WebPage webPage = messageObject.messageOwner.media.webpage;
            if (webPage != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("Web Page: ");
                if (!TextUtils.isEmpty(webPage.title)) sb.append(webPage.title).append(" - ");
                if (!TextUtils.isEmpty(webPage.description)) sb.append(webPage.description);
                if (!TextUtils.isEmpty(webPage.url)) sb.append(" (").append(webPage.url).append(")");
            }
        }

        // 4. Transcription
        if (!TextUtils.isEmpty(messageObject.getVoiceTranscription())) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Transcription: ").append(messageObject.messageOwner.voiceTranscription);
        }

        // 5. Media fallback if still empty or media-heavy
        if (sb.length() == 0 || (messageObject.messageOwner != null && messageObject.messageOwner.media != null && TextUtils.isEmpty(text))) {
            String tag = null;
            if (messageObject.type == MessageObject.TYPE_PHOTO) tag = "[Photo]";
            else if (messageObject.type == MessageObject.TYPE_VIDEO) tag = "[Video]";
            else if (messageObject.type == MessageObject.TYPE_ROUND_VIDEO) tag = "[Video Note]";
            else if (messageObject.type == MessageObject.TYPE_VOICE) tag = "[Voice Message]";
            else if (messageObject.type == MessageObject.TYPE_STICKER) tag = "[Sticker]";
            else if (messageObject.type == MessageObject.TYPE_GIF) tag = "[GIF]";
            else if (messageObject.type == MessageObject.TYPE_FILE) {
                String name = messageObject.getDocumentName();
                tag = "[File" + (!TextUtils.isEmpty(name) ? ": " + name : "") + "]";
            }
            else if (messageObject.type == MessageObject.TYPE_GEO) tag = "[Location]";
            else if (messageObject.type == MessageObject.TYPE_CONTACT) tag = "[Contact]";

            if (tag != null) {
                if (sb.length() > 0) sb.append("\n").append(tag);
                else sb.append(tag);
            }
        }

        // 6. Quoted message (only for top level)
        if (depth == 0 && messageObject.replyMessageObject != null) {
            String quotedContent = getMessageContent(messageObject.replyMessageObject, depth + 1);
            if (!TextUtils.isEmpty(quotedContent)) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("(Replying to: ").append(quotedContent).append(")");
            }
        }

        return sb.toString();
    }

    public interface ImageDownloadCallback {
        void onSuccess(android.graphics.Bitmap bitmap);
        void onError(String error);
    }

    public static void downloadImage(String url, ImageDownloadCallback callback) {
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull java.io.IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful()) {
                    callback.onError("HTTP " + response.code());
                    return;
                }
                byte[] bytes = response.body().bytes();
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap != null) {
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onSuccess(bitmap));
                } else {
                    callback.onError("Failed to decode bitmap");
                }
            }
        });
    }
}
