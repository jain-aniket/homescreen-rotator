package com.wallpaper.rotator.ui.import_photos

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.data.db.CropMethod
import com.wallpaper.rotator.data.db.SubjectType
import com.wallpaper.rotator.util.DisplayUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val selectedUris: List<Uri> = emptyList(),
    val cropMode: CropMethod = CropMethod.AUTO,
    val isProcessing: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val importComplete: Boolean = false,
    val errorMessage: String? = null
)

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<WallpaperRotatorApp>()
    private val repository get() = app.photoRepository
    private val cropEngine get() = app.cropEngine

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun setSelectedUris(uris: List<Uri>) {
        _uiState.update { it.copy(selectedUris = uris, importComplete = false, errorMessage = null) }
    }

    fun setCropMode(mode: CropMethod) {
        _uiState.update { it.copy(cropMode = mode) }
    }

    fun importPhotos() {
        val state = _uiState.value
        if (state.selectedUris.isEmpty() || state.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processedCount = 0, totalCount = state.selectedUris.size) }

            val wallpaperAR = DisplayUtils.getWallpaperAspectRatio(app)

            for ((index, uri) in state.selectedUris.withIndex()) {
                try {
                    val cropBox: RectF
                    val subjectType: SubjectType

                    if (state.cropMode == CropMethod.AUTO) {
                        val bitmap = app.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        } ?: continue

                        val result = cropEngine.calculateAutoCrop(bitmap, wallpaperAR)
                        cropBox = result.cropBox
                        subjectType = result.subjectType
                        bitmap.recycle()
                    } else {
                        val bitmap = app.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        } ?: continue
                        val w = bitmap.width.toFloat()
                        val h = bitmap.height.toFloat()
                        bitmap.recycle()

                        // Compute center crop at wallpaper AR
                        val targetW: Float
                        val targetH: Float
                        if (h / w > wallpaperAR) {
                            targetW = w
                            targetH = w * wallpaperAR
                        } else {
                            targetH = h
                            targetW = h / wallpaperAR
                        }
                        val left = (w - targetW) / 2f
                        val top = (h - targetH) / 2f
                        cropBox = RectF(left, top, left + targetW, top + targetH)
                        subjectType = SubjectType.LANDSCAPE
                    }

                    repository.importPhoto(uri, cropBox, subjectType, state.cropMode)
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Failed to import photo ${index + 1}: ${e.message}") }
                }
                _uiState.update { it.copy(processedCount = index + 1) }
            }

            _uiState.update { it.copy(isProcessing = false, importComplete = true) }
        }
    }

    fun resetState() {
        _uiState.value = ImportUiState()
    }
}
