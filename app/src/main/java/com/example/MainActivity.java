package com.example;

import static com.example.R.*;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";
    private static final long CHECK_INTERVAL_MS = 500; // Check motion & narration state every 500ms
    private static final long SETTLE_DELAY_MS = 700;   // Device must be still for 700ms after motion before analyzing

    private static final float ACCEL_THRESHOLD = 1.2f; // m/s^2 change threshold
    private static final float GYRO_THRESHOLD = 0.35f; // rad/s rotation threshold

    private static final String GEMINI_LIVE_PROMPT =
            "Analyze this live camera view. Determine the setting and most prominent item or feature shown. " +
                    "You are an enthusiastic tour guide. Make the viewer interested in what they are seeing. " +
                    "Write a very brief description of what is shown and why it is interesting or significant. " +
                    "Connect your response directly to what is visible in the image rather than listing generic facts. " +
                    "Aim for 50-100 words. " +
                    "Do not use bullet points, headings, or filler. " +
                    "Try to focus more on being informative rather than pointing out obvious things, although still point out things that are less obvious and/or significant. "+
                    "Return only the brief.";

    // UI Elements
    private MaterialButton btnSettings;
    private PreviewView cameraPreviewView;

    private View layoutStatusBadge;

    private TextView tvLiveStatus;

    private MaterialCardView cardResult;
    private TextView tvDescriptionResult;
    private LinearLayout layoutLoading;

    private MaterialButton btnToggleFlash;
    private MaterialButton btnToggleGeminiLive;
    private MaterialButton btnTurnOffGemini;

    private MaterialButton btnZoom1x;
    private MaterialButton btnZoom2x;
    private MaterialButton btnZoom5x;

    private MaterialButton btnCopyText;
    private MaterialButton btnShareText;

    // Camera State
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private final int lensFacing = CameraSelector.LENS_FACING_BACK;
    private boolean isFlashOn = false;

    // Sensors & Motion Detection State
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private float[] lastAccel = new float[3];
    private boolean hasLastAccel = false;
    private boolean hasCameraMoved = true; // Initialized to true so initial scene is analyzed
    private long lastMotionTime = System.currentTimeMillis();

    // Zoom State & Gesture Detector
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoomRatio = 1.0f;

    // Gemini AI, ElevenLabs, & Speech Recognition
    private GeminiApiClient geminiApiClient;
    private ElevenLabsClient elevenLabsClient;
    private SpeechRecognizerManager speechRecognizerManager;
    private Bitmap lastAnalyzedBitmap = null;

    private boolean isGeminiLiveEnabled = false;
    private boolean isAnalysisInProgress = false;
    private boolean isNarrating = false;

    private final Handler autoAnalysisHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoAnalysisRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isGeminiLiveEnabled) return;

            long now = System.currentTimeMillis();
            boolean isSettled = (now - lastMotionTime) >= SETTLE_DELAY_MS;
            boolean isUserSpeaking = speechRecognizerManager != null && speechRecognizerManager.isUserSpeaking();

            // Only trigger a new analysis if camera moved/zoomed, has settled, is not currently analyzing, is NOT currently narrating text, and user is NOT speaking!
            if (hasCameraMoved && isSettled && !isAnalysisInProgress && !isNarrating && !isUserSpeaking && cameraPreviewView != null) {
                Bitmap currentFrame = cameraPreviewView.getBitmap();
                if (currentFrame != null) {
                    hasCameraMoved = false; // Reset motion flag until device or zoom moves again
                    performGeminiAnalysis(currentFrame, true, null);
                }
            }

            // Schedule next check interval
            autoAnalysisHandler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    // Launchers
    private ActivityResultLauncher<String[]> permissionsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        geminiApiClient = new GeminiApiClient();
        elevenLabsClient = new ElevenLabsClient();

        initSpeechRecognizer();
        initSensors();
        initViews();
        setupGestureDetector();
        setupLaunchers();
        setupListeners();

        checkPermissionsAndStart();
    }

    private void initSpeechRecognizer() {
        speechRecognizerManager = new SpeechRecognizerManager(this, new SpeechRecognizerManager.SpeechListener() {
            @Override
            public void onSpeechStarted() {
                if (!isGeminiLiveEnabled) return;
                runOnUiThread(() -> tvLiveStatus.setText("🎙️ Listening to you..."));
            }

            @Override
            public void onSpeechResult(String spokenText) {
                Log.d(TAG, "Speech recognized: " + spokenText);
                if (!isGeminiLiveEnabled || isAnalysisInProgress || isNarrating) {
                    return;
                }

                speechRecognizerManager.stopListening();
                runOnUiThread(() -> tvLiveStatus.setText("💬 \"" + spokenText + "\""));

                if (cameraPreviewView != null) {
                    Bitmap currentFrame = cameraPreviewView.getBitmap();
                    if (currentFrame != null) {
                        performGeminiAnalysis(currentFrame, false, spokenText);
                    } else {
                        Toast.makeText(MainActivity.this, "Camera feed is not ready.", Toast.LENGTH_SHORT).show();
                        startSpeechListeningIfReady();
                    }
                }
            }

            @Override
            public void onListeningStateChanged(boolean isListening) {
                runOnUiThread(() -> {
                    if (isGeminiLiveEnabled && !isAnalysisInProgress && !isNarrating && !speechRecognizerManager.isUserSpeaking()) {
                        updateLiveModeUI();
                    }
                });
            }

            @Override
            public void onError(String errorMsg) {
                Log.w(TAG, "Speech recognition error: " + errorMsg);
            }
        });
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    private void initViews() {
        btnSettings = findViewById(R.id.btnSettings);
        cameraPreviewView = findViewById(R.id.cameraPreviewView);

        layoutStatusBadge = findViewById(R.id.layoutStatusBadge);

        tvLiveStatus = findViewById(R.id.tvLiveStatus);

        cardResult = findViewById(R.id.cardResult);
        tvDescriptionResult = findViewById(R.id.tvDescriptionResult);
        layoutLoading = findViewById(R.id.layoutLoading);

        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        btnToggleGeminiLive = findViewById(R.id.btnToggleGeminiLive);
        btnTurnOffGemini = findViewById(R.id.btnTurnOffGemini);

        btnZoom1x = findViewById(R.id.btnZoom1x);
        btnZoom2x = findViewById(R.id.btnZoom2x);
        btnZoom5x = findViewById(R.id.btnZoom5x);

        btnCopyText = findViewById(R.id.btnCopyText);
        btnShareText = findViewById(R.id.btnShareText);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        updateLiveModeUI();
        updateZoomButtonsUI(1.0f);
    }

    private void setupGestureDetector() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (camera != null) {
                    float currentRatio = 1.0f;
                    if (camera.getCameraInfo().getZoomState().getValue() != null) {
                        currentRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                    }
                    float scaleFactor = detector.getScaleFactor();
                    float newZoomRatio = currentRatio * scaleFactor;

                    setCameraZoomRatio(newZoomRatio);
                    return true;
                }
                return false;
            }
        });

        cameraPreviewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void setupLaunchers() {
        permissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    Boolean cameraGranted = permissions.getOrDefault(Manifest.permission.CAMERA, false);
                    Boolean audioGranted = permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false);

                    if (Boolean.TRUE.equals(cameraGranted)) {
                        startCameraX();
                    } else {
                        Toast.makeText(this, "Camera permission is required for live feed.", Toast.LENGTH_LONG).show();
                    }

                    if (Boolean.TRUE.equals(audioGranted)) {
                        startSpeechListeningIfReady();
                    } else {
                        Toast.makeText(this, "Audio permission is required for speech interaction.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupListeners() {
        btnToggleGeminiLive.setOnClickListener(v -> handleMainButtonClick());
        btnTurnOffGemini.setOnClickListener(v -> toggleGeminiLiveMode());

        btnToggleFlash.setOnClickListener(v -> {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                isFlashOn = !isFlashOn;
                camera.getCameraControl().enableTorch(isFlashOn);
                btnToggleFlash.setIcon(ContextCompat.getDrawable(this, isFlashOn ? R.drawable.flash_on : R.drawable.flash_off));
                btnToggleFlash.setBackgroundColor(isFlashOn ? 0xFFC11F25 : 0xFF650014);
            } else {
                Toast.makeText(this, "Flash is not supported on this camera device.", Toast.LENGTH_SHORT).show();
            }
        });

        btnZoom1x.setOnClickListener(v -> setCameraZoomRatio(1.0f));
        btnZoom2x.setOnClickListener(v -> setCameraZoomRatio(2.0f));
        btnZoom5x.setOnClickListener(v -> setCameraZoomRatio(5.0f));

        btnCopyText.setOnClickListener(v -> copyResultToClipboard());

        btnShareText.setOnClickListener(v -> shareResultText());
    }

    private void handleMainButtonClick() {
        boolean isManualMode = SettingsActivity.isManualTriggerModeEnabled(this);

        if (!isGeminiLiveEnabled) {
            toggleGeminiLiveMode();
            return;
        }

        if (isManualMode) {
            if (isAnalysisInProgress || isNarrating) {
                Toast.makeText(this, "Gemini is currently analyzing or narrating.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (cameraPreviewView != null) {
                Bitmap currentFrame = cameraPreviewView.getBitmap();
                if (currentFrame != null) {
                    performGeminiAnalysis(currentFrame, false, null);
                } else {
                    Toast.makeText(this, "Camera feed is not ready.", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            toggleGeminiLiveMode();
        }
    }

    private void setCameraZoomRatio(float zoomRatio) {
        if (camera != null) {
            float maxZoom = 10.0f;
            float minZoom = 1.0f;
            if (camera.getCameraInfo().getZoomState().getValue() != null) {
                maxZoom = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
                minZoom = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
            }

            float clampedRatio = Math.max(minZoom, Math.min(zoomRatio, maxZoom));
            camera.getCameraControl().setZoomRatio(clampedRatio);

            currentZoomRatio = clampedRatio;
            updateZoomButtonsUI(clampedRatio);

            // Mark camera moved so Gemini triggers analysis when zoom settles
            hasCameraMoved = true;
            lastMotionTime = System.currentTimeMillis();
        }
    }

    private void updateZoomButtonsUI(float zoomRatio) {
        btnZoom1x.setBackgroundColor(Color.parseColor(Math.abs(zoomRatio - 1.0f) < 0.3f ? "#FFC11F25" : "#00000000"));
        btnZoom2x.setBackgroundColor(Color.parseColor(Math.abs(zoomRatio - 2.0f) < 0.3f ? "#FFC11F25" : "#00000000"));
        btnZoom5x.setBackgroundColor(Color.parseColor(Math.abs(zoomRatio - 5.0f) < 0.3f ? "#FFC11F25" : "#00000000"));
    }

    private void terminateInFlightWork() {
        autoAnalysisHandler.removeCallbacks(autoAnalysisRunnable);
        if (speechRecognizerManager != null) {
            speechRecognizerManager.stopListening();
        }
        if (elevenLabsClient != null) {
            elevenLabsClient.stopAudio();
        }
        if (geminiApiClient != null) {
            geminiApiClient.cancelPendingRequests();
        }
        isAnalysisInProgress = false;
        isNarrating = false;
        if (layoutLoading != null) {
            layoutLoading.setVisibility(View.GONE);
        }
    }

    private void toggleGeminiLiveMode() {
        isGeminiLiveEnabled = !isGeminiLiveEnabled;
        boolean isManualMode = SettingsActivity.isManualTriggerModeEnabled(this);

        if (isGeminiLiveEnabled) {
            cameraPreviewView.setVisibility(View.VISIBLE);

            if (!isManualMode) {
                // Force initial analysis when live auto mode starts
                hasCameraMoved = true;
                lastMotionTime = System.currentTimeMillis() - SETTLE_DELAY_MS;

                autoAnalysisHandler.removeCallbacks(autoAnalysisRunnable);
                autoAnalysisHandler.post(autoAnalysisRunnable);
                // Toast.makeText(this, "Gemini Live mode started!", Toast.LENGTH_SHORT).show();
            } else {
                autoAnalysisHandler.removeCallbacks(autoAnalysisRunnable);
                // Toast.makeText(this, "Gemini active (Manual mode).", Toast.LENGTH_SHORT).show();
            }

            startSpeechListeningIfReady();
        } else {
            terminateInFlightWork();
            // Toast.makeText(this, "Gemini turned off.", Toast.LENGTH_SHORT).show();
        }

        updateLiveModeUI();
    }

    private void updateLiveModeUI() {
        boolean showStatus = SettingsActivity.isShowStatusIndicatorEnabled(this);
        if (layoutStatusBadge != null) {
            layoutStatusBadge.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        }

        boolean isManualMode = SettingsActivity.isManualTriggerModeEnabled(this);
        boolean isListeningForSpeech = speechRecognizerManager != null && speechRecognizerManager.isListeningRequested();

        if (isManualMode) {
            if (isGeminiLiveEnabled) {
                btnToggleGeminiLive.setText("Analyze Scene");
                btnToggleGeminiLive.setBackgroundColor(Color.parseColor("#C11F25"));
                btnToggleGeminiLive.setIconResource(R.drawable.ic_sparkles);

                if (btnTurnOffGemini != null) {
                    btnTurnOffGemini.setVisibility(View.VISIBLE);
                }


                if (isNarrating) {
                    tvLiveStatus.setText("Narrating...");
                } else if (isAnalysisInProgress) {
                    tvLiveStatus.setText("Analyzing...");
                } else if (isListeningForSpeech) {
                    tvLiveStatus.setText("Listening...");
                } else {
                    tvLiveStatus.setText("Active");
                }
            } else {
                btnToggleGeminiLive.setText("Turn On Gemini AI");
                btnToggleGeminiLive.setBackgroundColor(Color.parseColor("#C11F25"));
                btnToggleGeminiLive.setIconResource(R.drawable.ic_sparkles);

                if (btnTurnOffGemini != null) {
                    btnTurnOffGemini.setVisibility(View.GONE);
                }


                tvLiveStatus.setText("Off");
            }
        } else {
            if (btnTurnOffGemini != null) {
                btnTurnOffGemini.setVisibility(View.GONE);
            }

            if (isGeminiLiveEnabled) {
                btnToggleGeminiLive.setText("Turn Off Gemini AI");
                btnToggleGeminiLive.setBackgroundColor(Color.parseColor("#650014"));
                btnToggleGeminiLive.setIconResource(R.drawable.ic_sparkles);


                if (isNarrating) {
                    tvLiveStatus.setText("Narrating...");
                } else if (isAnalysisInProgress) {
                    tvLiveStatus.setText("Analyzing...");
                } else if (isListeningForSpeech) {
                    tvLiveStatus.setText("Active (Listening)");
                } else {
                    tvLiveStatus.setText("Active");
                }
            } else {
                btnToggleGeminiLive.setText("Turn On Gemini AI");
                btnToggleGeminiLive.setBackgroundColor(Color.parseColor("#C11F25"));
                btnToggleGeminiLive.setIconResource(R.drawable.ic_sparkles);


                tvLiveStatus.setText("Off");
            }
        }
    }

    private void checkPermissionsAndStart() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        if (cameraGranted) {
            startCameraX();
        }

        if (audioGranted) {
            startSpeechListeningIfReady();
        }

        if (!cameraGranted || !audioGranted) {
            permissionsLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            });
        }
    }

    private void startSpeechListeningIfReady() {
        if (!isGeminiLiveEnabled) {
            if (speechRecognizerManager != null) {
                speechRecognizerManager.stopListening();
            }
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (!isAnalysisInProgress && !isNarrating && speechRecognizerManager != null) {
                speechRecognizerManager.startListening();
            }
        }
    }

    private void startCameraX() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());

                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                setCameraZoomRatio(currentZoomRatio);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting CameraX preview", e);
                Toast.makeText(this, "Failed to start camera feed.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private String getGeminiPrompt(String userSpeech) {
        String historyContext = HistoryManager.getRecentHistoryContext();
        StringBuilder promptBuilder = new StringBuilder();

        if (!historyContext.isEmpty()) {
            promptBuilder.append("Context of recent items/scenes previously inspected with camera:\n")
                    .append(historyContext)
                    .append("\n\n");
        }

        if (userSpeech != null && !userSpeech.trim().isEmpty()) {
            promptBuilder.append("The user said the following out loud:\n\"")
                    .append(userSpeech.trim())
                    .append("\"\n\n")
                    .append("You are an enthusiastic, expert tour guide.\n");

            if (lastAnalyzedBitmap != null) {
                promptBuilder.append("Two image frames are provided:\n")
                        .append("- Image 1: The current live camera view.\n")
                        .append("- Image 2: The previously analyzed frame from earlier in this session.\n\n")
                        .append("Analyze the user's request: if it is a question, follow-up, or comment about what was previously shown or described, refer to Image 2 (and recent context). If it refers to what is currently visible in front of them, refer to Image 1. If relevant, compare or relate both images.\n");
            } else {
                promptBuilder.append("An image frame of the current live camera view is provided.\n")
                        .append("Answer the user's question or respond directly to their comment based on what is visible in the image (and recent context if relevant).\n");
            }

            promptBuilder.append("Provide a response of an appropriate length for what was asked—it can be brief or detailed as needed depending on the request.\n")
                    .append("Do not use bullet points, headings, Markdown, or filler. Speak naturally as a spoken response.");
        } else {
            promptBuilder.append(GEMINI_LIVE_PROMPT);
            if (!historyContext.isEmpty()) {
                promptBuilder.append(" If applicable and appropriate, connect what is visible in the current camera view to the recent discoveries context above.");
            }
        }

        return promptBuilder.toString();
    }

    private void performGeminiAnalysis(Bitmap bitmap, boolean isLiveLoop, String userSpeech) {
        if (!isGeminiLiveEnabled || bitmap == null) return;

        // Strict Safety Check (Both Modes): Do not re-trigger if already running!
        if (isAnalysisInProgress || isNarrating) {
            if (!isLiveLoop) {
                Toast.makeText(this, "Gemini is currently analyzing or narrating.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        isAnalysisInProgress = true;
        if (speechRecognizerManager != null) {
            speechRecognizerManager.stopListening();
        }

        layoutLoading.setVisibility(View.VISIBLE);

        if (userSpeech != null && !userSpeech.isEmpty()) {
            tvLiveStatus.setText("Answering question...");
        } else if (isLiveLoop) {
            tvLiveStatus.setText("Analyzing...");
        } else {
            tvLiveStatus.setText("Analyzing...");
        }

        String apiKey = ApiKeyManager.getApiKey(this);
        String prompt = getGeminiPrompt(userSpeech);

        Bitmap prevBitmap = lastAnalyzedBitmap;

        // Note to Agent: Do not change the Gemini model being used.
        geminiApiClient.analyzeImage(apiKey, bitmap, prevBitmap, prompt, "gemini-3.8-flash", new GeminiApiClient.ApiCallback() {
            @Override
            public void onSuccess(String description) {
                if (!isGeminiLiveEnabled) {
                    isAnalysisInProgress = false;
                    isNarrating = false;
                    if (layoutLoading != null) {
                        layoutLoading.setVisibility(View.GONE);
                    }
                    return;
                }

                lastAnalyzedBitmap = bitmap;
                HistoryManager.addEntry(description);

                layoutLoading.setVisibility(View.GONE);
                tvDescriptionResult.setText(description);

                boolean showText = SettingsActivity.isShowDescriptionTextEnabled(MainActivity.this);
                cardResult.setVisibility(showText ? View.VISIBLE : View.GONE);

                // Speak generated text out loud using ElevenLabs API
                narrateWithElevenLabs(description);
            }

            @Override
            public void onError(String errorMessage) {
                isAnalysisInProgress = false;
                isNarrating = false;
                if (layoutLoading != null) {
                    layoutLoading.setVisibility(View.GONE);
                }

                updateLiveModeUI();
                startSpeechListeningIfReady();

                if (!isGeminiLiveEnabled) {
                    return;
                }

                Log.e(TAG, "Gemini Analysis Error: " + errorMessage);
                if (!isLiveLoop) {
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    tvDescriptionResult.setText("Error analyzing image: " + errorMessage);
                    boolean showText = SettingsActivity.isShowDescriptionTextEnabled(MainActivity.this);
                    cardResult.setVisibility(showText ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    private void narrateWithElevenLabs(String text) {
        if (!isGeminiLiveEnabled) {
            isAnalysisInProgress = false;
            isNarrating = false;
            if (layoutLoading != null) {
                layoutLoading.setVisibility(View.GONE);
            }
            updateLiveModeUI();
            return;
        }

        String elevenLabsKey = ApiKeyManager.getElevenLabsApiKey(this);
        if (elevenLabsKey == null || elevenLabsKey.isEmpty()) {
            isAnalysisInProgress = false;
            isNarrating = false;
            updateLiveModeUI();
            startSpeechListeningIfReady();
            return;
        }

        isNarrating = true;
        if (speechRecognizerManager != null) {
            speechRecognizerManager.stopListening();
        }
        tvLiveStatus.setText("🔊 Gemini Live: Narrating...");

        boolean isFemale = SettingsActivity.isFemaleVoiceEnabled(this);

        elevenLabsClient.speakText(this, elevenLabsKey, text, isFemale, new ElevenLabsClient.NarrationCallback() {
            @Override
            public void onStart() {
                if (!isGeminiLiveEnabled) {
                    elevenLabsClient.stopAudio();
                    isNarrating = false;
                    isAnalysisInProgress = false;
                    updateLiveModeUI();
                    return;
                }
                isNarrating = true;
                if (speechRecognizerManager != null) {
                    speechRecognizerManager.stopListening();
                }
                tvLiveStatus.setText("🔊 Gemini Live: Narrating...");
            }

            @Override
            public void onCompletion() {
                isNarrating = false;
                isAnalysisInProgress = false;
                updateLiveModeUI();
                startSpeechListeningIfReady();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "ElevenLabs narration error: " + errorMessage);
                isNarrating = false;
                isAnalysisInProgress = false;
                updateLiveModeUI();
                startSpeechListeningIfReady();
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isGeminiLiveEnabled) return;

        long now = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float gx = event.values[0];
            float gy = event.values[1];
            float gz = event.values[2];
            float rotationSpeed = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);

            if (rotationSpeed > GYRO_THRESHOLD) {
                hasCameraMoved = true;
                lastMotionTime = now;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (hasLastAccel) {
                float dx = Math.abs(x - lastAccel[0]);
                float dy = Math.abs(y - lastAccel[1]);
                float dz = Math.abs(z - lastAccel[2]);
                float accelDelta = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (accelDelta > ACCEL_THRESHOLD) {
                    hasCameraMoved = true;
                    lastMotionTime = now;
                }
            }

            lastAccel[0] = x;
            lastAccel[1] = y;
            lastAccel[2] = z;
            hasLastAccel = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void copyResultToClipboard() {
        String text = tvDescriptionResult.getText().toString();
        if (!text.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Gemini Description", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied insight to clipboard!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareResultText() {
        String text = tvDescriptionResult.getText().toString();
        if (!text.isEmpty()) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Gemini Lens AI Insight");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(shareIntent, "Share Discovery via"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        terminateInFlightWork();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean showStatus = SettingsActivity.isShowStatusIndicatorEnabled(this);
        if (layoutStatusBadge != null) {
            layoutStatusBadge.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        }

        boolean showText = SettingsActivity.isShowDescriptionTextEnabled(this);
        if (!showText) {
            cardResult.setVisibility(View.GONE);
        } else if (tvDescriptionResult.getText() != null && !tvDescriptionResult.getText().toString().isEmpty()) {
            cardResult.setVisibility(View.VISIBLE);
        }

        boolean isManualMode = SettingsActivity.isManualTriggerModeEnabled(this);
        updateLiveModeUI();

        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        if (isGeminiLiveEnabled) {
            if (!isManualMode) {
                autoAnalysisHandler.removeCallbacks(autoAnalysisRunnable);
                autoAnalysisHandler.post(autoAnalysisRunnable);
            } else {
                autoAnalysisHandler.removeCallbacks(autoAnalysisRunnable);
            }
            startSpeechListeningIfReady();
        } else {
            autoAnalysisHandler.removeCallbacks(autoAnalysisRunnable);
            if (speechRecognizerManager != null) {
                speechRecognizerManager.stopListening();
            }
        }
    }

    @Override
    protected void onDestroy() {
        terminateInFlightWork();
        if (speechRecognizerManager != null) {
            speechRecognizerManager.destroy();
        }
        super.onDestroy();
    }
}
