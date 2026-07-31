package com.example.player.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MPVPlayerEngine : PlayerEngine, MPVLib.EventObserver {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var mediaPlayer: MediaPlayer? = null
    private var attachedSurface: Surface? = null
    private var currentUri: String? = null
    private var engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    override fun initialize(context: Context) {
        appContext = context.applicationContext
        MPVLib.create(context.applicationContext)
        MPVLib.addObserver(this)
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.init()
    }

    override fun loadMedia(uri: String, startPositionMs: Long) {
        currentUri = uri
        _state.update { it.copy(isBuffering = true, currentPositionMs = startPositionMs) }

        // Send load command to MPV
        MPVLib.command(arrayOf("loadfile", uri))
        if (startPositionMs > 0) {
            MPVLib.command(arrayOf("seek", (startPositionMs / 1000.0).toString(), "absolute"))
        }

        // Setup fallback Android MediaPlayer for native audio/video handling
        appContext?.let { ctx ->
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(ctx, Uri.parse(uri))
                    attachedSurface?.let { setSurface(it) }
                    setOnPreparedListener { mp ->
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                isPlaying = true,
                                durationMs = mp.duration.toLong(),
                                videoWidth = if (mp.videoWidth > 0) mp.videoWidth else 1920,
                                videoHeight = if (mp.videoHeight > 0) mp.videoHeight else 1080
                            )
                        }
                        if (startPositionMs > 0) {
                            mp.seekTo(startPositionMs.toInt())
                        }
                        mp.start()
                        startProgressTracker()
                    }
                    setOnErrorListener { _, _, _ ->
                        _state.update { it.copy(isBuffering = false, error = "خطأ في تشغيل الوسائط") }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                _state.update { it.copy(isBuffering = false, error = e.localizedMessage) }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = engineScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _state.update {
                            it.copy(
                                currentPositionMs = mp.currentPosition.toLong(),
                                isPlaying = true
                            )
                        }
                    }
                }
                delay(250)
            }
        }
    }

    override fun play() {
        MPVLib.setPropertyBoolean("pause", false)
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
        _state.update { it.copy(isPlaying = true) }
        startProgressTracker()
    }

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        _state.update { it.copy(isPlaying = false) }
        progressJob?.cancel()
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))
        mediaPlayer?.seekTo(positionMs.toInt())
        _state.update { it.copy(currentPositionMs = positionMs) }
    }

    override fun setSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                } catch (e: Exception) {}
            }
        }
        _state.update { it.copy(speed = speed) }
    }

    override fun setVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 200)
        MPVLib.setPropertyInt("volume", clamped)
        val factor = (clamped / 100f).coerceIn(0f, 1f)
        mediaPlayer?.setVolume(factor, factor)
        _state.update { it.copy(volume = clamped) }
    }

    override fun setAudioBoost(db: Float) {
        val boostVol = (100 + (db * 10)).toInt().coerceIn(0, 200)
        MPVLib.setPropertyInt("volume", boostVol)
        _state.update { it.copy(audioBoostDb = db) }
    }

    override fun toggleDecoder(useHw: Boolean) {
        val hwStr = if (useHw) "auto" else "no"
        MPVLib.setOptionString("hwdec", hwStr)
        _state.update { it.copy(isHwDecoder = useHw) }
    }

    override fun setAudioTrack(trackId: String) {
        MPVLib.setPropertyString("aid", trackId)
        _state.update { it.copy(currentAudioTrack = trackId) }
    }

    override fun setSubtitleTrack(trackId: String) {
        MPVLib.setPropertyString("sid", trackId)
        _state.update { it.copy(currentSubtitleTrack = trackId) }
    }

    override fun attachSurface(surface: Surface) {
        attachedSurface = surface
        MPVLib.attachSurface(surface)
        mediaPlayer?.setSurface(surface)
    }

    override fun detachSurface() {
        attachedSurface = null
        MPVLib.detachSurface()
        mediaPlayer?.setSurface(null)
    }

    override fun release() {
        progressJob?.cancel()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
        mediaPlayer?.release()
        mediaPlayer = null
        attachedSurface = null
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_FILE_LOADED -> _state.update { it.copy(isBuffering = false) }
            MPVLib.MPV_EVENT_SEEK -> _state.update { it.copy(isBuffering = true) }
            MPVLib.MPV_EVENT_PLAYBACK_RESTART -> _state.update { it.copy(isBuffering = false, isPlaying = true) }
        }
    }
}
