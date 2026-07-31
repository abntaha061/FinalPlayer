package com.example.player.engine

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val state: StateFlow<PlayerState>

    fun initialize(context: Context)
    fun loadMedia(uri: String, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setVolume(volumePercent: Int)
    fun setAudioBoost(db: Float)
    fun toggleDecoder(useHw: Boolean)
    fun setAudioTrack(trackId: String)
    fun setSubtitleTrack(trackId: String)
    fun attachSurface(surface: Surface)
    fun detachSurface()
    fun release()
}
