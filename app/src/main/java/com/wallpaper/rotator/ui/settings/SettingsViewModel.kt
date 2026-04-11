package com.wallpaper.rotator.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.service.RotationScheduler
import com.wallpaper.rotator.util.DisplayUtils
import com.wallpaper.rotator.util.WallpaperApply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val aspectRatioString: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val intervalHours: Float = 6f,
    val rotateOnSchedule: Boolean = true,
    val rotateOnUnlock: Boolean = false,
    val rotateOnBoot: Boolean = false,
    val removeDuplicatesOnImport: Boolean = true,
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
            prefs.rotateOnSchedule.collect { enabled ->
                _uiState.update { it.copy(rotateOnSchedule = enabled) }
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
            prefs.removeDuplicatesOnImport.collect { enabled ->
                _uiState.update { it.copy(removeDuplicatesOnImport = enabled) }
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
            val onSchedule = prefs.rotateOnSchedule.first()
            RotationScheduler.syncPeriodicRotation(app, onSchedule, hours)
        }
    }

    fun setRotateOnSchedule(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setRotateOnSchedule(enabled)
            val interval = prefs.rotationIntervalHours.first()
            RotationScheduler.syncPeriodicRotation(app, enabled, interval)
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

    fun setRemoveDuplicatesOnImport(enabled: Boolean) {
        viewModelScope.launch { prefs.setRemoveDuplicatesOnImport(enabled) }
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
                val (photo, _) = result
                val ok = WallpaperApply.applyPhotoAsWallpaper(app, photo, repository, prefs)
                if (ok) {
                    _uiState.update { it.copy(rotationMessage = "Wallpaper updated!") }
                } else {
                    _uiState.update { it.copy(rotationMessage = "Could not update wallpaper") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(rotationMessage = "Error: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(rotationMessage = null) }
    }
}
