package tw.nekomimi.nekogram.llm.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.Utilities;
import tw.nekomimi.nekogram.utils.HttpClient;

public class AiImageClient {
    private static final OkHttpClient client = HttpClient.INSTANCE.getLlmInstance().newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public interface Callback {
        void onSuccess(String filePath);
        void onError(String error);
    }

    public static void generateImage(int provider, String baseUrl, String apiKey, String prompt, Callback callback) {
        if (provider == 0) { // OpenAI Compatible
            generateOpenAI(baseUrl, apiKey, prompt, callback);
        } else { // Gemini
            generateGemini(baseUrl, apiKey, prompt, callback);
        }
    }

    private static void generateOpenAI(String baseUrl, String apiKey, String prompt, Callback callback) {
        String url = baseUrl;
        if (url == null || url.isEmpty()) {
            url = "https://api.openai.com/v1";
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/images/generations")) {
            url += "/images/generations";
        }

        try {
            JSONObject json = new JSONObject();
            json.put("prompt", prompt);
            json.put("n", 1);
            json.put("size", "1024x1024");

            RequestBody body = RequestBody.create(json.toString(), HttpClient.MEDIA_TYPE_JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseData = response.body().string();
                        if (!response.isSuccessful()) {
                            callback.onError("HTTP " + response.code() + ": " + responseData);
                            return;
                        }
                        JSONObject result = new JSONObject(responseData);
                        JSONArray data = result.getJSONArray("data");
                        String imageUrl = data.getJSONObject(0).getString("url");
                        downloadAndSave(imageUrl, callback);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private static void generateGemini(String baseUrl, String apiKey, String prompt, Callback callback) {
        String url = baseUrl;
        if (url == null || url.isEmpty()) {
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent";
        } else if (!url.startsWith("http")) {
            url = "https://generativelanguage.googleapis.com/v1beta/models/" + url;
        }

        // Determine if it's specialized (predict) or multimodal (generateContent)
        boolean isPredict = url.contains(":predict");
        if (!isPredict && !url.contains(":generateContent")) {
            // Default to generateContent for gemini-2.0, predict for others
            if (url.contains("gemini-2.0") || url.contains("gemini-exp")) {
                url += ":generateContent";
            } else {
                url += ":predict";
                isPredict = true;
            }
        }

        if (!url.contains("?key=") && apiKey != null && !apiKey.isEmpty()) {
            url += (url.contains("?") ? "&" : "?") + "key=" + apiKey;
        }

        final String finalUrl = url;
        final boolean finalIsPredict = isPredict;

        try {
            JSONObject json = new JSONObject();
            if (finalIsPredict) {
                // Imagen predict format
                JSONObject instances = new JSONObject();
                instances.put("prompt", prompt);
                JSONArray instancesArray = new JSONArray();
                instancesArray.put(instances);
                json.put("instances", instancesArray);
            } else {
                // Gemini generateContent multimodal format
                JSONArray parts = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", "Generate an image for: " + prompt);
                parts.put(textPart);
                
                JSONObject content = new JSONObject();
                content.put("parts", parts);
                
                JSONArray contents = new JSONArray();
                contents.put(content);
                json.put("contents", contents);
                
                JSONObject generationConfig = new JSONObject();
                JSONArray modalities = new JSONArray();
                modalities.put("IMAGE"); // Only request IMAGE to avoid modality text,image support error
                generationConfig.put("responseModalities", modalities);
                json.put("generationConfig", generationConfig);
            }

            RequestBody body = RequestBody.create(json.toString(), HttpClient.MEDIA_TYPE_JSON);
            Request request = new Request.Builder()
                    .url(finalUrl)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseData = response.body().string();
                        if (!response.isSuccessful()) {
                            String errorMsg = "HTTP " + response.code() + " for " + finalUrl + ": " + responseData;
                            if (response.code() == 400 || response.code() == 404) {
                                errorMsg += "\n\nTip: Ensure this model is enabled for your API key in Google AI Studio.";
                            }
                            callback.onError(errorMsg);
                            return;
                        }
                        
                        if (finalIsPredict) {
                            JSONObject result = new JSONObject(responseData);
                            JSONArray predictions = result.getJSONArray("predictions");
                            JSONObject first = predictions.getJSONObject(0);
                            if (first.has("bytesBase64Encoded")) {
                                 String base64 = first.getString("bytesBase64Encoded");
                                 byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                                 saveBytes(decoded, callback);
                            } else {
                                 callback.onError("No image data in response from " + finalUrl);
                            }
                        } else {
                            JSONObject result = new JSONObject(responseData);
                            JSONArray candidates = result.getJSONArray("candidates");
                            JSONObject firstCand = candidates.getJSONObject(0);
                            JSONObject content = firstCand.getJSONObject("content");
                            JSONArray parts = content.getJSONArray("parts");
                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject part = parts.getJSONObject(i);
                                if (part.has("inlineData")) {
                                    JSONObject inlineData = part.getJSONObject("inlineData");
                                    if (inlineData.getString("mimeType").startsWith("image/")) {
                                        String base64 = inlineData.getString("data");
                                        byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                                        saveBytes(decoded, callback);
                                        return;
                                    }
                                }
                            }
                            callback.onError("No image in Gemini multimodal response from " + finalUrl);
                        }
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private static void downloadAndSave(String url, Callback callback) {
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Download failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Download HTTP error: " + response.code());
                    return;
                }
                saveBytes(response.body().bytes(), callback);
            }
        });
    }

    private static void saveBytes(byte[] bytes, Callback callback) {
        try {
            File cacheDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "ai_images");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File file = new File(cacheDir, "ai_" + System.currentTimeMillis() + "_" + Utilities.random.nextInt(1000) + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }
            callback.onSuccess(file.getAbsolutePath());
        } catch (Exception e) {
            callback.onError("Save failed: " + e.getMessage());
        }
    }
}
