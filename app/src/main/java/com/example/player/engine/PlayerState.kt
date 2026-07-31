package com.example.player.engine

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val speed: Float = 1.0f,
    val volume: Int = 100,
    val audioBoostDb: Float = 0f,
    val isHwDecoder: Boolean = true,
    val currentAudioTrack: String? = null,
    val currentSubtitleTrack: String? = null,
    val title: String = "",
    val error: String? = null
)
