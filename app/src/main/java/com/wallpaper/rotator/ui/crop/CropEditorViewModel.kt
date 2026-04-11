package com.wallpaper.rotator.ui.crop

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.data.db.PhotoMetadata
import com.wallpaper.rotator.data.db.SubjectType
import com.wallpaper.rotator.util.DisplayUtils
import com.wallpaper.rotator.util.WallpaperApply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CropEditorUiState(
    val photo: PhotoMetadata? = null,
    val cropRect: RectF = RectF(),
    val subjectType: SubjectType = SubjectType.LANDSCAPE,
    val aspectRatio: Float = 2f,
    val imageWidth: Float = 0f,
    val imageHeight: Float = 0f,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
)

class CropEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WallpaperRotatorApp
    private val repository = app.photoRepository

    private val _uiState = MutableStateFlow(CropEditorUiState())
    val uiState: StateFlow<CropEditorUiState> = _uiState.asStateFlow()

    fun loadPhoto(photoId: Long) {
        viewModelScope.launch {
            val photo = repository.getPhotoById(photoId) ?: return@launch
            val ar = DisplayUtils.getWallpaperAspectRatio(app)

            // Load actual image dimensions from file
            val (imgWidth, imgHeight) = withContext(Dispatchers.IO) {
                val file = File(photo.filePath)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                Pair(options.outWidth.toFloat(), options.outHeight.toFloat())
            }

            _uiState.update {
                it.copy(
                    photo = photo,
                    cropRect = RectF(photo.cropX, photo.cropY, photo.cropX + photo.cropWidth, photo.cropY + photo.cropHeight),
                    subjectType = photo.detectedSubjectType,
                    aspectRatio = ar,
                    imageWidth = imgWidth,
                    imageHeight = imgHeight,
                    isLoading = false
                )
            }
        }
    }

    fun updateCropRect(rect: RectF) {
        _uiState.update { it.copy(cropRect = rect) }
    }

    fun saveCrop() {
        val state = _uiState.value
        val photo = state.photo ?: return
        viewModelScope.launch {
            val updated = photo.copy(
                cropX = state.cropRect.left,
                cropY = state.cropRect.top,
                cropWidth = state.cropRect.width(),
                cropHeight = state.cropRect.height()
            )
            repository.updatePhoto(updated)
            repository.regenerateThumbnail(updated)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun saveCropAndApply() {
        val state = _uiState.value
        val photo = state.photo ?: return
        if (!photo.isEnabled) return
        viewModelScope.launch {
            val updated = photo.copy(
                cropX = state.cropRect.left,
                cropY = state.cropRect.top,
                cropWidth = state.cropRect.width(),
                cropHeight = state.cropRect.height()
            )
            repository.updatePhoto(updated)
            repository.regenerateThumbnail(updated)
            _uiState.update { it.copy(photo = updated) }
            val ok = WallpaperApply.applyPhotoAsWallpaper(app, updated, repository, app.preferencesManager)
            if (ok) {
                _uiState.update { it.copy(isSaved = true) }
            }
        }
    }
}
