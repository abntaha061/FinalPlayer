package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.MainActivity
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

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
    val player = remember {
        val videoFile = File(filePath)
        val uri = if (filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("content://") || filePath.startsWith("file://")) {
            android.net.Uri.parse(filePath)
        } else {
            android.net.Uri.fromFile(videoFile)
        }

        val subtitleConfigs = detectedSubtitles.mapIndexed { index, file ->
            val lang = subtitleLanguages.getOrNull(index) ?: "Default"
            val subUri = android.net.Uri.fromFile(file)
            val isSrt = file.name.endsWith(".srt", ignoreCase = true)
            val mimeType = if (isSrt) "application/x-subrip" else "text/vtt"
            
            MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeType)
                .setLanguage(lang)
                .setSelectionFlags(if (lang == "Default" || lang == "ar") androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0)
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

    // Save history and lifecycle
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

    // Gesture State Controllers
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var currentBrightness by remember {
        mutableStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f)
    }
    if (currentBrightness < 0f) currentBrightness = 0.5f // Handle default auto status

    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var isIndicatorVisible by remember { mutableStateOf(false) }

    // On-Screen Controls HUD
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLockedMode by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var isPlayingState by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableStateOf(0L) }
    var currentPlayTime by remember { mutableStateOf(0L) }

    // Speed custom multiplier
    var speedMultiplier by remember { mutableStateOf(viewModel.getPlaybackSpeed()) }

    // Pinch-to-zoom parameters
    var scale by remember { mutableStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(0.75f, 4.0f)
    }

    // Read settings or setup scale mode
    var scaleMode by remember { mutableStateOf(viewModel.getDefaultScaleMode()) }
    val mediaFile = File(filePath)

    // Tracks update states
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                videoDuration = player.duration.coerceAtLeast(0)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
        }
        player.addListener(listener)
        // Set default speed configuration
        player.setPlaybackSpeed(speedMultiplier)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(250)
            currentPlayTime = player.currentPosition
        }
    }

    // Controls visibility timer
    LaunchedEffect(areControlsVisible, isPlayingState) {
        if (areControlsVisible && isPlayingState) {
            delay(viewModel.getHideControlsDelay() * 1000L)
            areControlsVisible = false
        }
    }

    // Live update immersive full screen mode depending on controller visibility
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

    // Restore orientation and system bars clean disclosure when player screen is closed
    DisposableEffect(Unit) {
        onDispose {
            // Restore default sensor orientation
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Restore system bars visible
            val window = activity?.window ?: return@onDispose
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // Lock screen overlay warning handler
    var isUnlockPromptVisible by remember { mutableStateOf(false) }

    BackHandler {
        if (isLockedMode) {
            isUnlockPromptVisible = true
            scope.launch {
                delay(2000)
                isUnlockPromptVisible = false
            }
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (!isLockedMode) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 3) {
                                // Double tap left (seek back 10 seconds)
                                player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                gestureIndicatorText = "⏪ -10s"
                            } else if (offset.x > screenWidth * 2 / 3) {
                                // Double tap right (seek forward 10 seconds)
                                player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                                gestureIndicatorText = "⏩ +10s"
                            } else {
                                // Double tap center (Play/Pause)
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            scope.launch {
                                isIndicatorVisible = true
                                delay(600)
                                isIndicatorVisible = false
                            }
                        }
                    },
                    onTap = {
                        areControlsVisible = !areControlsVisible
                    },
                    onLongPress = {
                        if (!isLockedMode) {
                            // Long press for 2x speed boost
                            player.setPlaybackSpeed(2.0f)
                            gestureIndicatorText = "2.0x ⏩"
                            scope.launch {
                                isIndicatorVisible = true
                            }
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
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            isIndicatorVisible = false
                            gestureIndicatorText = null
                        },
                        onDragCancel = {
                            isIndicatorVisible = false
                            gestureIndicatorText = null
                        },
                        onDrag = { change, dragAmount ->
                            val screenWidth = size.width
                            val screenHeight = size.height
                            if (dragAmount.y.absoluteValue > dragAmount.x.absoluteValue) {
                                // Vertical swipe (brightness or volume adjustment)
                                if (change.position.x < screenWidth / 2) {
                                    // Brightness side (Left Side)
                                    val delta = -dragAmount.y / screenHeight
                                    currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                    val layoutParams = activity?.window?.attributes
                                    layoutParams?.screenBrightness = currentBrightness
                                    activity?.window?.attributes = layoutParams
                                    gestureIndicatorText = "💡 ${"%.0f".format(currentBrightness * 100)}%"
                                } else {
                                    // Volume side (Right Side)
                                    val delta = -(dragAmount.y / screenHeight) * maxVolume
                                    currentVolume = (currentVolume + delta).coerceIn(0f, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        currentVolume.toInt(),
                                        0
                                    )
                                    val audioPercent = (currentVolume / maxVolume * 100).toInt()
                                    gestureIndicatorText = "🔊 ${audioPercent}%"
                                }
                                isIndicatorVisible = true
                            } else {
                                // Horizontal Swipe (Seeking)
                                val deltaSeek = (dragAmount.x / screenWidth) * 60000 // 60s max scrub
                                val seekTarget = (player.currentPosition + deltaSeek).toLong().coerceIn(0, player.duration)
                                player.seekTo(seekTarget)
                                val tSec = seekTarget / 1000
                                gestureIndicatorText = "🔍 %02d:%02d".format(tSec / 60, tSec % 60)
                                isIndicatorVisible = true
                            }
                        }
                    )
                }
            }
            .transformable(state = transformableState)
    ) {
        // Player Surface interop
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

        // Custom Title display inside black gradient (top screen)
        AnimatedVisibility(
            visible = areControlsVisible && !isLockedMode,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
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
                            text = mediaFile.nameWithoutExtension,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Size: %.2f MB".format(mediaFile.length() / (1024f * 1024f)),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            scaleMode = when (scaleMode) {
                                "FIT" -> "FILL"
                                "FILL" -> "STRETCH"
                                "STRETCH" -> "CROP"
                                else -> "FIT"
                            }
                            gestureIndicatorText = "Scale: $scaleMode"
                            scope.launch {
                                isIndicatorVisible = true
                                delay(800)
                                isIndicatorVisible = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Resize Mode", tint = Color.White)
                    }
                    // Rotation toggle button
                    var currentOrientationState by remember { mutableStateOf(activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
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
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "اتجاه رأسي"
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "أفقي كامل"
                                else -> "تلقائي (حسب الحساس)"
                            }
                            scope.launch {
                                isIndicatorVisible = true
                                delay(800)
                                isIndicatorVisible = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "اتجاه الشاشة",
                            tint = Color.White
                        )
                    }
                    // PiP Switcher
                    IconButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                activity?.enterPictureInPictureMode()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "Picture in picture", tint = Color.White)
                    }
                }
            }
        }

        // Gesture Slider Hud indicator overlays
        AnimatedVisibility(
            visible = isIndicatorVisible,
            enter = fadeIn(animationSpec = spring()),
            exit = fadeOut(animationSpec = spring())
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(vertical = 16.dp, horizontal = 24.dp)
            ) {
                Text(
                    text = gestureIndicatorText ?: "",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Locked Screen Indicator (Hidden secure targets)
        AnimatedVisibility(
            visible = isLockedMode && areControlsVisible,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Button(
                onClick = {
                    isLockedMode = false
                    gestureIndicatorText = "Unlocked"
                    scope.launch {
                        isIndicatorVisible = true
                        delay(600)
                        isIndicatorVisible = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock screen")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("انقر لفك القفل (Unlock Screen)")
                }
            }
        }

        // Controls Area (Bottom Screen HUD)
        AnimatedVisibility(
            visible = areControlsVisible && !isLockedMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                // Seek Bar Slider
                val totalSecs = videoDuration / 1000
                val curSecs = currentPlayTime / 1000
                val totalStr = "%02d:%02d".format(totalSecs / 60, totalSecs % 60)
                val curStr = "%02d:%02d".format(curSecs / 60, curSecs % 60)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(curStr, color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (videoDuration > 0) currentPlayTime.toFloat() / videoDuration else 0f,
                        onValueChange = { percent ->
                            val target = (percent * videoDuration).toLong()
                            player.seekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag("player_seek_bar")
                    )
                    Text(totalStr, color = Color.White, fontSize = 12.sp)
                }

                // Core Buttons Control Hub Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock interface button
                    IconButton(onClick = { isLockedMode = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock controls", tint = Color.LightGray)
                    }

                    // Backward skip
                    IconButton(onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Back 10s", tint = Color.LightGray)
                    }

                    // Play Pause Button
                    FloatingActionButton(
                        onClick = {
                            if (isPlayingState) player.pause() else player.play()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.testTag("player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black
                        )
                    }

                    // Forward skip
                    IconButton(onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.LightGray)
                    }

                    // Playback Speed modifier toggle
                    Box {
                        var isSpeedExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { isSpeedExpanded = true }) {
                            Icon(Icons.Default.Speed, contentDescription = "Playback Speed", tint = Color.LightGray)
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
                                        gestureIndicatorText = "Speed: ${speed}x"
                                        scope.launch {
                                            isIndicatorVisible = true
                                            delay(700)
                                            isIndicatorVisible = false
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Subtitles selection button
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
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                            .build()
                                        isSubtitlesExpanded = false
                                        gestureIndicatorText = "Subtitles: Off"
                                        scope.launch {
                                            isIndicatorVisible = true
                                            delay(700)
                                            isIndicatorVisible = false
                                        }
                                    }
                                )
                                subtitleLanguages.forEachIndexed { index, lang ->
                                    DropdownMenuItem(
                                        text = { Text("ترجمة: $lang", color = Color.White) },
                                        onClick = {
                                            isSubtitleEnabled = true
                                            selectedSubtitleLang = lang
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                                .setPreferredTextLanguage(lang)
                                                .build()
                                            isSubtitlesExpanded = false
                                            gestureIndicatorText = "Subtitles: $lang"
                                            scope.launch {
                                                isIndicatorVisible = true
                                                delay(700)
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
}
