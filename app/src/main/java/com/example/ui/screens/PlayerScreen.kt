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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
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

enum class SidePanelMenuState {
    MAIN,
    DETAILS,
    SUBTITLE_SETTINGS,
    ASPECT_RATIO
}

// Secondary control items data layout
data class ExtendedToolItem(
    val icon: String,
    val label: String,
    val id: String,
    val action: () -> Unit,
    val isActive: Boolean,
    val badgeText: String? = null
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val redAccent = Color(0xFFFF2A4B)

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
    var isHWAccelActive by remember { mutableStateOf(true) }
    var currentDecoder by remember { mutableStateOf("HW+") }
    var savedPosForDecoderChange by remember { mutableStateOf(0L) }

    // Init player with real HW / SW decoder selection
    val player = remember(filePath, currentDecoder) {
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

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            if (currentDecoder == "HW+") {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            } else {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            }
            setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunneling ->
                val decoders = try {
                    MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunneling)
                } catch (e: Exception) {
                    emptyList()
                }
                when (currentDecoder) {
                    "SW" -> {
                        val sw = decoders.filter { 
                            it.softwareOnly || !it.hardwareAccelerated || 
                            it.name.startsWith("OMX.google.", ignoreCase = true) || 
                            it.name.startsWith("c2.android.", ignoreCase = true)
                        }
                        if (sw.isNotEmpty()) sw else decoders
                    }
                    "HW" -> {
                        val hw = decoders.filter { it.hardwareAccelerated && !it.softwareOnly }
                        if (hw.isNotEmpty()) hw else decoders
                    }
                    else -> { // "HW+"
                        val hwPlus = decoders.filter { it.hardwareAccelerated }
                        if (hwPlus.isNotEmpty()) hwPlus else decoders
                    }
                }
            }
        }

        ExoPlayer.Builder(context, renderersFactory).build().also {
            it.setMediaItem(mediaItem)
            val firstLang = subtitleLanguages.firstOrNull() ?: "ar"
            it.trackSelectionParameters = it.trackSelectionParameters
                .buildUpon()
                .setPreferredTextLanguage(firstLang)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                .build()
            if (savedPosForDecoderChange > 0L) {
                it.seekTo(savedPosForDecoderChange)
            }
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
    var isFavorite by remember { mutableStateOf(false) }

    // Seek to last played position upon initial video load & sync favorite state
    LaunchedEffect(player, filePath) {
        val dbMedia = viewModel.getMediaByPath(filePath)
        if (dbMedia != null) {
            isFavorite = dbMedia.isFavorite
            val initialPosition = dbMedia.lastPlayPosition
            if (initialPosition > 0) {
                player.seekTo(initialPosition)
                currentPlayTime = initialPosition
            }
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
    // Decoder states are declared above player initialization
    var isDecoderDialogOpen by remember { mutableStateOf(false) }
    var playbackOrderIndex by remember { mutableStateOf(0) }

    var sleepTimerActive by remember { mutableStateOf(false) }
    var sleepTimerRemainingSecs by remember { mutableStateOf(0) }
    var sleepTimerInitialMinutes by remember { mutableStateOf(0) }
    var isSleepTimerEndOfVideo by remember { mutableStateOf(false) }
    var isSleepTimerDialogOpen by remember { mutableStateOf(false) }
    var isVideoDetailsDialogOpen by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isDeleteDialogOpen by remember { mutableStateOf(false) }
    val bookmarksList = remember { mutableStateListOf<Long>() }
    var isBookmarksDialogOpen by remember { mutableStateOf(false) }

    var pointA by remember { mutableStateOf<Long?>(null) }
    var pointB by remember { mutableStateOf<Long?>(null) }
    var isAbRepeatBarOpen by remember { mutableStateOf(false) }

    var isEqualizerOpen by remember { mutableStateOf(false) }
    var isEqualizerActive by remember { mutableStateOf(false) }
    var equalizerPresetIndex by remember { mutableStateOf(0) }
    var equalizerBandLevels by remember { mutableStateOf(floatArrayOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)) }
    var equalizerInstance by remember { mutableStateOf<Equalizer?>(null) }
    var loudnessEnhancerInstance by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }
    var currentVolRatio by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }

    var isMoreOptionsSheetOpen by remember { mutableStateOf(false) }
    var sidePanelMenuState by remember { mutableStateOf(SidePanelMenuState.MAIN) }
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
        val savedFontFamily = sharedPrefs.getString("sub_font_family", "default") ?: "default"
        
        mutableStateOf(
            SubtitleStyle(
                textSize = savedTextSize,
                textColor = Color(savedTextColor),
                backgroundColor = Color(savedBgColor),
                backgroundEnabled = savedBgEnabled,
                bold = savedBold,
                italic = savedItalic,
                fontFamily = savedFontFamily,
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
            .putString("sub_font_family", subtitleStyle.fontFamily)
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
                Toast.makeText(ctx, "تم حفظ لقطة الشاشة في ${file.absolutePath}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(ctx, "تم حفظ لقطة الشاشة (إطار احتياطي) في ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "خطأ أثناء التقاط لقطة الشاشة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Set equalizerband safely
    fun setEqualizerBand(band: Int, value: Float) {
        try {
            val eq = equalizerInstance
            if (eq != null) {
                val range = try { eq.bandLevelRange } catch (e: Exception) { shortArrayOf(-1500, 1500) }
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val targetLevel = (minLevel + (value + 1.0f) / 2.0f * (maxLevel - minLevel))
                    .toInt()
                    .coerceIn(minLevel, maxLevel)
                    .toShort()
                eq.setBandLevel(band.toShort(), targetLevel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val newList = equalizerBandLevels.clone()
        if (band in newList.indices) {
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

    LaunchedEffect(playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            when (playbackOrderIndex) {
                0 -> { // قائمة (Sequential)
                    if (hasNextVideo) {
                        val nextPath = allVideos[currentVideoIndex + 1].path
                        onNavigateToVideo(nextPath)
                    }
                }
                1 -> { // تكرار مرة (Repeat One)
                    player.seekTo(0)
                    player.playWhenReady = true
                    player.play()
                }
                2 -> { // تكرار الكل (Repeat All)
                    if (hasNextVideo) {
                        val nextPath = allVideos[currentVideoIndex + 1].path
                        onNavigateToVideo(nextPath)
                    } else if (allVideos.isNotEmpty()) {
                        val firstPath = allVideos[0].path
                        onNavigateToVideo(firstPath)
                    }
                }
                3 -> { // تشغيل عشوائي (Shuffle)
                    if (allVideos.size > 1) {
                        var randomIndex = (0 until allVideos.size).random()
                        if (randomIndex == currentVideoIndex) {
                            randomIndex = (randomIndex + 1) % allVideos.size
                        }
                        onNavigateToVideo(allVideos[randomIndex].path)
                    } else if (allVideos.isNotEmpty()) {
                        player.seekTo(0)
                        player.playWhenReady = true
                        player.play()
                    }
                }
                4 -> { // الإيقاف عند انتهاء الفيديو الحالي
                    // Stay on STATE_ENDED so end-of-video overlay menu is shown
                }
            }
        }
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

    val configuration = LocalConfiguration.current
    val isMainLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var isCustomSleepTimerDialogOpen by remember { mutableStateOf(false) }
    var customSleepTimerMins by remember { mutableStateOf(30f) }

    SplitScreenContainer(
        isLandscape = isMainLandscape,
        mainContent = {
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
                            "FILL", "املا", "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            "STRETCH", "تمديد" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            "Fit-H" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            "16:9", "4:3", "18:9", "19.5:9", "20:9", "1.85:1", "2.21:1", "2.35:1", "2.39:1" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
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
                        "FILL", "املا", "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "STRETCH", "تمديد" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "Fit-H" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        "16:9", "4:3", "18:9", "19.5:9", "20:9", "1.85:1", "2.21:1", "2.35:1", "2.39:1" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
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
                        val fontFamily = when (subtitleStyle.fontFamily) {
                            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                            "sans-serif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                            else -> androidx.compose.ui.text.font.FontFamily.Default
                        }
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
                            fontFamily = fontFamily,
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
                                    .clickable { isMoreOptionsSheetOpen = true }
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
                                        text = sleepTimerText,
                                        color = Color(0xFFFF5252),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                            onClick = {
                                sidePanelMenuState = SidePanelMenuState.MAIN
                                isMoreOptionsSheetOpen = true
                            }
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
                            if (player.currentPosition > 0L) {
                                savedPosForDecoderChange = player.currentPosition
                            }
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

        // Subtitle Panel handled inside sideContent slot

        // -----------------------------------------------------
        // SUBTITLE CUSTOMIZATION DIALOG (DataStore Persisted)
        // -----------------------------------------------------
        if (isSubtitleCustomizationOpen) {
            isSubtitlePanelViewOpen = true
            isSubtitleCustomizationOpen = false
        }


        // -----------------------------------------------------
        // EQUALIZER BLACK POPUP DIALOG WITH RED SLIDERS
        // -----------------------------------------------------
        if (isEqualizerOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isEqualizerOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.92f)
                        .padding(12.dp),
                    color = Color(0xFF08080C),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF2C2C).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header: Close, Title, Enable Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isEqualizerOpen = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.LightGray)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isEqualizerActive) "نشط ⚡" else "معطل",
                                    color = if (isEqualizerActive) Color(0xFFFF2C2C) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = isEqualizerActive,
                                    onCheckedChange = { active ->
                                        isEqualizerActive = active
                                        try {
                                            equalizerInstance?.enabled = active
                                        } catch (e: Exception) {}
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFFF2C2C),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color(0xFF22222A)
                                    )
                                )
                            }

                            Text(
                                text = "موازن الصوت (Equalizer) 🎚️",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        // Presets
                        Text(
                            text = "الأنماط الجاهزة (Presets):",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val eqPresetsList = listOf("افتراضي", "Bass Boost", "Treble Boost", "Flat", "Rock", "Pop", "Jazz", "Classical")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(eqPresetsList.size) { idx ->
                                val isSelected = equalizerPresetIndex == idx
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        equalizerPresetIndex = idx
                                        isEqualizerActive = true
                                        try {
                                            equalizerInstance?.enabled = true
                                            equalizerInstance?.usePreset(idx.toShort())
                                        } catch (e: Exception) {}

                                        val targetLevels = when (idx) {
                                            1 -> floatArrayOf(0.8f, 0.5f, 0.1f, 0.1f, 0.1f) // Bass Boost
                                            2 -> floatArrayOf(0.1f, 0.1f, 0.3f, 0.7f, 0.9f) // Treble Boost
                                            3 -> floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f) // Flat
                                            4 -> floatArrayOf(0.6f, 0.3f, -0.1f, 0.4f, 0.7f) // Rock
                                            5 -> floatArrayOf(0.3f, 0.5f, 0.6f, 0.4f, 0.2f) // Pop
                                            6 -> floatArrayOf(0.4f, 0.2f, -0.1f, 0.3f, 0.5f) // Jazz
                                            7 -> floatArrayOf(0.5f, 0.3f, 0.2f, 0.4f, 0.5f) // Classical
                                            else -> floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
                                        }
                                        for (b in targetLevels.indices) {
                                            setEqualizerBand(b, targetLevels[b])
                                        }
                                    },
                                    label = { Text(eqPresetsList[idx], fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF2C2C).copy(alpha = 0.3f),
                                        selectedLabelColor = Color(0xFFFF2C2C),
                                        containerColor = Color(0xFF1E1E26),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 5-Band RED Sliders
                        Text(
                            text = "شرائط التحكم بالترددات (Real Audio FX):",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val bandFrequenciesList = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
                        repeat(5) { band ->
                            val bandLevel = equalizerBandLevels.getOrElse(band) { 0f }
                            val dbValue = (bandLevel * 12).toInt()

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF14141C))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${if (dbValue > 0) "+" else ""}${dbValue} dB",
                                        color = if (dbValue != 0) Color(0xFFFF2C2C) else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = bandFrequenciesList[band],
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                AppSlider(
                                    value = bandLevel,
                                    onValueChange = { newVal ->
                                        setEqualizerBand(band, newVal)
                                        isEqualizerActive = true
                                        try { equalizerInstance?.enabled = true } catch (e: Exception) {}
                                    },
                                    valueRange = -1.0f..1.0f,
                                    activeColor = Color(0xFFFF2C2C), // شرائط حمراء
                                    inactiveColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                )
                            }
                        }

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    for (b in 0..4) {
                                        setEqualizerBand(b, 0.0f)
                                    }
                                    equalizerPresetIndex = 0
                                    Toast.makeText(context, "تمت إعادة ضبط موازن الصوت", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                            ) {
                                Text("إعادة ضبط", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { isEqualizerOpen = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2C2C)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("تم", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
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

        // Floating Sleep Timer Stop Button (وقف مؤقت النوم)
        AnimatedVisibility(
            visible = sleepTimerActive,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            Surface(
                color = Color(0xEC181820),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, redAccent.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable {
                    sleepTimerActive = false
                    isSleepTimerEndOfVideo = false
                    sleepTimerRemainingSecs = 0
                    Toast.makeText(context, "تم إيقاف مؤقت النوم", Toast.LENGTH_SHORT).show()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "إيقاف المؤقت",
                        tint = redAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSleepTimerEndOfVideo) "وقف مؤقت النوم (عند نهاية الفيديو)" 
                               else "وقف مؤقت النوم (${sleepTimerRemainingSecs / 60}:%02d)".format(sleepTimerRemainingSecs % 60),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -----------------------------------------------------
        // FLOATING A-B REPEAT WIDGET (كرر AB)
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isAbRepeatBarOpen || pointA != null || pointB != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 80.dp)
        ) {
            Surface(
                color = Color(0xEC181820),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(210.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "كرر AB",
                                tint = redAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "كرر AB",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { isAbRepeatBarOpen = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val formatTimeMs: (Long?) -> String = { ms ->
                            if (ms == null) "--:--"
                            else {
                                val s = (ms / 1000).toInt()
                                val sec = s % 60
                                val min = (s / 60) % 60
                                val hr = s / 3600
                                if (hr > 0) "%02d:%02d:%02d".format(hr, min, sec)
                                else "%02d:%02d".format(min, sec)
                            }
                        }

                        Surface(
                            color = if (pointA != null) redAccent.copy(alpha = 0.25f) else Color(0xFF262630),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (pointA != null) redAccent else Color.Transparent)
                        ) {
                            Text(
                                text = formatTimeMs(pointA),
                                color = if (pointA != null) redAccent else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text("~", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        Surface(
                            color = if (pointB != null) redAccent.copy(alpha = 0.25f) else Color(0xFF262630),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (pointB != null) redAccent else Color.Transparent)
                        ) {
                            Text(
                                text = formatTimeMs(pointB),
                                color = if (pointB != null) redAccent else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                pointA = player.currentPosition
                                Toast.makeText(context, "تم تحديد النقطة A", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pointA != null) redAccent else Color(0xFF32323D)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                pointB = player.currentPosition
                                Toast.makeText(context, "تم تحديد النقطة B وتفعيل التكرار", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pointB != null) redAccent else Color(0xFF32323D)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("B", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                pointA = null
                                pointB = null
                                Toast.makeText(context, "تم إلغاء التكرار", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("مسح", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Real File Delete Dialog
        if (isDeleteDialogOpen) {
            AlertDialog(
                onDismissRequest = { isDeleteDialogOpen = false },
                title = {
                    Text("حذف الفيديو 🗑️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Text(
                        "هل أنت تأكد من رغبتك في حذف هذا الفيديو نهائياً من الذاكرة؟\n\n${File(filePath).name}",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A4B)),
                        onClick = {
                            isDeleteDialogOpen = false
                            try {
                                val file = File(filePath)
                                val success = if (file.exists()) file.delete() else false
                                if (success) {
                                    Toast.makeText(context, "تم حذف الفيديو بنجاح", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "تعذر حذف الفيديو من القرص", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطأ أثناء الحذف: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isDeleteDialogOpen = false }) {
                        Text("إلغاء", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF24242A),
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Bookmarks Dialog
        if (isBookmarksDialogOpen) {
            AlertDialog(
                onDismissRequest = { isBookmarksDialogOpen = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("العلامات المرجعية المحفوظة 🔖", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = {
                            val pos = player.currentPosition
                            if (!bookmarksList.contains(pos)) {
                                bookmarksList.add(pos)
                                bookmarksList.sort()
                                Toast.makeText(context, "تم إضافة علامة مرجعية جديدة", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة", tint = Color(0xFFFF2A4B))
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (bookmarksList.isEmpty()) {
                            Text("لا توجد علامات مرجعية احفظها أثناء المشاهدة بالنقر على أيقونة (+) أعلاه.", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            bookmarksList.forEach { timestamp ->
                                val mins = (timestamp / 60000).toInt()
                                val secs = ((timestamp / 1000) % 60).toInt()
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF32323C)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player.seekTo(timestamp)
                                            isBookmarksDialogOpen = false
                                            Toast.makeText(context, "الانتقال إلى %02d:%02d".format(mins, secs), Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📌 %02d:%02d".format(mins, secs), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        IconButton(
                                            onClick = { bookmarksList.remove(timestamp) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isBookmarksDialogOpen = false }) {
                        Text("إغلاق", color = Color(0xFFFF2A4B))
                    }
                },
                containerColor = Color(0xFF24242A),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
},
sideContent = {
    if (isMoreOptionsSheetOpen) {
        BackHandler {
            if (sidePanelMenuState != SidePanelMenuState.MAIN) {
                sidePanelMenuState = SidePanelMenuState.MAIN
            } else {
                isMoreOptionsSheetOpen = false
            }
        }
    }

    if (isSubtitlePanelViewOpen) {
        BackHandler {
            isSubtitlePanelViewOpen = false
        }
    }

    AnimatedVisibility(
        visible = isMoreOptionsSheetOpen,
        enter = if (isMainLandscape) {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(250))
        } else {
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(250))
        },
        exit = if (isMainLandscape) {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(250))
        } else {
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(250))
        }
    ) {
        Surface(
            color = Color(0xFA0B0B0E),
            shape = if (isMainLandscape) {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            } else {
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            },
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = if (isMainLandscape) {
                Modifier
                    .fillMaxHeight()
                    .width(380.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Side Panel Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sidePanelMenuState != SidePanelMenuState.MAIN) {
                            IconButton(
                                onClick = { sidePanelMenuState = SidePanelMenuState.MAIN },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color(0xFFFF2A4B)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            val titleText = when (sidePanelMenuState) {
                                SidePanelMenuState.SUBTITLE_SETTINGS -> "إعدادات الترجمة (Subtitles)"
                                SidePanelMenuState.ASPECT_RATIO -> "نسبة العرض (Aspect Ratio)"
                                SidePanelMenuState.DETAILS -> "تفاصيل الفيديو والمعلومات"
                                else -> ""
                            }
                            Text(
                                text = titleText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "خيارات التشغيل",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
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
                    color = Color.White.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Sub-Screen Content via AnimatedContent
                AnimatedContent(
                    targetState = sidePanelMenuState,
                    label = "side_panel_navigation",
                    modifier = Modifier.weight(1f)
                ) { currentMenuState ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (currentMenuState) {
                            SidePanelMenuState.MAIN -> {
                                val dimWhite = Color.White.copy(alpha = 0.75f)
                                val dividerColor = Color.White.copy(alpha = 0.08f)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // الصف الأول: الترجمة + تشغيل في الخلفية (بخط صغير) + المعادل + المفضلة
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. الترجمة
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    isSubtitlePanelViewOpen = true
                                                    isMoreOptionsSheetOpen = false
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            CcSubtitleIcon(modifier = Modifier.size(24.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("الترجمة", fontSize = 11.sp, color = dimWhite, textAlign = TextAlign.Center)
                                        }

                                        // 2. تشغيل في الخلفية بخط صغير
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
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
                                                    Toast.makeText(context, "تم التحويل إلى التشغيل في الخلفية", Toast.LENGTH_SHORT).show()
                                                    isMoreOptionsSheetOpen = false
                                                    onBack()
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            HeadphonesCustomIcon(modifier = Modifier.size(24.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "تشغيل في الخلفية",
                                                fontSize = 9.sp, // بخط صغير كما طلب المستخدم
                                                color = dimWhite,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                lineHeight = 11.sp
                                            )
                                        }

                                        // 3. المعادل
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    isEqualizerOpen = true
                                                    isMoreOptionsSheetOpen = false
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = "المعادل",
                                                tint = if (isEqualizerActive) redAccent else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("المعادل", fontSize = 11.sp, color = dimWhite, textAlign = TextAlign.Center)
                                        }

                                        // 4. المفضلة
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    isFavorite = !isFavorite
                                                    scope.launch {
                                                        try {
                                                            val media = viewModel.getMediaByPath(filePath)
                                                            if (media != null) {
                                                                viewModel.toggleFavorite(media)
                                                            }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        if (isFavorite) "تمت الإضافة للمفضلة ❤️" else "تم الإلغاء من المفضلة",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "المفضلة",
                                                tint = if (isFavorite) redAccent else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "المفضلة",
                                                fontSize = 11.sp,
                                                color = if (isFavorite) redAccent else dimWhite,
                                                textAlign = TextAlign.Center,
                                                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = dividerColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))

                                    // الصف الثاني: حذف + مشاركة + العلامات + التفاصيل
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. حذف
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    isDeleteDialogOpen = true
                                                    isMoreOptionsSheetOpen = false
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "حذف",
                                                tint = Color(0xFFFF4D4D),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("حذف", fontSize = 11.sp, color = Color(0xFFFF4D4D), textAlign = TextAlign.Center)
                                        }

                                        // 2. مشاركة فعلياً
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    isMoreOptionsSheetOpen = false
                                                    try {
                                                        val videoFile = java.io.File(filePath)
                                                        if (videoFile.exists()) {
                                                            val contentUri = try {
                                                                androidx.core.content.FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.fileprovider",
                                                                    videoFile
                                                                )
                                                            } catch (e: Exception) {
                                                                android.net.Uri.fromFile(videoFile)
                                                            }
                                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                type = "video/*"
                                                                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                                                putExtra(android.content.Intent.EXTRA_TEXT, "شاهد هذا الفيديو: ${videoFile.name}")
                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            activity?.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة الفيديو عبر"))
                                                        } else {
                                                            Toast.makeText(context, "ملف الفيديو غير موجود", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "خطأ أثناء المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "مشاركة",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("مشاركة", fontSize = 11.sp, color = dimWhite, textAlign = TextAlign.Center)
                                        }

                                        // 3. العلامات
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    isBookmarksDialogOpen = true
                                                    isMoreOptionsSheetOpen = false
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "العلامات",
                                                tint = if (isBookmarked) redAccent else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("العلامات", fontSize = 11.sp, color = dimWhite, textAlign = TextAlign.Center)
                                        }

                                        // 4. التفاصيل
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    sidePanelMenuState = SidePanelMenuState.DETAILS
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = "التفاصيل",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("التفاصيل", fontSize = 11.sp, color = dimWhite, textAlign = TextAlign.Center)
                                        }
                                    }

                                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                                    // 2. PLAYBACK OPTIONS (خيارات التشغيل)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "خيارات التشغيل",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable {
                                                    isNightModeActive = !isNightModeActive
                                                     Toast.makeText(context, if (isNightModeActive) "تم تفعيل الوضع الداكن" else "تم إيقاف الوضع الداكن", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.NightsStay, contentDescription = "الوضع الداكن", tint = if (isNightModeActive) redAccent else Color.White, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text("الوضع الداكن", fontSize = 10.sp, color = if (isNightModeActive) redAccent else dimWhite)
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable {
                                                    isAbRepeatBarOpen = !isAbRepeatBarOpen
                                                    isMoreOptionsSheetOpen = false
                                                    Toast.makeText(context, "تم فتح شريط كرر AB", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Repeat, contentDescription = "كرر AB", tint = if (pointA != null || pointB != null || isAbRepeatBarOpen) redAccent else Color.White, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text("كرر AB", fontSize = 10.sp, color = if (pointA != null || pointB != null || isAbRepeatBarOpen) redAccent else dimWhite)
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable { isMirrorModeActive = !isMirrorModeActive }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ScreenRotation,
                                                    contentDescription = null,
                                                    tint = if (isMirrorModeActive) redAccent else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text("انعكاس", fontSize = 10.sp, color = if (isMirrorModeActive) redAccent else dimWhite)
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable { sidePanelMenuState = SidePanelMenuState.ASPECT_RATIO }
                                            ) {
                                                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text("نسبة العرض إلى الارتفاع", fontSize = 9.sp, color = dimWhite)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                                    // 3. PLAYBACK ORDER (ترتيب التشغيل)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "ترتيب التشغيل",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = {
                                                playbackOrderIndex = 4
                                                Toast.makeText(context, "الإيقاف عند انتهاء الفيديو الحالي", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.SwapHoriz,
                                                    contentDescription = "الإيقاف عند انتهاء الفيديو الحالي",
                                                    tint = if (playbackOrderIndex == 4) redAccent else dimWhite,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                playbackOrderIndex = 0
                                                player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                                player.shuffleModeEnabled = false
                                                Toast.makeText(context, "قائمة", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.SyncAlt,
                                                    contentDescription = "قائمة",
                                                    tint = if (playbackOrderIndex == 0) redAccent else dimWhite,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                playbackOrderIndex = 1
                                                player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                                                player.shuffleModeEnabled = false
                                                Toast.makeText(context, "تكرار مرة", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.RepeatOne,
                                                    contentDescription = "تكرار مرة",
                                                    tint = if (playbackOrderIndex == 1) redAccent else dimWhite,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                playbackOrderIndex = 3
                                                player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                                player.shuffleModeEnabled = true
                                                Toast.makeText(context, "تشغيل عشوائي", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Shuffle,
                                                    contentDescription = "تشغيل عشوائي",
                                                    tint = if (playbackOrderIndex == 3) redAccent else dimWhite,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                playbackOrderIndex = 2
                                                player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                                player.shuffleModeEnabled = false
                                                Toast.makeText(context, "تكرار الكل", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Repeat,
                                                    contentDescription = "تكرار الكل",
                                                    tint = if (playbackOrderIndex == 2) redAccent else dimWhite,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                                    // 4. DECODER (فك التشفير)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "فك التشفير",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "فك التشفير SW",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentDecoder == "SW") redAccent else dimWhite,
                                                modifier = Modifier
                                                    .clickable {
                                                        if (player.currentPosition > 0L) {
                                                            savedPosForDecoderChange = player.currentPosition
                                                        }
                                                        currentDecoder = "SW"
                                                        isHWAccelActive = false
                                                        Toast.makeText(context, "تم التبديل إلى فك التشفير SW", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(6.dp)
                                            )

                                            Text(
                                                text = "فك التشفير HW",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentDecoder == "HW") redAccent else dimWhite,
                                                modifier = Modifier
                                                    .clickable {
                                                        if (player.currentPosition > 0L) {
                                                            savedPosForDecoderChange = player.currentPosition
                                                        }
                                                        currentDecoder = "HW"
                                                        isHWAccelActive = true
                                                        Toast.makeText(context, "تم التبديل إلى فك التشفير HW", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(6.dp)
                                            )

                                            Text(
                                                text = "فك التشفير HW+",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentDecoder == "HW+") redAccent else dimWhite,
                                                modifier = Modifier
                                                    .clickable {
                                                        if (player.currentPosition > 0L) {
                                                            savedPosForDecoderChange = player.currentPosition
                                                        }
                                                        currentDecoder = "HW+"
                                                        isHWAccelActive = true
                                                        Toast.makeText(context, "تم التبديل إلى فك التشفير HW+", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(6.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                                    // 5. SLEEP TIMER (مؤقت النوم)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "مؤقت النوم",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val timerOptions = listOf(
                                                "إيقاف" to 0,
                                                "10 دقائق" to 10,
                                                "30 دقيقة" to 30,
                                                "60 دقيقة" to 60,
                                                "إلى النهاية" to -1
                                            )

                                            timerOptions.forEach { (label, value) ->
                                                val isSelected = when (value) {
                                                    0 -> !sleepTimerActive
                                                    -1 -> sleepTimerActive && sleepTimerInitialMinutes == -1
                                                    else -> sleepTimerActive && sleepTimerInitialMinutes == value
                                                }

                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) redAccent else dimWhite,
                                                    modifier = Modifier
                                                        .clickable {
                                                            when (value) {
                                                                0 -> {
                                                                    sleepTimerActive = false
                                                                    sleepTimerRemainingSecs = 0
                                                                    isSleepTimerEndOfVideo = false
                                                                    Toast.makeText(context, "تم إيقاف مؤقت النوم", Toast.LENGTH_SHORT).show()
                                                                }
                                                                -1 -> {
                                                                    sleepTimerInitialMinutes = -1
                                                                    isSleepTimerEndOfVideo = true
                                                                    sleepTimerActive = true
                                                                    Toast.makeText(context, "سيتم الإيقاف عند نهاية الفيديو الحالي", Toast.LENGTH_SHORT).show()
                                                                }
                                                                else -> {
                                                                    sleepTimerInitialMinutes = value
                                                                    sleepTimerRemainingSecs = value * 60
                                                                    sleepTimerActive = true
                                                                    isSleepTimerEndOfVideo = false
                                                                    Toast.makeText(context, "تم ضبط مؤقت النوم: $value دقيقة", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = dividerColor, thickness = 1.dp)

                                    // 6. VOLUME AND BRIGHTNESS SLIDERS
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Volume Slider
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${(currentVolRatio * 100).toInt()}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.width(28.dp),
                                                textAlign = TextAlign.Start
                                            )
                                            Slider(
                                                value = currentVolRatio,
                                                onValueChange = { ratio ->
                                                    currentVolRatio = ratio
                                                    val targetVol = (ratio * maxVolume).toInt().coerceIn(0, maxVolume.toInt())
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                                },
                                                valueRange = 0f..1f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = redAccent,
                                                    activeTrackColor = redAccent,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = if (currentVolRatio <= 0.01f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                contentDescription = "الصوت",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Brightness Slider
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${(currentBrightness.coerceIn(0.01f, 1f) * 100).toInt()}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.width(28.dp),
                                                textAlign = TextAlign.Start
                                            )
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
                                                    thumbColor = redAccent,
                                                    activeTrackColor = redAccent,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.WbSunny,
                                                contentDescription = "الإضاءة",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            SidePanelMenuState.SUBTITLE_SETTINGS -> {
                                // SUBTITLE SUB-SCREEN
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // Toggle Enable Card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF24242A)),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("تفعيل الترجمة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Switch(
                                                checked = isSubtitleEnabled,
                                                onCheckedChange = { enabled ->
                                                    isSubtitleEnabled = enabled
                                                    player.trackSelectionParameters = player.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                                                        .build()
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00C8FF))
                                            )
                                        }
                                    }

                                    // Pick External File
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF24242A)),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                try { subtitlePickerLauncher.launch(arrayOf("*/*")) } catch (e: Exception) { }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF00C8FF), modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("اختيار ملف ترجمة من الهاتف", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                                        }
                                    }

                                    // Size Slider
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF24242A))
                                            .padding(14.dp)
                                    ) {
                                        Text("حجم خط الترجمة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Slider(
                                            value = subtitleStyle.textSize,
                                            onValueChange = { subtitleStyle = subtitleStyle.copy(textSize = it) },
                                            valueRange = 0.6f..2.0f,
                                            colors = SliderDefaults.colors(thumbColor = Color(0xFF00C8FF), activeTrackColor = Color(0xFF00C8FF))
                                        )
                                    }

                                    // Color Choices
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF24242A))
                                            .padding(14.dp)
                                    ) {
                                        Text("لون نص الترجمة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            val colorChoices = listOf(
                                                Color.White to "أبيض",
                                                Color(0xFFFFD700) to "أصفر",
                                                Color(0xFF4CAF50) to "أخضر",
                                                Color(0xFF00C8FF) to "أزرق",
                                                Color(0xFFFF5252) to "أحمر"
                                            )
                                            colorChoices.forEach { (color, label) ->
                                                val isSelected = subtitleStyle.textColor == color
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) Color.White else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            subtitleStyle = subtitleStyle.copy(textColor = color)
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SidePanelMenuState.ASPECT_RATIO -> {
                                // ASPECT RATIO SUB-SCREEN MATCHING VIDEO FRAME 00:21
                                val generalRatios = listOf("احتواء", "املا", "تمديد", "الأصلي", "Fit-H")
                                val classicRatios = listOf("18:9", "16:9", "4:3", "19.5:9", "20:9")
                                val filmRatios = listOf("1.85:1", "2.21:1", "2.35:1", "2.39:1")

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Section 1: عام
                                    Text("عام", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = redAccent)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        generalRatios.forEach { mode ->
                                            val isSelected = scaleMode == mode || (mode == "احتواء" && scaleMode == "FIT") || (mode == "املا" && scaleMode == "FILL") || (mode == "تمديد" && scaleMode == "STRETCH")
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    scaleMode = mode
                                                    Toast.makeText(context, "نسبة العرض: $mode", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text(mode, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = redAccent,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF24242A),
                                                    labelColor = Color.White
                                                )
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Section 2: كلاسيكي
                                    Text("كلاسيكي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = redAccent)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        classicRatios.forEach { mode ->
                                            val isSelected = scaleMode == mode
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    scaleMode = mode
                                                    Toast.makeText(context, "نسبة العرض: $mode", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text(mode, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = redAccent,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF24242A),
                                                    labelColor = Color.White
                                                )
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Section 3: فيلم
                                    Text("فيلم", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = redAccent)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        filmRatios.forEach { mode ->
                                            val isSelected = scaleMode == mode
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    scaleMode = mode
                                                    Toast.makeText(context, "نسبة العرض: $mode", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text(mode, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = redAccent,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF24242A),
                                                    labelColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            SidePanelMenuState.DETAILS -> {
                                // VIDEO DETAILS SUB-SCREEN
                                val detailsList = listOf(
                                    "اسم الملف" to java.io.File(filePath).name,
                                    "دقة الفيديو" to resolutionLabel,
                                    "الأبعاد الإجمالية" to "${videoWidth} x ${videoHeight} px",
                                    "مدة الفيديو" to "%02d:%02d:%02d".format(
                                        java.util.concurrent.TimeUnit.MILLISECONDS.toHours(videoDuration),
                                        java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(videoDuration) % 60,
                                        java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(videoDuration) % 60
                                    ),
                                    "محرك فك التشفير" to currentDecoder,
                                    "مسار الملف" to filePath
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    detailsList.forEach { (label, value) ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF24242A)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(label, color = Color(0xFF00C8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(value, color = Color.White, fontSize = 12.sp)
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
    }

    AnimatedVisibility(
        visible = isSubtitlePanelViewOpen,
        enter = if (isMainLandscape) {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(250))
        } else {
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(250))
        },
        exit = if (isMainLandscape) {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(250))
        } else {
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(250))
        }
    ) {
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
    }
}
)
}

@Composable
private fun SplitScreenContainer(
    isLandscape: Boolean,
    mainContent: @Composable () -> Unit,
    sideContent: @Composable () -> Unit
) {
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                mainContent()
            }
            sideContent()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                mainContent()
            }
            sideContent()
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
