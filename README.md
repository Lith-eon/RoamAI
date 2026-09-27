# RoamAI

An AI tour guide Android application powered by **Google Gemini AI** and **ElevenLabs Voice Synthesis**. **RoamAI** turns your smartphone camera into an interactive guide that observes your surroundings, provides insightful descriptions, and responds to your spoken questions in real time. The purpose of this app is to get people more excited about exploring and more interested in the stuff around them - whether it be an iconic or rare object or place, or an everyday piece of infrastructure.

---

## 🌟 Key Features

* 🎥 **Real-Time AI Vision Analysis**: Powered by Gemini (`gemini-3.8-flash` / `gemini-1.5-flash`) to analyze live camera frames and generate brief, insightful tour-guide descriptions of items, landmarks, or scenes.
* 🎙️ **Voice Interaction & Speech Queries**: Ask questions out loud using speech recognition. Ask follow-ups or inquire about specific details in the camera view or previous discoveries.
* 🔊 **Realistic Voice Narration**: Integrated with **ElevenLabs TTS API** for lifelike audio narration. Supports male and female voice selection with speed optimization.
* 📐 **Sensor-Based Motion & Settling Detection**: Uses hardware sensors (Accelerometer & Gyroscope) to detect when the camera moves and settles, triggering automatic analysis without button clutter.
* 🔍 **Pinch-to-Zoom & Quick Toggles**: Smooth camera zoom using multi-touch gestures or quick 1x / 2x / 5x preset buttons built with Android CameraX.
* 🧠 **Session Context Memory**: Tracks recent discoveries in memory so Gemini can answer contextual questions (*"What was that item I looked at earlier?"*).
* ⚙️ **Flexible Trigger Modes & Customization**:
  * **Automatic Live Mode**: Automatic vision analysis when camera settles.
  * **Manual Trigger Mode**: Tap to analyze on demand.
  * Togglable status badges, description text overlays, and voice choices in Settings.
* 📋 **Copy & Share**: Quick actions to copy insights to your clipboard or share text with other apps.

---

## 🛠️ Tech Stack & Architecture

* **Languages**: Java & Kotlin
* **SDKs & Frameworks**:
  * **Android SDK**: Target 36, Min SDK 24
  * **CameraX**: `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`
  * **Networking**: OkHttp 3 & Retrofit
  * **AI & Voice Services**:
    * Google Gemini API (`generativelanguage.googleapis.com`)
    * ElevenLabs Text-to-Speech API
    * Android `SpeechRecognizer` API
  * **Sensors**: Android Hardware `SensorManager` (Accelerometer & Gyroscope)
  * **UI Components**: Google Material Components 3, Translucent HUD UI, Custom Preferences

---

## 🚀 Getting Started

### Prerequisites

1. **Android Studio**: Latest version recommended.
2. **Android Device or Emulator**: Physical Android device with a camera and microphone recommended for live camera and speech feature testing.
3. **API Keys**:
   * **Gemini API Key**: Obtain from [Google AI Studio](https://aistudio.google.com/).
   * **ElevenLabs API Key**: Obtain from [ElevenLabs](https://elevenlabs.io/).

---

### Setup Instructions

1. **Clone the Repository**:
   ```bash
   git clone [https://github.com/Lith-eon/RoamAI.git](https://github.com/Lith-eon/RoamAI.git)
2. **Configure API Keys**:
   Add your API keys to `local.properties` in the project root directory (or create a `.env` file):
   > *Note: The Secrets Gradle Plugin automatically injects these properties into `BuildConfig.GEMINI_API_KEY` and `BuildConfig.ELEVENLABS_API_KEY` at build time.*

3. **Open in Android Studio**:
   * Launch Android Studio.
   * Select **Open** and choose the project directory.
   * Sync Gradle files when prompted.

4. **Build and Run**:
   * Connect an Android device (or launch an emulator with camera & microphone support).
   * Click **Run** (`Shift + F10`) to install and launch the app.
   * Grant Camera and Microphone permissions when requested.

---

## 📱 Usage Guide

1. **Start Gemini Live**:
   * Tap **Turn On Gemini AI** at the bottom of the screen to activate live camera analysis and speech recognition.
2. **Point & Discover**:
   * Aim your camera at an object, landmark, or scene. Once the device holds still for a brief moment, Gemini automatically analyzes the scene and narrates the insight out loud.
3. **Ask Questions**:
   * Speak naturally into your microphone (e.g., *"What is this object used for?"* or *"Compare this to what I showed you earlier"*). Gemini will respond using visual context and your spoken prompt.
4. **Zoom & Flashlight Controls**:
   * Use the **1x / 2x / 5x** buttons on the right or pinch on the camera feed to adjust camera zoom.
   * Toggle the top flashlight icon when in low-light environments.
5. **Adjust Settings**:
   * Tap the **Settings** gear icon in the top right to switch between **Automatic** and **Manual** trigger modes, toggle status indicators, or change the ElevenLabs narration voice (Male/Female).



   
