package tw.nekomimi.nekogram.llm.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.telegram.messenger.AndroidUtilities;
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

    public static void generateImage(int provider, String baseUrl, String apiKey, String prompt, String originalImagePath, Callback callback) {
        if (provider == 0) {
            generateOpenAI(baseUrl, apiKey, prompt, callback);
        } else if (provider == 1) {
            generateGemini(baseUrl, apiKey, prompt, originalImagePath, callback);
        } else if (provider == 2) {
            generatePollinations(prompt, originalImagePath, callback);
        } else if (provider == 3) {
            generateSiliconFlow(baseUrl, apiKey, prompt, originalImagePath, callback);
        } else if (provider == 4) {
            generateAiHorde(apiKey, prompt, originalImagePath, callback);
        } else if (provider == 5) {
            generateCloudflare(baseUrl, apiKey, prompt, originalImagePath, callback);
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

    private static void generateGemini(String baseUrl, String apiKey, String prompt, String originalImagePath, Callback callback) {
        String url = baseUrl;
        if (url == null || url.isEmpty()) {
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";
        } else if (!url.startsWith("http")) {
            url = "https://generativelanguage.googleapis.com/v1beta/models/" + url;
        }

        // Determine if it's specialized (predict) or multimodal (generateContent)
        boolean isPredict = url.contains(":predict");
        if (!isPredict && !url.contains(":generateContent")) {
            // Updated detection for 2.5 and 3.1 series
            if (url.contains("gemini-1.5") || url.contains("gemini-2.0") || url.contains("gemini-2.5") || url.contains("gemini-3.1") || url.contains("gemini-exp")) {
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

                // Add original image if provided (Image-to-Image)
                if (originalImagePath != null && !originalImagePath.isEmpty()) {
                    try {
                        File imageFile = new File(originalImagePath);
                        if (imageFile.exists()) {
                            byte[] data = readBytes(imageFile);
                            String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                            JSONObject imagePart = new JSONObject();
                            JSONObject inlineData = new JSONObject();
                            inlineData.put("mimeType", "image/jpeg");
                            inlineData.put("data", base64);
                            imagePart.put("inlineData", inlineData);
                            parts.put(imagePart);
                        }
                    } catch (Exception ignore) {}
                }

                JSONObject textPart = new JSONObject();
                String prefix;
                if (originalImagePath != null && !originalImagePath.isEmpty()) {
                    prefix = "You are an AI photo editor. Look at the provided photo and modify it according to this instruction: ";
                } else {
                    prefix = "Generate an image for: ";
                }
                textPart.put("text", prefix + prompt);
                parts.put(textPart);
                
                JSONObject content = new JSONObject();
                content.put("parts", parts);
                
                JSONArray contents = new JSONArray();
                contents.put(content);
                json.put("contents", contents);
                
                JSONObject generationConfig = new JSONObject();
                JSONArray modalities = new JSONArray();
                modalities.put("IMAGE"); // Only request IMAGE to avoid modality support error
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
                            String errorMsg = "AI Generation Error: HTTP " + response.code() + " for " + finalUrl + ": " + responseData;
                            if (response.code() == 400 || response.code() == 404) {
                                errorMsg += "\n\nTip: Google updated their model list for April 2026. Ensure you have the latest Gemini 2.5 or 3.1 models enabled in your AI Studio.";
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
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Download failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    saveBytes(response.body().bytes(), callback);
                } else {
                    callback.onError("Download failed: HTTP " + response.code());
                }
            }
        });
    }

    private static void generatePollinations(String prompt, String originalImagePath, Callback callback) {
        String url = "https://gen.pollinations.ai/v1/images/generations";
        
        try {
            JSONObject json = new JSONObject();
            json.put("prompt", prompt);
            
            if (originalImagePath != null && !originalImagePath.isEmpty()) {
                File f = new File(originalImagePath);
                if (f.exists()) {
                    byte[] data = readBytes(f);
                    String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                    json.put("image", "data:image/jpeg;base64," + base64);
                    json.put("model", "p-image-edit");
                } else {
                    json.put("model", "flux");
                }
            } else {
                json.put("model", "flux");
            }

            RequestBody body = RequestBody.create(json.toString(), HttpClient.MEDIA_TYPE_JSON);
            Request request = new Request.Builder()
                    .url(url)
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
                            callback.onError("Pollinations.ai Error: " + responseData);
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

    private static void generateSiliconFlow(String baseUrl, String apiKey, String prompt, String originalImagePath, Callback callback) {
        // Different fallback list depending on whether we are editing or generating new
        final String[] fallbackModels;
        if (originalImagePath != null && !originalImagePath.isEmpty()) {
            // These models support true Image-to-Image (I2I) / reference images
            fallbackModels = new String[]{
                    "stabilityai/stable-diffusion-xl-base-1.0",
                    "Kwai-Kolors/Kolors",
                    "THUDM/CogView3Plus"
            };
        } else {
            // High speed text-to-image models
            fallbackModels = new String[]{
                    "black-forest-labs/FLUX.1-schnell",
                    "stabilityai/stable-diffusion-xl-base-1.0",
                    "THUDM/CogView3Plus"
            };
        }
        
        generateSiliconFlowWithRetry(0, fallbackModels, baseUrl, apiKey, prompt, originalImagePath, callback);
    }

    private static void generateSiliconFlowWithRetry(int index, String[] models, String baseUrl, String apiKey, String prompt, String originalImagePath, Callback callback) {
        if (index >= models.length) {
            callback.onError("All SiliconFlow models are currently disabled or restricted. Please check your account balance.");
            return;
        }

        String url = "https://api.siliconflow.cn/v1/images/generations";
        String model = models[index];

        // If user provided a specific model name in the URL field, use it first
        if (index == 0 && baseUrl != null && !baseUrl.isEmpty()) {
            if (!baseUrl.startsWith("http")) {
                model = baseUrl;
            } else if (!baseUrl.contains("openai.com") && !baseUrl.endsWith("/generations")) {
                String[] parts = baseUrl.split("/");
                model = parts[parts.length - 1];
            }
        }

        try {
            JSONObject json = new JSONObject();
            json.put("model", model);
            json.put("prompt", prompt);

            if (originalImagePath != null && !originalImagePath.isEmpty()) {
                File f = new File(originalImagePath);
                if (f.exists()) {
                    byte[] data = readBytes(f);
                    String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                    json.put("image", "data:image/jpeg;base64," + base64);
                }
            }

            final String currentModel = model;
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
                            // If model is disabled (30003) or not found (20012), try the next fallback
                            if (responseData.contains("30003") || responseData.contains("20012") || responseData.contains("Model disabled") || responseData.contains("Model does not exist")) {
                                AndroidUtilities.runOnUIThread(() -> generateSiliconFlowWithRetry(index + 1, models, baseUrl, apiKey, prompt, originalImagePath, callback));
                                return;
                            }
                            callback.onError("SiliconFlow (" + currentModel + ") Error: " + responseData);
                            return;
                        }
                        JSONObject result = new JSONObject(responseData);
                        JSONArray images = result.optJSONArray("images");
                        if (images == null) images = result.optJSONArray("data");
                        
                        if (images != null && images.length() > 0) {
                            JSONObject first = images.getJSONObject(0);
                            String urlOrBase64 = first.optString("url");
                            if (urlOrBase64.isEmpty()) urlOrBase64 = first.optString("b64_json");
                            
                            if (urlOrBase64.startsWith("http")) {
                                downloadAndSave(urlOrBase64, callback);
                            } else {
                                byte[] decoded = Base64.decode(urlOrBase64, Base64.DEFAULT);
                                saveBytes(decoded, callback);
                            }
                        } else {
                            callback.onError("No image in response: " + responseData);
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



    private static void generateAiHorde(String apiKey, String prompt, String originalImagePath, Callback callback) {
        String url = "https://aihorde.net/api/v2/generate/async";
        String actualApiKey = (apiKey == null || apiKey.isEmpty()) ? "0000000000" : apiKey;

        try {
            JSONObject json = new JSONObject();
            json.put("prompt", prompt);
            
            JSONObject params = new JSONObject();
            params.put("n", 1);
            params.put("steps", 20);
            params.put("width", 512);
            params.put("height", 512);
            params.put("sampler_name", "k_euler");
            if (originalImagePath != null && !originalImagePath.isEmpty()) {
                params.put("denoising_strength", 0.65);
            }
            json.put("params", params);

            if (originalImagePath != null && !originalImagePath.isEmpty()) {
                File f = new File(originalImagePath);
                if (f.exists()) {
                    byte[] data = readBytes(f);
                    String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                    json.put("source_image", base64);
                    json.put("source_processing", "img2img");
                }
            }

            JSONArray models = new JSONArray();
            models.put("stable_diffusion");
            json.put("models", models);

            RequestBody body = RequestBody.create(json.toString(), HttpClient.MEDIA_TYPE_JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", actualApiKey)
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
                            callback.onError("AI Horde Error: " + responseData);
                            return;
                        }
                        JSONObject result = new JSONObject(responseData);
                        String id = result.getString("id");
                        // Start polling
                        checkAiHordeStatus(id, actualApiKey, callback, 0);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private static void checkAiHordeStatus(String id, String apiKey, Callback callback, int attempt) {
        if (attempt > 60) { // Timeout after ~180s
            callback.onError("AI Horde request timed out. Please try again.");
            return;
        }

        String url = "https://aihorde.net/api/v2/generate/check/" + id;
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", apiKey)
                .get()
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
                    JSONObject result = new JSONObject(responseData);
                    boolean done = result.optBoolean("done", false);
                    if (done) {
                        getAiHordeResult(id, apiKey, callback);
                    } else {
                        // Poll again after 3 seconds
                        AndroidUtilities.runOnUIThread(() -> checkAiHordeStatus(id, apiKey, callback, attempt + 1), 3000);
                    }
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    private static void getAiHordeResult(String id, String apiKey, Callback callback) {
        String url = "https://aihorde.net/api/v2/generate/status/" + id;
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", apiKey)
                .get()
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
                    JSONObject result = new JSONObject(responseData);
                    JSONArray generations = result.getJSONArray("generations");
                    if (generations.length() > 0) {
                        JSONObject first = generations.getJSONObject(0);
                        String imgData = first.getString("img"); // Horde returns base64 in "img"
                        byte[] decoded = Base64.decode(imgData, Base64.DEFAULT);
                        saveBytes(decoded, callback);
                    } else {
                        callback.onError("No generations found in AI Horde response");
                    }
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    private static void generateCloudflare(String accountId, String apiToken, String prompt, String originalImagePath, Callback callback) {
        if (accountId == null || accountId.isEmpty() || accountId.startsWith("http")) {
            callback.onError("Cloudflare Error: Please enter your Cloudflare Account ID into the 'Model URL' field.");
            return;
        }

        String model = (originalImagePath != null && !originalImagePath.isEmpty())
                ? "@cf/stabilityai/stable-diffusion-v1-5-img2img"
                : "@cf/stabilityai/stable-diffusion-xl-base-1.0";

        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;

        try {
            JSONObject json = new JSONObject();
            json.put("prompt", prompt);

            if (originalImagePath != null && !originalImagePath.isEmpty()) {
                File f = new File(originalImagePath);
                if (f.exists()) {
                    byte[] data = readBytes(f);
                    JSONArray imageArray = new JSONArray();
                    for (byte b : data) {
                        imageArray.put(b & 0xFF);
                    }
                    json.put("image", imageArray);
                    json.put("strength", 0.6);
                }
            }

            RequestBody body = RequestBody.create(json.toString(), HttpClient.MEDIA_TYPE_JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiToken)
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
                        if (!response.isSuccessful()) {
                            String responseData = response.body().string();
                            callback.onError("Cloudflare Error (HTTP " + response.code() + "): " + responseData);
                            return;
                        }
                        // Cloudflare image endpoints return binary data directly
                        saveBytes(response.body().bytes(), callback);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        long length = file.length();
        if (length > Integer.MAX_VALUE) {
            throw new IOException("File is too large");
        }
        byte[] bytes = new byte[(int) length];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(bytes);
        }
        return bytes;
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
