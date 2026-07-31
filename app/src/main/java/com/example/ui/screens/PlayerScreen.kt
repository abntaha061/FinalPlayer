package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MediaViewModel
import com.example.player.PlayerViewModel
import com.example.player.SheetType
import com.example.player.engine.MPVSurfaceView
import com.example.player.model.AspectMode
import com.example.ui.common.GestureOverlay
import com.example.ui.common.PlayerControls
import com.example.ui.common.sheets.*
import java.io.File

@Composable
fun PlayerScreen(
    filePath: String,
    viewModel: MediaViewModel,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit = {},
    playerViewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val playerState by playerViewModel.playerState.collectAsState()
    val settings by playerViewModel.playbackSettings.collectAsState()
    val activeSheet by playerViewModel.activeSheet.collectAsState()
    val areControlsVisible by playerViewModel.areControlsVisible.collectAsState()
    val isControlsLocked by playerViewModel.isControlsLocked.collectAsState()
    val pointA by playerViewModel.pointA.collectAsState()
    val pointB by playerViewModel.pointB.collectAsState()

    val fileName = remember(filePath) {
        File(filePath).name
    }

    // Load video on entering screen
    LaunchedEffect(filePath) {
        playerViewModel.loadVideo(filePath)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. ENGINE SURFACE VIEW (AndroidView hosting MPVSurfaceView)
        AndroidView(
            factory = { ctx ->
                MPVSurfaceView(ctx).apply {
                    bindEngine(playerViewModel.engine)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. SUBTITLE OVERLAY
        ComposeSubtitleOverlay(
            text = playerState.currentSubtitleTrack,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 3. GESTURE INTERACTION OVERLAY
        GestureOverlay(
            onTap = { playerViewModel.toggleControlsVisibility() },
            onDoubleTapLeft = { playerViewModel.seekTo((playerState.currentPositionMs - 10000).coerceAtLeast(0L)) },
            onDoubleTapRight = { playerViewModel.seekTo((playerState.currentPositionMs + 10000).coerceAtMost(playerState.durationMs)) },
            onBrightnessChange = { playerViewModel.setBrightness(it) },
            onVolumeChange = { playerViewModel.setVolume(it) },
            onSeekDelta = { delta ->
                val target = (playerState.currentPositionMs + delta).coerceIn(0L, playerState.durationMs)
                playerViewModel.seekTo(target)
            },
            currentBrightness = settings.brightness,
            currentVolume = settings.volume,
            currentPositionMs = playerState.currentPositionMs,
            durationMs = playerState.durationMs
        )

        // 4. ON-SCREEN CONTROLS TOOLBAR & SEEKBAR
        PlayerControls(
            isVisible = areControlsVisible,
            isLocked = isControlsLocked,
            title = fileName,
            playerState = playerState,
            onBack = onBack,
            onTogglePlayPause = { playerViewModel.togglePlayPause() },
            onSeek = { playerViewModel.seekTo(it) },
            onToggleLock = { playerViewModel.toggleLock() },
            onOpenSheet = { playerViewModel.openSheet(it) },
            onAbRepeatToggle = { playerViewModel.setAbRepeatPoint() },
            hasAbRepeat = pointA != null || pointB != null
        )

        // 5. SIDE SHEETS OVERLAY CONTAINER
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
        ) {
            AnimatedVisibility(
                visible = activeSheet != SheetType.NONE,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                when (activeSheet) {
                    SheetType.SETTINGS -> PlayerSettingsSideSheet(
                        settings = settings,
                        onClose = { playerViewModel.closeSheet() },
                        onSpeedChange = { playerViewModel.setSpeed(it) },
                        onAspectModeChange = { playerViewModel.setAspectMode(it) },
                        onNightModeToggle = { playerViewModel.setNightMode(it) }
                    )
                    SheetType.QUALITY -> QualitySettingsSideSheet(
                        onClose = { playerViewModel.closeSheet() },
                        onQualitySelected = { playerViewModel.closeSheet() }
                    )
                    SheetType.AUDIO -> AudioSettingsSideSheet(
                        onClose = { playerViewModel.closeSheet() },
                        currentTrackId = playerState.currentAudioTrack,
                        onSelectTrack = { playerViewModel.engine.setAudioTrack(it) },
                        audioBoostDb = settings.audioBoostDb,
                        onAudioBoostChange = { playerViewModel.setAudioBoost(it) }
                    )
                    SheetType.SUBTITLE -> SubtitleSettingsSideSheet(
                        onClose = { playerViewModel.closeSheet() },
                        currentTrackId = playerState.currentSubtitleTrack,
                        onSelectTrack = { playerViewModel.engine.setSubtitleTrack(it) }
                    )
                    SheetType.ENHANCE -> EnhanceSettingsSideSheet(
                        settings = settings,
                        onClose = { playerViewModel.closeSheet() },
                        onAudioBoostChange = { playerViewModel.setAudioBoost(it) }
                    )
                    SheetType.DECODER -> DecoderSideSheet(
                        isHwDecoder = settings.isHwDecoder,
                        onClose = { playerViewModel.closeSheet() },
                        onToggleDecoder = { playerViewModel.setHwDecoder(it) }
                    )
                    SheetType.CHAPTERS -> ChaptersSideSheet(
                        onClose = { playerViewModel.closeSheet() },
                        onSeekToChapter = { playerViewModel.seekTo(it) }
                    )
                    else -> {}
                }
            }
        }
    }
}
