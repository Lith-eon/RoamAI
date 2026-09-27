package com.example;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiApiClient {
    private static final String TAG = "GeminiApiClient";
    private static final String DEFAULT_MODEL = "gemini-1.5-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface ApiCallback {
        void onSuccess(String description);
        void onError(String errorMessage);
    }

    public GeminiApiClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void analyzeImage(String apiKey, Bitmap bitmap, String prompt, String modelName, ApiCallback callback) {
        analyzeImage(apiKey, bitmap, null, prompt, modelName, callback);
    }

    public void analyzeImage(String apiKey, Bitmap currentBitmap, Bitmap previousBitmap, String prompt, String modelName, ApiCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("Gemini API Key is missing.");
            return;
        }

        final String selectedModel = (modelName == null || modelName.isEmpty()) ? DEFAULT_MODEL : modelName;

        executorService.execute(() -> {
            try {
                String base64Current = bitmapToBase64Jpeg(currentBitmap);
                String base64Previous = (previousBitmap != null && previousBitmap != currentBitmap)
                        ? bitmapToBase64Jpeg(previousBitmap)
                        : null;

                // Build Request JSON
                JSONObject rootJson = new JSONObject();
                JSONArray contentsArray = new JSONArray();
                JSONObject contentObject = new JSONObject();
                JSONArray partsArray = new JSONArray();

                // Part 1: Text Prompt
                JSONObject textPart = new JSONObject();
                textPart.put("text", (prompt != null && !prompt.trim().isEmpty()) 
                        ? prompt 
                        : "Describe this image in detail.");
                partsArray.put(textPart);

                // Part 2: Current Camera Image
                if (base64Current != null && !base64Current.isEmpty()) {
                    if (base64Previous != null && !base64Previous.isEmpty()) {
                        JSONObject labelPart = new JSONObject();
                        labelPart.put("text", "Image 1 (Current live camera view):");
                        partsArray.put(labelPart);
                    }

                    JSONObject imagePart = new JSONObject();
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", "image/jpeg");
                    inlineData.put("data", base64Current);
                    imagePart.put("inlineData", inlineData);
                    partsArray.put(imagePart);
                }

                // Part 3: Previous Analyzed Image (if provided)
                if (base64Previous != null && !base64Previous.isEmpty()) {
                    JSONObject labelPart = new JSONObject();
                    labelPart.put("text", "Image 2 (Previously inspected frame from earlier in session):");
                    partsArray.put(labelPart);

                    JSONObject imagePart = new JSONObject();
                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mimeType", "image/jpeg");
                    inlineData.put("data", base64Previous);
                    imagePart.put("inlineData", inlineData);
                    partsArray.put(imagePart);
                }

                contentObject.put("parts", partsArray);
                contentsArray.put(contentObject);
                rootJson.put("contents", contentsArray);

                // Generation Config for complete responses
                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.7);
                rootJson.put("generationConfig", genConfig);

                String requestBodyString = rootJson.toString();
                String url = BASE_URL + selectedModel + ":generateContent?key=" + apiKey.trim();

                RequestBody body = RequestBody.create(
                        requestBodyString, 
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() == null) {
                        notifyError(callback, "Server returned an empty response body.");
                        return;
                    }

                    String responseString = response.body().string();
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Gemini API Error (" + response.code() + "): " + responseString);
                        String errorMsg = parseErrorMessage(responseString, response.code());
                        notifyError(callback, errorMsg);
                        return;
                    }

                    String generatedText = parseGeneratedText(responseString);
                    notifySuccess(callback, generatedText);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error executing Gemini API call", e);
                notifyError(callback, "Request failed: " + e.getLocalizedMessage());
            }
        });
    }

    private String bitmapToBase64Jpeg(Bitmap bitmap) {
        if (bitmap == null) return null;

        // Scale down image to 512px max dimension for ultrafast API latency
        int maxDimension = 512;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap resizedBitmap = bitmap;
        if (width > maxDimension || height > maxDimension) {
            float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
            int newWidth = Math.round(scale * width);
            int newHeight = Math.round(scale * height);
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
        byte[] byteArray = baos.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    private String parseGeneratedText(String jsonResponse) {
        try {
            JSONObject root = new JSONObject(jsonResponse);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject firstCandidate = candidates.getJSONObject(0);
                JSONObject content = firstCandidate.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part.has("text")) {
                                sb.append(part.getString("text"));
                            }
                        }
                        if (sb.length() > 0) {
                            return sb.toString().trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Gemini response JSON", e);
        }
        return "No text output returned by Gemini model.";
    }

    private String parseErrorMessage(String errorJsonString, int httpCode) {
        try {
            JSONObject root = new JSONObject(errorJsonString);
            if (root.has("error")) {
                JSONObject errorObj = root.getJSONObject("error");
                String message = errorObj.optString("message", "HTTP " + httpCode);
                return "Gemini API Error (" + httpCode + "): " + message;
            }
        } catch (Exception ignored) {
        }
        return "Gemini API Error (" + httpCode + ")";
    }

    private void notifySuccess(ApiCallback callback, String text) {
        mainHandler.post(() -> callback.onSuccess(text));
    }

    private void notifyError(ApiCallback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage));
    }
}
