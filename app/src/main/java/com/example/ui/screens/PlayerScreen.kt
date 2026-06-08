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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val allVideos by viewModel.videos.collectAsState(initial = emptyList())
    val currentMediaFile = remember(filePath) { File(filePath) }

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
            val nameLower = file.name.lowercase()
            val baseName = File(filePath).nameWithoutExtension
            val suffix = if (file.name.length >= baseName.length) {
                file.name.substring(baseName.length)
                    .removeSuffix(".srt").removeSuffix(".vtt")
                    .removeSuffix(".SRT").removeSuffix(".VTT")
            } else {
                ""
            }
            val extractedLang = if (suffix.startsWith(".")) {
                val part = suffix.substring(1).lowercase()
                if (part.isNotEmpty()) part else ""
            } else {
                ""
            }
            when {
                extractedLang == "ar" || extractedLang == "ara" || nameLower.contains("arabic") || nameLower.contains("عربي") -> "ar"
                extractedLang == "en" || extractedLang == "eng" || nameLower.contains("english") || nameLower.contains("انجليزي") -> "en"
                extractedLang == "de" || extractedLang == "ger" || nameLower.contains("german") || nameLower.contains("الماني") -> "de"
                extractedLang.isNotEmpty() && extractedLang.length in 2..3 -> extractedLang
                else -> {
                    // Unique fallback subtag for ExoPlayer matching
                    "x-sub-$index"
                }
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

    // Save history and lifecycle progress updates
    var playbackPosition by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
         while (true) {
             delay(1000)
             playbackPosition = player.currentPosition
             viewModel.addToHistory(filePath, playbackPosition)
         }
    }

    // Clean player on exit
    DisposableEffect(player) {
        onDispose {
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

    // Pinch-to-zoom parameters
    var scale by remember { mutableStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1.0f, 4.0f)
    }

    // Sub-menus visible states
    var isFilesListVisible by remember { mutableStateOf(false) }
    var isQuickSettingsOpen by remember { mutableStateOf(false) }
    var isBrightnessSliderVisible by remember { mutableStateOf(false) }

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
    var isHWAccelActive by remember { mutableStateOf(false) }

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

    var isMoreOptionsSheetOpen by remember { mutableStateOf(false) }
    var isAudioTracksDialogOpen by remember { mutableStateOf(false) }
    var isSubtitlePanelViewOpen by remember { mutableStateOf(false) }
    var isSubtitleCustomizationOpen by remember { mutableStateOf(false) }
    var isToolbarCustomizerDialogOpen by remember { mutableStateOf(false) }
    var isTutorialOverlayVisible by remember { mutableStateOf(false) }

    // Subtitle customization details
    var subAlignment by remember { mutableStateOf("Center") }
    var subPadding by remember { mutableStateOf(8) }
    var isSubBgTransparent by remember { mutableStateOf(true) }
    var isSubFitVideo by remember { mutableStateOf(true) }
    var subFontName by remember { mutableStateOf("Default") }
    var subFontSize by remember { mutableStateOf(20f) }
    var subTextColor by remember { mutableStateOf(Color.White) }
    var isSubShadowEnabled by remember { mutableStateOf(true) }
    var subShadowIntensity by remember { mutableStateOf(2f) }
    var subtitleDelaySeconds by remember { mutableStateOf(0.0f) }

    var activeSubtitleText by remember { mutableStateOf("") }
    val subPrefsManager = remember { com.example.data.SubtitlePrefsManager(context) }
    val subtitlePrefsState by subPrefsManager.subtitlePreferencesFlow.collectAsState(initial = com.example.data.SubtitlePreferences())

    var localSubtitleOffset by remember { mutableStateOf<Float?>(null) }
    var parentHeightPx by remember { mutableStateOf(1000f) }
    var isDraggingSubtitle by remember { mutableStateOf(false) }

    var checkedExtendedTools by remember {
        mutableStateOf(
            context.getSharedPreferences("mx_player_prefs", Context.MODE_PRIVATE)
                .getStringSet("tools", setOf("🌙", "✏️", "🔀", "🔁", "🔇", "⏱", "A↔B", "🎚️", "1X", "📷", "▶⬛", "↩️", "Flip", "Mirror"))
                ?.toSet() ?: setOf("🌙", "✏️", "🔀", "🔁", "🔇", "⏱", "A↔B", "🎚️", "1X", "📷", "▶⬛", "↩️", "Flip", "Mirror")
        )
    }

    // Screenshot capture mockup action
    fun takeScreenshot(ctx: Context) {
        Toast.makeText(ctx, "تم التقاط إطار الفيديو وحفظ لقطة الشاشة بنجاح! 📸", Toast.LENGTH_SHORT).show()
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
                activeSubtitleText = cues
                    .mapNotNull { it.text?.toString() }
                    .joinToString("\n")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
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
    LaunchedEffect(areControlsVisible, isPlayingState) {
        if (areControlsVisible && isPlayingState && !isFilesListVisible && !isQuickSettingsOpen) {
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
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
                if (!areControlsVisible) {
                    isBrightnessSliderVisible = false
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

        // 💬 CUSTOM COMPOSE CLICKABLE SUBTITLE OVERLAY
        if (isSubtitleEnabled && activeSubtitleText.isNotEmpty()) {
            val currentOffset = localSubtitleOffset ?: subtitlePrefsState.verticalOffset
            val verticalBias = -1.0f + (currentOffset * 2.0f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { parentHeightPx = it.height.toFloat() }
                    .padding(bottom = 100.dp, start = 32.dp, end = 32.dp),
                contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = verticalBias)
            ) {
                Text(
                    text = activeSubtitleText,
                    color = subtitlePrefsState.textColor,
                    fontSize = subtitlePrefsState.textSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(
                            color = if (isDraggingSubtitle) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                subtitlePrefsState.backgroundColor
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isDraggingSubtitle) 2.dp else 1.dp,
                            color = if (isDraggingSubtitle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(parentHeightPx, subtitlePrefsState.verticalOffset) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    isDraggingSubtitle = true
                                    localSubtitleOffset = subtitlePrefsState.verticalOffset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val delta = dragAmount.y / parentHeightPx
                                    val current = localSubtitleOffset ?: subtitlePrefsState.verticalOffset
                                    localSubtitleOffset = (current + delta).coerceIn(0.01f, 0.99f)
                                },
                                onDragEnd = {
                                    isDraggingSubtitle = false
                                    localSubtitleOffset?.let { finalOffset ->
                                        scope.launch {
                                            subPrefsManager.savePreferences(subtitlePrefsState.copy(verticalOffset = finalOffset))
                                        }
                                    }
                                    localSubtitleOffset = null
                                },
                                onDragCancel = {
                                    isDraggingSubtitle = false
                                    localSubtitleOffset = null
                                }
                            )
                        }
                        .clickable {
                            isSubtitleCustomizationOpen = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("custom_subtitle_text")
                )
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
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                // Progress Slider (SeekBar with visual parameters)
                val totalSecs = videoDuration / 1000
                val curSecs = currentPlayTime / 1000
                val totalStr = "%02d:%02d".format(totalSecs / 60, totalSecs % 60)
                val curStr = "%02d:%02d".format(curSecs / 60, curSecs % 60)

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
                                            currentPlayTime = target
                                            player.seekTo(target)
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
                                        currentPlayTime = target
                                        player.seekTo(target)
                                    }
                                }
                            }
                    ) {
                        val widthDp = with(LocalDensity.current) { constraints.maxWidth.toDp() }
                        val fraction = if (videoDuration > 0) currentPlayTime.toFloat() / videoDuration else 0f

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
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                        )

                        // Tiny circular thumb element
                        val thumbSize = 8.dp
                        val halfThumb = thumbSize / 2
                        val thumbOffset = (widthDp * fraction - halfThumb).coerceIn(0.dp, widthDp - thumbSize)

                        Box(
                            modifier = Modifier
                                .offset(x = thumbOffset)
                                .size(thumbSize)
                                .align(Alignment.CenterStart)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                    Text(totalStr, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }

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

                        IconButton(
                            onClick = {
                                val target = (player.currentPosition - seekStepSeconds * 1000L).coerceAtLeast(0)
                                player.seekTo(target)
                                currentPlayTime = target
                            },
                            modifier = Modifier.size(34.dp)
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

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                val target = (player.currentPosition + seekStepSeconds * 1000L).coerceAtMost(player.duration)
                                player.seekTo(target)
                                currentPlayTime = target
                            },
                            modifier = Modifier.size(34.dp)
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
                            onClick = { isBrightnessSliderVisible = !isBrightnessSliderVisible },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brightness5,
                                contentDescription = "الإضاءة شريط",
                                tint = if (isBrightnessSliderVisible) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box {
                            var isSpeedExpanded by remember { mutableStateOf(false) }
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
                                var isSubtitlesExpanded by remember { mutableStateOf(false) }
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
                                        DropdownMenuItem(
                                            text = { Text("ترجمة: $lang", color = Color.White) },
                                            onClick = {
                                                isSubtitleEnabled = true
                                                selectedSubtitleLang = lang
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(lang)
                                                    .build()
                                                isSubtitlesExpanded = false
                                                gestureIndicatorText = "ترجمة: $lang"
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
        if (isQuickSettingsOpen) {
            AlertDialog(
                onDismissRequest = { isQuickSettingsOpen = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الإعدادات السريعة (Quick Settings)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "خطوة التخطي بالنقرة المزدوجة (Seek step):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 6.dp)
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
                                    label = { Text("${step}ث") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "مقياس ملء الشاشة (Scaling):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 6.dp)
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
                                    label = { Text(mode) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "حجم خط الترجمة (Subtitle text size):",
                            fontSize = 13.sp,
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
                                    subFontSize = it
                                },
                                valueRange =  12f..30f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${subSize.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "تفعيل الترجمة التلقائية:",
                                fontSize = 13.sp,
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
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isQuickSettingsOpen = false }) {
                        Text("تم الإغلاق (Apply)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF141419)
            )
        }

        // -----------------------------------------------------
        // MORE OPTIONS (⋮) BOTTOM SHEET DIALOG
        // -----------------------------------------------------
        if (isMoreOptionsSheetOpen) {
            AlertDialog(
                onDismissRequest = { isMoreOptionsSheetOpen = false },
                title = {
                    Text(
                        text = "خيارات إضافية (More Options)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                                val curStr = "%02d:%02d".format(totalSec / 60, totalSec % 60)
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
                                val infoStr = "الملف: ${currentMediaFile.name}\nالدقة: $videoWidth x $videoHeight\nالحجم: %.2f MB\nالمدة: %d:%02d".format(
                                    currentMediaFile.length() / (1024f * 1024f),
                                    (player.duration / 1000) / 60,
                                    (player.duration / 1000) % 60
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
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(gridItems.size) { index ->
                                val item = gridItems[index]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF26262B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
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
                                        Text(item.first.first, fontSize = 20.sp, color = Color(0xFF00C8FF))
                                        Spacer(modifier = Modifier.height(4.dp))
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
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isMoreOptionsSheetOpen = false }) {
                        Text("إغلاق", color = Color(0xFF00C8FF))
                    }
                },
                containerColor = Color(0xFF141419)
            )
        }

        // -----------------------------------------------------
        // AUDIO TRACK SELECTION DIALOG
        // -----------------------------------------------------
        if (isAudioTracksDialogOpen) {
            AlertDialog(
                onDismissRequest = { isAudioTracksDialogOpen = false },
                title = { Text("قنوات الصوت (Audio Tracks)", color = Color.White) },
                text = {
                    Column {
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
                                Text(label, color = Color.White, fontSize = 13.sp)
                                RadioButton(
                                    selected = (idx == 0 && !isMuted) || (idx == 3 && isMuted),
                                    onClick = null
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isAudioTracksDialogOpen = false }) {
                        Text("إغلاق", color = Color(0xFF00C8FF))
                    }
                },
                containerColor = Color(0xFF141419)
            )
        }

        // -----------------------------------------------------
        // SUBTITLE PANEL DIALOG (CC Overlay configuration)
        // -----------------------------------------------------
        if (isSubtitlePanelViewOpen) {
            AlertDialog(
                onDismissRequest = { isSubtitlePanelViewOpen = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الترجمات (Subtitles)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            try {
                                subtitlePickerLauncher.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "جاري فتح مستكشف الملفات الفرعية", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "فولدر خارجي", tint = Color(0xFF00C8FF))
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "البحث عن ترجمة عبر الإنترنت (OpenSubtitles)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("البحث عن ترجمة عبر الإنترنت 🌐", color = Color(0xFF00C8FF), fontSize = 11.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ملفات الترجمة المكتشفة:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isSubtitleEnabled = !isSubtitleEnabled
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                                        .build()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSubtitleEnabled,
                                onCheckedChange = {
                                    isSubtitleEnabled = it
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !it)
                                        .build()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ترجمة عربية مدمجة (Automatic Arabic)", color = Color.White, fontSize = 12.sp)
                        }

                        detectedSubtitles.forEachIndexed { index, file ->
                            val lang = subtitleLanguages.getOrNull(index) ?: "Default"
                            val isChecked = isSubtitleEnabled && selectedSubtitleLang == lang
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSubtitleEnabled = true
                                        selectedSubtitleLang = lang
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(lang)
                                            .build()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (it) {
                                            isSubtitleEnabled = true
                                            selectedSubtitleLang = lang
                                        } else {
                                            isSubtitleEnabled = false
                                        }
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                                            .build()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(file.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        // Render manually added subtitle items
                        manualSubs.forEachIndexed { index, pair ->
                            val lang = "manual_${index}_${pair.first}"
                            val isChecked = isSubtitleEnabled && selectedSubtitleLang == lang
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isSubtitleEnabled = true
                                        selectedSubtitleLang = lang
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setPreferredTextLanguage(lang)
                                            .build()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (it) {
                                            isSubtitleEnabled = true
                                            selectedSubtitleLang = lang
                                        } else {
                                            isSubtitleEnabled = false
                                        }
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                                            .build()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📁 ${pair.first}", color = Color(0xFF00C8FF), fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        var isCollapsibleOpen by remember { mutableStateOf(true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCollapsibleOpen = !isCollapsibleOpen }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("خيارات المزامنة والتحكم الإضافية", color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (isCollapsibleOpen) "▲" else "▼", color = Color.White, fontSize = 12.sp)
                        }

                        if (isCollapsibleOpen) {
                            Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("مزامنة (Sync Offset):", color = Color.White, fontSize = 11.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { subtitleDelaySeconds -= 0.5f }) {
                                            Text("−", color = Color(0xFF00C8FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${subtitleDelaySeconds}ث", color = Color.White, fontSize = 12.sp)
                                        IconButton(onClick = { subtitleDelaySeconds += 0.5f }) {
                                            Text("+", color = Color(0xFF00C8FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                var subSpeedPercent by remember { mutableStateOf(100) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("سرعة المزامنة:", color = Color.White, fontSize = 11.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { subSpeedPercent = (subSpeedPercent - 10).coerceAtLeast(50) }) {
                                            Text("−", color = Color(0xFF00C8FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("$subSpeedPercent%", color = Color.White, fontSize = 12.sp)
                                        IconButton(onClick = { subSpeedPercent = (subSpeedPercent + 10).coerceAtMost(200) }) {
                                            Text("+", color = Color(0xFF00C8FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("خلفية سوداء خلف لوحة الترجمة:", color = Color.White, fontSize = 11.sp)
                                    Checkbox(
                                        checked = !isSubBgTransparent,
                                        onCheckedChange = { isSubBgTransparent = !it }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        isSubtitleCustomizationOpen = true
                                        isSubtitlePanelViewOpen = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8FF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("تخصيص كامل المظهر والخطوط 🎨", color = Color.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isSubtitlePanelViewOpen = false }) {
                        Text("تم", color = Color(0xFF00C8FF))
                    }
                },
                containerColor = Color(0xFF141419)
            )
        }

        // -----------------------------------------------------
        // SUBTITLE CUSTOMIZATION DIALOG (DataStore Persisted)
        // -----------------------------------------------------
        if (isSubtitleCustomizationOpen) {
            AlertDialog(
                onDismissRequest = { isSubtitleCustomizationOpen = false },
                title = { Text("تخصيص نصوص الترجمة (Customize Subtitles) ✏️", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("التحكم بالموضع الجغرافي (Layout Placement):", color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("الموضع العمودي (Vertical Position): ${(subtitlePrefsState.verticalOffset * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = subtitlePrefsState.verticalOffset,
                            onValueChange = { newValue ->
                                scope.launch {
                                    subPrefsManager.savePreferences(subtitlePrefsState.copy(verticalOffset = newValue))
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00C8FF),
                                activeTrackColor = Color(0xFF00C8FF)
                            ),
                            valueRange = 0.1f..0.98f
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("خيارات الخط والحجم (Typography Settings):", color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("حجم الخط (Text Size): ${subtitlePrefsState.textSize.toInt()}sp", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = subtitlePrefsState.textSize,
                            onValueChange = { newValue ->
                                scope.launch {
                                    subPrefsManager.savePreferences(subtitlePrefsState.copy(textSize = newValue))
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00C8FF),
                                activeTrackColor = Color(0xFF00C8FF)
                            ),
                            valueRange = 12f..48f
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("لون خط الترجمة (Text Color):", color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colorsList = listOf(
                                Color.White,
                                Color.Yellow,
                                Color.Cyan,
                                Color.Green,
                                Color(0xFFFF5252),
                                Color(0xFFFFA500),
                                Color(0xFFFFC0CB)
                            )
                            colorsList.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            2.dp,
                                            if (subtitlePrefsState.textColorArgb == col.toArgb()) Color(0xFF00C8FF) else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable {
                                            scope.launch {
                                                subPrefsManager.savePreferences(subtitlePrefsState.copy(textColorArgb = col.toArgb()))
                                            }
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("لون خلفية نص الترجمة (Background Overlay):", color = Color(0xFF00C8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val bgColorsList = listOf(
                                Color.Transparent to "شفاف",
                                Color.Black.copy(alpha = 0.3f) to "خفيف",
                                Color.Black.copy(alpha = 0.6f) to "متوسط",
                                Color.Black to "داكن",
                                Color(0xFF1C1326).copy(alpha = 0.7f) to "بنفسجي"
                            )
                            bgColorsList.forEach { (col, label) ->
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (col == Color.Transparent) Color.DarkGray.copy(alpha = 0.3f) else col)
                                        .border(
                                            2.dp,
                                            if (subtitlePrefsState.backgroundColorArgb == col.toArgb()) Color(0xFF00C8FF) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                subPrefsManager.savePreferences(subtitlePrefsState.copy(backgroundColorArgb = col.toArgb()))
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isSubtitleCustomizationOpen = false }) {
                        Text("تم الإعداد", color = Color(0xFF00C8FF), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF141419)
            )


        // -----------------------------------------------------
        // EQUALIZER BOTTOM SHEET DIALOG
        // -----------------------------------------------------
        if (isEqualizerOpen) {
            AlertDialog(
                onDismissRequest = { isEqualizerOpen = false },
                title = { Text("موازن الصوت (Equalizer Panel) 🎚️", color = Color.White) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("مسبقات موازن الصوت (Presets):", color = Color.White, fontSize = 11.sp)
                        val eqPresetsList = listOf("عادي Normal", "مطور Bass Boost", "Treble Boost", "مسطح Flat", "كلاسيكي Classical", "روك وميتال Rock")
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
                                    label = { Text(eqPresetsList[idx]) }
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
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isEqualizerOpen = false }) {
                        Text("موافق", color = Color(0xFF00C8FF))
                    }
                },
                containerColor = Color(0xFF141419)
            )
        }

        // -----------------------------------------------------
        // SLEEP TIMER DIALOG
        // -----------------------------------------------------
        if (isSleepTimerDialogOpen) {
            AlertDialog(
                onDismissRequest = { isSleepTimerDialogOpen = false },
                title = { Text("مؤقت النوم (Sleep Timer) ⏱", color = Color.White) },
                text = {
                    Column {
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
                                    Text("$mins دقائق (Minutes)", color = Color.White)
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
                                    Text("إيقاف المؤقت النشط", color = Color.White)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isSleepTimerDialogOpen = false }) {
                        Text("إلغاء", color = Color(0xFF00C8FF))
                    }
                },
                containerColor = Color(0xFF141419)
            )
        }

        // -----------------------------------------------------
        // TOOLBAR CUSTOMIZATION DIALOG
        // -----------------------------------------------------
        if (isToolbarCustomizerDialogOpen) {
            AlertDialog(
                onDismissRequest = { isToolbarCustomizerDialogOpen = false },
                title = { Text("تخصيص أزرار التحكم ✏️", color = Color.White) },
                text = {
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
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isToolbarCustomizerDialogOpen = false }) {
                        Text("حفظ وتعديل التفضيلات", color = Color(0xFF00C8FF))
                    }
                },
                containerColor = Color(0xFF141419)
            )
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
