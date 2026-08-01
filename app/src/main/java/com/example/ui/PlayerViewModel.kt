// app/src/main/java/com/example/ui/PlayerViewModel.kt
package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppSettings
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val appSettingsRepository = AppSettingsRepository(application)
    val mediaRepository = MediaRepository(application)

    val appSettings: StateFlow<AppSettings> = appSettingsRepository.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val resumePlaybackMode: String
        get() = appSettings.value.resumePlaybackMode

    val seekIncrementSeconds: Int
        get() = appSettings.value.seekIncrementSeconds

    val autoPlayNext: Boolean
        get() = appSettings.value.autoPlayNext

    val defaultOrientation: String
        get() = appSettings.value.defaultOrientation

    val autoPip: Boolean
        get() = appSettings.value.autoPip

    val rememberBrightness: Boolean
        get() = appSettings.value.rememberBrightness

    val rememberSpeed: Boolean
        get() = appSettings.value.rememberSpeed

    val rememberAspectRatio: Boolean
        get() = appSettings.value.rememberAspectRatio

    suspend fun getSavedPlaybackPosition(path: String): Long {
        return mediaRepository.getMediaByPath(path)?.lastPlayPosition ?: 0L
    }

    fun updatePlaybackPosition(path: String, position: Long) {
        viewModelScope.launch {
            mediaRepository.updatePlaybackPosition(path, position)
        }
    }
}
