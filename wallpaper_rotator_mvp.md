# Smart Wallpaper Rotator — MVP Spec

## Overview
An Android app that rotates wallpapers from your Google Photos library with intelligent auto-cropping. On-device pose detection (MoveNet) identifies whether each photo is a face, full body, or landscape, then applies the optimal crop to fit your phone's wallpaper aspect ratio without cramping the subject.

## Core Features

### 1. Photo Import
- **Source**: Google Photos folder, album, or individual photos
- **Crop mode selection**: When importing, user chooses crop mode *once* (Auto or Manual) — applies to all selected photos
- **Processing**:
  - **Auto crop**: Run pose detection on each photo, calculate crop box automatically, save metadata
  - **Manual crop**: User reviews each photo in crop editor, adjusts manually, saves metadata
- **Storage**: Photos stored locally in app cache; track crop metadata (crop box coords, subject type, crop method)

### 2. Smart Auto-Crop Engine
- **Model**: MoveNet Lightning or Thunder (TensorFlow Lite)
- **Detection logic**:
  - Run pose detection on each imported photo
  - If no pose detected → use landscape (vertical center crop)
  - If multiple poses detected (multiple people):
    - Attempt to find bounding box around all detected people (union of all pose bounds)
    - If multi-person crop would be too wide or awkward → fallback to primary person (closest to center, highest confidence)
  - If single pose detected:
    - Check face confidence (nose + eyes + ears all > 0.5)
    - If face exists:
      - Check if body extends far below face (vertical distance > 25% of image height AND lower limbs visible)
      - If full body → crop head-to-feet
      - Else → crop around face and shoulders
    - If no face → crop to visible body parts (shoulders/torso/legs)
  - Adjust final crop to match wallpaper aspect ratio (auto-detected from device display dimensions)

### 3. Photo Library & Management
- **Gallery view**: Thumbnails of all imported photos with current crop preview
- **Interaction model**: 
  - **Tap**: Opens crop editor to adjust crop for that photo
  - **Press and hold**: Enters select mode (UI switches to checkboxes) — long-press toggles that photo's selection
  - **In select mode, tap**: Single-tap toggles checkbox for that photo
- **Header actions** (visible when photos are selected):
  - **Delete**: Remove selected photos from library permanently
  - **Deselect from rotation**: Keep photos in library but toggle them off (won't be rotated)
- **Exit select mode**: Tap "Done" button or tap empty space

### 4. Rotation Settings
- **Device aspect ratio**: Auto-detected from display dimensions on app launch (e.g., 9:20 on Pixel phones)
- **Interval**: Spinner or number input (e.g., 1 hour, 6 hours, daily, weekly)
- **On unlock/boot**: Toggle whether phone unlock and/or device boot triggers rotation
- **Manual rotate**: Button to force next wallpaper immediately

### 5. Wallpaper Application
- **Set wallpaper**: On rotation trigger, pick next enabled photo from library (circular queue)
- **Apply crop**: Resize/crop the bitmap to fit device wallpaper dimensions, set as lock screen and/or home screen

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **ML**: TensorFlow Lite (MoveNet model bundled in app)
- **Storage**: Room database for photo metadata, app cache for image files
- **Scheduling**: WorkManager for rotation triggers
- **Google Photos**: MediaStore API to access library (no Google Photos API dependency)
- **Wallpaper**: WallpaperManager
- **Display metrics**: DisplayMetrics to auto-detect device screen aspect ratio

## Design System & Theming
- **Material Design 3**: Use MD3 components (buttons, cards, FABs, chips) from Material3 Compose library
- **Material You (Dynamic Color)**: On Android 12+, use system-generated color palette from device wallpaper via `dynamicColorScheme()`. Fallback to standard MD3 color scheme on older devices
- **Cohesive UI**: All surfaces, text, and accent colors derive from the Material You palette for a unified, elegant look
- **Shapes**: Use MD3 shape tokens (`RoundedCornerShape`) — large rounded corners on cards and modals, medium on buttons/inputs, small on chips
- **Motion**: Smooth transitions and animations throughout (crop editor pan/zoom, photo gallery scroll, wallpaper apply feedback)
- **Accessibility**: Ensure color contrast meets WCAG AA standards; support system font scaling
- **Typography**: Use Material3 type scale for consistency (headline, title, body, label families)

## Database Schema
```
PhotoMetadata
  - photoId (PK)
  - filePath (local cache path)
  - cropX, cropY, cropWidth, cropHeight (crop box in original image coords)
  - detectedSubjectType (enum: FACE, FULL_BODY, LANDSCAPE)
  - cropMethod (enum: AUTO, MANUAL)
  - isEnabled (boolean)
  - createdAt (timestamp)
```

## Screens
1. **Import screen**: Photo picker + crop mode selector (Auto/Manual toggle applies to all selected photos)
2. **Library screen**: Gallery grid with crop previews. Tap to edit crop, press-hold to enter select mode. In select mode: checkboxes appear, header shows "Delete" and "Deselect from rotation" buttons
3. **Crop editor screen**: Full-screen image with draggable crop box, pinch zoom, done/cancel buttons
4. **Settings screen**: Device aspect ratio (auto-detected, displayed as read-only), interval spinner, unlock/boot toggles, manual rotate button

## Pseudo-Flow

### Import Flow
1. User selects Google Photos folder, album, or individual photos
2. User chooses crop mode *once*: "Auto crop" or "Manual crop"
3. For each selected photo:
   - If "Auto crop": Run pose detection → calculate crop box → save metadata
   - If "Manual crop": Show crop editor for each photo, user adjusts, save metadata
4. Store photos in app cache, record metadata in database

### Library Flow
1. Show thumbnail gallery with current crop preview
2. User taps photo → opens crop editor to adjust crop
3. User presses and holds photo → enters select mode (checkboxes appear)
4. In select mode:
   - Single-tap toggles checkbox
   - Header shows "Delete" (remove from app) and "Deselect from rotation" (toggle off) buttons
5. Tap "Done" to exit select mode

### Rotation Flow
1. WorkManager trigger (on schedule, unlock, or boot)
2. Query database for next enabled photo (circular queue)
3. Load photo bitmap from cache
4. Extract crop box from metadata, apply to bitmap
5. Resize bitmap to device wallpaper dimensions (auto-detected aspect ratio)
6. Set wallpaper via WallpaperManager
7. Update last-rotated timestamp

### Auto-Crop Logic (Pseudocode)
```
poses = moveNet.detect(image)
wallpaperAR = getDeviceWallpaperAspectRatio()  // Auto-detected

if poses.isEmpty():
  crop = landscapeCrop(image, wallpaperAR)
else if poses.size() > 1:
  // Multiple people detected
  allPoses = poses
  multiPersonBounds = union(allPoses)  // Bounding box around all people
  
  if multiPersonBounds.width / multiPersonBounds.height > wallpaperAR + 0.5:
    // Too wide for aspect ratio, fallback to primary person
    pose = allPoses[0]  // Closest to center, highest confidence
  else:
    // Use multi-person crop
    crop = personGroupCrop(multiPersonBounds, wallpaperAR)
    return crop
else:
  // Single person
  pose = poses[0]
  
  hasFace = (
    pose[NOSE].score > 0.5 &&
    pose[L_EYE].score > 0.5 &&
    pose[R_EYE].score > 0.5
  )
  
  if !hasFace:
    crop = bodyBoundingBox(pose, wallpaperAR)
  else:
    hasLowerBody = (
      pose[L_KNEE].score > 0.3 ||
      pose[R_KNEE].score > 0.3 ||
      pose[L_ANKLE].score > 0.3 ||
      pose[R_ANKLE].score > 0.3
    )
    
    verticalDistance = lowestLimb(pose).y - faceCenter(pose).y
    isFullBody = hasLowerBody && verticalDistance > imageHeight * 0.25
    
    if isFullBody:
      crop = fullBodyCrop(pose, wallpaperAR)
    else:
      crop = faceCrop(pose, wallpaperAR)

return crop
```

## MVP Scope (Phase 1)
- ✓ Import from Google Photos (file picker)
- ✓ Auto-crop detection (MoveNet)
- ✓ Manual crop editor
- ✓ Photo library with gallery view
- ✓ Toggle photos on/off
- ✓ Delete photos
- ✓ Rotation on schedule (WorkManager)
- ✓ Set wallpaper (lock screen + home screen)
- ✓ Settings: interval + unlock/boot toggles

## Out of Scope (Future)
- Cloud sync (backup to Drive)
- Multi-device sync
- Animated wallpapers
- Batch import optimization
- AI pose refinement (e.g., hands, face landmarks)
- Widget for quick rotate
