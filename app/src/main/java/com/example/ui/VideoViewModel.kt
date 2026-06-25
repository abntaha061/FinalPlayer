// File: /app/src/main/java/com/example/ui/VideoViewModel.kt
package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaStoreScanner
import com.example.data.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoUiState {
    object Loading : VideoUiState
    data class Success(val videos: List<VideoItem>) : VideoUiState
    data class Error(val message: String) : VideoUiState
}

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Loading)
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    init {
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = VideoUiState.Loading
            try {
                val result = MediaStoreScanner.scanVideos(getApplication())
                _videos.value = result
                _uiState.value = VideoUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = VideoUiState.Error(e.localizedMessage ?: "Unknown scanning error")
            }
        }
    }
}
