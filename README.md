# Gemini Lens AI 🔍✨

An intelligent, real-time AI tour guide Android application powered by **Google Gemini AI** and **ElevenLabs Voice Synthesis**. **Gemini Lens AI** turns your smartphone camera into an interactive guide that observes your surroundings, provides insightful descriptions, and responds to your spoken questions in real time.

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
   
