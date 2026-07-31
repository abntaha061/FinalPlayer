package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun GestureOverlay(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onSeekDelta: (Long) -> Unit,
    currentBrightness: Float,
    currentVolume: Int,
    currentPositionMs: Long,
    durationMs: Long
) {
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gestureValue by remember { mutableStateOf("") }
    var gestureProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(gestureType) {
        if (gestureType != null) {
            delay(1200)
            gestureType = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            onDoubleTapLeft()
                            gestureType = GestureType.REWIND
                            gestureValue = "-10 ثوانٍ"
                        } else {
                            onDoubleTapRight()
                            gestureType = GestureType.FORWARD
                            gestureValue = "+10 ثوانٍ"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDragY = 0f
                var totalDragX = 0f
                var startBrightness = currentBrightness
                var startVolume = currentVolume

                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        totalDragX = 0f
                        startBrightness = currentBrightness
                        startVolume = currentVolume
                    },
                    onDragEnd = {
                        gestureType = null
                    },
                    onDragCancel = {
                        gestureType = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX)) {
                            // Vertical drag: Left side = Brightness, Right side = Volume
                            val isLeft = change.position.x < size.width / 2
                            val deltaY = -dragAmount.y / size.height

                            if (isLeft) {
                                val newBrightness = (startBrightness + deltaY).coerceIn(0.05f, 1.0f)
                                startBrightness = newBrightness
                                onBrightnessChange(newBrightness)
                                gestureType = GestureType.BRIGHTNESS
                                gestureProgress = newBrightness
                                gestureValue = "${(newBrightness * 100).toInt()}%"
                            } else {
                                val newVolume = (startVolume + (deltaY * 100)).toInt().coerceIn(0, 100)
                                startVolume = newVolume
                                onVolumeChange(newVolume)
                                gestureType = GestureType.VOLUME
                                gestureProgress = newVolume / 100f
                                gestureValue = "$newVolume%"
                            }
                        } else {
                            // Horizontal drag: Seek
                            val deltaMs = (totalDragX / size.width * 60000).toLong()
                            onSeekDelta(deltaMs)
                            val targetMs = (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
                            gestureType = if (deltaMs >= 0) GestureType.FORWARD else GestureType.REWIND
                            gestureValue = "%02d:%02d".format((targetMs / 60000).toInt(), ((targetMs / 1000) % 60).toInt())
                        }
                    }
                )
            }
    ) {
        // HUD Overlay Indicator
        AnimatedVisibility(
            visible = gestureType != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when (gestureType) {
                        GestureType.BRIGHTNESS -> Icons.Default.Brightness6
                        GestureType.VOLUME -> Icons.Default.VolumeUp
                        GestureType.FORWARD -> Icons.Default.FastForward
                        GestureType.REWIND -> Icons.Default.FastRewind
                        else -> Icons.Default.Brightness6
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gestureValue,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private enum class GestureType {
    BRIGHTNESS, VOLUME, FORWARD, REWIND
}
