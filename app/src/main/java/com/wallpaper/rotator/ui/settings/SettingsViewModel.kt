package com.wallpaper.rotator.ui.settings

import android.app.Application
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.service.RotationScheduler
import com.wallpaper.rotator.util.DisplayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

data class SettingsUiState(
    val aspectRatioString: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val intervalHours: Float = 6f,
    val rotateOnUnlock: Boolean = false,
    val rotateOnBoot: Boolean = false,
    val enabledPhotoCount: Int = 0,
    val rotationMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WallpaperRotatorApp
    private val prefs = app.preferencesManager
    private val repository = app.photoRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val (w, h) = DisplayUtils.getWallpaperDimensions(application)
        val arString = DisplayUtils.getAspectRatioString(application)
        _uiState.update { it.copy(aspectRatioString = arString, screenWidth = w, screenHeight = h) }

        viewModelScope.launch {
            prefs.rotationIntervalHours.collect { hours ->
                _uiState.update { it.copy(intervalHours = hours) }
            }
        }
        viewModelScope.launch {
            prefs.rotateOnUnlock.collect { enabled ->
                _uiState.update { it.copy(rotateOnUnlock = enabled) }
            }
        }
        viewModelScope.launch {
            prefs.rotateOnBoot.collect { enabled ->
                _uiState.update { it.copy(rotateOnBoot = enabled) }
            }
        }
        viewModelScope.launch {
            repository.getEnabledPhotos().collect { photos ->
                _uiState.update { it.copy(enabledPhotoCount = photos.size) }
            }
        }
    }

    fun setInterval(hours: Float) {
        viewModelScope.launch {
            prefs.setRotationInterval(hours)
            RotationScheduler.scheduleRotation(app, hours)
        }
    }

    fun setRotateOnUnlock(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setRotateOnUnlock(enabled)
            // Receiver registration handled via Flow collection in WallpaperRotatorApp
        }
    }

    fun setRotateOnBoot(enabled: Boolean) {
        viewModelScope.launch { prefs.setRotateOnBoot(enabled) }
    }

    fun rotateNow() {
        viewModelScope.launch {
            try {
                val lastIndex = prefs.lastRotatedIndex.first()
                val result = repository.getNextForRotation(lastIndex)
                if (result == null) {
                    _uiState.update { it.copy(rotationMessage = "No enabled photos to rotate") }
                    return@launch
                }
                val (photo, newIndex) = result
                withContext(Dispatchers.IO) {
                    val file = File(photo.filePath)
                    if (!file.exists()) return@withContext

                    val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext
                    val crop = RectF(photo.cropX, photo.cropY, photo.cropX + photo.cropWidth, photo.cropY + photo.cropHeight)

                    val cropLeft = crop.left.toInt().coerceIn(0, original.width - 1)
                    val cropTop = crop.top.toInt().coerceIn(0, original.height - 1)
                    val cropW = crop.width().toInt().coerceAtMost(original.width - cropLeft)
                    val cropH = crop.height().toInt().coerceAtMost(original.height - cropTop)

                    if (cropW <= 0 || cropH <= 0) {
                        original.recycle()
                        return@withContext
                    }

                    val cropped = Bitmap.createBitmap(original, cropLeft, cropTop, cropW, cropH)
                    if (cropped !== original) original.recycle()

                    val (targetW, targetH) = DisplayUtils.getWallpaperDimensions(app)

                    // Scale preserving aspect ratio, filling the target (may overshoot one dimension).
                    // Ceil after multiply: truncating with toInt() can undershoot target dimensions and
                    // break the center-crop createBitmap step.
                    val scaleRatio = max(
                        targetW.toDouble() / cropped.width,
                        targetH.toDouble() / cropped.height
                    )
                    val scaledW = ceil(cropped.width * scaleRatio).toInt()
                    val scaledH = ceil(cropped.height * scaleRatio).toInt()
                    val scaled = Bitmap.createScaledBitmap(cropped, scaledW, scaledH, true)
                    if (scaled !== cropped) cropped.recycle()

                    // Center-crop to exact screen dimensions
                    val cropX = (scaledW - targetW) / 2
                    val cropY = (scaledH - targetH) / 2
                    val wallpaperBitmap = Bitmap.createBitmap(scaled, cropX, cropY, targetW, targetH)
                    if (wallpaperBitmap !== scaled) scaled.recycle()

                    val wallpaperManager = WallpaperManager.getInstance(app)
                    wallpaperManager.setBitmap(wallpaperBitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                    wallpaperBitmap.recycle()
                }
                prefs.setLastRotatedIndex(newIndex)
                _uiState.update { it.copy(rotationMessage = "Wallpaper updated!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(rotationMessage = "Error: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(rotationMessage = null) }
    }
}
