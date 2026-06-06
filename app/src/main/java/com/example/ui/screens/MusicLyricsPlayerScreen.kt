package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MediaFile
import com.example.ui.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MusicLyricsPlayerScreen(
    viewModel: MediaViewModel,
    onBack: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isAudioPlaying.collectAsState()
    val progress by viewModel.audioProgress.collectAsState()
    val duration by viewModel.audioDuration.collectAsState()

    val track = currentTrack ?: return

    val colors = remember(track.id) { getAuroraColors(track) }
    val lyrics = remember(track.id) { LyricsProvider.getLyricsForTrack(track.title, track.artist, track.path) }

    // Active lyric calculation
    val activeIndex = remember(progress, lyrics) {
        val idx = lyrics.indexOfLast { progress >= it.timeMs }
        if (idx == -1) 0 else idx
    }

    // Adaptive orientation check
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("aurora_music_player_screen")
    ) {
        // 1. Aurora Background layer
        AuroraBackground(colors = colors)

        // Dim dark filter
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        // Column structure
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize Player", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "جاري التشغيل الآن (NOW PLAYING)",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track.album ?: "ألبوم افتراضي (Default Album)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(track) },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite track",
                        tint = if (track.isFavorite) Color.Red else Color.White
                    )
                }
            }

            // Body Area based on Adaptive Orientation
            if (isLandscape) {
                // Wide Landscape Layout: Art on left, right-aligned scroll lyrics on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Artwork and Slider
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RotatingAlbumArt(isPlaying = isPlaying, colors = colors, modifier = Modifier.scale(0.85f))
                        Spacer(modifier = Modifier.height(16.dp))
                        TrackMetaData(title = track.title, artist = track.artist)
                        Spacer(modifier = Modifier.height(8.dp))
                        PlaybackControlsSection(
                            progress = progress,
                            duration = duration,
                            isPlaying = isPlaying,
                            onPlayPause = { viewModel.toggleAudioPlayPause() },
                            onSeek = { viewModel.seekAudioTo(it) }
                        )
                    }

                    // Divider
                    Spacer(modifier = Modifier.width(24.dp))

                    // Right Column: Synchronized Scrolling Lyrics
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "كلمات الأغنية (Lyrics) 📜",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                        SynchronizedLyricsList(
                            lyrics = lyrics,
                            activeIndex = activeIndex,
                            onLineClicked = { viewModel.seekAudioTo(it.timeMs) }
                        )
                    }
                }
            } else {
                // Portrait Layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    RotatingAlbumArt(isPlaying = isPlaying, colors = colors)
                    Spacer(modifier = Modifier.height(24.dp))
                    TrackMetaData(title = track.title, artist = track.artist)

                    // Compact Split for Portrait: Lyrics in bottom half
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "كلمات متزامنة في اليمين (Synchronized Lyrics)",
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Right
                                )
                                Icon(
                                    imageVector = Icons.Default.FormatAlignRight,
                                    contentDescription = "RTL Lyrics",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            SynchronizedLyricsList(
                                lyrics = lyrics,
                                activeIndex = activeIndex,
                                onLineClicked = { viewModel.seekAudioTo(it.timeMs) }
                            )
                        }
                    }

                    // Bottom Control Slider Block (Portrait)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlaybackControlsSection(
                                progress = progress,
                                duration = duration,
                                isPlaying = isPlaying,
                                onPlayPause = { viewModel.toggleAudioPlayPause() },
                                onSeek = { viewModel.seekAudioTo(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackMetaData(title: String, artist: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = artist ?: "فنان غير معروف (Unknown Artist)",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SynchronizedLyricsList(
    lyrics: List<LyricLine>,
    activeIndex: Int,
    onLineClicked: (LyricLine) -> Unit
) {
    val listState = rememberLazyListState()

    // Smooth scroll to align active lyric line in center of list viewports
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.size) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -180 // Subtract pixel offset to sit active item centrally
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 120.dp, top = 20.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.05f else 0.95f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "lyric_scale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1.0f else 0.45f,
                animationSpec = tween(durationMillis = 300),
                label = "lyric_alpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { onLineClicked(line) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd // Right-aligned lyrics per user's prompt!
            ) {
                Text(
                    text = line.text,
                    color = if (isActive) Color.White else Color.LightGray,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = if (isActive) 18.sp else 15.sp,
                    textAlign = TextAlign.Right, // RTL reading flow for Arabic lyrics
                    modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
                )
            }
        }
    }
}

@Composable
fun PlaybackControlsSection(
    progress: Long,
    duration: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    // Math formaters
    val progressSec = progress / 1000
    val durationSec = duration / 1000
    val progressStr = "%02d:%02d".format(progressSec / 60, progressSec % 60)
    val durationStr = "%02d:%02d".format(durationSec / 60, durationSec % 60)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(progressStr, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Slider(
                value = if (duration > 0) progress.toFloat() / duration else 0f,
                onValueChange = { onSeek((it * duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("lyrics_player_seek")
            )
            Text(durationStr, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onSeek((progress - 10000).coerceAtLeast(0)) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(32.dp))

            FloatingActionButton(
                onClick = onPlayPause,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp).testTag("lyrics_player_toggle")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Toggle Audio",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(
                onClick = { onSeek((progress + 10000).coerceAtMost(duration)) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White)
            }
        }
    }
}

@Composable
fun RotatingAlbumArt(
    isPlaying: Boolean,
    colors: Pair<Color, Color>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_transition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotate"
    )

    // Freeze angle if paused to resemble real life CD turntable
    val animatedAngle = if (isPlaying) rotationAngle else 0f

    Box(
        modifier = modifier
            .size(200.dp)
            .graphicsLayer { rotationZ = animatedAngle },
        contentAlignment = Alignment.Center
    ) {
        // Outer Vinyl Frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0C0F), shape = CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            // Grooves drawing
            Box(
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .align(Alignment.Center)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(0.7f)
                    .align(Alignment.Center)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .align(Alignment.Center)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            )
        }

        // Concentric Vinyl Center Album Arts
        Box(
            modifier = Modifier
                .fillMaxSize(0.42f)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(colors.first, colors.second, colors.first)
                    ),
                    shape = CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Spindle hole
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFF050508), shape = CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}

@Composable
fun AuroraBackground(colors: Pair<Color, Color>, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_lights")

    // Slow organic floating movement vectors for the aurora lights
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = SineBehaviorEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_pulse1"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = SineBehaviorEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_pulse2"
    )

    val translationX1 by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = SineBehaviorEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translation_x1"
    )
    val translationY1 by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(8500, easing = SineBehaviorEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translation_y1"
    )

    val translationX2 by infiniteTransition.animateFloat(
        initialValue = 70f,
        targetValue = -70f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = SineBehaviorEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translation_x2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050508)) // Dark cosmos velvet canvas
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().blur(50.dp)) {
            val width = size.width
            val height = size.height

            // Aurora Cloud 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.first.copy(alpha = pulseAlpha1), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(
                        x = width * 0.35f + translationX1.dp.toPx(),
                        y = height * 0.3f + translationY1.dp.toPx()
                    ),
                    radius = width * 1.1f
                ),
                radius = width * 1.1f,
                center = androidx.compose.ui.geometry.Offset(
                    x = width * 0.35f + translationX1.dp.toPx(),
                    y = height * 0.3f + translationY1.dp.toPx()
                )
            )

            // Aurora Cloud 2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.second.copy(alpha = pulseAlpha2), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(
                        x = width * 0.72f + translationX2.dp.toPx(),
                        y = height * 0.65f + translationY1.dp.toPx()
                    ),
                    radius = width * 1.2f
                ),
                radius = width * 1.2f,
                center = androidx.compose.ui.geometry.Offset(
                    x = width * 0.72f + translationX2.dp.toPx(),
                    y = height * 0.65f + translationY1.dp.toPx()
                )
            )
        }
    }
}

private val SineBehaviorEasing = Easing { fraction ->
    val sinVal = kotlin.math.sin(fraction * java.lang.Math.PI - java.lang.Math.PI / 2)
    ((sinVal + 1) / 2).toFloat()
}

fun getAuroraColors(track: MediaFile): Pair<Color, Color> {
    val title = track.title.lowercase()
    val artist = (track.artist ?: "").lowercase()
    return when {
        title.contains("أعطني") || title.contains("fairuz") || title.contains("ناي") || artist.contains("فيروز") -> {
            Pair(Color(0xFF034F6A), Color(0xFF14C39C))
        }
        title.contains("ألقاك") || title.contains("kulthum") || title.contains("غداً") || artist.contains("كلثوم") -> {
            Pair(Color(0xFF7A071E), Color(0xFFDF9E21))
        }
        title.contains("فنجان") || title.contains("abdel") || title.contains("حليم") || artist.contains("حليم") -> {
            Pair(Color(0xFF331B6A), Color(0xFF8B2EFF))
        }
        else -> {
            val hash = (track.title + (track.artist ?: "")).hashCode()
            val hue1 = (hash.absoluteValue % 360).toFloat()
            val hue2 = (hue1 + 130) % 360
            Pair(Color.hsv(hue1, 0.7f, 0.35f), Color.hsv(hue2, 0.8f, 0.65f))
        }
    }
}
