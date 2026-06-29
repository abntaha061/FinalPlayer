package com.example.ui

import android.app.Application
import android.content.Context
import android.media.audiofx.Equalizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.entities.HistoryEntity
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.ScannedFolder
import com.example.data.observer.RealTimeMediaWatcher
import com.example.data.repository.MediaRepository
import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val context = application.applicationContext

    private val _subtitleOffsetY = MutableStateFlow(0f)
    val subtitleOffsetY: StateFlow<Float> = _subtitleOffsetY.asStateFlow()

    fun moveSubtitle(deltaY: Float) {
        // Allow moving between -40% and +5% of screen height
        _subtitleOffsetY.value = (_subtitleOffsetY.value + deltaY)
            .coerceIn(-0.40f, 0.05f)
    }

    // --- BACKGROUND AUDIO PLAYER ---
    @Volatile
    private var audioPlayer: ExoPlayer? = null

    private val _currentTrack = MutableStateFlow<MediaFile?>(null)
    val currentTrack: StateFlow<MediaFile?> = _currentTrack.asStateFlow()

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    private val _audioProgress = MutableStateFlow(0L)
    val audioProgress: StateFlow<Long> = _audioProgress.asStateFlow()

    private val _audioDuration = MutableStateFlow(0L)
    val audioDuration: StateFlow<Long> = _audioDuration.asStateFlow()

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private fun getAudioPlayer(): ExoPlayer {
        if (audioPlayer == null) {
            audioPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isAudioPlaying.value = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _audioDuration.value = duration.coerceAtLeast(0L)
                        } else if (state == Player.STATE_ENDED) {
                            _isAudioPlaying.value = false
                            playNextAudio()
                        }
                    }
                })
            }
            viewModelScope.launch {
                while (true) {
                    audioPlayer?.let {
                        if (it.isPlaying) {
                            _audioProgress.value = it.currentPosition
                        }
                    }
                    delay(250)
                }
            }
        }
        return audioPlayer!!
    }

    fun playAudio(track: MediaFile) {
        if (_currentTrack.value?.path == track.path && audioPlayer != null) {
            toggleAudioPlayPause()
            return
        }
        _currentTrack.value = track
        viewModelScope.launch {
            val player = getAudioPlayer()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(track.path))
            player.prepare()
            player.playWhenReady = true
            _isAudioPlaying.value = true
            addToHistory(track.path, 0L)
        }
    }

    fun toggleAudioPlayPause() {
        val player = getAudioPlayer()
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekAudioTo(positionMs: Long) {
        val player = getAudioPlayer()
        player.seekTo(positionMs)
        _audioProgress.value = positionMs
    }

    fun stopAudio() {
        audioPlayer?.stop()
        _isAudioPlaying.value = false
        _currentTrack.value = null
    }

    fun playNextAudio() {
        viewModelScope.launch {
            try {
                val audios = repository.audioFlow.first()
                if (audios.isNotEmpty()) {
                    val current = _currentTrack.value
                    if (current != null) {
                        val index = audios.indexOfFirst { it.path == current.path }
                        if (index != -1 && index < audios.size - 1) {
                            playAudio(audios[index + 1])
                        } else {
                            playAudio(audios[0])
                        }
                    } else {
                        playAudio(audios[0])
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error playing next track", e)
            }
        }
    }

    fun playPreviousAudio() {
        viewModelScope.launch {
            try {
                val audios = repository.audioFlow.first()
                if (audios.isNotEmpty()) {
                    val current = _currentTrack.value
                    if (current != null) {
                        val index = audios.indexOfFirst { it.path == current.path }
                        if (index > 0) {
                            playAudio(audios[index - 1])
                        } else if (index == 0) {
                            playAudio(audios[audios.size - 1])
                        } else {
                            playAudio(audios[0])
                        }
                    } else {
                        playAudio(audios[0])
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error playing previous track", e)
            }
        }
    }

    fun setFullPlayerOpen(open: Boolean) {
        _isFullPlayerOpen.value = open
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer?.release()
        audioPlayer = null
        realTimeWatcher?.unregisterObservers()
        realTimeWatcher = null
    }

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

    // --- FOLDER NAVIGATION STATE PERSISTENCE ---
    private val _selectedFolderPath = MutableStateFlow<String?>(null)
    val selectedFolderPath: StateFlow<String?> = _selectedFolderPath.asStateFlow()

    fun setSelectedFolderPath(path: String?) {
        _selectedFolderPath.value = path
    }

    // --- PRIVATE PASSPHRASE SECURE WALLET ---
    private val sharedPrefs = context.getSharedPreferences("finalplayer_preferences", Context.MODE_PRIVATE)

    private val _isPrivateFolderLocked = MutableStateFlow(true)
    val isPrivateFolderLocked: StateFlow<Boolean> = _isPrivateFolderLocked.asStateFlow()

    val themeColorHexState = MutableStateFlow(sharedPrefs.getString("app_theme_color_hex", "#FFD500F9") ?: "#FFD500F9")
    val resumeButtonPositionState = MutableStateFlow(sharedPrefs.getString("resume_button_position_side", "LEFT") ?: "LEFT")
    val appThemeModeState = MutableStateFlow(sharedPrefs.getString("app_theme_mode", "SYSTEM") ?: "SYSTEM")

    private var realTimeWatcher: RealTimeMediaWatcher? = null

    init {
        // Run an automatic incremental scan on launch
        launchIncrementalScan()
        // Register real-time change observers (ContentObserver & FileObservers)
        startRealTimeWatcher()
        // Designate periodic light scanner via WorkManager
        schedulePeriodicLightScan()
    }

    fun launchIncrementalScan() {
        viewModelScope.launch {
            _isScanning.value = true
            val addedCount = repository.triggerScan(context) { progress ->
                _scanProgress.value = progress
            }
            seedSampleTracksIfNeeded()
            _isScanning.value = false

            // Notify user with elegant Toast if more than 3 files are added / detected in real time
            if (addedCount > 3) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "تم إضافة $addedCount ملفات جديدة ✨", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startRealTimeWatcher() {
        if (realTimeWatcher == null) {
            realTimeWatcher = RealTimeMediaWatcher(
                context = context,
                scope = viewModelScope,
                repository = repository,
                onTriggerScan = {
                    launchIncrementalScan()
                }
            )
            realTimeWatcher?.registerObservers()
        }
    }

    private fun schedulePeriodicLightScan() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.data.worker.MediaScanWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "MediaLightCycleScan",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
            Log.d("MediaViewModel", "Scheduled 15-minute lightweight background periodic scans.")
        } catch (e: Exception) {
            Log.e("MediaViewModel", "Failed to schedule background periodic scan", e)
        }
    }

    private suspend fun seedSampleTracksIfNeeded() {
        try {
            val audios = repository.audioFlow.first()
            if (audios.isEmpty()) {
                val samples = listOf(
                    MediaFile(
                        path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        title = "أعطني الناي وغنّ (Give Me the Flute)",
                        duration = 372000L,
                        size = 5420000L,
                        dateModified = System.currentTimeMillis(),
                        isVideo = false,
                        artist = "فيروز (Fairuz)",
                        album = "القصائد الخالدة"
                    ),
                    MediaFile(
                        path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        title = "أ غداً ألقاك (Will I Meet You Tomorrow)",
                        duration = 423000L,
                        size = 6200000L,
                        dateModified = System.currentTimeMillis() - 86400000L,
                        isVideo = false,
                        artist = "أم كلثوم (Umm Kulthum)",
                        album = "روائع أم كلثوم"
                    ),
                    MediaFile(
                        path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        title = "قارئة الفنجان (The Cup Reader)",
                        duration = 502000L,
                        size = 7500000L,
                        dateModified = System.currentTimeMillis() - 172800000L,
                        isVideo = false,
                        artist = "عبد الحليم حافظ (Abdel Halim)",
                        album = "العندليب الأسمر"
                    )
                )
                repository.insertMediaFiles(samples)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getMediaByPath(path: String): MediaFile? {
        return repository.getMediaByPath(path)
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

    fun markAsPlayed(path: String) {
        viewModelScope.launch {
            repository.markAsPlayed(path)
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

    fun getAudioSortOption(): Int = sharedPrefs.getInt("audio_sort_option", 0)
    fun saveAudioSortOption(option: Int) = sharedPrefs.edit().putInt("audio_sort_option", option).apply()

    fun getThemeColorHex(): String = sharedPrefs.getString("app_theme_color_hex", "#FFD500F9") ?: "#FFD500F9"
    fun saveThemeColorHex(hex: String) {
        sharedPrefs.edit().putString("app_theme_color_hex", hex).apply()
        themeColorHexState.value = hex
    }

    fun getResumeButtonPosition(): String = sharedPrefs.getString("resume_button_position_side", "LEFT") ?: "LEFT"
    fun saveResumeButtonPosition(side: String) {
        sharedPrefs.edit().putString("resume_button_position_side", side).apply()
        resumeButtonPositionState.value = side
    }

    fun getAppThemeMode(): String = sharedPrefs.getString("app_theme_mode", "SYSTEM") ?: "SYSTEM"
    fun saveAppThemeMode(mode: String) {
        sharedPrefs.edit().putString("app_theme_mode", mode).apply()
        appThemeModeState.value = mode
    }

    // --- COPIES, MOVES, DELETES & RENAMES MANAGER ---
    fun copyPaths(paths: List<String>, targetDir: String, onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { path ->
                val srcFile = File(path)
                if (srcFile.exists()) {
                    val destFile = File(targetDir, srcFile.name)
                    copyFileOrDirectory(srcFile, destFile)
                }
            }
            withContext(Dispatchers.Main) {
                onFinished()
                launchIncrementalScan()
            }
        }
    }

    private fun copyFileOrDirectory(src: File, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) {
                dest.mkdirs()
            }
            val children = src.listFiles()
            if (children != null) {
                for (child in children) {
                    copyFileOrDirectory(child, File(dest, child.name))
                }
            }
        } else {
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            src.copyTo(dest, overwrite = true)
        }
    }

    fun movePaths(paths: List<String>, targetDir: String, onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.example.data.local.MediaDatabase.getDatabase(context).mediaDao()
            paths.forEach { path ->
                val srcFile = File(path)
                if (srcFile.exists()) {
                    val destFile = File(targetDir, srcFile.name)
                    if (srcFile.renameTo(destFile)) {
                        dao.deleteFolder(path)
                        dao.deleteMediaFileByPath(path)
                    } else {
                        copyFileOrDirectory(srcFile, destFile)
                        deleteFileOrDirectory(srcFile)
                        dao.deleteFolder(path)
                        dao.deleteMediaFileByPath(path)
                    }
                }
            }
            val allMedia = dao.getAllMediaFilesFlow().first()
            val pathsToDelete = mutableListOf<String>()
            paths.forEach { movedPath ->
                allMedia.forEach { media ->
                    if (media.path == movedPath || media.path.startsWith(movedPath + File.separator)) {
                        pathsToDelete.add(media.path)
                    }
                }
            }
            if (pathsToDelete.isNotEmpty()) {
                dao.deleteMediaFilesByPaths(pathsToDelete)
            }
            withContext(Dispatchers.Main) {
                onFinished()
                launchIncrementalScan()
            }
        }
    }

    fun deletePaths(paths: List<String>, onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.example.data.local.MediaDatabase.getDatabase(context).mediaDao()
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    deleteFileOrDirectory(file)
                }
                dao.deleteFolder(path)
                dao.deleteMediaFileByPath(path)
            }
            val allMedia = dao.getAllMediaFilesFlow().first()
            val pathsToDelete = mutableListOf<String>()
            paths.forEach { deletedPath ->
                allMedia.forEach { media ->
                    if (media.path == deletedPath || media.path.startsWith(deletedPath + File.separator)) {
                        pathsToDelete.add(media.path)
                    }
                }
            }
            if (pathsToDelete.isNotEmpty()) {
                dao.deleteMediaFilesByPaths(pathsToDelete)
            }
            withContext(Dispatchers.Main) {
                onFinished()
                launchIncrementalScan()
            }
        }
    }

    private fun deleteFileOrDirectory(file: File) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteFileOrDirectory(child)
                }
            }
        }
        file.delete()
    }

    fun renamePath(oldPath: String, newName: String, onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.example.data.local.MediaDatabase.getDatabase(context).mediaDao()
            val oldFile = File(oldPath)
            if (oldFile.exists()) {
                if (oldFile.isDirectory) {
                    val parent = oldFile.parentFile
                    if (parent != null) {
                        val newFile = File(parent, newName)
                        if (oldFile.renameTo(newFile)) {
                            dao.deleteFolder(oldPath)
                            dao.insertFolder(
                                ScannedFolder(
                                    folderPath = newFile.absolutePath,
                                    lastModifiedTs = System.currentTimeMillis(),
                                    fileCount = 0,
                                    lastScannedAt = System.currentTimeMillis()
                                )
                            )
                            val allMedia = dao.getAllMediaFilesFlow().first()
                            allMedia.forEach { media ->
                                if (media.path.startsWith(oldPath + File.separator)) {
                                    val relPath = media.path.substring(oldPath.length)
                                    val newPath = newFile.absolutePath + relPath
                                    dao.deleteMediaFileByPath(media.path)
                                    dao.insertMediaFile(
                                        media.copy(
                                            id = 0,
                                            path = newPath
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val parent = oldFile.parentFile
                    if (parent != null) {
                        val newFile = File(parent, newName)
                        if (oldFile.renameTo(newFile)) {
                            val oldMedia = dao.getMediaFileByPath(oldPath)
                            if (oldMedia != null) {
                                dao.deleteMediaFileByPath(oldPath)
                                dao.insertMediaFile(
                                    oldMedia.copy(
                                        id = 0,
                                        path = newFile.absolutePath,
                                        title = newFile.name
                                    )
                                )
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onFinished()
                launchIncrementalScan()
            }
        }
    }
}
