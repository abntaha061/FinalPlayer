package com.example.player.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class AspectMode(val label: String) {
    FIT("FIT"),
    STRETCH("STRETCH"),
    CROP("CROP"),
    ORIGINAL("ORIGINAL")
}

enum class PlayerButton(val displayName: String, val icon: ImageVector) {
    BACK_ARROW("Back", Icons.AutoMirrored.Rounded.ArrowBack),
    VIDEO_TITLE("Title", Icons.Rounded.Title),
    DECODER("Decoder", Icons.Rounded.Memory),
    CHAPTERS("Chapters", Icons.Rounded.ViewList),
    SUBTITLES("Subtitles", Icons.Rounded.Subtitles),
    AUDIO_TRACK("Audio", Icons.Rounded.Audiotrack),
    SMART_ENHANCE("Enhance", Icons.Rounded.Tune),
    MORE_OPTIONS("More Options", Icons.Rounded.MoreVert),
    LOCK_CONTROLS("Lock", Icons.Rounded.Lock),
    PICTURE_IN_PICTURE("PiP", Icons.Rounded.PictureInPicture),
    ASPECT_RATIO("Aspect Ratio", Icons.Rounded.AspectRatio),
    BACKGROUND_PLAY("Background Play", Icons.Rounded.Headset),
    STREAM_QUALITY("Quality", Icons.Rounded.HighQuality),
    SCREEN_ROTATION("Rotate", Icons.Rounded.ScreenRotation),
    NONE("None", Icons.Rounded.Clear)
}

enum class DecoderMode { AUTO, HW, SW, HW_PLUS }

data class TrackInfo(val id: String = "", val name: String = "", val language: String = "")
data class ChapterInfo(val title: String = "", val startMs: Long = 0L, val endMs: Long = 0L)

data class SubtitleTrack(val id: String, val name: String, val language: String = "ar")
data class AudioTrack(val id: String, val name: String, val channels: String = "Stereo")
data class VideoQuality(val id: String, val label: String, val resolution: String)
data class Chapter(val title: String, val startMs: Long, val endMs: Long)

enum class DoubleTapAction { BOTH, PLAY_PAUSE, FAST_FORWARD, REWIND, NONE }
enum class MultiFingerAction { PLAY_PAUSE, FAST_PLAY, MUTE, SCREENSHOT, PINCH_ZOOM, NONE }
enum class SubtitleFont { DEFAULT, SANS_SERIF, SERIF, MONOSPACE, ARABIC_CAIRO, ARABIC_TAJAWAL, ARABIC_AMIRI }
enum class EnhanceMode { OFF, SMART, CUSTOM }
enum class FullScreenMode { STRETCH, CROP, FIT }
enum class OrientationMode { LANDSCAPE, PORTRAIT, AUTO, SYSTEM_DEFAULT }
enum class SoftButtonMode { AUTO_HIDE, SHOW, HIDE }

data class PlaybackSettings(
    val brightness: Float = 0.8f,
    val volume: Int = 100,
    val speed: Float = 1.0f,
    val aspectMode: AspectMode = AspectMode.FIT,
    val audioBoostDb: Float = 0f,
    val isHwDecoder: Boolean = true,
    val isNightMode: Boolean = false,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val longPressEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val seekGestureEnabled: Boolean = true,
    val brightnessSensitivity: Float = 1.0f,
    val volumeSensitivity: Float = 1.0f,
    val seekSpeedSecPerCm: Float = 10f,
    val twoFingerAction: MultiFingerAction = MultiFingerAction.PLAY_PAUSE,
    val threeFingerAction: MultiFingerAction = MultiFingerAction.SCREENSHOT,
    val doubleTapAction: DoubleTapAction = DoubleTapAction.BOTH,
    val topLeftControls: String = "DECODER,CHAPTERS",
    val topRightControls: String = "SUBTITLES,AUDIO_TRACK,SMART_ENHANCE,MORE_OPTIONS",
    val bottomLeftControls: String = "LOCK_CONTROLS,PICTURE_IN_PICTURE",
    val bottomRightControls: String = "ASPECT_RATIO,BACKGROUND_PLAY,SCREEN_ROTATION",
    val portraitTopLeftControls: String = "DECODER,CHAPTERS",
    val portraitTopRightControls: String = "SUBTITLES,AUDIO_TRACK,SMART_ENHANCE,MORE_OPTIONS",
    val portraitBottomControls: String = "LOCK_CONTROLS,PICTURE_IN_PICTURE,ASPECT_RATIO,BACKGROUND_PLAY,SCREEN_ROTATION",
    val customPlaybackSpeed: Float = 1.25f,
    val tapAndHoldSpeed: Float = 2.0f,
    val doubleTapSeekDuration: Long = 10000L,
    val controlIconSize: String = "medium",
    val seekBarStyle: String = "standard",
    val showSeekButtons: Boolean = true,
    val showNextPrevButtons: Boolean = true,
    val showRemainingTime: Boolean = false,
    val showBatteryClockOverlay: Boolean = false,
    val showScreenRotationButton: Boolean = true,
    val seekDurationSeconds: Int = 10,
    val isBottomLayoutEnabled: Boolean = false,
    val showControlGradients: Boolean = true,
    val enhanceMode: EnhanceMode = EnhanceMode.OFF,
    val ytdlQuality: Int = -1,
    val backgroundPlayEnabled: Boolean = false,
    val softButtonMode: SoftButtonMode = SoftButtonMode.AUTO_HIDE,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val pauseWhenObstructed: Boolean = false,
    val keepAwakeAlways: Boolean = true,
    val subtitleTextSizeScale: Float = 1.0f,
    val subtitleBgStyle: Int = 0,
    val subtitleFont: SubtitleFont = SubtitleFont.DEFAULT,
    val isSubtitleBold: Boolean = true,
    val subtitleGesturesEnabled: Boolean = true,
    val subtitleVerticalOffset: Float = 0.05f
)
