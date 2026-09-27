package com.example;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ElevenLabsClient {
    private static final String TAG = "ElevenLabsClient";
    private static final String MALE_VOICE_ID = "ugD7KkAuI52llD9UpKVO";
    private static final String FEMALE_VOICE_ID = "qIH9nL32c7wk5wfPjick";
    private static final String API_URL = "https://api.elevenlabs.io/v1/text-to-speech/";

    private final okhttp3.OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private MediaPlayer mediaPlayer;
    private final AtomicLong currentNarrationId = new AtomicLong(0);

    public interface NarrationCallback {
        void onStart();
        void onCompletion();
        void onError(String errorMessage);
    }

    public ElevenLabsClient() {
        this.httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void speakText(Context context, String apiKey, String text, boolean isFemaleVoice, NarrationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onError("Text is empty.");
            return;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            Log.e(TAG, "ElevenLabs API key is missing");
            if (callback != null) callback.onError("ElevenLabs API key is missing.");
            return;
        }

        stopAudio();
        final long narrationId = currentNarrationId.incrementAndGet();

        executorService.execute(() -> {
            if (narrationId != currentNarrationId.get()) return;

            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("text", text.trim());
                jsonBody.put("model_id", "eleven_turbo_v2_5");

                JSONObject voiceSettings = new JSONObject();
                voiceSettings.put("stability", 0.2);
                voiceSettings.put("similarity_boost", 0.70);
                voiceSettings.put("speed", 1.19);
                voiceSettings.put("use_speaker_boost", true);
                jsonBody.put("voice_settings", voiceSettings);

                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                        jsonBody.toString(),
                        okhttp3.MediaType.parse("application/json; charset=utf-8")
                );

                String voiceId = isFemaleVoice ? FEMALE_VOICE_ID : MALE_VOICE_ID;
                String url = API_URL + voiceId;
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(url)
                        .addHeader("xi-api-key", apiKey.trim())
                        .addHeader("Accept", "audio/mpeg")
                        .post(body)
                        .build();

                if (narrationId != currentNarrationId.get()) return;

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (narrationId != currentNarrationId.get()) return;

                    if (!response.isSuccessful() || response.body() == null) {
                        String errorText = response.body() != null ? response.body().string() : "Empty body";
                        Log.e(TAG, "ElevenLabs API error (" + response.code() + "): " + errorText);
                        notifyError(callback, narrationId, "ElevenLabs API error (" + response.code() + ")");
                        return;
                    }

                    // Save MP3 stream to local cache file
                    File cacheFile = new File(context.getCacheDir(), "elevenlabs_narration.mp3");
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            if (narrationId != currentNarrationId.get()) return;
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                    }

                    if (narrationId != currentNarrationId.get()) return;

                    // Play audio on main thread
                    mainHandler.post(() -> playAudioFile(cacheFile, narrationId, callback));
                }

            } catch (Exception e) {
                if (narrationId != currentNarrationId.get()) return;
                Log.e(TAG, "Error executing ElevenLabs TTS request", e);
                notifyError(callback, narrationId, "ElevenLabs TTS request failed: " + e.getLocalizedMessage());
            }
        });
    }

    public void speakText(Context context, String apiKey, String text, NarrationCallback callback) {
        speakText(context, apiKey, text, false, callback);
    }

    private void playAudioFile(File audioFile, long narrationId, NarrationCallback callback) {
        if (narrationId != currentNarrationId.get()) return;
        try {
            stopAudioInternal();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            PlaybackParams params = new PlaybackParams();
            params.setSpeed(1.1f);
            mediaPlayer.setPlaybackParams(params);

            if (narrationId != currentNarrationId.get()) {
                stopAudioInternal();
                return;
            }

            if (callback != null) callback.onStart();

            mediaPlayer.setOnCompletionListener(mp -> {
                stopAudioInternal();
                if (narrationId == currentNarrationId.get() && callback != null) {
                    callback.onCompletion();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                stopAudioInternal();
                if (narrationId == currentNarrationId.get() && callback != null) {
                    callback.onError("MediaPlayer error code " + what);
                }
                return true;
            });

            mediaPlayer.start();

        } catch (Exception e) {
            Log.e(TAG, "Error playing audio file", e);
            stopAudioInternal();
            if (narrationId == currentNarrationId.get() && callback != null) {
                callback.onError("Audio playback error: " + e.getMessage());
            }
        }
    }

    public void stopAudio() {
        currentNarrationId.incrementAndGet();
        stopAudioInternal();
    }

    private void stopAudioInternal() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private void notifyError(NarrationCallback callback, long narrationId, String message) {
        mainHandler.post(() -> {
            if (narrationId == currentNarrationId.get() && callback != null) {
                callback.onError(message);
            }
        });
    }
}
