# 📱 Wallpaper Rotator

<p align="center">
  <b>A smart, privacy-first Android wallpaper rotator with intelligent on-device auto-cropping.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Design-Material%203%20Dynamic-black?style=flat-square" alt="Material 3" />
  <img src="https://img.shields.io/badge/ML-Google%20ML%20Kit-FF6F00?style=flat-square&logo=google&logoColor=white" alt="ML Kit" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-informational?style=flat-square" alt="Min SDK" />
</p>

---

## ✨ Overview

Most wallpaper rotators simply take arbitrary images and blindly center-crop or stretch them to fit your screen—often cutting off people's heads, faces, or important details.

**Wallpaper Rotator** solves this with **on-device computer vision**. Using Google ML Kit Pose Detection, the app understands what’s inside each photo—identifying whether it’s a portrait, full-body shot, or landscape—and dynamically computes an aesthetically optimal crop tailored to your device's exact screen aspect ratio.

All processing happens **100% on your device**. No photos or data ever leave your phone.

---

## 🚀 Key Features

<table>
  <tr>
    <td width="50%">
      <h3>🧠 Intelligent Auto-Crop</h3>
      <ul>
        <li><b>On-device Pose Detection</b> powered by Google ML Kit.</li>
        <li>Distinguishes between <b>close-up faces</b>, <b>full body postures</b>, and <b>landscapes</b>.</li>
        <li>Calculates optimal framing around subjects without awkward limb or head cutoffs.</li>
        <li>Auto-adapts to any device's screen aspect ratio (e.g. 20:9, 19.5:9, 16:9).</li>
      </ul>
    </td>
    <td width="50%">
      <h3>✂️ Interactive Crop Studio</h3>
      <ul>
        <li>Full-screen crop inspector with live ratio constraints.</li>
        <li>Smooth drag-to-reposition framing box.</li>
        <li>Manual override support for any photo at any time.</li>
        <li>Instantly test-apply any wallpaper directly to home and lock screens.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🔄 Flexible Rotation Engine</h3>
      <ul>
        <li><b>Scheduled Background Rotation</b>: Reliable battery-friendly rotation via Android <code>WorkManager</code>.</li>
        <li><b>Rotate on Screen Unlock</b>: Wake your phone to a fresh photo every time.</li>
        <li><b>Rotate on Device Boot</b>: Seamlessly resumes after restart.</li>
        <li><b>One-Tap Force Rotate</b>: Trigger the next wallpaper on demand.</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🖼️ Streamlined Library & UI</h3>
      <ul>
        <li><b>High-Performance Gallery</b>: Custom-generated crop thumbnail cache for instant, 60fps scrolling.</li>
        <li><b>Rotation Queue Controls</b>: Toggle photos in/out of rotation with one tap.</li>
        <li><b>Duplicate Detection</b>: Prevents re-importing identical images.</li>
        <li><b>Material You Dynamic Theming</b>: Adapts color accents from your current wallpaper (Android 12+).</li>
      </ul>
    </td>
  </tr>
</table>

---

## 🛠️ How It Works

```
   Import Photos
         │
         ▼
 ┌───────────────┐      No Pose
 │ ML Kit Pose   │ ─────────────────►  Landscape Center Crop
 │ Detection     │
 └───────┬───────┘
         │ Pose Detected
         ▼
 ┌───────────────┐   Lower limbs visible & vertical span > 25%
 │ Subject Type  │ ──────────────────────────────────────────► Full-Body Framing
 │ Analysis      │
 └───────┬───────┘
         │ Face only
         ▼
   Face & Shoulders Crop
         │
         ▼
 ┌────────────────────────────────────────┐
 │ Fit & Clamp to Device Aspect Ratio     │
 └───────────────────┬────────────────────┘
                     ▼
 ┌────────────────────────────────────────┐
 │ WorkManager Periodic Rotation Queue    │
 └────────────────────────────────────────┘
```

1. **Detection**: Upon import, photos run through `CropEngine` which checks for key landmarks (nose, eyes, shoulders, hips, knees, ankles).
2. **Subject Classification**:
   - **Face / Portrait**: Bounds around head and shoulders with upper breathing room.
   - **Full Body**: Includes full vertical posture down to feet with proportional padding.
   - **Landscape / Scenic**: Falls back to balanced center framing if no human subject is present.
3. **Aspect Fitting**: The bounding box expands or constrains to match the phone's native wallpaper aspect ratio without stretching pixel data.
4. **Rotation & Application**: `WallpaperRotationWorker` pulls from a circular Room database queue and safely sets the cropped bitmap via Android's `WallpaperManager`.

---

## 🏗️ Architecture & Tech Stack

This project follows modern Android architecture best practices with unidirectional data flow (UDF) and clean separation of concerns:

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
- **Image Pipeline**: [Coil](https://coil-kt.github.io/coil/) with custom bitmap crop transformations
- **Machine Learning**: [Google ML Kit Pose Detection](https://developers.google.com/ml-kit/vision/pose-detection) (`18.0.0-beta4`)
- **Local Persistence**: [Room](https://developer.android.com/training/data-storage/room) (SQLite) + [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **State & Concurrency**: Kotlin Coroutines & `StateFlow`

```
app/src/main/java/com/wallpaper/rotator/
├── data/           # Room entities, DAOs, and repository layer
├── ml/             # Pose detector wrapper & auto-crop geometry engine
├── service/        # WorkManager worker & BroadcastReceivers (boot / screen unlock)
├── ui/
│   ├── crop/       # Interactive crop canvas & manual adjustment
│   ├── import/     # Photo picker & batch processing
│   ├── library/    # High-performance grid gallery & selection actions
│   ├── settings/   # Custom interval configuration & rotation preferences
│   └── theme/      # Material 3 dynamic color scheme tokens
└── util/           # Display metrics, thumbnail generation, wallpaper applier
```

---

## 📥 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 34 (compileSdk)
- Test device or emulator running Android 8.0+ (API 26+)

### Build & Run

```bash
# Clone the repository
git clone https://github.com/jain-aniket/homescreen-rotator.git
cd homescreen-rotator

# Ensure local.properties points to your Android SDK:
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Build the debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Privacy & Permissions

This app requires only standard permissions necessary for its core utility:
* `SET_WALLPAPER`: To apply chosen images to the lock and home screen.
* `RECEIVE_BOOT_COMPLETED`: To re-register background rotation after rebooting.
* `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`: To let you pick photos from your gallery.

**Zero analytics. Zero network calls. Zero third-party trackers.**
