package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.SheetType
import com.example.player.engine.PlayerState

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isLocked: Boolean,
    title: String,
    playerState: PlayerState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onOpenSheet: (SheetType) -> Unit,
    onAbRepeatToggle: () -> Unit,
    hasAbRepeat: Boolean
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "قفل الشاشة",
                        tint = if (isLocked) Color(0xFFFF2A4B) else Color.White
                    )
                }
            }

            if (!isLocked) {
                // CENTER PLAY/PAUSE/REWIND/FORWARD CONTROLS
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = { onSeek((playerState.currentPositionMs - 10000).coerceAtLeast(0L)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "إرجاع 10 ثوانٍ",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Surface(
                        onClick = onTogglePlayPause,
                        shape = CircleShape,
                        color = Color(0xFFFF2A4B),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "تشغيل / إيقاف",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onSeek((playerState.currentPositionMs + 10000).coerceAtMost(playerState.durationMs)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "تقديم 10 ثوانٍ",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // BOTTOM PLAYBACK BAR & SHEET SHORTCUTS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Time position & Seek Slider
                    val currentSecs = playerState.currentPositionMs / 1000
                    val totalSecs = playerState.durationMs / 1000

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d:%02d".format(currentSecs / 60, currentSecs % 60),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "%02d:%02d".format(totalSecs / 60, totalSecs % 60),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = if (playerState.durationMs > 0) playerState.currentPositionMs.toFloat() else 0f,
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..(if (playerState.durationMs > 0) playerState.durationMs.toFloat() else 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF2A4B),
                            activeTrackColor = Color(0xFFFF2A4B),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // BOTTOM ROW ACTION BUTTONS FOR SIDE SHEETS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onOpenSheet(SheetType.SETTINGS) }) {
                            Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White)
                        }
                        IconButton(onClick = { onOpenSheet(SheetType.AUDIO) }) {
                            Icon(Icons.Default.Audiotrack, contentDescription = "الصوت", tint = Color.White)
                        }
                        IconButton(onClick = { onOpenSheet(SheetType.SUBTITLE) }) {
                            Icon(Icons.Default.Subtitles, contentDescription = "الترجمة", tint = Color.White)
                        }
                        IconButton(onClick = { onOpenSheet(SheetType.QUALITY) }) {
                            Icon(Icons.Default.HighQuality, contentDescription = "الجودة", tint = Color.White)
                        }
                        IconButton(onClick = { onOpenSheet(SheetType.ENHANCE) }) {
                            Icon(Icons.Default.Tune, contentDescription = "المعادل والتحسين", tint = Color.White)
                        }
                        IconButton(onClick = { onOpenSheet(SheetType.DECODER) }) {
                            Icon(Icons.Default.Memory, contentDescription = "المفكك", tint = Color.White)
                        }
                        IconButton(onClick = { onOpenSheet(SheetType.CHAPTERS) }) {
                            Icon(Icons.Default.ViewList, contentDescription = "الفصول", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
