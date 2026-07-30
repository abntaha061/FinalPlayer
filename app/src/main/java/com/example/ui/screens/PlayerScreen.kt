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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.data.local.entities.MediaFile
import com.example.ui.components.AppSlider
import com.example.ui.components.PipCustomIcon
import com.example.ui.components.OrientationCustomIcon
import com.example.ui.components.CcSubtitleIcon
import com.example.ui.components.SpeedometerCustomIcon
import com.example.ui.components.CustomLockIcon
import com.example.ui.components.CustomPlayPauseButton
import com.example.ui.components.CustomSeek10Icon
import com.example.ui.components.HeadphonesCustomIcon
import com.example.ui.components.CustomScreenshotCropIcon
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
    val view = androidx.compose.ui.platform.LocalView.current
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
    val isPip by viewModel.isPipMode.collectAsState()
    val currentAccentColor = remember(themeColorHex) { Color(android.graphics.Color.parseColor(themeColorHex)) }
    val currentMediaFile = remember(filePath) { File(filePath) }

    val fileName = remember(filePath) {
        if (filePath.startsWith("content://")) {
            var name: String? = null
            try {
                context.contentResolver.query(Uri.parse(filePath), null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            name ?: "ملف خارجي"
        } else if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            try {
                Uri.parse(filePath).lastPathSegment ?: "رابط خارجي"
            } catch (e: Exception) {
                "رابط خارجي"
            }
        } else {
            File(filePath).name
        }
    }

    val fileNameWithoutExtension = remember(fileName) {
        if (fileName.contains('.')) {
            fileName.substringBeforeLast('.')
        } else {
            fileName
        }
    }

    val fileSizeFormatted = remember(filePath) {
        if (filePath.startsWith("content://")) {
            var size: Long = 0
            try {
                context.contentResolver.query(Uri.parse(filePath), null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (size > 0) "%.2f MB".format(size / (1024f * 1024f)) else "غير معروف"
        } else if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            "بث مباشر / رابط"
        } else {
            "%.2f MB".format(File(filePath).length() / (1024f * 1024f))
        }
    }

    val fileExtension = remember(filePath) {
        if (filePath.startsWith("content://")) {
            try {
                context.contentResolver.getType(Uri.parse(filePath))?.substringAfterLast('/')?.uppercase() ?: "فيديو"
            } catch (e: Exception) {
                "فيديو"
            }
        } else if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            "رابط / بث"
        } else {
            File(filePath).extension.uppercase()
        }
    }

    val absolutePathDisplay = remember(filePath) {
        if (filePath.startsWith("content://") || filePath.startsWith("http://") || filePath.startsWith("https://")) {
            filePath
        } else {
            File(filePath).absolutePath
        }
    }

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

    // Clean up screen flags and restore original orientation/insets on leave
    DisposableEffect(Unit) {
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

    // Manage screen keep awake dynamically: keep screen on ONLY when media is playing
    DisposableEffect(isPlayingState) {
        if (isPlayingState) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }
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
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    LaunchedEffect(filePath) {
        scale = 1.0f
        offset = androidx.compose.ui.geometry.Offset.Zero
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val oldScale = scale
        scale = (scale * zoomChange).coerceIn(0.25f, 4.0f)
        
        val rawX = offset.x + panChange.x * scale
        val rawY = offset.y + panChange.y * scale
        
        val w = containerSize.width.toFloat()
        val h = containerSize.height.toFloat()
        
        if (w > 0f && h > 0f) {
            val maxOffsetX = if (scale > 1.0f) (scale - 1.0f) * w / 2.0f else 0f
            val maxOffsetY = if (scale > 1.0f) (scale - 1.0f) * h / 2.0f else 0f
            
            offset = androidx.compose.ui.geometry.Offset(
                x = rawX.coerceIn(-maxOffsetX, maxOffsetX),
                y = rawY.coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = androidx.compose.ui.geometry.Offset(rawX, rawY)
        }
    }

    // Sub-menus visible states
    var isFilesListVisible by remember { mutableStateOf(false) }
    var isQuickSettingsOpen by remember { mutableStateOf(false) }
    var isBrightnessSliderVisible by remember { mutableStateOf(false) }
    var isSpeedExpanded by remember { mutableStateOf(false) }
    var isSubtitlesExpanded by remember { mutableStateOf(false) }
    var isUnlockPromptVisible by remember { mutableStateOf(false) }
    var isLongPressFastForwarding by remember { mutableStateOf(false) }
    var selectedLongPressSpeed by remember { mutableStateOf(2.0f) }

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
    var showScaleIndicator by remember { mutableStateOf(false) }
    var scaleIndicatorText by remember { mutableStateOf("") }
    var isFirstScaleLoad by remember { mutableStateOf(true) }

    LaunchedEffect(scaleMode) {
        if (isFirstScaleLoad) {
            isFirstScaleLoad = false
            return@LaunchedEffect
        }
        val arabicMode = when (scaleMode) {
            "FIT" -> "ملائمة"
            "FILL" -> "100%"
            "STRETCH" -> "تمدد"
            "CROP" -> "القص"
            else -> "ملائمة"
        }
        scaleIndicatorText = arabicMode
        showScaleIndicator = true
        kotlinx.coroutines.delay(1200)
        showScaleIndicator = false
    }

    val mainActivity = context as? com.example.MainActivity
    var videoSourceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mainActivity?.updatePipParams(null, null, false)
        }
    }

    LaunchedEffect(videoWidth, videoHeight, videoSourceRect, isPip, isPlayingState) {
        val aspectRational = if (videoWidth > 0 && videoHeight > 0) {
            android.util.Rational(videoWidth, videoHeight)
        } else {
            android.util.Rational(16, 9)
        }
        mainActivity?.updatePipParams(
            aspectRatio = aspectRational,
            sourceRect = videoSourceRect,
            autoEnter = !isPip,
            isPlaying = isPlayingState,
            onPlayPause = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            },
            onRewind = {
                val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                player.seekTo(target)
            },
            onForward = {
                val target = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                player.seekTo(target)
            }
        )
    }

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
    var playbackOrderIndex by remember { mutableStateOf(0) }

    var sleepTimerActive by remember { mutableStateOf(false) }
    var sleepTimerRemainingSecs by remember { mutableStateOf(0) }
    var sleepTimerInitialMinutes by remember { mutableStateOf(0) }
    var isSleepTimerEndOfVideo by remember { mutableStateOf(false) }
    var isSleepTimerDialogOpen by remember { mutableStateOf(false) }
    var isVideoDetailsDialogOpen by remember { mutableStateOf(false) }

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

    LaunchedEffect(isPip) {
        if (isPip) {
            areControlsVisible = false
            isQuickSettingsOpen = false
            isSpeedExpanded = false
            isSubtitlesExpanded = false
            isFilesListVisible = false
            isVideoDetailsDialogOpen = false
            isDecoderDialogOpen = false
            isSleepTimerDialogOpen = false
            isEqualizerOpen = false
            isMoreOptionsSheetOpen = false
            isAudioTracksDialogOpen = false
            isSubtitlePanelViewOpen = false
            isSubtitleCustomizationOpen = false
            isToolbarCustomizerDialogOpen = false
        }
    }

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
    var centerDoubleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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

    // Screenshot capture action
    fun takeScreenshot(ctx: Context) {
        try {
            val storageDir = android.os.Environment.getExternalStorageDirectory()
            val directory = if (storageDir != null && storageDir.exists()) {
                File(storageDir, "Pictures/FinalPlayer")
            } else {
                File("/storage/emulated/0/Pictures/FinalPlayer")
            }
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, "Screenshot_${System.currentTimeMillis()}.jpg")
            
            var frameSaved = false
            val currentPosUs = player.currentPosition * 1000L // current position in microseconds
            
            try {
                android.media.MediaMetadataRetriever().use { retriever ->
                    if (filePath.startsWith("content://")) {
                        ctx.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                        }
                    } else {
                        retriever.setDataSource(filePath)
                    }
                    val bitmap = retriever.getFrameAtTime(currentPosUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                    if (bitmap != null) {
                        val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                        
                        if (isSubtitleEnabled && activeSubtitleText.isNotEmpty()) {
                            val canvas = android.graphics.Canvas(mutableBitmap)
                            val bmpWidth = mutableBitmap.width
                            val bmpHeight = mutableBitmap.height
                            
                            val lines = activeSubtitleText.split("\n")
                            val baseTextSize = (bmpHeight * 0.045f * subtitleStyle.textSize).coerceAtLeast(24f)
                            
                            val paintFill = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textSize = baseTextSize
                                color = subtitleStyle.textColor.toArgb()
                                textAlign = android.graphics.Paint.Align.CENTER
                                if (subtitleStyle.bold && subtitleStyle.italic) {
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
                                } else if (subtitleStyle.bold) {
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                } else if (subtitleStyle.italic) {
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                                } else {
                                    typeface = android.graphics.Typeface.DEFAULT
                                }
                            }
                            
                            val paintOutline = android.graphics.Paint(paintFill).apply {
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = baseTextSize * 0.12f
                                color = subtitleStyle.edgeColor.toArgb()
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            
                            val lineSpacing = baseTextSize * 0.2f
                            val totalHeight = lines.size * baseTextSize + (lines.size - 1) * lineSpacing
                            
                            val bottomMargin = bmpHeight * 0.08f + (subtitleStyle.bottomPadding * bmpHeight * 2f).coerceAtLeast(0f)
                            val startY = bmpHeight - bottomMargin - totalHeight + baseTextSize
                            
                            if (subtitleStyle.backgroundEnabled) {
                                val bgPaint = android.graphics.Paint().apply {
                                    color = subtitleStyle.backgroundColor.toArgb()
                                    style = android.graphics.Paint.Style.FILL
                                }
                                val paddingX = baseTextSize * 0.4f
                                val paddingY = baseTextSize * 0.15f
                                for (i in lines.indices) {
                                    val line = lines[i]
                                    val w = paintFill.measureText(line)
                                    if (w > 0) {
                                        val lineY = startY + i * (baseTextSize + lineSpacing)
                                        val rectLeft = (bmpWidth / 2f) - (w / 2f) - paddingX
                                        val rectRight = (bmpWidth / 2f) + (w / 2f) + paddingX
                                        val rectTop = lineY - baseTextSize - paddingY
                                        val rectBottom = lineY + paddingY
                                        canvas.drawRoundRect(
                                            rectLeft, rectTop, rectRight, rectBottom,
                                            baseTextSize * 0.15f, baseTextSize * 0.15f,
                                            bgPaint
                                        )
                                    }
                                }
                            }
                            
                            for (i in lines.indices) {
                                val line = lines[i]
                                val x = bmpWidth / 2f
                                val y = startY + i * (baseTextSize + lineSpacing)
                                
                                if (subtitleStyle.edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
                                    canvas.drawText(line, x, y, paintOutline)
                                } else if (subtitleStyle.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
                                    paintFill.setShadowLayer(3f, 2f, 2f, subtitleStyle.edgeColor.toArgb())
                                }
                                canvas.drawText(line, x, y, paintFill)
                                if (subtitleStyle.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
                                    paintFill.clearShadowLayer()
                                }
                            }
                        }
                        
                        java.io.FileOutputStream(file).use { out ->
                            mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        frameSaved = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (frameSaved) {
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                Toast.makeText(ctx, "تم حفظ لقطة الشاشة في ${file.absolutePath} 📸", Toast.LENGTH_SHORT).show()
            } else {
                // Fallback: create a dummy valid JPEG file so that it at least opens and works instead of being 0 bytes
                val width = 1280
                val height = 720
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.DKGRAY)
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.drawText("FinalPlayer Screenshot", (width / 2).toFloat(), (height / 2).toFloat(), paint)
                
                if (isSubtitleEnabled && activeSubtitleText.isNotEmpty()) {
                    val lines = activeSubtitleText.split("\n")
                    for (i in lines.indices) {
                        canvas.drawText(lines[i], (width / 2).toFloat(), (height / 2 + 60 + i * 45).toFloat(), paint)
                    }
                }
                
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                Toast.makeText(ctx, "تم حفظ لقطة الشاشة (إطار احتياطي) في ${file.absolutePath} 📸", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "خطأ أثناء التقاط لقطة الشاشة: ${e.message}", Toast.LENGTH_SHORT).show()
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
    LaunchedEffect(sleepTimerActive, isSleepTimerEndOfVideo) {
        if (sleepTimerActive) {
            while (sleepTimerActive) {
                delay(1000)
                if (isSleepTimerEndOfVideo) {
                    val duration = player.duration
                    if (duration > 0) {
                        val remaining = ((duration - player.currentPosition) / 1000).toInt().coerceAtLeast(0)
                        sleepTimerRemainingSecs = remaining
                        if (remaining <= 0 || player.playbackState == Player.STATE_ENDED) {
                            player.pause()
                            sleepTimerActive = false
                            isSleepTimerEndOfVideo = false
                            break
                        }
                    } else {
                        sleepTimerRemainingSecs = 0
                    }
                } else {
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

    // Do not auto-advance automatically when video finishes; wait for user input on the overlay menu
    LaunchedEffect(playbackState) {
        // Intentionally left empty so video stops on STATE_ENDED and shows the end-of-video overlay menu
    }

    val isAnyPopupOpen = isQuickSettingsOpen ||
            isSpeedExpanded ||
            isSubtitlesExpanded ||
            isDecoderDialogOpen ||
            isSleepTimerDialogOpen ||
            isVideoDetailsDialogOpen ||
            isEqualizerOpen ||
            isMoreOptionsSheetOpen ||
            isAudioTracksDialogOpen ||
            isSubtitlePanelViewOpen ||
            isSubtitleCustomizationOpen ||
            isToolbarCustomizerDialogOpen ||
            isFilesListVisible ||
            isBrightnessSliderVisible ||
            isTutorialOverlayVisible ||
            isUnlockPromptVisible

    val closeAllPopups = {
        isQuickSettingsOpen = false
        isSpeedExpanded = false
        isSubtitlesExpanded = false
        isFilesListVisible = false
        isVideoDetailsDialogOpen = false
        isDecoderDialogOpen = false
        isSleepTimerDialogOpen = false
        isEqualizerOpen = false
        isMoreOptionsSheetOpen = false
        isAudioTracksDialogOpen = false
        isSubtitlePanelViewOpen = false
        isSubtitleCustomizationOpen = false
        isToolbarCustomizerDialogOpen = false
        isBrightnessSliderVisible = false
        isTutorialOverlayVisible = false
        isUnlockPromptVisible = false
    }

    // Ensure controls remain visible while any menu, sheet or dialog is open
    LaunchedEffect(isAnyPopupOpen) {
        if (isAnyPopupOpen) {
            areControlsVisible = true
        }
    }

    // Auto fade controls delay helper - pauses when any dialog or menu is open
    LaunchedEffect(
        areControlsVisible,
        isPlayingState,
        isAnyPopupOpen
    ) {
        if (areControlsVisible && isPlayingState && !isAnyPopupOpen) {
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

    BackHandler {
        if (isLockedMode) {
            isUnlockPromptVisible = true
            scope.launch {
                delay(2000)
                isUnlockPromptVisible = false
            }
        } else if (isAnyPopupOpen) {
            closeAllPopups()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(videoDuration, isLockedMode, isPip, isAnyPopupOpen) {
                if (isPip || isAnyPopupOpen) {
                    return@pointerInput
                }
                if (isLockedMode) {
                    detectTapGestures(
                        onTap = {
                            if (isAnyPopupOpen) {
                                closeAllPopups()
                                areControlsVisible = true
                            } else {
                                areControlsVisible = !areControlsVisible
                                if (!areControlsVisible) {
                                    isBrightnessSliderVisible = false
                                }
                            }
                        }
                    )
                } else {
                    awaitPointerEventScope {
                        var lastTapTime = 0L
                        var lastTapPosition = androidx.compose.ui.geometry.Offset.Zero
                        var tapCount = 0
                        
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            if (isPip || isAnyPopupOpen) {
                                continue
                            }
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
                                    selectedLongPressSpeed = 2.0f
                                    player.setPlaybackSpeed(2.0f)
                                    if (!wasPlayingBeforeFastForward) {
                                        player.play()
                                    }
                                }
                            }
                            
                            while (pointerInputChange != null && pointerInputChange.pressed) {
                                val event = awaitPointerEvent()
                                if (isPip || isAnyPopupOpen) {
                                    currentGestureType = "NONE"
                                    showVolumeIndicator = false
                                    showBrightnessIndicator = false
                                    showSeekDragIndicator = false
                                    longPressJob?.cancel()
                                    longPressJob = null
                                    singleTapJob?.cancel()
                                    singleTapJob = null
                                    if (isLongPressFastForwarding) {
                                        player.setPlaybackSpeed(speedMultiplier)
                                        isLongPressFastForwarding = false
                                    }
                                    pointerInputChange = null
                                    break
                                }
                                // Check for multi-touch (pinch-to-zoom)
                                val activePointers = event.changes.filter { it.pressed }
                                if (activePointers.size > 1) {
                                    // Multi-touch active! Cancel single finger gesture tracking
                                    currentGestureType = "NONE"
                                    showVolumeIndicator = false
                                    showBrightnessIndicator = false
                                    showSeekDragIndicator = false
                                    longPressJob?.cancel()
                                    longPressJob = null
                                    singleTapJob?.cancel()
                                    singleTapJob = null
                                    if (isLongPressFastForwarding) {
                                        player.setPlaybackSpeed(speedMultiplier)
                                        isLongPressFastForwarding = false
                                    }
                                    pointerInputChange = null
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change != null && change.pressed) {
                                    if (change.isConsumed) {
                                        currentGestureType = "NONE"
                                        showVolumeIndicator = false
                                        showBrightnessIndicator = false
                                        showSeekDragIndicator = false
                                        longPressJob?.cancel()
                                        longPressJob = null
                                        singleTapJob?.cancel()
                                        singleTapJob = null
                                        if (isLongPressFastForwarding) {
                                            player.setPlaybackSpeed(speedMultiplier)
                                            isLongPressFastForwarding = false
                                        }
                                        pointerInputChange = null
                                        break
                                    }
                                    pointerInputChange = change
                                    val totalX = change.position.x - startPos.x
                                    val totalY = startPos.y - change.position.y
                                    
                                    val threshold = 16f
                                    if (currentGestureType == "NONE" && !isLongPressFastForwarding && (kotlin.math.abs(totalX) >= threshold || kotlin.math.abs(totalY) >= threshold)) {
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

                                    if (isLongPressFastForwarding) {
                                        change.consume()
                                        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                                        val touchX = change.position.x
                                        val fraction = (touchX / size.width.toFloat()).coerceIn(0f, 0.999f)
                                        val speedIndex = (fraction * speedOptions.size).toInt().coerceIn(0, speedOptions.size - 1)
                                        val newSpeed = speedOptions[speedIndex]
                                        if (newSpeed != selectedLongPressSpeed) {
                                            selectedLongPressSpeed = newSpeed
                                            player.setPlaybackSpeed(newSpeed)
                                            try {
                                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                            } catch (e: Exception) {}
                                        }
                                    } else if (currentGestureType != "NONE") {
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
                                    val isClose = upTime - lastTapTime < 350 && 
                                                  kotlin.math.abs(startPos.x - lastTapPosition.x) < 120f && 
                                                  kotlin.math.abs(startPos.y - lastTapPosition.y) < 120f
                                    
                                    if (isClose) {
                                        tapCount++
                                    } else {
                                        tapCount = 1
                                    }
                                    
                                    if (tapCount == 2) {
                                        // Cancel single tap action
                                        singleTapJob?.cancel()
                                        singleTapJob = null
                                        
                                        val fraction = startPos.x / size.width.toFloat()
                                        if (fraction < 0.35f) {
                                            val target = (player.currentPosition - seekStepSeconds * 1000L).coerceAtLeast(0)
                                            player.seekTo(target)
                                            currentPlayTime = target
                                            showRewindOverlay = true
                                            audiofySeekJob?.cancel()
                                            audiofySeekJob = scope.launch {
                                                delay(1200)
                                                audiofySeekSeconds = null
                                            }
                                            scope.launch {
                                                delay(700)
                                                showRewindOverlay = false
                                            }
                                            tapCount = 0 // reset
                                        } else if (fraction > 0.65f) {
                                            val target = (player.currentPosition + seekStepSeconds * 1000L).coerceAtMost(videoDuration)
                                            player.seekTo(target)
                                            currentPlayTime = target
                                            showForwardOverlay = true
                                            audiofySeekJob?.cancel()
                                            audiofySeekJob = scope.launch {
                                                delay(1200)
                                                audiofySeekSeconds = null
                                            }
                                            scope.launch {
                                                delay(700)
                                                showForwardOverlay = false
                                            }
                                            tapCount = 0 // reset
                                        } else {
                                            // Center double tap: delay play/pause to see if a triple tap is coming
                                            centerDoubleTapJob?.cancel()
                                            centerDoubleTapJob = scope.launch {
                                                delay(250)
                                                if (player.isPlaying) {
                                                    player.pause()
                                                } else {
                                                    player.play()
                                                }
                                                tapCount = 0 // reset after execution
                                            }
                                        }
                                        lastTapTime = upTime
                                        lastTapPosition = startPos
                                    } else if (tapCount >= 3) {
                                        val fraction = startPos.x / size.width.toFloat()
                                        if (fraction >= 0.35f && fraction <= 0.65f) {
                                            // Cancel double tap action
                                            centerDoubleTapJob?.cancel()
                                            centerDoubleTapJob = null
                                            
                                            // Trigger screenshot!
                                            takeScreenshot(context)
                                        }
                                        tapCount = 0 // reset
                                        lastTapTime = 0L
                                        lastTapPosition = androidx.compose.ui.geometry.Offset.Zero
                                    } else {
                                        // tapCount == 1
                                        // Delay single tap action to allow potential double/triple taps to cancel/intercept it
                                        singleTapJob?.cancel()
                                        singleTapJob = scope.launch {
                                            delay(250)
                                            if (isAnyPopupOpen) {
                                                closeAllPopups()
                                                areControlsVisible = true
                                            } else {
                                                areControlsVisible = !areControlsVisible
                                                if (!areControlsVisible) {
                                                    isBrightnessSliderVisible = false
                                                }
                                            }
                                            tapCount = 0 // reset after execution
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
                        scaleY = if (isVerticalFlipActive) -scale else scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .onGloballyPositioned { layoutCoordinates ->
                        val bounds = layoutCoordinates.boundsInWindow()
                        videoSourceRect = android.graphics.Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    }
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

        // Top-Center Long-Press Speed Scrubbing Bar Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = !isPip && isLongPressFastForwarding,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
        ) {
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(20.dp))
                    .border(width = 1.dp, color = currentAccentColor.copy(alpha = 0.6f), shape = RoundedCornerShape(20.dp))
                    .padding(vertical = 10.dp, horizontal = 14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            tint = currentAccentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val speedStr = if (selectedLongPressSpeed == selectedLongPressSpeed.toInt().toFloat()) "${selectedLongPressSpeed.toInt()}x" else "${selectedLongPressSpeed}x"
                        Text(
                            text = "سرعة التشغيل: $speedStr",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(اسحب يمين/يسار للتغيير)",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        speedOptions.forEach { sp ->
                            val isSelected = kotlin.math.abs(sp - selectedLongPressSpeed) < 0.05f
                            val label = if (sp == sp.toInt().toFloat()) "${sp.toInt()}x" else "${sp}x"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) currentAccentColor else Color.White.copy(alpha = 0.15f)
                                    )
                                    .padding(vertical = 6.dp, horizontal = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = if (isSelected) 13.sp else 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top-Center HUD Notification Pill (system status alerts when not long pressing)
        androidx.compose.animation.AnimatedVisibility(
            visible = !isPip && !isLongPressFastForwarding && (isIndicatorVisible && gestureIndicatorText != null),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            val pillText = gestureIndicatorText ?: ""
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(percent = 50))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(percent = 50))
                    .padding(vertical = 8.dp, horizontal = 18.dp)
            ) {
                Text(
                    text = pillText,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        // ----------------------------------------------------------------------
        // END OF VIDEO OVERLAY (قائمة عند انتهاء الفيديو)
        // ----------------------------------------------------------------------
        androidx.compose.animation.AnimatedVisibility(
            visible = !isPip && (playbackState == Player.STATE_ENDED),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B1B22).copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 380.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(currentAccentColor.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Finished",
                                tint = currentAccentColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "تم انتهاء الفيديو",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        player.seekTo(0)
                                        player.playWhenReady = true
                                        player.play()
                                        currentPlayTime = 0L
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = "Replay",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إعادة",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (hasNextVideo) {
                                            val nextPath = allVideos[currentVideoIndex + 1].path
                                            onNavigateToVideo(nextPath)
                                        } else if (allVideos.isNotEmpty()) {
                                            val firstPath = allVideos[0].path
                                            onNavigateToVideo(firstPath)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = currentAccentColor,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "التالي",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
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
                        imageVector = when (seekStepSeconds) {
                            5 -> Icons.Default.Replay5
                            30 -> Icons.Default.Replay30
                            else -> Icons.Default.Replay10
                        },
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${seekStepSeconds}- ثوانٍ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        imageVector = when (seekStepSeconds) {
                            5 -> Icons.Default.Forward5
                            30 -> Icons.Default.Forward30
                            else -> Icons.Default.Forward10
                        },
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${seekStepSeconds}+ ثوانٍ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // 🔊 Elegant Volume Gesture visual indicator (top center, 0 to 15, boost 15 to 30)
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            val volumeValue = if (draggedVolRatio <= 1.0f) {
                (draggedVolRatio * 15f).toInt().coerceIn(0, 15)
            } else {
                (15 + (draggedVolRatio - 1.0f) * 15f).toInt().coerceIn(15, 30)
            }
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(50))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(50))
                    .padding(vertical = 6.dp, horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color(0xFF00C8FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$volumeValue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // ☀️ Elegant Brightness Gesture visual indicator (top center, 0 to 15)
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            val brightnessValue = (draggedBrightness * 15f).toInt().coerceIn(0, 15)
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(50))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(50))
                    .padding(vertical = 6.dp, horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Brightness5,
                        contentDescription = "Brightness",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$brightnessValue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
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

        // ⬛ Sizing/Scaling Mode Center Indicator (MX Player style)
        AnimatedVisibility(
            visible = showScaleIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = Color(0xFF00C8FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = scaleIndicatorText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
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
                    
                    var textLayoutResult by remember(activeSubtitleText) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

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
                            .border(
                                width = if (isDraggingSubtitle) 1.5.dp else 0.dp,
                                color = if (isDraggingSubtitle) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { isSubtitleCustomizationOpen = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
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
                        val primaryColor = MaterialTheme.colorScheme.primary
                        Text(
                            text = activeSubtitleText,
                            color = subtitleStyle.textColor,
                            fontSize = (16f * subtitleStyle.textSize).sp,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            textAlign = TextAlign.Center,
                            lineHeight = (16f * subtitleStyle.textSize * 1.25f).sp,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier.drawWithCache {
                                val padX = 6.dp.toPx()
                                val padY = 2.dp.toPx()
                                val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                val draggingBgColor = primaryColor.copy(alpha = 0.5f)

                                onDrawWithContent {
                                    val layoutResult = textLayoutResult
                                    if (layoutResult != null && (subtitleStyle.backgroundEnabled || isDraggingSubtitle)) {
                                        val bgColor = if (isDraggingSubtitle) draggingBgColor else subtitleStyle.backgroundColor

                                        for (i in 0 until layoutResult.lineCount) {
                                            val l = layoutResult.getLineLeft(i)
                                            val r = layoutResult.getLineRight(i)
                                            val t = layoutResult.getLineTop(i)
                                            val b = layoutResult.getLineBottom(i)

                                            if (r > l) {
                                                drawRoundRect(
                                                    color = bgColor,
                                                    topLeft = Offset(l - padX, t - padY),
                                                    size = Size((r - l) + (padX * 2), (b - t) + (padY * 2)),
                                                    cornerRadius = cornerRadius
                                                )
                                            }
                                        }
                                    }
                                    drawContent()
                                }
                            },
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
                                text = fileNameWithoutExtension,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // SLEEP TIMER BADGE / PILL
                        if (sleepTimerActive) {
                            val sleepTimerText = if (isSleepTimerEndOfVideo) {
                                "نهاية الفيديو"
                            } else {
                                val mins = sleepTimerRemainingSecs / 60
                                val secs = sleepTimerRemainingSecs % 60
                                "%02d:%02d".format(mins, secs)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFF5252).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .clickable { isSleepTimerDialogOpen = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Sleep Timer",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "⏰ $sleepTimerText",
                                        color = Color(0xFFFF5252),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // DECODER CHIP (HW / HW+ / SW)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable { isDecoderDialogOpen = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentDecoder,
                                color = Color(0xFF00C8FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // SCREENSHOT BUTTON
                        IconButton(
                            onClick = { takeScreenshot(context) }
                        ) {
                            CustomScreenshotCropIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }

                        // HEADPHONES AUDIO MODE BUTTON
                        IconButton(
                            onClick = {
                                val currentPos = player.currentPosition
                                val mediaFile = MediaFile(
                                    id = filePath.hashCode().toLong(),
                                    title = java.io.File(filePath).nameWithoutExtension,
                                    path = filePath,
                                    size = java.io.File(filePath).length(),
                                    dateModified = System.currentTimeMillis(),
                                    duration = videoDuration,
                                    isVideo = false
                                )
                                viewModel.playAudio(mediaFile)
                                viewModel.seekAudioTo(currentPos)
                                onBack()
                            }
                        ) {
                            HeadphonesCustomIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }

                        // PIP BUTTON
                        IconButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    runCatching { activity?.enterPictureInPictureMode() }
                                }
                            }
                        ) {
                            PipCustomIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
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
            visible = !isPip && isLockedMode && areControlsVisible,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable {
                        isLockedMode = false
                        gestureIndicatorText = "🔓 تم فك قفل الشاشة"
                        scope.launch {
                            isIndicatorVisible = true
                            delay(700)
                            isIndicatorVisible = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                CustomLockIcon(
                    modifier = Modifier.size(36.dp),
                    tint = Color.White,
                    holeColor = Color.Black
                )
            }
        }

        // -----------------------------------------------------
        // ON-SCREEN COMPACT SLIDER FOR BRIGHTNESS
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = !isPip && isBrightnessSliderVisible && areControlsVisible && !isLockedMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
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
                    
                    AppSlider(
                        value = currentBrightness,
                        onValueChange = {
                            currentBrightness = it
                            val layoutParams = activity?.window?.attributes
                            layoutParams?.screenBrightness = currentBrightness
                            activity?.window?.attributes = layoutParams
                        },
                        valueRange = 0.05f..1.0f,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = Color.DarkGray,
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable { isLockedMode = true }
                                .testTag("lock_button")
                        ) {
                            CustomLockIcon(
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black,
                                holeColor = Color.White
                            )
                        }

                        IconButton(onClick = { isFilesListVisible = !isFilesListVisible }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.FeaturedPlayList,
                                contentDescription = "قائمة الفيديوهات",
                                tint = if (isFilesListVisible) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
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
                                .size(36.dp)
                                .graphicsLayer {
                                    scaleX = rewindScale
                                    scaleY = rewindScale
                                }
                        ) {
                            CustomSeek10Icon(
                                isForward = false,
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        CustomPlayPauseButton(
                            isPlaying = isPlayingState,
                            onClick = {
                                if (isPlayingState) player.pause() else player.play()
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("player_play_pause")
                        )

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
                                .size(36.dp)
                                .graphicsLayer {
                                    scaleX = forwardScale
                                    scaleY = forwardScale
                                }
                        ) {
                            CustomSeek10Icon(
                                isForward = true,
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                            OrientationCustomIcon(
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { isSpeedExpanded = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                SpeedometerCustomIcon(
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
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
                                    CcSubtitleIcon(
                                        modifier = Modifier.size(20.dp),
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
            } // End CompositionLocalProvider
        } // End AnimatedVisibility

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
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(Color.Black.copy(alpha = 0.92f))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
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
                AppSlider(
                    value = subSize,
                    onValueChange = {
                        subSize = it
                        viewModel.saveSubtitleSize(it)
                        subtitleStyle = subtitleStyle.copy(textSize = it / 16f)
                    },
                    valueRange = 12f..30f,
                    activeColor = Color(0xFF00C8FF),
                    inactiveColor = Color.White.copy(alpha = 0.25f),
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
        // ADVANCED SETTINGS OVERLAY MENU (قائمة الإعدادات الشفافة)
        // -----------------------------------------------------
        var isCustomSleepTimerDialogOpen by remember { mutableStateOf(false) }
        var customSleepTimerMins by remember { mutableStateOf(30f) }

        if (isMoreOptionsSheetOpen) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isMoreOptionsSheetOpen = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24).copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .width(if (isLandscape) 520.dp else 340.dp)
                        .fillMaxHeight(if (isLandscape) 0.92f else 0.85f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume click inside overlay */ }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF00C8FF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "الإعدادات المتقدمة",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = { isMoreOptionsSheetOpen = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = Color.LightGray
                                )
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Requirement 3: Grid Items using LazyVerticalGrid
                        val playbackOrderLabel = when (playbackOrderIndex) {
                            1 -> "تكرار الملف"
                            2 -> "تكرار الكل"
                            3 -> "عشوائي"
                            else -> "عادي"
                        }
                        val playbackOrderIcon = when (playbackOrderIndex) {
                            1 -> Icons.Default.RepeatOne
                            2 -> Icons.Default.Repeat
                            3 -> Icons.Default.Shuffle
                            else -> Icons.Default.PlayArrow
                        }

                        data class OverlayAction(
                            val title: String,
                            val subtitle: String,
                            val icon: @Composable () -> Unit,
                            val onClick: () -> Unit
                        )

                        val gridItems = listOf(
                            OverlayAction(
                                title = "الترجمة",
                                subtitle = if (isSubtitleEnabled) "مُفعل" else "معطل",
                                icon = { CcSubtitleIcon(modifier = Modifier.size(20.dp), tint = Color(0xFF00C8FF)) },
                                onClick = {
                                    isSubtitlePanelViewOpen = true
                                    isMoreOptionsSheetOpen = false
                                }
                            ),
                            OverlayAction(
                                title = "الصوت",
                                subtitle = "المسارات الصوتية",
                                icon = { HeadphonesCustomIcon(modifier = Modifier.size(20.dp), tint = Color(0xFF00C8FF)) },
                                onClick = {
                                    isAudioTracksDialogOpen = true
                                    isMoreOptionsSheetOpen = false
                                }
                            ),
                            OverlayAction(
                                title = "نسبة العرض",
                                subtitle = scaleMode,
                                icon = { Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color(0xFF00C8FF), modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    scaleMode = when (scaleMode) {
                                        "FIT" -> "FILL"
                                        "FILL" -> "STRETCH"
                                        "STRETCH" -> "CROP"
                                        "CROP" -> "16:9"
                                        else -> "FIT"
                                    }
                                }
                            ),
                            OverlayAction(
                                title = "انعكاس",
                                subtitle = if (isMirrorModeActive) "مُفعل" else "معطل",
                                icon = { Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFF00C8FF), modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    isMirrorModeActive = !isMirrorModeActive
                                }
                            ),
                            OverlayAction(
                                title = "فك التشفير",
                                subtitle = currentDecoder,
                                icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF00C8FF), modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    currentDecoder = when (currentDecoder) {
                                        "HW" -> "HW+"
                                        "HW+" -> "SW"
                                        else -> "HW"
                                    }
                                    isHWAccelActive = (currentDecoder == "HW" || currentDecoder == "HW+")
                                }
                            ),
                            OverlayAction(
                                title = "ترتيب التشغيل",
                                subtitle = playbackOrderLabel,
                                icon = { Icon(playbackOrderIcon, contentDescription = null, tint = Color(0xFF00C8FF), modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    playbackOrderIndex = (playbackOrderIndex + 1) % 4
                                    when (playbackOrderIndex) {
                                        0 -> {
                                            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                            player.shuffleModeEnabled = false
                                        }
                                        1 -> {
                                            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                                            player.shuffleModeEnabled = false
                                        }
                                        2 -> {
                                            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                            player.shuffleModeEnabled = false
                                        }
                                        3 -> {
                                            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                            player.shuffleModeEnabled = true
                                        }
                                    }
                                }
                            ),
                            OverlayAction(
                                title = "تشغيل بالخلفية",
                                subtitle = "نافذة عائمة / PiP",
                                icon = { PipCustomIcon(modifier = Modifier.size(20.dp), tint = Color(0xFF00C8FF)) },
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                        runCatching { activity?.enterPictureInPictureMode() }
                                    }
                                    isMoreOptionsSheetOpen = false
                                }
                            ),
                            OverlayAction(
                                title = "تفاصيل الفيديو",
                                subtitle = "معلومات الملف",
                                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00C8FF), modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    isVideoDetailsDialogOpen = true
                                    isMoreOptionsSheetOpen = false
                                }
                            )
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(gridItems) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF27272E)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .clickable { item.onClick() }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        item.icon()
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 9.sp,
                                            color = Color(0xFF00C8FF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Requirement 4: Sleep Timer Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF27272E))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Color(0xFF00C8FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مؤقت النوم (Sleep Timer)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                if (sleepTimerActive) {
                                    val mins = sleepTimerRemainingSecs / 60
                                    val secs = sleepTimerRemainingSecs % 60
                                    Text(
                                        text = "متبقي: %02d:%02d".format(mins, secs),
                                        fontSize = 11.sp,
                                        color = Color(0xFF00C8FF),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val quickTimers = listOf(
                                    "إيقاف" to 0,
                                    "10د" to 10,
                                    "30د" to 30,
                                    "60د" to 60
                                )
                                quickTimers.forEach { (label, mins) ->
                                    val isSelected = if (mins == 0) !sleepTimerActive else (sleepTimerActive && sleepTimerInitialMinutes == mins)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF00C8FF) else Color(0xFF33333E))
                                            .clickable {
                                                if (mins == 0) {
                                                    sleepTimerActive = false
                                                    sleepTimerRemainingSecs = 0
                                                } else {
                                                    sleepTimerInitialMinutes = mins
                                                    sleepTimerRemainingSecs = mins * 60
                                                    sleepTimerActive = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else Color.White
                                        )
                                    }
                                }

                                // Custom button
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF33333E))
                                        .clickable {
                                            isCustomSleepTimerDialogOpen = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "مخصص ⚙️",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00C8FF)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Requirement 5: Volume & Brightness Sliders Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF27272E))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Volume Slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (currentVolRatio <= 0.01f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "الصوت",
                                    tint = Color(0xFF00C8FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Slider(
                                    value = currentVolRatio,
                                    onValueChange = { ratio ->
                                        currentVolRatio = ratio
                                        val targetVol = (ratio * maxVolume).toInt().coerceIn(0, maxVolume.toInt())
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00C8FF),
                                        activeTrackColor = Color(0xFF00C8FF),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${(currentVolRatio * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            // Brightness Slider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "الإضاءة",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Slider(
                                    value = currentBrightness.coerceIn(0.01f, 1f),
                                    onValueChange = { bright ->
                                        currentBrightness = bright
                                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                            screenBrightness = bright
                                        }
                                    },
                                    valueRange = 0.01f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFC107),
                                        activeTrackColor = Color(0xFFFFC107),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${(currentBrightness.coerceIn(0.01f, 1f) * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Sleep Timer Dialog
        if (isCustomSleepTimerDialogOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isCustomSleepTimerDialogOpen = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222228),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "تحديد مؤقت نوم مخصص",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${customSleepTimerMins.toInt()} دقيقة",
                            color = Color(0xFF00C8FF),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = customSleepTimerMins,
                            onValueChange = { customSleepTimerMins = it },
                            valueRange = 1f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00C8FF),
                                activeTrackColor = Color(0xFF00C8FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isCustomSleepTimerDialogOpen = false }) {
                                Text("إلغاء", color = Color.LightGray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val mins = customSleepTimerMins.toInt()
                                    sleepTimerInitialMinutes = mins
                                    sleepTimerRemainingSecs = mins * 60
                                    sleepTimerActive = true
                                    isCustomSleepTimerDialogOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8FF))
                            ) {
                                Text("تأكيد", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------
        // VIDEO DETAILS DIALOG
        // -----------------------------------------------------
        if (isVideoDetailsDialogOpen) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isVideoDetailsDialogOpen = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1E22),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .width(if (isLandscape) 420.dp else 300.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isVideoDetailsDialogOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
                            }
                            Text(
                                text = "تفاصيل الفيديو ℹ️",
                                color = Color(0xFF00C8FF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                        
                        HorizontalDivider(
                            color = Color(0xFF00C8FF).copy(alpha = 0.3f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        
                        // Detailed Information Items
                        val durationSec = player.duration / 1000
                        val formattedDuration = if (durationSec > 0) {
                            "%02d:%02d:%02d".format(durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60)
                        } else {
                            "غير معروف"
                        }
                        
                        val detailsList = listOf(
                            "اسم الملف" to fileName,
                            "المسار الكامل" to absolutePathDisplay,
                            "حجم الملف" to fileSizeFormatted,
                            "المدة الزمنية" to formattedDuration,
                            "أبعاد الفيديو" to "$videoWidth × $videoHeight",
                            "صيغة الملف" to fileExtension
                        )
                        
                        detailsList.forEach { (label, value) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = label,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = value,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { isVideoDetailsDialogOpen = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8FF)),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("تم", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
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
                            Toast.makeText(context, "تم التبديل إلى $decoder", Toast.LENGTH_SHORT).show()
                            isDecoderDialogOpen = false
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = decoder,
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
                            Toast.makeText(context, "تم التبديل إلى $decoder", Toast.LENGTH_SHORT).show()
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
                    AppSlider(
                        value = equalizerBandLevels[band],
                        onValueChange = { newVal ->
                            setEqualizerBand(band, newVal)
                            isEqualizerActive = true
                        },
                        valueRange = -1.0f..1.0f,
                        activeColor = Color(0xFF00C8FF),
                        inactiveColor = Color.White.copy(alpha = 0.25f),
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
            
            val timesList = listOf(
                Pair(5, "5 دقائق"),
                Pair(10, "10 دقائق"),
                Pair(15, "15 دقيقة"),
                Pair(20, "20 دقيقة"),
                Pair(30, "30 دقيقة")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timesList.forEach { (mins, label) ->
                    Button(
                        onClick = {
                            isSleepTimerEndOfVideo = false
                            sleepTimerInitialMinutes = mins
                            sleepTimerRemainingSecs = mins * 60
                            sleepTimerActive = true
                            isSleepTimerDialogOpen = false
                            gestureIndicatorText = "تم تفعيل مؤقت النوم: $label"
                            scope.launch { isIndicatorVisible = true; delay(900); isIndicatorVisible = false }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, color = Color.White, fontSize = 13.sp)
                    }
                }
                
                // نهاية الفيديو (End of video)
                Button(
                    onClick = {
                        isSleepTimerEndOfVideo = true
                        sleepTimerInitialMinutes = 0
                        val remaining = ((player.duration - player.currentPosition) / 1000).toInt().coerceAtLeast(0)
                        sleepTimerRemainingSecs = remaining
                        sleepTimerActive = true
                        isSleepTimerDialogOpen = false
                        gestureIndicatorText = "تم تفعيل مؤقت النوم: نهاية الفيديو"
                        scope.launch { isIndicatorVisible = true; delay(900); isIndicatorVisible = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C8FF).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("نهاية الفيديو (End of Video) 🎬", color = Color(0xFF00C8FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                if (sleepTimerActive) {
                    Button(
                        onClick = {
                            sleepTimerActive = false
                            isSleepTimerEndOfVideo = false
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
    val totalSecs = (videoDuration / 1000).coerceAtLeast(0)
    val curSecs = (currentPlayTimeProvider() / 1000).coerceAtLeast(0)

    val totalHours = totalSecs / 3600
    val totalMinutes = (totalSecs % 3600) / 60
    val totalSeconds = totalSecs % 60

    val curHours = curSecs / 3600
    val curMinutes = (curSecs % 3600) / 60
    val curSeconds = curSecs % 60

    val totalStr = if (totalHours > 0) {
        "%02d:%02d:%02d".format(totalHours, totalMinutes, totalSeconds)
    } else {
        "%02d:%02d".format(totalMinutes, totalSeconds)
    }

    val curStr = if (totalHours > 0) {
        "%02d:%02d:%02d".format(curHours, curMinutes, curSeconds)
    } else {
        "%02d:%02d".format(curMinutes, curSeconds)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = curStr,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(28.dp)
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
                    detectDragGestures { change, _ ->
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
            val fraction = if (videoDuration > 0) (currentPlayTimeProvider().toFloat() / videoDuration).coerceIn(0f, 1f) else 0f

            // Inactive track (grey thin line)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.35f), CircleShape)
            )

            // Active track (white line)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White, CircleShape)
            )

            // Prominent solid white circular thumb (18.dp)
            val thumbSize = 18.dp
            val halfThumb = thumbSize / 2
            val thumbOffset = (widthDp * fraction - halfThumb).coerceIn(0.dp, widthDp - thumbSize)

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .background(Color.White, CircleShape)
            )
        }

        Text(
            text = totalStr,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
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
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }, // Right side panel
                color = Color(0xFF141419),
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
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
