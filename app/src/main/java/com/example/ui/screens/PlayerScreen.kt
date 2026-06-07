package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

    // Customizable double tap / skip steps: 5, 10, 15, 30, 60 seconds
    var seekStepSeconds by remember { mutableStateOf(10) }

    // Keep screen on during media playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        detectedSubtitles.map { file ->
            val baseName = File(filePath).nameWithoutExtension
            val suffix = file.name.substring(baseName.length)
                .removeSuffix(".srt").removeSuffix(".vtt")
                .removeSuffix(".SRT").removeSuffix(".VTT")
            if (suffix.startsWith(".")) {
                val part = suffix.substring(1)
                if (part.isNotEmpty()) part else "Default"
            } else {
                "Default"
            }
        }
    }

    var isSubtitleEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleLang by remember { mutableStateOf<String?>(null) }

    // Init player
    val player = remember(filePath) {
        val videoFile = File(filePath)
        val uri = if (filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("content://") || filePath.startsWith("file://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(videoFile)
        }

        val subtitleConfigs = detectedSubtitles.mapIndexed { index, file ->
            val lang = subtitleLanguages.getOrNull(index) ?: "Default"
            val subUri = Uri.fromFile(file)
            val isSrt = file.name.endsWith(".srt", ignoreCase = true)
            val mimeType = if (isSrt) "application/x-subrip" else "text/vtt"
            
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeType)
                .setLanguage(lang)
                .setSelectionFlags(if (lang == "Default" || lang == "ar") C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setMediaItem(mediaItem)
            prepare()
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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        player.addListener(listener)
        // Set configuration speeds
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

    // Lock screen warning modal status
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

    // Auto rotation orientation parameters
    var currentOrientationState by remember { 
        mutableStateOf(activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) 
    }

    // Video Resolution Label formatting
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
            .pointerInput(isLockedMode, scale, seekStepSeconds, speedMultiplier) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (!isLockedMode) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 3) {
                                // Double tap left side (seek backward)
                                val seekTarget = (player.currentPosition - seekStepSeconds * 1000L).coerceAtLeast(0)
                                player.seekTo(seekTarget)
                                currentPlayTime = seekTarget
                                gestureIndicatorText = "⏪ -${seekStepSeconds}ث"
                            } else if (offset.x > screenWidth * 2 / 3) {
                                // Double tap right side (seek forward)
                                val seekTarget = (player.currentPosition + seekStepSeconds * 1000L).coerceAtMost(player.duration)
                                player.seekTo(seekTarget)
                                currentPlayTime = seekTarget
                                gestureIndicatorText = "⏩ +${seekStepSeconds}ث"
                            } else {
                                // Double tap center: reset zoom if zoomed, else play/pause
                                if (scale > 1.05f) {
                                    scale = 1f
                                    gestureIndicatorText = "🔍 100% حجم طبيعي"
                                } else {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                            scope.launch {
                                isIndicatorVisible = true
                                delay(900)
                                isIndicatorVisible = false
                            }
                        }
                    },
                    onTap = {
                        areControlsVisible = !areControlsVisible
                        if (!areControlsVisible) {
                            isBrightnessSliderVisible = false
                        }
                    },
                    onLongPress = {
                        if (!isLockedMode) {
                            // Long press fast forward acceleration on active hold
                            player.setPlaybackSpeed(2.0f)
                            gestureIndicatorText = "2x ⏩ تسريع مضاعف"
                            isIndicatorVisible = true
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (!isLockedMode) {
                            player.setPlaybackSpeed(speedMultiplier)
                            isIndicatorVisible = false
                        }
                    }
                )
            }
            .pointerInput(isLockedMode) {
                if (!isLockedMode) {
                    var isDragDecisionMade = false
                    var isVerticalDrag = false
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            isIndicatorVisible = false
                            gestureIndicatorText = null
                            isSeekingBySwipe = false
                        },
                        onDragCancel = {
                            isIndicatorVisible = false
                            gestureIndicatorText = null
                            isSeekingBySwipe = false
                        },
                        onDrag = { change, dragAmount ->
                            val screenWidth = size.width
                            val screenHeight = size.height

                            if (!isDragDecisionMade) {
                                isVerticalDrag = dragAmount.y.absoluteValue > dragAmount.x.absoluteValue
                                isDragDecisionMade = true
                            }

                            if (isVerticalDrag) {
                                if (change.position.x < screenWidth / 2) {
                                    // Brightness Side (Left Screen Slide Direction y)
                                    val delta = -dragAmount.y / screenHeight
                                    currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                    val layoutParams = activity?.window?.attributes
                                    layoutParams?.screenBrightness = currentBrightness
                                    activity?.window?.attributes = layoutParams
                                    gestureIndicatorText = "💡 سطوع: ${"%.0f".format(currentBrightness * 100)}%"
                                } else {
                                    // Volume Side (Right Screen Slide Direction y)
                                    val delta = -(dragAmount.y / screenHeight) * maxVolume
                                    currentVolume = (currentVolume + delta).coerceIn(0f, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        currentVolume.toInt(),
                                        0
                                    )
                                    val audioPercent = (currentVolume / maxVolume * 100).toInt()
                                    gestureIndicatorText = "🔊 صوت: ${audioPercent}%"
                                }
                                isIndicatorVisible = true
                            } else {
                                // Horizontal Swipe anywhere (Scrubbing Seek Position + Preview details)
                                isSeekingBySwipe = true
                                val durationVal = player.duration.coerceAtLeast(60000L)
                                val deltaSeek = (dragAmount.x / screenWidth) * durationVal
                                val seekTarget = (player.currentPosition + deltaSeek).toLong().coerceIn(0, player.duration)
                                swipeSeekPosition = seekTarget
                                player.seekTo(seekTarget)
                                currentPlayTime = seekTarget

                                val tSec = seekTarget / 1000
                                gestureIndicatorText = "🔍 Seek: %02d:%02d".format(tSec / 60, tSec % 60)
                                isIndicatorVisible = true
                            }
                        }
                    )
                }
            }
            .transformable(state = transformableState)
    ) {
        // AndroidView rendering Surface Player Canvas
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
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
                view.resizeMode = when (scaleMode) {
                    "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    "CROP" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
        )

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
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentMediaFile.nameWithoutExtension,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = resolutionLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "سرعة: ${speedMultiplier}x",
                                color = Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "%.1f MB".format(currentMediaFile.length() / (1024f * 1024f)),
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Auto-Rotate Orientation Lock Option Button
                    IconButton(
                        onClick = {
                            val targetOrientation = if (currentOrientationState == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else if (currentOrientationState == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                            activity?.requestedOrientation = targetOrientation
                            currentOrientationState = targetOrientation
                            gestureIndicatorText = when (targetOrientation) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "🔒 اتجاه رأسي"
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "🔒 اتجاه أفقي"
                                else -> "🔄 دوران تلقائي"
                            }
                            scope.launch {
                                isIndicatorVisible = true
                                delay(800)
                                isIndicatorVisible = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = when (currentOrientationState) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Icons.Default.ScreenLockPortrait
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> Icons.Default.ScreenLockLandscape
                                else -> Icons.Default.ScreenRotation
                            },
                            contentDescription = "Auto Rotate Lock",
                            tint = Color.White
                        )
                    }

                    // Aspect Resize Mode Controller
                    IconButton(
                        onClick = {
                            scaleMode = when (scaleMode) {
                                "FIT" -> "FILL"
                                "FILL" -> "STRETCH"
                                "STRETCH" -> "CROP"
                                else -> "FIT"
                            }
                            gestureIndicatorText = "علاقة العرض: $scaleMode"
                            scope.launch {
                                isIndicatorVisible = true
                                delay(800)
                                isIndicatorVisible = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Scale Mode", tint = Color.White)
                    }

                    // Picture-in-Picture Button
                    IconButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                activity?.enterPictureInPictureMode()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "PiP Mode", tint = Color.White)
                    }
                }
            }
        }

        // -----------------------------------------------------
        // MULTI-TOUCH GESTURE VOL/BRIGHTNESS/SPEED PROGRESS FLOATING HUD overlay
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isIndicatorVisible,
            enter = fadeIn(animationSpec = spring()),
            exit = fadeOut(animationSpec = spring())
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = gestureIndicatorText ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val progressRatio = remember(gestureIndicatorText, currentVolume, maxVolume, currentBrightness, speedMultiplier) {
                        val txt = gestureIndicatorText ?: ""
                        when {
                            txt.contains("صوت") -> currentVolume / maxVolume
                            txt.contains("سطوع") -> currentBrightness
                            txt.contains("تسريع") -> (speedMultiplier / 4.0f).coerceIn(0f, 1f)
                            else -> 0.5f
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(5.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressRatio)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // -----------------------------------------------------
        // HORIZONTAL SWIPE / SCRUB PROGRESS FRAME PREVIEW Overlay (معاينة إطار مرئي)
        // -----------------------------------------------------
        AnimatedVisibility(
            visible = isSeekingBySwipe,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val previewTargetTime = swipeSeekPosition
            val curSec = previewTargetTime / 1000
            val previewStr = "%02d:%02d".format(curSec / 60, curSec % 60)

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.9f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
                    .width(220.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(6) {
                        Box(modifier = Modifier.size(8.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                    }
                }

                // Scene representation frame card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Movie Scene",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Frame Preview الإطار",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = previewStr,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                val pct = if (videoDuration > 0) (previewTargetTime.toFloat() / videoDuration * 100).toInt() else 0
                Text(
                    text = "موضع الإطار: $pct%",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(6) {
                        Box(modifier = Modifier.size(8.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                    }
                }
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
        // ON-SCREEN COMPACT SLIDER FOR BRIGHTNESS/LIGHT BUTTON
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(curStr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = if (videoDuration > 0) currentPlayTime.toFloat() / videoDuration else 0f,
                        onValueChange = { percent ->
                            val target = (percent * videoDuration).toLong()
                            currentPlayTime = target
                            player.seekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .testTag("player_seek_bar")
                    )
                    Text(totalStr, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }

                // Buttons control toolbar panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left row controls (Lock screen icon, files explorer menu, Quick settings)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Accidental taps lock
                        IconButton(onClick = { isLockedMode = true }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock controls", tint = Color.White)
                        }

                        // Files explorer quick drawer toggle (زر قائمة الملفات)
                        IconButton(onClick = { isFilesListVisible = !isFilesListVisible }) {
                            Icon(
                                imageVector = Icons.Default.FeaturedPlayList,
                                contentDescription = "قائمة الفيديوهات",
                                tint = if (isFilesListVisible) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Quick settings dialog widget (أزرار إعدادات سريعة)
                        IconButton(onClick = { isQuickSettingsOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات التشغيل", tint = Color.White)
                        }
                    }

                    // Centered row controls (Skip Previous, Seek Backward, Play/Pause, Seek Forward, Skip Next)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Previous video button
                        IconButton(
                            onClick = {
                                if (hasPreviousVideo) {
                                    val prevPath = allVideos[currentVideoIndex - 1].path
                                    onNavigateToVideo(prevPath)
                                }
                            },
                            enabled = hasPreviousVideo
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous File",
                                tint = if (hasPreviousVideo) Color.White else Color.Gray
                            )
                        }

                        // Seek Backward (-custom/10s)
                        IconButton(onClick = {
                            val target = (player.currentPosition - seekStepSeconds * 1000L).coerceAtLeast(0)
                            player.seekTo(target)
                            currentPlayTime = target
                        }) {
                            Icon(
                                imageVector = when (seekStepSeconds) {
                                    5 -> Icons.Default.Replay5
                                    30 -> Icons.Default.Replay30
                                    else -> Icons.Default.Replay10
                                },
                                contentDescription = "Back Step",
                                tint = Color.LightGray
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Master FAB Play-Pause
                        LargeFloatingActionButton(
                            onClick = {
                                if (isPlayingState) player.pause() else player.play()
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Control Toggle",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Seek Forward (+custom/10s)
                        IconButton(onClick = {
                            val target = (player.currentPosition + seekStepSeconds * 1000L).coerceAtMost(player.duration)
                            player.seekTo(target)
                            currentPlayTime = target
                        }) {
                            Icon(
                                imageVector = when (seekStepSeconds) {
                                    5 -> Icons.Default.Forward5
                                    30 -> Icons.Default.Forward30
                                    else -> Icons.Default.Forward10
                                },
                                contentDescription = "Forward Step",
                                tint = Color.LightGray
                            )
                        }

                        // Next video button
                        IconButton(
                            onClick = {
                                if (hasNextVideo) {
                                    val nextPath = allVideos[currentVideoIndex + 1].path
                                    onNavigateToVideo(nextPath)
                                }
                            },
                            enabled = hasNextVideo
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next File",
                                tint = if (hasNextVideo) Color.White else Color.Gray
                            )
                        }
                    }

                    // Right row details (Brightness toggle slider bar, subtitles selection dropdown list)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick brightness control panel (زر الإضاءة)
                        IconButton(onClick = { isBrightnessSliderVisible = !isBrightnessSliderVisible }) {
                            Icon(
                                imageVector = Icons.Default.Brightness5,
                                contentDescription = "الإضاءة شريط",
                                tint = if (isBrightnessSliderVisible) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Speed selection dropdown menu
                        Box {
                            var isSpeedExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { isSpeedExpanded = true }) {
                                Icon(Icons.Default.Speed, contentDescription = "Speed multiplier rate", tint = Color.White)
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

                        // Subtitle custom selector tracks Configuration
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

                    Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 8.dp))

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
        // QUICK PLAYBACK OPTIONS DIALOG (أزرار التحكم والإعدادات السريعة)
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
                        // Double Tap Seek steps Customizable
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

                        // Video scaling format
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

                        // Subtitle Size slider preview selection
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
                                },
                                valueRange = 12f..30f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${subSize.toInt()}dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtitle status toggle
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
    }
}
