package com.wallpaper.rotator.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaper.rotator.WallpaperRotatorApp
import com.wallpaper.rotator.data.db.PhotoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val photos: List<PhotoMetadata> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isSelectMode: Boolean = false,
    val isLoading: Boolean = true
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WallpaperRotatorApp).photoRepository

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllPhotos().collect { photos ->
                _uiState.update { it.copy(photos = photos, isLoading = false) }
            }
        }
    }

    fun enterSelectMode(photoId: Long) {
        _uiState.update {
            it.copy(
                isSelectMode = true,
                selectedIds = setOf(photoId)
            )
        }
    }

    fun toggleSelection(photoId: Long) {
        _uiState.update { state ->
            val newSelection = state.selectedIds.toMutableSet()
            if (newSelection.contains(photoId)) newSelection.remove(photoId)
            else newSelection.add(photoId)
            state.copy(selectedIds = newSelection)
        }
    }

    fun exitSelectMode() {
        _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deletePhotos(ids)
            _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
        }
    }

    fun deselectFromRotation() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.toggleEnabled(ids, false)
            _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
        }
    }

    fun toggleSelectionEnabled() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return

        // Determine the new state based on current state of selected photos
        val selectedPhotos = _uiState.value.photos.filter { it.photoId in ids }
        val allEnabled = selectedPhotos.all { it.isEnabled }

        // If all are enabled, disable them; if any are disabled, enable them
        val newEnabledState = !allEnabled

        viewModelScope.launch {
            repository.toggleEnabled(ids, newEnabledState)
            // Keep selection mode active so user can toggle multiple times if desired
        }
    }

    fun getSelectedPhotosState(): Pair<Boolean, Boolean>? {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return null
        val selectedPhotos = _uiState.value.photos.filter { it.photoId in ids }
        if (selectedPhotos.isEmpty()) return null
        val allEnabled = selectedPhotos.all { it.isEnabled }
        val anyEnabled = selectedPhotos.any { it.isEnabled }
        return Pair(allEnabled, anyEnabled)
    }
}
