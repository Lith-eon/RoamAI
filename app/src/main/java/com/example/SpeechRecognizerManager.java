package com.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class SpeechRecognizerManager {
    private static final String TAG = "SpeechRecognizerManager";
    private static final long RESTART_DELAY_MS = 400;

    public interface SpeechListener {
        void onSpeechStarted();
        void onSpeechResult(String spokenText);
        void onListeningStateChanged(boolean isListening);
        void onError(String errorMsg);
    }

    private final Context context;
    private final SpeechListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private boolean isListeningRequested = false;
    private boolean isSpeaking = false;

    public SpeechRecognizerManager(Context context, SpeechListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        initSpeechRecognizer();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition is not available on this device.");
            return;
        }

        handler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Log.d(TAG, "onReadyForSpeech");
                        if (listener != null) {
                            listener.onListeningStateChanged(true);
                        }
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech");
                        isSpeaking = true;
                        if (listener != null) {
                            listener.onSpeechStarted();
                        }
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech");
                        isSpeaking = false;
                    }

                    @Override
                    public void onError(int error) {
                        Log.w(TAG, "SpeechRecognizer error: " + getErrorString(error));
                        isSpeaking = false;

                        if (listener != null) {
                            listener.onListeningStateChanged(false);
                        }

                        if (isListeningRequested) {
                            scheduleRestart();
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        Log.d(TAG, "onResults");
                        isSpeaking = false;
                        if (listener != null) {
                            listener.onListeningStateChanged(false);
                        }

                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0).trim();
                            if (!spokenText.isEmpty() && listener != null) {
                                listener.onSpeechResult(spokenText);
                                return;
                            }
                        }

                        if (isListeningRequested) {
                            scheduleRestart();
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {}

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });
            } catch (Exception e) {
                Log.e(TAG, "Error initializing SpeechRecognizer", e);
            }
        });
    }

    public void startListening() {
        isListeningRequested = true;
        handler.removeCallbacks(restartRunnable);
        handler.post(this::startListeningInternal);
    }

    private void startListeningInternal() {
        if (!isListeningRequested) return;

        if (speechRecognizer == null) {
            initSpeechRecognizer();
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

                speechRecognizer.startListening(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting speech recognition", e);
            scheduleRestart();
        }
    }

    public void stopListening() {
        isListeningRequested = false;
        isSpeaking = false;
        handler.removeCallbacks(restartRunnable);
        handler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                } catch (Exception ignored) {}
            }
            if (listener != null) {
                listener.onListeningStateChanged(false);
            }
        });
    }

    private final Runnable restartRunnable = this::startListeningInternal;

    private void scheduleRestart() {
        if (!isListeningRequested) return;
        handler.removeCallbacks(restartRunnable);
        handler.postDelayed(restartRunnable, RESTART_DELAY_MS);
    }

    public boolean isUserSpeaking() {
        return isSpeaking;
    }

    public boolean isListeningRequested() {
        return isListeningRequested;
    }

    public void destroy() {
        isListeningRequested = false;
        isSpeaking = false;
        handler.removeCallbacks(restartRunnable);
        handler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
        });
    }

    private String getErrorString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RecognitionService busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown speech error (" + error + ")";
        }
    }
}
