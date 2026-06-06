package com.example.ui

import android.app.Application
import android.content.Context
import android.media.audiofx.Equalizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.HistoryEntity
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.ScannedFolder
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val context = application.applicationContext

    // --- REACTIVE STREAMS ---
    val videos = repository.videosFlow
    val audio = repository.audioFlow
    val favorites = repository.favoritesFlow
    val folders = repository.foldersFlow
    val playlists = repository.playlistsFlow
    val history = repository.historyFlow
    val privateFiles = repository.privateFilesFlow

    // --- SCANNER STATE ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    // --- SLEEP TIMER STATE ---
    private val _sleepTimeRemaining = MutableStateFlow<Long?>(null) // In seconds
    val sleepTimeRemaining: StateFlow<Long?> = _sleepTimeRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    // --- PRIVATE PASSPHRASE SECURE WALLET ---
    private val sharedPrefs = context.getSharedPreferences("finalplayer_preferences", Context.MODE_PRIVATE)

    private val _isPrivateFolderLocked = MutableStateFlow(true)
    val isPrivateFolderLocked: StateFlow<Boolean> = _isPrivateFolderLocked.asStateFlow()

    init {
        // Run an automatic incremental scan on launch
        launchIncrementalScan()
    }

    fun launchIncrementalScan() {
        viewModelScope.launch {
            _isScanning.value = true
            repository.triggerScan(context) { progress ->
                _scanProgress.value = progress
            }
            _isScanning.value = false
        }
    }

    // --- MEDIA MANIPULATIONS ---
    fun toggleFavorite(media: MediaFile) {
        viewModelScope.launch {
            repository.toggleFavorite(media.id, !media.isFavorite)
        }
    }

    fun setPrivateStatus(media: MediaFile, isPrivate: Boolean) {
        viewModelScope.launch {
            repository.setPrivateStatus(media.id, isPrivate)
        }
    }

    fun deleteFile(media: MediaFile) {
        viewModelScope.launch {
            repository.deleteFile(media.path)
        }
    }

    fun renameFile(media: MediaFile, newName: String) {
        viewModelScope.launch {
            repository.renameFile(media.path, newName)
        }
    }

    // --- PLAYLIST MANAGEMENT ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addToPlaylist(playlistId: Long, path: String) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, path)
        }
    }

    fun getPlaylistMedia(playlistId: Long) = repository.getPlaylistMediaFlow(playlistId)

    // --- HISTORY CONTROL ---
    fun addToHistory(path: String, position: Long) {
        viewModelScope.launch {
            repository.addHistory(path, position)
            repository.updatePlaybackPosition(path, position)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // --- CONFIGURATION MANAGEMENT ---
    fun getPasscode(): String? {
        return sharedPrefs.getString("secure_private_passcode", null)
    }

    fun savePasscode(passcode: String) {
        sharedPrefs.edit().putString("secure_private_passcode", passcode).apply()
        _isPrivateFolderLocked.value = true
    }

    fun unlockPrivateFolder(passcode: String): Boolean {
        val saved = getPasscode()
        return if (saved == passcode) {
            _isPrivateFolderLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockPrivateFolder() {
        _isPrivateFolderLocked.value = true
    }

    // --- AUDIO / EQUALIZER CONFIGURATION ---
    private var equalizer: Equalizer? = null
    fun applyEqualizerPreset(presetIndex: Short, audioSessionId: Int) {
        try {
            if (equalizer == null || equalizer?.id != audioSessionId) {
                equalizer = Equalizer(0, audioSessionId)
            }
            equalizer?.let {
                if (presetIndex < it.numberOfPresets) {
                    it.usePreset(presetIndex)
                    it.enabled = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- SLEEP TIMER ENGINE ---
    fun setSleepTimer(minutes: Int, onTimerFinished: () -> Unit) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimeRemaining.value = null
            return
        }

        _sleepTimeRemaining.value = minutes.toLong() * 60
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes.toLong() * 60
            while (remaining > 0) {
                delay(1000)
                remaining--
                _sleepTimeRemaining.value = remaining
            }
            _sleepTimeRemaining.value = null
            onTimerFinished()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimeRemaining.value = null
    }

    // --- SOUND & LOOK GLOBAL DEFAULTS ---
    fun getPlaybackSpeed(): Float = sharedPrefs.getFloat("default_playback_speed", 1.0f)
    fun savePlaybackSpeed(speed: Float) = sharedPrefs.edit().putFloat("default_playback_speed", speed).apply()

    fun getHideControlsDelay(): Int = sharedPrefs.getInt("hide_playback_controls_delay_seconds", 3)
    fun saveHideControlsDelay(seconds: Int) = sharedPrefs.edit().putInt("hide_playback_controls_delay_seconds", seconds).apply()

    fun getSubtitleSize(): Float = sharedPrefs.getFloat("subtitle_text_size_dp", 18f)
    fun saveSubtitleSize(dpSize: Float) = sharedPrefs.edit().putFloat("subtitle_text_size_dp", dpSize).apply()

    fun getSubtitleColor(): Int = sharedPrefs.getInt("subtitle_color_hex", 0xFFFFFFFF.toInt())
    fun saveSubtitleColor(hexColor: Int) = sharedPrefs.edit().putInt("subtitle_color_hex", hexColor).apply()

    fun getSubtitlesEnabled(): Boolean = sharedPrefs.getBoolean("subtitles_enabled_by_default", true)
    fun saveSubtitlesEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("subtitles_enabled_by_default", enabled).apply()

    fun getAudioBoostEnabled(): Boolean = sharedPrefs.getBoolean("audio_boost_max_200_enabled", false)
    fun saveAudioBoostEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("audio_boost_max_200_enabled", enabled).apply()

    fun getDefaultScaleMode(): String = sharedPrefs.getString("default_fit_scale_mode", "FIT") ?: "FIT"
    fun saveDefaultScaleMode(mode: String) = sharedPrefs.edit().putString("default_fit_scale_mode", mode).apply()
}
