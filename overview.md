# Wallpaper Rotator -- Codebase Overview

## What This App Does

An Android app that rotates device wallpapers from imported photos. On-device ML pose detection (ML Kit / MoveNet) identifies whether each photo contains a face, full body, or landscape, then computes an optimal crop to fit the phone's wallpaper aspect ratio. Photos rotate on a schedule via WorkManager, and optionally on screen unlock or device boot.

## Toolchain & Versions

| Component | Version | Notes |
|---|---|---|
| Kotlin | 1.9.22 | JVM target 17 |
| Kotlin Compiler Extension | 1.5.10 | Must match Kotlin 1.9.22 |
| Android Gradle Plugin | 8.2.2 | |
| Gradle | 8.5 | Wrapper checked in; `gradle.properties` sets `-Xmx4096m` |
| KSP | 1.9.22-1.0.17 | Used for Room annotation processing |
| Compile SDK / Target SDK | 34 | |
| Min SDK | 26 | Android 8.0 |
| Compose BOM | 2024.02.00 | Governs all `androidx.compose.*` versions |
| Room | 2.6.1 | |
| WorkManager | 2.9.0 | |
| Navigation Compose | 2.7.7 | |
| Lifecycle | 2.7.0 | `runtime-compose` + `viewmodel-compose` |
| DataStore Preferences | 1.0.0 | |
| ML Kit Pose Detection | 18.0.0-beta4 | Bundled model declared in manifest |
| Coil Compose | 2.5.0 | Image loading |

**Important version constraint:** The Kotlin version, KSP version, and Compose compiler extension version are tightly coupled. If you bump Kotlin, you must update KSP (`{kotlinVersion}-1.0.x`) and the Compose compiler extension to a compatible release.

## Build & Run

```bash
# Debug APK (outputs to app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug

# If the daemon OOMs, use:
./gradlew assembleDebug --no-daemon
```

`local.properties` must contain `sdk.dir` pointing to your Android SDK (not checked into git).

## Package & Directory Structure

All source lives under `app/src/main/java/com/wallpaper/rotator/`. The package is `com.wallpaper.rotator`.

```
com.wallpaper.rotator/
├── WallpaperRotatorApp.kt          # Application subclass; manual DI container + ScreenUnlockReceiver lifecycle
├── MainActivity.kt                  # Single-activity Compose host; re-schedules rotation on app start
│
├── data/
│   ├── db/
│   │   ├── PhotoMetadata.kt         # Room @Entity + SubjectType/CropMethod enums + TypeConverters
│   │   ├── PhotoMetadataDao.kt       # Room @Dao interface
│   │   └── AppDatabase.kt           # Room database singleton
│   └── repository/
│       └── PhotoRepository.kt       # Data operations: import, delete, toggle, rotation queue
│
├── ml/
│   ├── PoseDetector.kt              # ML Kit PoseDetection wrapper (single-image mode)
│   └── CropEngine.kt                # Auto-crop algorithm: landscape/face/full-body detection + aspect fit
│
├── service/
│   ├── RotationScheduler.kt         # WorkManager scheduling (periodic + one-shot)
│   ├── WallpaperRotationWorker.kt   # CoroutineWorker: loads next photo, crops, sets wallpaper
│   ├── BootReceiver.kt              # BroadcastReceiver for BOOT_COMPLETED
│   └── ScreenUnlockReceiver.kt      # BroadcastReceiver for USER_PRESENT
│
├── ui/
│   ├── theme/
│   │   ├── Color.kt                 # MD3 light/dark color tokens
│   │   ├── Type.kt                  # MD3 typography scale
│   │   └── Theme.kt                 # WallpaperRotatorTheme: dynamic color on API 31+, static fallback
│   ├── navigation/
│   │   └── NavGraph.kt              # Screen sealed class + NavHost routes
│   ├── import_photos/
│   │   ├── ImportScreen.kt          # Photo picker + crop mode selector
│   │   └── ImportViewModel.kt       # Manages import state + auto-crop processing
│   ├── library/
│   │   ├── LibraryScreen.kt         # Grid gallery + select mode + bulk actions
│   │   └── LibraryViewModel.kt      # Photo list + selection state
│   ├── crop/
│   │   ├── CropEditorScreen.kt      # Full-screen image + draggable/resizable crop overlay
│   │   └── CropEditorViewModel.kt   # Load/save crop metadata
│   └── settings/
│       ├── SettingsScreen.kt         # Interval, toggles, manual rotate, display info
│       └── SettingsViewModel.kt      # Preferences read/write + immediate rotation
│
└── util/
    ├── DisplayUtils.kt              # Screen dimensions + aspect ratio helpers
    └── PreferencesManager.kt        # DataStore wrapper for rotation settings
```

## Architecture Patterns

### Dependency Injection

Manual DI via `WallpaperRotatorApp`. All shared singletons are `lazy` properties on the Application class:

```kotlin
// WallpaperRotatorApp.kt
val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
val photoRepository: PhotoRepository by lazy { PhotoRepository(database.photoMetadataDao(), this) }
val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
val poseDetector: PoseDetector by lazy { PoseDetector() }
val cropEngine: CropEngine by lazy { CropEngine(poseDetector) }
```

ViewModels access these via `(application as WallpaperRotatorApp).propertyName`. All ViewModels extend `AndroidViewModel` for this reason.

### Navigation

Navigation Compose with a `sealed class Screen` defining four routes:

| Route | Screen | Arguments |
|---|---|---|
| `library` | LibraryScreen | none (start destination) |
| `import` | ImportScreen | none |
| `crop_editor/{photoId}` | CropEditorScreen | `photoId: Long` |
| `settings` | SettingsScreen | none |

Screen composables receive navigation callbacks as lambda parameters (e.g., `onNavigateBack`, `onNavigateToCrop`). They do not hold `NavController` references.

### Data Flow

- **Room** stores `PhotoMetadata` entities. The DAO exposes `Flow<List<PhotoMetadata>>` for reactive UI updates and suspend functions for writes.
- **DataStore Preferences** stores rotation settings (interval, toggles, last rotated index). `PreferencesManager` exposes `Flow` properties and suspend setters.
- **ViewModels** collect these Flows into `MutableStateFlow<UiState>` data classes, which Compose screens observe via `collectAsState()`.

### Theming

Material 3 with Material You dynamic color:
- On API 31+ (Android 12): uses `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` derived from the user's wallpaper
- Below API 31: falls back to a static teal-green color scheme defined in `Color.kt`
- Typography uses the default MD3 type scale in `Type.kt`
- All UI uses `MaterialTheme.colorScheme.*` tokens -- no hardcoded colors in composables

### Photo Storage

Imported photos are copied to `context.filesDir/photos/` with UUID filenames. The `PhotoMetadata` entity stores the absolute `filePath` to this local copy. Crop coordinates (`cropX`, `cropY`, `cropWidth`, `cropHeight`) are in original image pixel space.

## Key Domain Types

```kotlin
enum class SubjectType { FACE, FULL_BODY, LANDSCAPE }
enum class CropMethod  { AUTO, MANUAL }

data class PhotoMetadata(
    val photoId: Long,           // auto-generated PK
    val filePath: String,        // absolute path to cached image
    val cropX: Float,            // crop box origin X (pixels in original image)
    val cropY: Float,            // crop box origin Y
    val cropWidth: Float,        // crop box width
    val cropHeight: Float,       // crop box height
    val detectedSubjectType: SubjectType,
    val cropMethod: CropMethod,
    val isEnabled: Boolean,      // included in rotation queue
    val createdAt: Long          // epoch millis
)
```

## Auto-Crop Algorithm (CropEngine)

The core ML logic in `CropEngine.calculateAutoCrop()`:

1. Run ML Kit pose detection on the bitmap
2. If no pose detected -> **landscape** center crop
3. If pose detected, check face confidence (nose + both eyes > 0.5):
   - No face -> bounding box around visible body parts
   - Face detected -> check for full body (lower limbs visible AND vertical span > 25% image height):
     - Full body -> head-to-feet crop with padding
     - Face only -> face + shoulders crop
4. Fit the resulting bounding box to the device's wallpaper aspect ratio (height/width)
5. Clamp to image bounds (re-deriving the other dimension after each clamp to preserve AR)

Aspect ratio is always `height / width` (portrait orientation, e.g., ~2.0 for a typical phone).

## Rotation System

- **RotationScheduler** wraps WorkManager. `scheduleRotation()` creates a `PeriodicWorkRequest` (minimum 15 minutes). `triggerImmediate()` fires a one-shot worker.
- **WallpaperRotationWorker** is a `CoroutineWorker` that: loads the next enabled photo (circular queue via `lastRotatedIndex`), applies the stored crop, scales preserving aspect ratio (fill + center-crop to exact screen dims), and calls `WallpaperManager.setBitmap()` for both home and lock screen.
- **BootReceiver** re-schedules rotation on `BOOT_COMPLETED` and optionally triggers immediate rotation.
- **ScreenUnlockReceiver** fires on `USER_PRESENT` if the unlock toggle is enabled. Dynamically registered/unregistered in `WallpaperRotatorApp` (Application-scoped, so it survives Activity destruction while the device is locked).
- **MainActivity** re-schedules periodic rotation on every app start as a safety net against lost WorkManager jobs (e.g., after reboot).

## Consistency Rules

When working in this codebase, follow these patterns:

- **UI state:** Each screen has a `data class XxxUiState` and a `XxxViewModel` that exposes `StateFlow<XxxUiState>`. Screens collect with `collectAsState()`.
- **Navigation:** Add new routes to the `Screen` sealed class in `NavGraph.kt`. Screen composables take navigation lambdas, never `NavController`.
- **Repository access:** Go through `PhotoRepository`, not the DAO directly, from ViewModels.
- **Preferences:** Add new keys to `PreferencesManager` with matching `Flow` property + suspend setter.
- **Coroutines:** IO work uses `withContext(Dispatchers.IO)`. DAO calls are suspend functions. ViewModels use `viewModelScope`.
- **Theming:** Use `MaterialTheme.colorScheme.*` and `MaterialTheme.typography.*` exclusively. Never hardcode `Color(0xFF...)` in composables.
- **Image loading:** Use Coil's `AsyncImage` composable with `File(photo.filePath)` as the model. Library thumbnails use a custom `CropTransformation` to show the stored crop region rather than a generic center crop.
- **Gradle:** Dependencies without explicit versions are governed by the Compose BOM. Room and other non-Compose deps have explicit versions in `app/build.gradle.kts`.

## Manifest Permissions

```xml
SET_WALLPAPER                    -- required to change wallpaper
RECEIVE_BOOT_COMPLETED           -- re-schedule rotation after reboot
READ_MEDIA_IMAGES                -- photo picker (API 33+)
READ_EXTERNAL_STORAGE (max 32)   -- photo picker (API 26-32)
```

The ML Kit pose model is declared for auto-download:
```xml
<meta-data android:name="com.google.mlkit.vision.DEPENDENCIES" android:value="pose" />
```

## What's Not Built Yet

Refer to `wallpaper_rotator_mvp.md` "Out of Scope" section. Notable gaps in the current MVP:

- No runtime permission request UI (READ_MEDIA_IMAGES needs to be requested at runtime on API 33+)
- No unit or integration tests
- No ProGuard/R8 minification enabled for release builds
- The crop editor supports drag-to-move and corner-drag resize but not pinch-to-zoom
