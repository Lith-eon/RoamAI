package com.example;

import android.content.Context;

public class ApiKeyManager {

    public static String getApiKey(Context context) {
        try {
            String buildConfigKey = BuildConfig.GEMINI_API_KEY;
            if (buildConfigKey != null) {
                return buildConfigKey.trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String getElevenLabsApiKey(Context context) {
        try {
            String key = BuildConfig.ELEVENLABS_API_KEY;
            if (key != null) {
                return key.trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
