package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.local.entities.MediaFile
import com.example.ui.MediaViewModel
import com.example.util.VideoProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    video: MediaFile,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit,
    onTrimFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(video.duration.coerceAtLeast(1000L).toFloat()) }
    val totalDuration = remember(video.duration) { video.duration.coerceAtLeast(1000L).toFloat() }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("${File(video.path).nameWithoutExtension}_trimmed") }

    // ExoPlayer Setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.path))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Monitor playback position to loop within range [startMs, endMs]
    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition
            currentPositionMs = pos
            isPlaying = exoPlayer.isPlaying
            if (pos >= endMs.toLong()) {
                exoPlayer.seekTo(startMs.toLong())
                if (exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
            }
            delay(100)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101014)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White
                    )
                }

                Text(
                    text = "قص وتعديل الفيديو (Trim Video)",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showNameDialog = true },
                    enabled = !isSaving && (endMs - startMs >= 500f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "حفظ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Video Preview Player Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Big Center Play/Pause toggle
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.currentPosition < startMs.toLong() || exoPlayer.currentPosition >= endMs.toLong()) {
                                exoPlayer.seekTo(startMs.toLong())
                            }
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "تشغيل/إيقاف",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (isSaving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "جاري قص وقص الفيديو بدون إعادة ترميز...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Trimming Range Controls Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C24))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time Range Indicators Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(text = "البداية (Start)", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = formatTimeMs(startMs.toLong()),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "مدة المقطع (Duration)", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = formatTimeMs((endMs - startMs).toLong()),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "النهاية (End)", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = formatTimeMs(endMs.toLong()),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // RangeSlider for selecting Start and End
                    RangeSlider(
                        value = startMs..endMs,
                        onValueChange = { range ->
                            startMs = range.start
                            endMs = range.endInclusive
                            exoPlayer.seekTo(range.start.toLong())
                        },
                        valueRange = 0f..totalDuration,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Seek to start button & Seek to end button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                exoPlayer.seekTo(startMs.toLong())
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("معاينة البداية", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                exoPlayer.seekTo((endMs - 1500f).coerceAtLeast(startMs).toLong())
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("معاينة النهاية", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Output File Name Dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showNameDialog = false },
            title = {
                Text(
                    text = "حفظ الفيديو المقصوص",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "أدخل اسم الملف الجديد للفيديو المقصوص:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("اسم الملف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNameDialog = false
                        isSaving = true
                        exoPlayer.pause()
                        scope.launch {
                            val result = VideoProcessor.trimVideo(
                                context = context,
                                inputVideoPath = video.path,
                                outputFileName = newFileName.trim(),
                                startMs = startMs.toLong(),
                                endMs = endMs.toLong()
                            )
                            isSaving = false
                            if (result != null) {
                                Toast.makeText(context, "تم حفظ الفيديو المقصوص بنجاح", Toast.LENGTH_LONG).show()
                                viewModel.launchIncrementalScan()
                                onTrimFinished()
                            } else {
                                Toast.makeText(context, "فشل في قص الفيديو، يرجى المحاولة لاحقاً", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = newFileName.isNotBlank()
                ) {
                    Text("حفظ الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("إلغاء")
                }
            },
            containerColor = Color(0xFF22222A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
