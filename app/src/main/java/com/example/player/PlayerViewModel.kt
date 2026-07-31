package com.example.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MediaDatabase
import com.example.data.local.entities.WatchHistoryEntity
import com.example.data.repository.PlaybackSettingsRepository
import com.example.player.engine.MPVPlayerEngine
import com.example.player.engine.PlayerEngine
import com.example.player.engine.PlayerState
import com.example.player.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SheetType {
    NONE, SETTINGS, QUALITY, AUDIO, SUBTITLE, ENHANCE, DECODER, CHAPTERS
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val engine: PlayerEngine = MPVPlayerEngine()
    private val repository = PlaybackSettingsRepository(application)
    private val database = MediaDatabase.getDatabase(application)
    private val watchHistoryDao = database.watchHistoryDao()

    val playerState: StateFlow<PlayerState> = engine.state
    val playbackSettings: StateFlow<PlaybackSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaybackSettings()
    )

    private val _activeSheet = MutableStateFlow(SheetType.NONE)
    val activeSheet: StateFlow<SheetType> = _activeSheet.asStateFlow()

    private val _areControlsVisible = MutableStateFlow(true)
    val areControlsVisible: StateFlow<Boolean> = _areControlsVisible.asStateFlow()

    private val _isControlsLocked = MutableStateFlow(false)
    val isControlsLocked: StateFlow<Boolean> = _isControlsLocked.asStateFlow()

    // A-B Repeat State
    private val _pointA = MutableStateFlow<Long?>(null)
    val pointA: StateFlow<Long?> = _pointA.asStateFlow()

    private val _pointB = MutableStateFlow<Long?>(null)
    val pointB: StateFlow<Long?> = _pointB.asStateFlow()

    // Current media path
    private var currentFilePath: String = ""
    private var autoHideJob: Job? = null

    init {
        engine.initialize(application)

        // Sync settings with engine
        viewModelScope.launch {
            playbackSettings.collect { settings ->
                engine.setSpeed(settings.speed)
                engine.setVolume(settings.volume)
                engine.setAudioBoost(settings.audioBoostDb)
                engine.toggleDecoder(settings.isHwDecoder)
            }
        }
    }

    fun loadVideo(path: String) {
        currentFilePath = path
        viewModelScope.launch {
            val savedHistory = watchHistoryDao.getHistory(path)
            val startPos = savedHistory?.lastPositionMs ?: 0L
            engine.loadMedia(path, startPos)
            resetAutoHideTimer()
        }
    }

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            engine.pause()
            saveCurrentWatchPosition()
        } else {
            engine.play()
        }
        resetAutoHideTimer()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        saveCurrentWatchPosition()
        resetAutoHideTimer()
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch {
            repository.updateSpeed(speed)
        }
    }

    fun setVolume(volume: Int) {
        viewModelScope.launch {
            repository.updateVolume(volume)
        }
    }

    fun setBrightness(brightness: Float) {
        viewModelScope.launch {
            repository.updateBrightness(brightness)
        }
    }

    fun setAspectMode(mode: AspectMode) {
        viewModelScope.launch {
            repository.updateAspectMode(mode)
        }
    }

    fun setAudioBoost(db: Float) {
        viewModelScope.launch {
            repository.updateAudioBoost(db)
        }
    }

    fun setHwDecoder(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHwDecoder(enabled)
        }
    }

    fun setNightMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNightMode(enabled)
        }
    }

    fun openSheet(sheet: SheetType) {
        _activeSheet.value = sheet
        _areControlsVisible.value = true
        autoHideJob?.cancel()
    }

    fun closeSheet() {
        _activeSheet.value = SheetType.NONE
        resetAutoHideTimer()
    }

    fun toggleControlsVisibility() {
        if (_isControlsLocked.value) {
            _areControlsVisible.value = !_areControlsVisible.value
            return
        }
        _areControlsVisible.value = !_areControlsVisible.value
        if (_areControlsVisible.value) {
            resetAutoHideTimer()
        }
    }

    fun toggleLock() {
        _isControlsLocked.value = !_isControlsLocked.value
        if (_isControlsLocked.value) {
            closeSheet()
            _areControlsVisible.value = false
        } else {
            _areControlsVisible.value = true
            resetAutoHideTimer()
        }
    }

    fun setAbRepeatPoint() {
        val currentPos = playerState.value.currentPositionMs
        if (_pointA.value == null) {
            _pointA.value = currentPos
        } else if (_pointB.value == null) {
            if (currentPos > _pointA.value!!) {
                _pointB.value = currentPos
            } else {
                _pointB.value = _pointA.value
                _pointA.value = currentPos
            }
        } else {
            _pointA.value = null
            _pointB.value = null
        }
    }

    private fun saveCurrentWatchPosition() {
        if (currentFilePath.isBlank()) return
        val pos = playerState.value.currentPositionMs
        val duration = playerState.value.durationMs
        viewModelScope.launch {
            watchHistoryDao.saveHistory(
                WatchHistoryEntity(
                    filePath = currentFilePath,
                    lastPositionMs = pos,
                    durationMs = duration
                )
            )
        }
    }

    private fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(4000)
            if (_activeSheet.value == SheetType.NONE && !_isControlsLocked.value) {
                _areControlsVisible.value = false
            }
        }
    }

    override fun onCleared() {
        saveCurrentWatchPosition()
        engine.release()
        super.onCleared()
    }
}
