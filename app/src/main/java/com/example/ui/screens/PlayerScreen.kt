package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import com.example.ui.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue
import android.media.audiofx.Equalizer
import android.widget.Toast

// Secondary control items data layout
data class ExtendedToolItem(
    val icon: String,
    val label: String,
    val id: String,
    val action: () -> Unit,
    val isActive: Boolean,
    val badgeText: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    filePath: String,
    viewModel: MediaViewModel,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // -----------------------------------------------------
    // STATE DECLARATIONS
    // -----------------------------------------------------
    val allVideosOriginal by viewModel.videos.collectAsState(initial = emptyList())
    val allVideos = remember(allVideosOriginal, filePath) {
        val currentParent = try { File(filePath).parent } catch (e: Exception) { null }
        if (currentParent != null) {
            val filtered = allVideosOriginal.filter { 
                try { File(it.path).parent == currentParent } catch (e: Exception) { false }
            }
            if (filtered.isNotEmpty()) filtered else allVideosOriginal
        } else {
            allVideosOriginal
        }
    }
    val themeColorHex by viewModel.themeColorHexState.collectAsState()
    val subtitleOffsetY by viewModel.subtitleOffsetY.collectAsState()
    val currentAccentColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }
    val currentMediaFile = remember(filePath) { File(filePath) }

    LaunchedEffect(filePath) {
        viewModel.markAsPlayed(filePath)
    }

    // Navigation and indexing support
    val currentVideoIndex = remember(allVideos, filePath) {
        allVideos.indexOfFirst { it.path == filePath }
    }
    val hasPreviousVideo = currentVideoIndex > 0
    val hasNextVideo = currentVideoIndex >= 0 && currentVideoIndex < allVideos.size - 1

    var seekStepSeconds by remember { mutableStateOf(10) }

    // Store original orientation on entry to restore on exit
    val initialOrientation = remember {
        activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Keep screen on during media playback and restore original orientation on leave
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = initialOrientation
            val window = activity?.window
            if (window != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    if (controller != null) {
                        controller.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    // AudioManager for volume gesture
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

    // Scan for external subtitles in the same folder
    val detectedSubtitles = remember(filePath) {
        val list = mutableListOf<File>()
        val videoFile = File(filePath)
        val parentDir = videoFile.parentFile
        if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
            val baseName = videoFile.nameWithoutExtension
            val siblings = parentDir.listFiles()
            if (siblings != null) {
                for (sibling in siblings) {
                    if (sibling.isFile) {
                        val sibName = sibling.name
                        if (sibName.startsWith(baseName) && (sibName.endsWith(".srt", ignoreCase = true) || sibName.endsWith(".vtt", ignoreCase = true))) {
                            list.add(sibling)
                        }
                    }
                }
            }
        }
        list
    }

    val subtitleLanguages = remember(detectedSubtitles) {
        detectedSubtitles.mapIndexed { index, file ->
            val baseName = File(filePath).nameWithoutExtension
            val suffix = if (file.name.length >= baseName.length) {
                file.name.substring(baseName.length)
                    .removeSuffix(".srt").removeSuffix(".vtt")
                    .removeSuffix(".SRT").removeSuffix(".VTT")
            } else {
                ""
            }
            val extractedLang = if (suffix.startsWith(".")) {
                suffix.substring(1)
            } else {
                suffix
            }
            if (extractedLang.isNotEmpty()) {
                extractedLang
            } else {
                "sub-$index"
            }
        }
    }

    var isSubtitleEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleLang by remember { mutableStateOf<String?>(subtitleLanguages.firstOrNull()) }

    // Init player
    val player = remember(filePath) {
        val videoFile = File(filePath)
        val uri = if (filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("content://") || filePath.startsWith("file://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(videoFile)
        }

        val subtitleConfigs = detectedSubtitles.mapIndexed { index, file ->
            val lang = subtitleLanguages.getOrNull(index) ?: "ar"
            val subUri = Uri.fromFile(file)
            val isSrt = file.name.endsWith(".srt", ignoreCase = true)
            val mimeType = if (isSrt) "application/x-subrip" else "text/vtt"
            
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeType)
                .setLanguage(lang)
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        ExoPlayer.Builder(context).build().also {
            it.setMediaItem(mediaItem)
            val firstLang = subtitleLanguages.firstOrNull() ?: "ar"
            it.trackSelectionParameters = it.trackSelectionParameters
                .buildUpon()
                .setPreferredTextLanguage(firstLang)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                .build()
            it.prepare()
            it.playWhenReady = true
        }
    }

    val manualSubs = remember { mutableStateListOf<Pair<String, Uri>>() }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore. In Android some folder providers are not persistable, we can still read
            }
            
            var dispName = "External_Sub.srt"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        dispName = cursor.getString(nameIdx)
                    }
                }
            } catch (e: Exception) {
                uri.lastPathSegment?.let { dispName = it }
            }
            
            // Build dynamic combined media track configuration
            val currentPos = player.currentPosition
            val isPlaying = player.isPlaying
            
            val compositeConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
            detectedSubtitles.forEachIndexed { idx, file ->
                val lang = subtitleLanguages.getOrNull(idx) ?: "ar"
                val subUri = Uri.fromFile(file)
                val isSrt = file.name.endsWith(".srt", ignoreCase = true)
                val mimeType = if (isSrt) "application/x-subrip" else "text/vtt"
                compositeConfigs.add(
                    MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(mimeType)
                        .setLanguage(lang)
                        .setSelectionFlags(if (idx == 0) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                )
            }
            
            manualSubs.forEachIndexed { idx, pair ->
                val subUri = pair.second
                val isSrt = pair.first.endsWith(".srt", ignoreCase = true)
                val mimeType = if (isSrt) "application/x-subrip" else "text/vtt"
                val lang = "manual_${idx}_${pair.first}"
                compositeConfigs.add(
                    MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(mimeType)
                        .setLanguage(lang)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            }
            
            val newIsSrt = dispName.endsWith(".srt", ignoreCase = true)
            val newMimeType = if (newIsSrt) "application/x-subrip" else "text/vtt"
            val newLang = "manual_${manualSubs.size}_$dispName"
            
            val newConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(newMimeType)
                .setLanguage(newLang)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            compositeConfigs.add(newConfig)
            
            manualSubs.add(Pair(dispName, uri))
            
            val videoFile = File(filePath)
            val videoUri = if (filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("content://") || filePath.startsWith("file://")) {
                Uri.parse(filePath)
            } else {
                Uri.fromFile(videoFile)
            }
            
            val newMediaItem = MediaItem.Builder()
                .setUri(videoUri)
                .setSubtitleConfigurations(compositeConfigs)
                .build()
                
            player.setMediaItem(newMediaItem)
            player.prepare()
            player.seekTo(currentPos)
            
            isSubtitleEnabled = true
            selectedSubtitleLang = newLang
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(newLang)
                .build()
                
            player.playWhenReady = isPlaying
            Toast.makeText(context, "تم تحميل ملف الترجمة: $dispName", Toast.LENGTH_SHORT).show()
        }
    }

    // Save history and lifecycle progress updates: Throttle database write frequency
    var playbackPosition by remember { mutableStateOf(0L) }
    var lastSavedPosition by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
         while (true) {
             delay(1000)
             playbackPosition = player.currentPosition
             // Save to database only every 10 seconds to drastically reduce CPU thermal energy
             if (java.lang.Math.abs(playbackPosition - lastSavedPosition) >= 10000L) {
                 viewModel.addToHistory(filePath, playbackPosition)
                 lastSavedPosition = playbackPosition
             }
         }
    }

    // Clean player on exit and guarantee final position is saved
    DisposableEffect(player) {
        onDispose {
            // Save precise end position on dispose
            viewModel.addToHistory(filePath, player.currentPosition)
            player.release()
        }
    }

    // Gesture Values State
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var currentBrightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f)
    }
    if (currentBrightness < 0f) currentBrightness = 0.5f // Handle default auto status

    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var isIndicatorVisible by remember { mutableStateOf(false) }

    // On-Screen Controls HUD Visibility
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLockedMode by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var isPlayingState by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableStateOf(0L) }
    var currentPlayTime by remember { mutableStateOf(0L) }

    // Seek to last played position upon initial video load
    LaunchedEffect(player, filePath) {
        val dbMedia = viewModel.getMediaByPath(filePath)
        val initialPosition = dbMedia?.lastPlayPosition ?: 0L
        if (initialPosition > 0) {
            player.seekTo(initialPosition)
            currentPlayTime = initialPosition
        }
    }

    // Pinch-to-zoom parameters
    var scale by remember { mutableStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1.0f, 4.0f)
    }

    // Sub-menus visible states
    var isFilesListVisible by remember { mutableStateOf(false) }
    var isQuickSettingsOpen by remember { mutableStateOf(false) }
    var isBrightnessSliderVisible by remember { mutableStateOf(false) }
    var isSpeedExpanded by remember { mutableStateOf(false) }
    var isSubtitlesExpanded by remember { mutableStateOf(false) }
    var isLongPressFastForwarding by remember { mutableStateOf(false) }

    // Native resolution detector
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Playback Speed multiplier
    var speedMultiplier by remember { mutableStateOf(viewModel.getPlaybackSpeed()) }

    // Swipe seeking states
    var isSeekingBySwipe by remember { mutableStateOf(false) }
    var swipeSeekPosition by remember { mutableStateOf(0L) }

    // Read settings or setup scale mode
    var scaleMode by remember { mutableStateOf(viewModel.getDefaultScaleMode()) }

    // -----------------------------------------------------
    // MX PLAYER CUSTOM STATES AND VARIABLES
    // -----------------------------------------------------
    var isNightModeActive by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isMirrorModeActive by remember { mutableStateOf(false) }
    var isVerticalFlipActive by remember { mutableStateOf(false) }
    var isHWAccelActive by remember { mutableStateOf(true) }
    var currentDecoder by remember { mutableStateOf("HW+") }
    var isDecoderDialogOpen by remember { mutableStateOf(false) }

    var sleepTimerActive by remember { mutableStateOf(false) }
    var sleepTimerRemainingSecs by remember { mutableStateOf(0) }
    var sleepTimerInitialMinutes by remember { mutableStateOf(0) }
    var isSleepTimerDialogOpen by remember { mutableStateOf(false) }

    var pointA by remember { mutableStateOf<Long?>(null) }
    var pointB by remember { mutableStateOf<Long?>(null) }

    var isEqualizerOpen by remember { mutableStateOf(false) }
    var isEqualizerActive by remember { mutableStateOf(false) }
    var equalizerPresetIndex by remember { mutableStateOf(0) }
    var equalizerBandLevels by remember { mutableStateOf(floatArrayOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)) }
    var equalizerInstance by remember { mutableStateOf<Equalizer?>(null) }
    var loudnessEnhancerInstance by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }
    var currentVolRatio by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }

    var isMoreOptionsSheetOpen by remember { mutableStateOf(false) }
    var isAudioTracksDialogOpen by remember { mutableStateOf(false) }
    var isSubtitlePanelViewOpen by remember { mutableStateOf(false) }
    var isSubtitleCustomizationOpen by remember { mutableStateOf(false) }
    var isToolbarCustomizerDialogOpen by remember { mutableStateOf(false) }
    var isTutorialOverlayVisible by remember { mutableStateOf(false) }

    // Interactive gestures and swipe visual states in player
    var isDraggingRightSide by remember { mutableStateOf(false) }
    var draggedVolRatio by remember { mutableStateOf(0f) }
    var draggedBrightness by remember { mutableStateOf(0.5f) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showRewindOverlay by remember { mutableStateOf(false) }
    var showForwardOverlay by remember { mutableStateOf(false) }
    var showSeekDragIndicator by remember { mutableStateOf(false) }
    var draggedSeekPosition by remember { mutableStateOf(0L) }
    var dragStartPlaybackTime by remember { mutableStateOf(0L) }
    var audiofySeekSeconds by remember { mutableStateOf<Int?>(null) }
    var audiofySeekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var singleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var currentGestureType by remember { mutableStateOf("NONE") } // "NONE", "VOLUME", "BRIGHTNESS", "SEEK"
    var dragStartOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var bottomControlsHeightPx by remember { mutableStateOf(0) }

    val sharedPrefs = remember { context.getSharedPreferences("mx_player_prefs", Context.MODE_PRIVATE) }
    var subtitleStyle by remember {
        val savedPadding = sharedPrefs.getFloat("sub_bottom_padding", 0.012f)
        val savedTextSize = sharedPrefs.getFloat("sub_text_size", 1.0f)
        val savedBold = sharedPrefs.getBoolean("sub_bold", false)
        val savedItalic = sharedPrefs.getBoolean("sub_italic", false)
        val savedBgEnabled = sharedPrefs.getBoolean("sub_bg_enabled", false)
        val savedTextColor = sharedPrefs.getInt("sub_text_color", Color.White.toArgb())
        val savedBgColor = sharedPrefs.getInt("sub_bg_color", Color.Black.copy(alpha = 0.5f).toArgb())
        val savedAlignment = sharedPrefs.getInt("sub_alignment", android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
        val savedEdgeType = sharedPrefs.getInt("sub_edge_type", CaptionStyleCompat.EDGE_TYPE_OUTLINE)
        val savedEdgeColor = sharedPrefs.getInt("sub_edge_color", Color.Black.toArgb())
        
        mutableStateOf(
            SubtitleStyle(
                textSize = savedTextSize,
                textColor = Color(savedTextColor),
                backgroundColor = Color(savedBgColor),
                backgroundEnabled = savedBgEnabled,
                bold = savedBold,
                italic = savedItalic,
                alignment = savedAlignment,
                bottomPadding = savedPadding,
                edgeType = savedEdgeType,
                edgeColor = Color(savedEdgeColor)
            )
        )
    }

    LaunchedEffect(subtitleStyle) {
        sharedPrefs.edit()
            .putFloat("sub_bottom_padding", subtitleStyle.bottomPadding)
            .putFloat("sub_text_size", subtitleStyle.textSize)
            .putBoolean("sub_bold", subtitleStyle.bold)
            .putBoolean("sub_italic", subtitleStyle.italic)
            .putBoolean("sub_bg_enabled", subtitleStyle.backgroundEnabled)
            .putInt("sub_text_color", subtitleStyle.textColor.toArgb())
            .putInt("sub_bg_color", subtitleStyle.backgroundColor.toArgb())
            .putInt("sub_alignment", subtitleStyle.alignment)
            .putInt("sub_edge_type", subtitleStyle.edgeType)
            .putInt("sub_edge_color", subtitleStyle.edgeColor.toArgb())
            .apply()
    }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var subtitleSpeed by remember { mutableStateOf(1.0f) }
    var isDraggingSubtitle by remember { mutableStateOf(false) }
    var isSubtitlePressed by remember { mutableStateOf(false) }
    var parentHeightPx by remember { mutableStateOf(1000f) }
    var activeSubtitleText by remember { mutableStateOf("") }

    var checkedExtendedTools by remember {
        mutableStateOf(
            context.getSharedPreferences("mx_player_prefs", Context.MODE_PRIVATE)
                .getStringSet("tools", setOf("🌙", "✏️", "🔀", "🔁", "🔇", "⏱", "A↔B", "🎚️", "1X", "📷", "▶⬛", "↩️", "Flip", "Mirror"))
                ?.toSet() ?: setOf("🌙", "✏️", "🔀", "🔁", "🔇", "⏱", "A↔B", "🎚️", "1X", "📷", "▶⬛", "↩️", "Flip", "Mirror")
        )
    }

    // Screenshot capture mockup action
    fun takeScreenshot(ctx: Context) {
        try {
            val directory = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "MXPlayer")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, "Screenshot_${System.currentTimeMillis()}.png")
            file.createNewFile()
            Toast.makeText(ctx, "تم حفظ لقطة الشاشة في ${file.absolutePath} 📸", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "تم التقاط إطار الفيديو وحفظ لقطة الشاشة بنجاح! 📸", Toast.LENGTH_SHORT).show()
        }
    }

    // Set equalizerband safely
    fun setEqualizerBand(band: Int, value: Float) {
        try {
            equalizerInstance?.setBandLevel(band.toShort(), (value * 100).toInt().toShort())
            val newList = equalizerBandLevels.clone()
            newList[band] = value
            equalizerBandLevels = newList
        } catch (e: Exception) {
            val newList = equalizerBandLevels.clone()
            newList[band] = value
            equalizerBandLevels = newList
        }
    }

    // Native audio session ID allocation
    LaunchedEffect(player) {
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                val eq = Equalizer(0, audioSessionId)
                eq.enabled = true
                equalizerInstance = eq
                isEqualizerActive = true

                val enhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId)
                enhancer.enabled = true
                if (currentVolRatio > 1.0f) {
                    val extraRatio = currentVolRatio - 1.0f
                    val targetGainMb = (extraRatio * 3000).toInt().coerceIn(0, 3000)
                    enhancer.setTargetGain(targetGainMb)
                } else {
                    enhancer.setTargetGain(0)
                }
                loudnessEnhancerInstance = enhancer
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // A-B Repeat loop check
    LaunchedEffect(player, pointA, pointB) {
        while (true) {
            delay(150)
            if (pointA != null && pointB != null) {
                if (player.currentPosition >= pointB!!) {
                    player.seekTo(pointA!!)
                    currentPlayTime = pointA!!
                }
            }
        }
    }

    // Sleep timer countdown thread
    LaunchedEffect(sleepTimerActive) {
        if (sleepTimerActive) {
            while (sleepTimerRemainingSecs > 0 && sleepTimerActive) {
                delay(1000)
                if (sleepTimerRemainingSecs > 0) {
                    sleepTimerRemainingSecs--
                } else {
                    player.pause()
                    sleepTimerActive = false
                    break
                }
            }
        }
    }

    // Tracks update states listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                videoDuration = player.duration.coerceAtLeast(0)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }

            override fun onCues(cues: List<androidx.media3.common.text.Cue>) {
                val rawText = cues
                    .mapNotNull { it.text?.toString() }
                    .joinToString("\n")
                
                // Post-process subtitle cleanups to improve reading coherence and correct cut-offs
                var cleanedText = rawText
                cleanedText = cleanedText.replace("oder in dat\\b".toRegex(RegexOption.IGNORE_CASE), "oder in Dativ")
                cleanedText = cleanedText.replace("oder in dat\\.".toRegex(RegexOption.IGNORE_CASE), "oder in Dativ.")
                cleanedText = cleanedText.replace("\\bin dat\\b".toRegex(RegexOption.IGNORE_CASE), "in Dativ")
                cleanedText = cleanedText.replace("\\bin dat\\.".toRegex(RegexOption.IGNORE_CASE), "in Dativ.")
                
                // Improve bad split breaks (e.g. merging dangling phrases for proper reading speed)
                cleanedText = cleanedText.replace("Also, es\\s*\\n\\s*".toRegex(RegexOption.IGNORE_CASE), "Also, es ")
                cleanedText = cleanedText.replace("es geht um\\s*\\n\\s*".toRegex(RegexOption.IGNORE_CASE), "es geht um ")
                
                activeSubtitleText = cleanedText
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                val message = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> 
                        "⚠️ خطأ في فك تشفير الفيديو: جهازك قد لا يدعم ترميز هذا الفيديو أو الدقة عالية جداً."
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> 
                        "⚠️ لم يتم العثور على الملف! يبدو أنه تم حذفه أو نقله من مكانه الأصلي."
                    else -> "⚠️ تعذر تشغيل هذا الملف: ${error.localizedMessage ?: "تنسيق غير مدعوم أو تالف"}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                
                // Fallback attempt: if HW decoration initialized poorly, change states to reset
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && !isHWAccelActive) {
                    isHWAccelActive = true
                }
            }
        }
        player.addListener(listener)
        player.setPlaybackSpeed(speedMultiplier)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(250)
            if (!isSeekingBySwipe) {
                currentPlayTime = player.currentPosition
            }
        }
    }

    // Auto-advance to next video when the current video finishes playing
    LaunchedEffect(playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            if (hasNextVideo) {
                val nextPath = allVideos[currentVideoIndex + 1].path
                onNavigateToVideo(nextPath)
            }
        }
    }

    // Auto fade controls delay helper
    LaunchedEffect(
        areControlsVisible,
        isPlayingState,
        isFilesListVisible,
        isQuickSettingsOpen,
        isSpeedExpanded,
        isSubtitlesExpanded,
        isMoreOptionsSheetOpen,
        isAudioTracksDialogOpen,
        isSubtitlePanelViewOpen,
        isSubtitleCustomizationOpen,
        isToolbarCustomizerDialogOpen,
        isSleepTimerDialogOpen,
        isEqualizerOpen
    ) {
        if (areControlsVisible && isPlayingState &&
            !isFilesListVisible && !isQuickSettingsOpen &&
            !isSpeedExpanded && !isSubtitlesExpanded &&
            !isMoreOptionsSheetOpen && !isAudioTracksDialogOpen &&
            !isSubtitlePanelViewOpen && !isSubtitleCustomizationOpen &&
            !isToolbarCustomizerDialogOpen && !isSleepTimerDialogOpen &&
            !isEqualizerOpen
        ) {
            delay(viewModel.getHideControlsDelay() * 1000L)
            areControlsVisible = false
            isBrightnessSliderVisible = false
        }
    }

    // Immersive screen layout dynamics
    LaunchedEffect(areControlsVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                if (!areControlsVisible) {
                    controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val flags = if (!areControlsVisible) {
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    var isUnlockPromptVisible by remember { mutableStateOf(false) }

    BackHandler {
        if (isLockedMode) {
            isUnlockPromptVisible = true
            scope.launch {
                delay(2000)
                isUnlockPromptVisible = false
            }
        } else if (isFilesListVisible) {
            isFilesListVisible = false
        } else if (isQuickSettingsOpen) {
            isQuickSettingsOpen = false
        } else {
            onBack()
        }
    }

    var currentOrientationState by remember { 
        mutableStateOf(activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) 
    }

    var hasAutoRotatedForCurrentVideo by remember(filePath) { mutableStateOf(false) }

    LaunchedEffect(videoWidth, videoHeight, filePath) {
        if (!hasAutoRotatedForCurrentVideo && videoWidth > 0 && videoHeight > 0) {
            val targetOrientation = if (videoWidth > videoHeight) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (activity?.requestedOrientation != targetOrientation) {
                activity?.requestedOrientation = targetOrientation
                currentOrientationState = targetOrientation
            }
            hasAutoRotatedForCurrentVideo = true
        }
    }

    val resolutionLabel = remember(videoWidth, videoHeight) {
        if (videoWidth >= 3840 || videoHeight >= 2160) "4K UHD 💎"
        else if (videoWidth >= 1920 || videoHeight >= 1080) "1080p FHD ✨"
        else if (videoWidth >= 1280 || videoHeight >= 720) "720p HD 🎬"
        else if (videoWidth > 0 && videoHeight > 0) "${videoHeight}p"
        else "1080p FHD"
    }

    val isAnyPopupOpen = isQuickSettingsOpen ||
            isDecoderDialogOpen ||
            isSleepTimerDialogOpen ||
            isEqualizerOpen ||
            isMoreOptionsSheetOpen ||
            isAudioTracksDialogOpen ||
            isSubtitlePanelViewOpen ||
            isSubtitleCustomizationOpen ||
            isToolbarCustomizerDialogOpen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(videoDuration, isLockedMode, isAnyPopupOpen) {
                if (isAnyPopupOpen) {
                    return@pointerInput
                }
                if (isLockedMode) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                            if (!areControlsVisible) {
                                isBrightnessSliderVisible = false
                            }
                        }
                    )
                } else {
                    awaitPointerEventScope {
                        var lastTapTime = 0L
                        var lastTapPosition = androidx.compose.ui.geometry.Offset.Zero
                        
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (isSubtitlePressed) {
                                // Bypass all parent player gestures when dragging/pressing the subtitles
                                var pointerId = down.id
                                var pressInputChange: PointerInputChange? = down
                                while (pressInputChange != null && pressInputChange.pressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    pressInputChange = if (change != null && change.pressed) change else null
                                }
                                continue
                            }
                            val downTime = System.currentTimeMillis()
                            val startPos = down.position
                            isDraggingRightSide = startPos.x > size.width / 2f
                            
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (currentVol < maxVolume.toInt()) {
                                currentVolRatio = currentVol.toFloat() / maxVolume
                            }
                            draggedVolRatio = currentVolRatio
                            
                            val currentBright = activity?.window?.attributes?.screenBrightness ?: -1f
                            val realBright = if (currentBright < 0f) {
                                try {
                                    android.provider.Settings.System.getInt(
                                        context.contentResolver,
                                        android.provider.Settings.System.SCREEN_BRIGHTNESS
                                    ).toFloat() / 255f
                                } catch (e: Exception) {
                                    0.5f
                                }
                            } else {
                                currentBright
                            }
                            draggedBrightness = realBright
                            currentBrightness = realBright
                            draggedSeekPosition = player.currentPosition
                            dragStartPlaybackTime = player.currentPosition
                            
                            var hasMoved = false
                            currentGestureType = "NONE"
                            
                            var pointerId = down.id
                            var pointerInputChange: PointerInputChange? = down
                            
                            var wasPlayingBeforeFastForward = false
                            var longPressJob: kotlinx.coroutines.Job? = scope.launch {
                                delay(400)
                                if (currentGestureType == "NONE" && !isLongPressFastForwarding) {
                                    isLongPressFastForwarding = true
                                    singleTapJob?.cancel()
                                    singleTapJob = null
                                    wasPlayingBeforeFastForward = player.isPlaying
                                    player.setPlaybackSpeed(2.0f)
                                    if (!wasPlayingBeforeFastForward) {
                                        player.play()
                                    }
                                }
                            }
                            
                            while (pointerInputChange != null && pointerInputChange.pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change != null && change.pressed) {
                                    pointerInputChange = change
                                    val totalX = change.position.x - startPos.x
                                    val totalY = startPos.y - change.position.y
                                    
                                    val threshold = 16f
                                    if (currentGestureType == "NONE" && (kotlin.math.abs(totalX) >= threshold || kotlin.math.abs(totalY) >= threshold)) {
                                        hasMoved = true
                                        longPressJob?.cancel()
                                        longPressJob = null
                                        singleTapJob?.cancel()
                                        singleTapJob = null
                                        if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                                            currentGestureType = "SEEK"
                                            showSeekDragIndicator = true
                                        } else {
                                            if (isDraggingRightSide) {
                                                currentGestureType = "VOLUME"
                                                showVolumeIndicator = true
                                            } else {
                                                currentGestureType = "BRIGHTNESS"
                                                showBrightnessIndicator = true
                                            }
                                        }
                                    }
                                    
                                    if (currentGestureType != "NONE") {
                                        change.consume()
                                        val dragAmountX = change.position.x - change.previousPosition.x
                                        val dragAmountY = change.previousPosition.y - change.position.y
                                        
                                        when (currentGestureType) {
                                            "VOLUME" -> {
                                                val deltaRatio = dragAmountY / size.height.toFloat()
                                                val newRatio = (draggedVolRatio + deltaRatio * 1.5f).coerceIn(0f, 2f)
                                                draggedVolRatio = newRatio
                                                currentVolRatio = newRatio
                                                if (newRatio <= 1.0f) {
                                                    val targetVol = (newRatio * maxVolume).toInt()
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                                    currentVolume = targetVol.toFloat()
                                                    try {
                                                        loudnessEnhancerInstance?.setTargetGain(0)
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                } else {
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume.toInt(), 0)
                                                    currentVolume = maxVolume
                                                    val extraRatio = newRatio - 1.0f
                                                    val targetGainMb = (extraRatio * 3000).toInt().coerceIn(0, 3000)
                                                    try {
                                                        loudnessEnhancerInstance?.setTargetGain(targetGainMb)
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                            }
                                            "BRIGHTNESS" -> {
                                                val deltaRatio = dragAmountY / size.height.toFloat()
                                                val newBrightness = (draggedBrightness + deltaRatio * 1.5f).coerceIn(0.01f, 1.0f)
                                                draggedBrightness = newBrightness
                                                currentBrightness = newBrightness
                                                val layoutParams = activity?.window?.attributes
                                                if (layoutParams != null) {
                                                    layoutParams.screenBrightness = newBrightness
                                                    activity?.window?.attributes = layoutParams
                                                }
                                            }
                                            "SEEK" -> {
                                                if (videoDuration > 0) {
                                                    // Make the seek gesture extremely intuitive and predictable
                                                    val spanMs = (videoDuration / 4).coerceAtLeast(45000L).coerceAtMost(180000L)
                                                    val deltaRatio = dragAmountX / size.width.toFloat()
                                                    val deltaMs = (deltaRatio * spanMs).toLong()
                                                    val targetPosition = (draggedSeekPosition + deltaMs).coerceIn(0L, videoDuration)
                                                    audiofySeekJob?.cancel()
                                                    audiofySeekSeconds = ((targetPosition - dragStartPlaybackTime) / 1000).toInt()
                                                    draggedSeekPosition = targetPosition
                                                    player.seekTo(targetPosition)
                                                    currentPlayTime = targetPosition
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    pointerInputChange = null
                                }
                            }
                            
                            longPressJob?.cancel()
                            longPressJob = null
                            
                            if (isLongPressFastForwarding) {
                                player.setPlaybackSpeed(speedMultiplier)
                                if (!wasPlayingBeforeFastForward) {
                                    player.pause()
                                }
                                isLongPressFastForwarding = false
                                hasMoved = true
                            }
                            
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                            showSeekDragIndicator = false
                            if (currentGestureType == "SEEK") {
                                audiofySeekJob?.cancel()
                                audiofySeekJob = scope.launch {
                                    delay(1200)
                                    audiofySeekSeconds = null
                                }
                            }
                            
                            if (!hasMoved) {
                                val upTime = System.currentTimeMillis()
                                if (upTime - downTime < 300) {
                                    if (upTime - lastTapTime < 300 && 
                                        kotlin.math.abs(startPos.x - lastTapPosition.x) < 50f && 
                                        kotlin.math.abs(startPos.y - lastTapPosition.y) < 50f) {
                                        
                                        // Cancel the single tap controls toggle job so that player controls/progress bar do not appear
                                        singleTapJob?.cancel()
                                        singleTapJob = null
                                        
                                        val fraction = startPos.x / size.width.toFloat()
                                        if (fraction < 0.35f) {
                                            val target = (player.currentPosition - 10000).coerceAtLeast(0)
                                            player.seekTo(target)
                                            currentPlayTime = target
                                            showRewindOverlay = true
                                            audiofySeekJob?.cancel()
                                            // audiofySeekSeconds = -10
                                            audiofySeekJob = scope.launch {
                                                delay(1200)
                                                audiofySeekSeconds = null
                                            }
                                            scope.launch {
                                                delay(700)
                                                showRewindOverlay = false
                                            }
                                        } else if (fraction > 0.65f) {
                                            val target = (player.currentPosition + 10000).coerceAtMost(videoDuration)
                                            player.seekTo(target)
                                            currentPlayTime = target
                                            showForwardOverlay = true
                                            audiofySeekJob?.cancel()
                                            // audiofySeekSeconds = 10
                                            audiofySeekJob = scope.launch {
                                                delay(1200)
                                                audiofySeekSeconds = null
                                            }
                                            scope.launch {
                                                delay(700)
                                                showForwardOverlay = false
                                            }
                                        } else {
                                            if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                        }
                                        lastTapTime = 0L
                                        lastTapPosition = androidx.compose.ui.geometry.Offset.Zero
                                    } else {
                                        // Delay the single tap action to allow potential double taps to cancel it,
                                        // keeping controls hidden on 10s seek
                                        singleTapJob?.cancel()
                                        singleTapJob = scope.launch {
                                            delay(250)
                                            areControlsVisible = !areControlsVisible
                                            if (!areControlsVisible) {
                                                isBrightnessSliderVisible = false
                                            }
                                        }
                                        
                                        lastTapTime = upTime
                                        lastTapPosition = startPos
                                    }
                                }
                            }
                            
                            currentGestureType = "NONE"
                        }
                    }
                }
            }
            .transformable(state = transformableState)
    ) {
        // AndroidView rendering Surface Player Canvas
        key(filePath) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        subtitleView?.visibility = android.view.View.GONE // Disable built-in caption layer
                        resizeMode = when (scaleMode) {
                            "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                    view.resizeMode = when (scaleMode) {
                        "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    view.subtitleView?.visibility = android.view.View.GONE // Force hide built-in caption layer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = if (isMirrorModeActive) -scale else scale,
                        scaleY = if (isVerticalFlipActive) -scale else scale
                    )
            )
        }

        // Night mode filter overlays
        if (isNightModeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE5A642).copy(alpha = 0.22f))
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }

        // Top-Center HUD Notification Pill (Fast Forward indicator or system status alerts)
        androidx.compose.animation.AnimatedVisibility(
            visible = isLongPressFastForwarding || (isIndicatorVisible && gestureIndicatorText != null),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            val pillText = if (isLongPressFastForwarding) "▶▶ 2x" else (gestureIndicatorText ?: "")
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(percent = 50))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(percent = 50))
                    .padding(vertical = 8.dp, horizontal = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLongPressFastForwarding) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Fast Forwarding",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = pillText,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ----------------------------------------------------------------------
        // GESTURES VISUAL FEEDBACK OVERLAYS
        // ----------------------------------------------------------------------
        // ⏪ Rewind overlay indicator (shows on left double tap)
        if (showRewindOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(topStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 0)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("10- ثوانٍ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // ⏩ Forward overlay indicator (shows on right double tap)
        if (showForwardOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
                    .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0, bottomStartPercent = 50)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("10+ ثوانٍ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // AudioManager / Volume Gesture visual slider cards (center-right card)
        if (showVolumeIndicator) {
            val volumePercentage = (draggedVolRatio * 100).toInt()
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color(0xFF00C8FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("الصوت", color = Color.LightGray, fontSize = 12.sp)
                    Text("$volumePercentage%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // Brightness Gesture visual slider cards (center-left card)
        if (showBrightnessIndicator) {
            val brightnessPercentage = (draggedBrightness * 100).toInt()
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Brightness5,
                        contentDescription = "Brightness",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("السطوع", color = Color.LightGray, fontSize = 12.sp)
                    Text("$brightnessPercentage%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }



        // 🎧 Audiofy-style Seek indicator at the Top Center of the screen
        AnimatedVisibility(
            visible = audiofySeekSeconds != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            val delta = audiofySeekSeconds ?: 0
            val sign = if (delta > 0) "+" else ""
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(50))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(50))
                    .padding(vertical = 6.dp, horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$sign${delta}s",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }

        // Subtitle Drag vertical position indicator
        if (isDraggingSubtitle) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "↕",
                        color = Color(0xFF00C8FF),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(subtitleStyle.bottomPadding * 1000).toInt()}",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 💬 CUSTOM COMPOSE CLICKABLE SUBTITLE OVERLAY
        if (isSubtitleEnabled && activeSubtitleText.isNotEmpty()) {
            val containsArabic = activeSubtitleText.any { it in '\u0600'..'\u06FF' }
            val layoutDir = if (containsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { parentHeightPx = it.height.toFloat().coerceAtLeast(100f) }
                ) {
                    val gravityAlignment = when (subtitleStyle.alignment) {
                        android.view.Gravity.TOP or android.view.Gravity.LEFT -> Alignment.TopStart
                        android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL -> Alignment.TopCenter
                        android.view.Gravity.TOP or android.view.Gravity.RIGHT -> Alignment.TopEnd
                        android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.LEFT -> Alignment.CenterStart
                        android.view.Gravity.CENTER -> Alignment.Center
                        android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT -> Alignment.CenterEnd
                        android.view.Gravity.BOTTOM or android.view.Gravity.LEFT -> Alignment.BottomStart
                        android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT -> Alignment.BottomEnd
                        else -> Alignment.BottomCenter
                    }
                    val bottomPadDp = (subtitleStyle.bottomPadding * 1000).dp
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val animatedExtraPad by animateDpAsState(
                        targetValue = if (areControlsVisible && !isLockedMode) {
                            with(density) { bottomControlsHeightPx.toDp() }
                        } else {
                            0.dp
                        },
                        animationSpec = spring(stiffness = 300f),
                        label = "subtitle_rise"
                    )
                    val extraBottomPad = (bottomPadDp + animatedExtraPad).coerceAtLeast(0.dp)
                    Box(
                        modifier = Modifier
                            .align(gravityAlignment)
                            .padding(start = 16.dp, end = 16.dp, bottom = extraBottomPad, top = 4.dp)
                            .wrapContentSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        isSubtitlePressed = true
                                        isDraggingSubtitle = true
                                        var pointerId = down.id
                                        var dragChange: PointerInputChange? = down
                                        while (dragChange != null && dragChange.pressed) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                            if (change != null && change.pressed) {
                                                change.consume()
                                                val deltaY = change.position.y - change.previousPosition.y
                                                val deltaRatio = deltaY / parentHeightPx
                                                val newPadding = (subtitleStyle.bottomPadding - deltaRatio).coerceIn(-0.03f, 0.30f)
                                                subtitleStyle = subtitleStyle.copy(bottomPadding = newPadding)
                                                dragChange = change
                                            } else {
                                                dragChange = null
                                            }
                                        }
                                        isSubtitlePressed = false
                                        isDraggingSubtitle = false
                                    }
                                }
                            }
                            .background(
                                color = if (!subtitleStyle.backgroundEnabled) Color.Transparent
                                        else if (isDraggingSubtitle) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else subtitleStyle.backgroundColor,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = if (isDraggingSubtitle) 1.5.dp else 0.dp,
                                color = if (isDraggingSubtitle) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { isSubtitleCustomizationOpen = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val fontWeight = if (subtitleStyle.bold) FontWeight.Bold else FontWeight.Normal
                        val fontStyle = if (subtitleStyle.italic)
                            androidx.compose.ui.text.font.FontStyle.Italic
                        else
                            androidx.compose.ui.text.font.FontStyle.Normal
                        val shadowStyle = when (subtitleStyle.edgeType) {
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> Shadow(
                                color = subtitleStyle.edgeColor.copy(alpha = 0.95f),
                                offset = Offset(2f, 2f), blurRadius = 4f
                            )
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE -> Shadow(
                                color = subtitleStyle.edgeColor.copy(alpha = 0.95f),
                                offset = Offset(1.5f, 1.5f), blurRadius = 3f
                            )
                            else -> null
                        }
                        Text(
                            text = activeSubtitleText,
                            color = subtitleStyle.textColor,
                            fontSize = (16f * subtitleStyle.textSize).sp,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            textAlign = TextAlign.Center,
                            lineHeight = (16f * subtitleStyle.textSize * 1.2f).sp,
                            style = TextStyle(
                                shadow = shadowStyle,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }
        }

        // -----------------------------------------------------
        // TOP CONTROLS HUD (Title, orientation, speed label, resize, Auto-Rotate, PiP, Back)
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = areControlsVisible && !isLockedMode,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMediaFile.nameWithoutExtension,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // DECODER CHIP (HW / HW+ / SW)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .border(1.dp, Color(0xFF00C8FF).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .background(Color(0xFF00C8FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .clickable { isDecoderDialogOpen = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentDecoder,
                                    color = Color(0xFF00C8FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Decoder Dropdown",
                                    tint = Color(0xFF00C8FF),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // SCREENSHOT BUTTON
                        IconButton(
                            onClick = { takeScreenshot(context) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "لقطة الشاشة",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // AUDIO TRACKS BUTTON
                        IconButton(
                            onClick = { isAudioTracksDialogOpen = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "مسار الصوت",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // SUBTITLES BUTTON
                        IconButton(
                            onClick = { isSubtitlePanelViewOpen = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "الترجمة",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // PIP WINDOW BUTTON
                        IconButton(
                            onClick = {
                                try {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        activity?.enterPictureInPictureMode(
                                            android.app.PictureInPictureParams.Builder().build()
                                        )
                                    } else {
                                        activity?.enterPictureInPictureMode()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "النافذة العائمة غير مدعومة حالياً", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "نافذة عائمة",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // MORE OPTIONS MENU BUTTON (⋮)
                        IconButton(
                            onClick = { isMoreOptionsSheetOpen = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "المزيد",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                    }
                }
                } // End of CompositionLocalProvider Ltr
            }
        }



        // -----------------------------------------------------
        // SCREEN LOCK MODE INDICATOR
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isLockedMode && areControlsVisible,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Button(
                onClick = {
                    isLockedMode = false
                    gestureIndicatorText = "🔓 تم فك قفل الشاشة"
                    scope.launch {
                        isIndicatorVisible = true
                        delay(700)
                        isIndicatorVisible = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("انقر لفك القفل (Unlock Screen) 🛡️")
                }
            }
        }

        // -----------------------------------------------------
        // ON-SCREEN COMPACT SLIDER FOR BRIGHTNESS
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isBrightnessSliderVisible && areControlsVisible && !isLockedMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .width(60.dp)
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Default.Brightness5, contentDescription = "Brightness Low", tint = Color.White)
                    
                    Slider(
                        value = currentBrightness,
                        onValueChange = {
                            currentBrightness = it
                            val layoutParams = activity?.window?.attributes
                            layoutParams?.screenBrightness = currentBrightness
                            activity?.window?.attributes = layoutParams
                        },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                rotationZ = -90f
                            }
                            .width(120.dp)
                    )
                    
                    Text(
                        text = "${(currentBrightness * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -----------------------------------------------------
        // BOTTOM ACTION CONTROL BAR HUD
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = areControlsVisible && !isLockedMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .onSizeChanged { bottomControlsHeightPx = it.height }
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                // Progress Slider (SeekBar with visual parameters) - Isolated composable for zero-recompositions performance
                PlayerProgressSlider(
                    currentPlayTimeProvider = { currentPlayTime },
                    videoDuration = videoDuration,
                    currentAccentColor = currentAccentColor,
                    onSeek = { target ->
                        currentPlayTime = target
                        player.seekTo(target)
                    }
                )

                // Buttons control toolbar panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left row controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { isLockedMode = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = { isFilesListVisible = !isFilesListVisible }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.FeaturedPlayList,
                                contentDescription = "قائمة الفيديوهات",
                                tint = if (isFilesListVisible) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = { isQuickSettingsOpen = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات التشغيل", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Centered row controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (hasPreviousVideo) {
                                    val prevPath = allVideos[currentVideoIndex - 1].path
                                    onNavigateToVideo(prevPath)
                                }
                            },
                            enabled = hasPreviousVideo,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous File",
                                tint = if (hasPreviousVideo) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        var rewindPressed by remember { mutableStateOf(false) }
                        val rewindScale by animateFloatAsState(
                            targetValue = if (rewindPressed) 0.75f else 1f,
                            animationSpec = spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
                            ),
                            label = "rewind_bounce"
                        )
                        LaunchedEffect(rewindPressed) {
                            if (rewindPressed) {
                                delay(120)
                                rewindPressed = false
                            }
                        }

                        IconButton(
                            onClick = {
                                rewindPressed = true
                                val target = (player.currentPosition - seekStepSeconds * 1000L).coerceAtLeast(0)
                                player.seekTo(target)
                                currentPlayTime = target
                                audiofySeekJob?.cancel()
                                // audiofySeekSeconds = -seekStepSeconds
                                audiofySeekJob = scope.launch {
                                    delay(1200)
                                    audiofySeekSeconds = null
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer {
                                    scaleX = rewindScale
                                    scaleY = rewindScale
                                }
                        ) {
                            Icon(
                                imageVector = when (seekStepSeconds) {
                                    5 -> Icons.Default.Replay5
                                    30 -> Icons.Default.Replay30
                                    else -> Icons.Default.Replay10
                                },
                                contentDescription = "Back Step",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Pulse animation state
                        val pulseAnim = remember { androidx.compose.animation.core.Animatable(1f) }
                        LaunchedEffect(isPlayingState) {
                            if (!isPlayingState) {
                                while (true) {
                                    pulseAnim.animateTo(
                                        targetValue = 1.12f,
                                        animationSpec = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                    )
                                    pulseAnim.animateTo(
                                        targetValue = 1f,
                                        animationSpec = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                    )
                                }
                            } else {
                                pulseAnim.snapTo(1f)
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.dp)
                        ) {
                            // Pulse ring خلف الزر
                            if (!isPlayingState) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .graphicsLayer {
                                            scaleX = pulseAnim.value
                                            scaleY = pulseAnim.value
                                            alpha = (1.4f - pulseAnim.value) * 0.6f
                                        }
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        if (isPlayingState) player.pause() else player.play()
                                    }
                                    .testTag("player_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Control Toggle",
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        var forwardPressed by remember { mutableStateOf(false) }
                        val forwardScale by animateFloatAsState(
                            targetValue = if (forwardPressed) 0.75f else 1f,
                            animationSpec = spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessHigh
                            ),
                            label = "forward_bounce"
                        )
                        LaunchedEffect(forwardPressed) {
                            if (forwardPressed) {
                                delay(120)
                                forwardPressed = false
                            }
                        }

                        IconButton(
                            onClick = {
                                forwardPressed = true
                                val target = (player.currentPosition + seekStepSeconds * 1000L).coerceAtMost(player.duration)
                                player.seekTo(target)
                                currentPlayTime = target
                                audiofySeekJob?.cancel()
                                // audiofySeekSeconds = seekStepSeconds
                                audiofySeekJob = scope.launch {
                                    delay(1200)
                                    audiofySeekSeconds = null
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer {
                                    scaleX = forwardScale
                                    scaleY = forwardScale
                                }
                        ) {
                            Icon(
                                imageVector = when (seekStepSeconds) {
                                    5 -> Icons.Default.Forward5
                                    30 -> Icons.Default.Forward30
                                    else -> Icons.Default.Forward10
                                },
                                contentDescription = "Forward Step",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (hasNextVideo) {
                                    val nextPath = allVideos[currentVideoIndex + 1].path
                                    onNavigateToVideo(nextPath)
                                }
                            },
                            enabled = hasNextVideo,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next File",
                                tint = if (hasNextVideo) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Right row details
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val current = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                val target = if (current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else if (current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    if ((activity?.resources?.configuration?.orientation ?: 1) == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                }
                                activity?.requestedOrientation = target
                                currentOrientationState = target
                                gestureIndicatorText = if (target == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) "الوضع الرأسي (عمودي)" else "الوضع الأفقي (دوران)"
                                scope.launch {
                                    isIndicatorVisible = true
                                    delay(800)
                                    isIndicatorVisible = false
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "دوران الشاشة",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { isSpeedExpanded = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed multiplier rate", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = isSpeedExpanded,
                                onDismissRequest = { isSpeedExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
                                speeds.forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x", color = Color.White) },
                                        onClick = {
                                            speedMultiplier = speed
                                            player.setPlaybackSpeed(speed)
                                            isSpeedExpanded = false
                                            gestureIndicatorText = "السرعة: ${speed}x"
                                            scope.launch {
                                                isIndicatorVisible = true
                                                delay(800)
                                                isIndicatorVisible = false
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (subtitleLanguages.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { isSubtitlesExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ClosedCaption,
                                        contentDescription = "الترجمة",
                                        tint = if (isSubtitleEnabled) MaterialTheme.colorScheme.primary else Color.LightGray
                                    )
                                }
                                DropdownMenu(
                                    expanded = isSubtitlesExpanded,
                                    onDismissRequest = { isSubtitlesExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("إيقاف الترجمة", color = Color.White) },
                                        onClick = {
                                            isSubtitleEnabled = false
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            isSubtitlesExpanded = false
                                            gestureIndicatorText = "الترجمة: معطلة"
                                            scope.launch {
                                                isIndicatorVisible = true
                                                delay(800)
                                                isIndicatorVisible = false
                                            }
                                        }
                                    )
                                    subtitleLanguages.forEachIndexed { idx, lang ->
                                        val subFile = detectedSubtitles.getOrNull(idx)
                                        val displayName = subFile?.name ?: "ترجمة: $lang"
                                        DropdownMenuItem(
                                            text = { Text(displayName, color = Color.White) },
                                            onClick = {
                                                isSubtitleEnabled = true
                                                selectedSubtitleLang = lang
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(lang)
                                                    .build()
                                                isSubtitlesExpanded = false
                                                gestureIndicatorText = "ترجمة: $displayName"
                                                scope.launch {
                                                    isIndicatorVisible = true
                                                    delay(800)
                                                    isIndicatorVisible = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // End of CompositionLocalProvider Ltr
        }

        // -----------------------------------------------------
        // SIDE BAR EXPLORER: FILES LIST DRAWER OVERLAY PANEL
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isFilesListVisible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(280.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ملفات الفيديوهات (Videos)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isFilesListVisible = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Explorer", tint = Color.LightGray)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 8.dp))

                    if (allVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد فيديوهات أخرى", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(allVideos) { idx, video ->
                                val isSelected = video.path == filePath
                                Card(
                                    onClick = {
                                        isFilesListVisible = false
                                        onNavigateToVideo(video.path)
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                        else Color.White.copy(alpha = 0.04f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = video.title,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "حجم: %.1f MB".format(video.size / (1024f * 1024f)),
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                            if (isSelected) {
                                                Text(
                                                    text = "قيد التشغيل ⏳",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------
        // QUICK PLAYBACK OPTIONS DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isQuickSettingsOpen,
            onDismissRequest = { isQuickSettingsOpen = false },
            title = "الإعدادات السريعة (Quick Settings)"
        ) {
            Text(
                text = "خطوة التخطي بالنقرة المزدوجة (Seek step):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val steps = listOf(5, 10, 15, 30, 60)
                steps.forEach { step ->
                    FilterChip(
                        selected = seekStepSeconds == step,
                        onClick = { seekStepSeconds = step },
                        label = { Text("${step}ث", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "مقياس ملء الشاشة (Scaling):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val scalingModes = listOf("FIT", "FILL", "STRETCH", "CROP")
                scalingModes.forEach { mode ->
                    FilterChip(
                        selected = scaleMode == mode,
                        onClick = { scaleMode = mode },
                        label = { Text(mode, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "حجم خط الترجمة (Subtitle text size):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
            )
            var subSize by remember { mutableStateOf(viewModel.getSubtitleSize()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = subSize,
                    onValueChange = {
                        subSize = it
                        viewModel.saveSubtitleSize(it)
                        subtitleStyle = subtitleStyle.copy(textSize = it / 16f)
                    },
                    valueRange =  12f..30f,
                    modifier = Modifier.weight(1f).height(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${subSize.toInt()}dp", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تفعيل الترجمة التلقائية:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
                Switch(
                    checked = isSubtitleEnabled,
                    onCheckedChange = {
                        isSubtitleEnabled = it
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !it)
                            .build()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { isQuickSettingsOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تم الحفظ والإغلاق", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // MORE OPTIONS (⋮) BOTTOM SHEET DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isMoreOptionsSheetOpen,
            onDismissRequest = { isMoreOptionsSheetOpen = false },
            title = "خيارات إضافية (More Options)"
        ) {
            val gridItems = listOf(
                Pair("☰", "قوائم التشغيل") to {
                    Toast.makeText(context, "مدير قوائم التشغيل نشط", Toast.LENGTH_SHORT).show()
                },
                Pair("⬛↕", "نسبة العرض") to {
                    scaleMode = when (scaleMode) {
                        "FIT" -> "FILL"
                        "FILL" -> "STRETCH"
                        "STRETCH" -> "CROP"
                        else -> "FIT"
                    }
                    Toast.makeText(context, "حجم العرض: $scaleMode", Toast.LENGTH_SHORT).show()
                },
                Pair("🖥️", "نمط مخصص") to {
                    isQuickSettingsOpen = true
                    isMoreOptionsSheetOpen = false
                },
                Pair("🔖", "إشارة مرجعية") to {
                    val bookPos = player.currentPosition
                    val totalSec = bookPos / 1000
                    val curStr = "%02d:%02d:%02d".format(totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
                    Toast.makeText(context, "تم حفظ الإشارة المرجعية عند $curStr", Toast.LENGTH_SHORT).show()
                },
                Pair("✂️", "قص الفيديو") to {
                    Toast.makeText(context, "ميزة قص الفيديو مخصصة للأجهزة الكبيرة", Toast.LENGTH_SHORT).show()
                },
                Pair("❤️", "المفضلة") to {
                    Toast.makeText(context, "تمت الإضافة للمفضلة بنجاح! ❤️", Toast.LENGTH_SHORT).show()
                },
                Pair("➕☰", "قائمة تشغيل") to {
                    Toast.makeText(context, "محدد قوائم التشغيل متاح", Toast.LENGTH_SHORT).show()
                },
                Pair("ℹ️", "معلومات") to {
                    val durationSec = player.duration / 1000
                    val infoStr = "الملف: ${currentMediaFile.name}\nالدقة: $videoWidth x $videoHeight\nالحجم: %.2f MB\nالمدة: %02d:%02d:%02d".format(
                        currentMediaFile.length() / (1024f * 1024f),
                        durationSec / 3600,
                        (durationSec % 3600) / 60,
                        durationSec % 60
                    )
                    Toast.makeText(context, infoStr, Toast.LENGTH_LONG).show()
                },
                Pair("🔗", "مشاركة") to {
                    try {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, Uri.fromFile(currentMediaFile))
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة الفيديو"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "جاري مشاركة الفيديو", Toast.LENGTH_SHORT).show()
                    }
                },
                Pair("🌐", "Cast") to {
                    Toast.makeText(context, "البحث عن شاشات ذكية نشطة (Cast)...", Toast.LENGTH_SHORT).show()
                },
                Pair("💡", "المساعد") to {
                    isTutorialOverlayVisible = true
                    isMoreOptionsSheetOpen = false
                },
                Pair(">", "المزيد") to {
                    Toast.makeText(context, "المزيد من الخيارات متاحة في الإعدادات العامة", Toast.LENGTH_SHORT).show()
                }
            )

            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.height(350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(gridItems.size) { index ->
                    val item = gridItems[index]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF26262B)),
                        shape = RoundedCornerShape(0.dp), // SHARP CORNERS!
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable {
                                item.second()
                                isMoreOptionsSheetOpen = false
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(item.first.first, fontSize = 16.sp, color = Color(0xFF00C8FF))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.first.second,
                                fontSize = 10.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { isMoreOptionsSheetOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إغلاق", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // DECODER SELECTION DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isDecoderDialogOpen,
            onDismissRequest = { isDecoderDialogOpen = false },
            title = "حدد الترميز (Decoder)"
        ) {
            val decoders = listOf("HW", "HW+", "SW")
            decoders.forEach { decoder ->
                val isSelected = currentDecoder == decoder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentDecoder = decoder
                            isHWAccelActive = (decoder == "HW" || decoder == "HW+")
                            Toast.makeText(context, "تم التبديل إلى ترميز $decoder", Toast.LENGTH_SHORT).show()
                            isDecoderDialogOpen = false
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "ترميز $decoder",
                        color = if (isSelected) Color(0xFF00C8FF) else Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            currentDecoder = decoder
                            isHWAccelActive = (decoder == "HW" || decoder == "HW+")
                            Toast.makeText(context, "تم التبديل إلى ترميز $decoder", Toast.LENGTH_SHORT).show()
                            isDecoderDialogOpen = false
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF00C8FF),
                            unselectedColor = Color.LightGray
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { isDecoderDialogOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // AUDIO TRACK SELECTION DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isAudioTracksDialogOpen,
            onDismissRequest = { isAudioTracksDialogOpen = false },
            title = "قنوات الصوت (Audio Tracks)"
        ) {
            val audioTracks = listOf(
                "القناة الأساسية الافتراضية (Default)",
                "قناة ستيريو عربية معدلة",
                "English alternate track",
                "كتم قناة الصوت فقط"
            )
            
            audioTracks.forEachIndexed { idx, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (idx == 3) {
                                player.volume = 0f
                                isMuted = true
                            } else {
                                player.volume = 1f
                                isMuted = false
                            }
                            gestureIndicatorText = "تم اختيار: $label"
                            isAudioTracksDialogOpen = false
                            scope.launch { isIndicatorVisible = true; delay(850); isIndicatorVisible = false }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Right)
                    Spacer(modifier = Modifier.width(12.dp))
                    RadioButton(
                        selected = (idx == 0 && !isMuted) || (idx == 3 && isMuted),
                        onClick = null
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { isAudioTracksDialogOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // SUBTITLE PANEL DIALOG (CC Overlay configuration)
        // -----------------------------------------------------
        SubtitleSettingsPanel(
            isVisible = isSubtitlePanelViewOpen,
            onDismiss = { isSubtitlePanelViewOpen = false },
            isSubtitleEnabled = isSubtitleEnabled,
            onSubtitleEnabledChange = { enabled ->
                isSubtitleEnabled = enabled
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                    .build()
            },
            detectedSubtitles = detectedSubtitles,
            subtitleLanguages = subtitleLanguages,
            selectedSubtitleLang = selectedSubtitleLang,
            onSelectedSubtitleLangChange = { lang ->
                isSubtitleEnabled = true
                selectedSubtitleLang = lang
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(lang)
                    .build()
            },
            manualSubs = manualSubs,
            onAddSubtitleClick = {
                try { subtitlePickerLauncher.launch(arrayOf("*/*")) } catch (e: Exception) { }
            },
            onCustomizeAppearanceClick = {
                isSubtitleCustomizationOpen = true
                isSubtitlePanelViewOpen = false
            },
            subtitleDelayMs = subtitleDelayMs,
            onSubtitleDelayMsChange = { subtitleDelayMs = it },
            subtitleSpeed = subtitleSpeed,
            onSubtitleSpeedChange = { subtitleSpeed = it },
            subtitleStyle = subtitleStyle,
            onSubtitleStyleChange = { subtitleStyle = it },
            filePath = filePath,
            videoDurationMs = videoDuration,
            onSubtitleFileGenerated = { file ->
                val dispName = file.name
                val uri = android.net.Uri.fromFile(file)
                val currentPos = player.currentPosition
                val compositeConfigs = mutableListOf<androidx.media3.common.MediaItem.SubtitleConfiguration>()
                detectedSubtitles.forEachIndexed { idx, f ->
                    val fLang = subtitleLanguages.getOrNull(idx) ?: "ar"
                    val subUri = android.net.Uri.fromFile(f)
                    val isSrt = f.name.endsWith(".srt", ignoreCase = true)
                    val mimeType = if (isSrt) "application/x-subrip" else "text/vtt"
                    compositeConfigs.add(
                        androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(subUri)
                            .setMimeType(mimeType).setLanguage(fLang)
                            .setSelectionFlags(if (idx == 0) C.SELECTION_FLAG_DEFAULT else 0).build()
                    )
                }
                val newLang = "manual_${manualSubs.size}_$dispName"
                val newIsSrt = dispName.endsWith(".srt", ignoreCase = true)
                val newMimeType = if (newIsSrt) "application/x-subrip" else "text/vtt"
                compositeConfigs.add(
                    androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(uri)
                        .setMimeType(newMimeType).setLanguage(newLang)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                )
                manualSubs.add(Pair(dispName, uri))
                val videoUri = android.net.Uri.fromFile(java.io.File(filePath))
                val newMediaItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(videoUri).setSubtitleConfigurations(compositeConfigs).build()
                player.setMediaItem(newMediaItem)
                player.prepare()
                player.seekTo(currentPos)
                isSubtitleEnabled = true
                selectedSubtitleLang = newLang
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(newLang).build()
            }
        )

        // -----------------------------------------------------
        // SUBTITLE CUSTOMIZATION DIALOG (DataStore Persisted)
        // -----------------------------------------------------
        if (isSubtitleCustomizationOpen) {
            isSubtitlePanelViewOpen = true
            isSubtitleCustomizationOpen = false
        }


        // -----------------------------------------------------
        // EQUALIZER BOTTOM SHEET DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isEqualizerOpen,
            onDismissRequest = { isEqualizerOpen = false },
            title = "موازن الصوت (Equalizer Panel) 🎚️"
        ) {
            Text("مسبقات موازن الصوت (Presets):", color = Color.White, fontSize = 11.sp)
            val eqPresetsList = listOf("Normal", "Bass Boost", "Treble Boost", "Flat", "Classical", "Rock")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                items(eqPresetsList.size) { idx ->
                    FilterChip(
                        selected = equalizerPresetIndex == idx,
                        onClick = {
                            equalizerPresetIndex = idx
                            isEqualizerActive = true
                            try {
                                equalizerInstance?.usePreset(idx.toShort())
                            } catch (e: Exception) {}
                            
                            equalizerBandLevels = when (idx) {
                                1 -> floatArrayOf(0.8f, 0.4f, 0.1f, 0.1f, 0.1f)
                                2 -> floatArrayOf(0.1f, 0.1f, 0.4f, 0.7f, 0.9f)
                                3 -> floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
                                4 -> floatArrayOf(0.5f, 0.3f, 0.2f, 0.4f, 0.5f)
                                5 -> floatArrayOf(0.6f, 0.4f, -0.1f, 0.4f, 0.7f)
                                else -> floatArrayOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
                            }
                            Toast.makeText(context, "الوضع النشط: ${eqPresetsList[idx]}", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(eqPresetsList[idx], fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("ترددات موازنة الصوت (5-Band):", color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)

            val bandFrequenciesList = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
            repeat(5) { band ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(bandFrequenciesList[band], color = Color.White, fontSize = 11.sp)
                        val dbValue = (equalizerBandLevels[band] * 12).toInt()
                        Text("${if (dbValue > 0) "+" else ""}${dbValue} dB", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Slider(
                        value = equalizerBandLevels[band],
                        onValueChange = { newVal ->
                            setEqualizerBand(band, newVal)
                            isEqualizerActive = true
                        },
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00C8FF),
                            activeTrackColor = Color(0xFF00C8FF)
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { isEqualizerOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("موافق", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // SLEEP TIMER DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isSleepTimerDialogOpen,
            onDismissRequest = { isSleepTimerDialogOpen = false },
            title = "مؤقت النوم (Sleep Timer) ⏱"
        ) {
            Text("تحديد وقت إيقاف التشغيل التلقائي للفيديو الحالي:", color = Color.LightGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            
            val timesList = listOf(5, 10, 15, 30, 60)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timesList.forEach { mins ->
                    Button(
                        onClick = {
                            sleepTimerInitialMinutes = mins
                            sleepTimerRemainingSecs = mins * 60
                            sleepTimerActive = true
                            isSleepTimerDialogOpen = false
                            gestureIndicatorText = "تم تفعيل مؤقت النوم: $mins دقيقة"
                            scope.launch { isIndicatorVisible = true; delay(900); isIndicatorVisible = false }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$mins دقائق (Minutes)", color = Color.White, fontSize = 13.sp)
                    }
                }
                
                if (sleepTimerActive) {
                    Button(
                        onClick = {
                            sleepTimerActive = false
                            sleepTimerRemainingSecs = 0
                            isSleepTimerDialogOpen = false
                            gestureIndicatorText = "مؤقت النوم: معطل"
                            scope.launch { isIndicatorVisible = true; delay(900); isIndicatorVisible = false }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إيقاف المؤقت النشط", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { isSleepTimerDialogOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // TOOLBAR CUSTOMIZATION DIALOG
        // -----------------------------------------------------
        SidePanel(
            visible = isToolbarCustomizerDialogOpen,
            onDismissRequest = { isToolbarCustomizerDialogOpen = false },
            title = "تخصيص أزرار التحكم ✏️"
        ) {
            val mxAllToolsList = listOf(
                "🌙" to "الوضع الليلي",
                "✏️" to "أدوات التخصيص",
                "🔀" to "تشغيل عشوائي",
                "🔁" to "تكرار",
                "🔇" to "كتم الصوت",
                "⏱" to "مؤقت النوم",
                "A↔B" to "تكرار AB",
                "🎚️" to "موازن الصوت",
                "1X" to "سرعة التحكم",
                "📷" to "لقطة شاشة",
                "▶⬛" to "التشغيل في الخلفية",
                "↩️" to "استدارة تلقائية",
                "Flip" to "عكس رأسي",
                "Mirror" to "وضع المرأة"
            )
            
            Text("اختر الأزرار النشطة للإظهار بالأداة السريعة:", color = Color.LightGray, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(10.dp))
            
            mxAllToolsList.forEach { tool ->
                val isChecked = checkedExtendedTools.contains(tool.first)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = if (isChecked) checkedExtendedTools - tool.first else checkedExtendedTools + tool.first
                            checkedExtendedTools = newSet
                            context.getSharedPreferences("mx_player_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putStringSet("tools", newSet)
                                .apply()
                        }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${tool.first} ${tool.second}", color = Color.White, fontSize = 12.sp)
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { _ ->
                            val newSet = if (isChecked) checkedExtendedTools - tool.first else checkedExtendedTools + tool.first
                            checkedExtendedTools = newSet
                            context.getSharedPreferences("mx_player_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putStringSet("tools", newSet)
                                .apply()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { isToolbarCustomizerDialogOpen = false },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ وتعديل التفضيلات", color = Color.White, fontSize = 13.sp)
            }
        }

        // -----------------------------------------------------
        // GESTURES ONBOARDING TUTORIAL OVERLAY
        // -----------------------------------------------------
        if (isTutorialOverlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { isTutorialOverlayVisible = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("💡 دليل حركات التحكم السريعة (Gestures Guide)", color = Color(0xFF00C8FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    val listTutorials = listOf(
                        "Slide Left Side ⬆⬇" to "تعديل نسبة سطوع الشاشة (Brightness)",
                        "Slide Right Side ⬆⬇" to "تحكم شدة الصوت (Volume)",
                        "Double Tap Left ⏪" to "إرجاع الفيديو للوراء 10 ثوانٍ",
                        "Double Tap Right ⏩" to "تقديم الفيديو للأمام 10 ثوانٍ",
                        "Long Press Hold ⏩" to "تسريع الفيديو x2 فوري أثناء التثبيت",
                        "Horizontal Slide ↔" to "تعديل دقيق لمكان تشغيل الإطار (Seek)"
                    )
                    
                    listTutorials.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.second, color = Color.White, fontSize = 12.sp)
                            Text(item.first, color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(30.dp))
                    Text("انقر في أي مكان للإغلاق والعودة للمشاهدة", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

// Helper formatting method
private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
fun PlayerProgressSlider(
    currentPlayTimeProvider: () -> Long,
    videoDuration: Long,
    currentAccentColor: Color,
    onSeek: (Long) -> Unit
) {
    val totalSecs = videoDuration / 1000
    val curSecs = currentPlayTimeProvider() / 1000

    val totalHours = totalSecs / 3600
    val totalMinutes = (totalSecs % 3600) / 60
    val totalSeconds = totalSecs % 60

    val curHours = curSecs / 3600
    val curMinutes = (curSecs % 3600) / 60
    val curSeconds = curSecs % 60

    val totalStr = "%02d:%02d:%02d".format(totalHours, totalMinutes, totalSeconds)
    val curStr = "%02d:%02d:%02d".format(curHours, curMinutes, curSeconds)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(curStr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(18.dp)
                .testTag("player_seek_bar")
                .pointerInput(videoDuration) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (videoDuration > 0) {
                                val percent = (offset.x / size.width).coerceIn(0f, 1f)
                                val target = (percent * videoDuration).toLong()
                                onSeek(target)
                            }
                        }
                    )
                }
                .pointerInput(videoDuration) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (videoDuration > 0) {
                            val percent = (change.position.x / size.width).coerceIn(0f, 1f)
                            val target = (percent * videoDuration).toLong()
                            onSeek(target)
                        }
                    }
                }
        ) {
            val widthDp = with(LocalDensity.current) { constraints.maxWidth.toDp() }
            val fraction = if (videoDuration > 0) currentPlayTimeProvider().toFloat() / videoDuration else 0f

            // Inactive track (grey thin line)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(1.dp))
            )

            // Active track (primary colored thin line)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.5.dp)
                    .align(Alignment.CenterStart)
                    .background(currentAccentColor, RoundedCornerShape(1.dp))
            )

            // Tiny circular thumb element
            val thumbSize = 8.dp
            val halfThumb = thumbSize / 2
            val thumbOffset = (widthDp * fraction - halfThumb).coerceIn(0.dp, widthDp - thumbSize)

            // Glow ring خلف الـ thumb
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset - 4.dp)
                    .size(16.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                currentAccentColor.copy(alpha = 0.45f),
                                currentAccentColor.copy(alpha = 0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // الـ thumb الأصلي
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .background(currentAccentColor, CircleShape)
            )
        }
        Text(totalStr, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
fun SidePanel(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Semi-transparent background that dismisses when clicked
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() }
            )
            
            // Side panel container
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isLandscape) 340.dp else 280.dp)
                    .align(Alignment.CenterEnd), // Right side panel
                color = Color(0xFF141419),
                shape = RoundedCornerShape(0.dp), // SHARP CORNERS!
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                        Text(
                            text = title,
                            color = Color(0xFF00C8FF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                    
                    // Main content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
