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
import java.util.concurrent.TimeUnit;

import xyz.nextalone.nagram.NaConfig;

public class AIAssistanceHelper {

    public interface AiGenerationCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void requestReply(int currentAccount, String prompt, String chatContext, ChatAnimeAssistantView.AssistantRequestCallback callback) {
        final String decoratedPrompt = "Persona: You are Alexgram's anime-style floating assistant. " +
                "Tone: friendly, playful, slightly teasing but respectful. " +
                "Keep responses concise, practical, and conversational. " +
                "Identity rule: when the user asks about 'my name' or 'who am I', refer to the account owner from context, never other chat participants. " +
                "User prompt: " + prompt;


        if (NaConfig.INSTANCE.getUsePollinationsAi().Bool()) {
            callAiApi("https://text.pollinations.ai/v1/chat/completions", null, "openai", decoratedPrompt, chatContext, null, new AiGenerationCallback() {
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

        callAiApi(url1, key1, "gpt-4o", decoratedPrompt, chatContext, null, new AiGenerationCallback() {
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

                    callAiApi(url2, key2, "gpt-4o", decoratedPrompt, chatContext, null, new AiGenerationCallback() {
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

    public static void callAiApi(String apiUrl, String apiKey, String model, String userPrompt, String originalMessageText, File imageFile, AiGenerationCallback callback) {
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
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();

            JSONObject jsonBody = new JSONObject();

            if (isGeminiNative) {
                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                JSONArray parts = new JSONArray();

                String combinedText = "System Instruction: You are a helpful assistant replying to a message in a chat.\n\n" +
                        "Context Message:\n---\n" + (originalMessageText != null ? originalMessageText : "") + "\n---\n\n" +
                        "User Request: " + userPrompt;
                
                JSONObject textPart = new JSONObject();
                textPart.put("text", combinedText);
                parts.put(textPart);

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
                            parts.put(imagePart);
                        }
                    } catch (Throwable e) { /* ignore image error */ }
                }

                contentObj.put("parts", parts);
                contents.put(contentObj);
                jsonBody.put("contents", contents);

            } else {
                jsonBody.put("model", model != null ? model : "gpt-4o"); 

                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "You are a helpful assistant replying to a message in a chat.");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");

                JSONArray contentArray = new JSONArray();

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                String content = "Context Message:\n---\n" + (originalMessageText != null ? originalMessageText : "") + "\n---\n\nUser Instruction: " + userPrompt;
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

    public static String buildContext(@Nullable BaseFragment fragment, int currentAccount, long dialogId, @Nullable ArrayList<MessageObject> messages) {
        StringBuilder sb = new StringBuilder(512);
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

        if (messages != null && !messages.isEmpty()) {
            int added = 0;
            sb.append("Recent interactions:\n");
            for (int i = 0; i < messages.size() && added < 3; i++) {
                MessageObject messageObject = messages.get(i);
                if (messageObject == null) {
                    continue;
                }
                CharSequence text = messageObject.messageText;
                if (TextUtils.isEmpty(text) && messageObject.caption != null) {
                    text = messageObject.caption;
                }
                if (!TextUtils.isEmpty(text)) {
                    String sample = text.toString();
                    if (sample.length() > 160) {
                        sample = sample.substring(0, 160) + "...";
                    }
                    sb.append("- ").append(sample.replace('\n', ' ')).append("\n");
                    added++;
                }
            }
        }

        return sb.toString();
    }
}
