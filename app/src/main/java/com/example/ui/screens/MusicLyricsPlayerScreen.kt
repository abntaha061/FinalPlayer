package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.local.entities.MediaFile
import com.example.ui.MediaViewModel
import com.example.ui.components.TrackArtwork
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
    val duration by viewModel.audioDuration.collectAsState()

    val track = currentTrack ?: return

    BackHandler {
        onBack()
    }

    val baseColors = remember(track.id) { getAuroraColors(track) }
    var colors by remember(track.id) { mutableStateOf(baseColors) }
    val lyrics = remember(track.id) { LyricsProvider.getLyricsForTrack(track.title, track.artist, track.path) }

    var albumArtBitmap by remember(track.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(track.path) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            var bitmapRes: android.graphics.Bitmap? = null
            var colorsRes: AuroraColors? = null
            try {
                retriever.setDataSource(track.path)
                val art = retriever.embeddedPicture
                if (art != null) {
                    val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                    bitmapRes = bitmap
                    colorsRes = extractAuroraColorsFromBitmap(bitmap)
                }
            } catch (e: Exception) {
                // error
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }
            bitmapRes to colorsRes
        }

        albumArtBitmap = result.first
        colors = result.second ?: baseColors
    }

    // Inactivity fade out for player control system
    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastInteractionTime) {
        delay(7000L)
        areControlsVisible = false
    }

    // Adaptive orientation check
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("aurora_music_player_screen")
            .pointerInput(Unit) {
                detectTapGestures {
                    areControlsVisible = !areControlsVisible
                    if (areControlsVisible) {
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        // 1. Aurora Background layer
        AuroraBackground(colors = colors, albumArtBitmap = albumArtBitmap)

        // Dim dark filter
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // Overlay layout: list stretches full screen, controls overlay on top
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Highly visible synchronized lyrics taking up the main screen space
            SynchronizedLyricsList(
                lyrics = lyrics,
                progressFlow = viewModel.audioProgress,
                onLineClicked = { viewModel.seekAudioTo(it.timeMs) },
                onUserInteraction = {
                    areControlsVisible = true
                    lastInteractionTime = System.currentTimeMillis()
                },
                onTapBackground = {
                    areControlsVisible = !areControlsVisible
                    if (areControlsVisible) {
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            )

            // Header Bar wrapped in animated visibility to match lower panel/seekbar
            AnimatedVisibility(
                visible = areControlsVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { -it / 3 }),
                exit = fadeOut(animationSpec = tween(500)) + slideOutVertically(targetOffsetY = { -it / 3 })
            ) {
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
            }

            // Animated visibility for secondary controls group (seekbar & bottom players panel) - PureSonic Style!
            AnimatedVisibility(
                visible = areControlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { it / 3 }),
                exit = fadeOut(animationSpec = tween(500)) + slideOutVertically(targetOffsetY = { it / 3 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Removed Song Metadata (Title & Artist) per user request to hide them from the mini player/bottom control center

                    Spacer(modifier = Modifier.height(8.dp))

                    // Isolated Audio progress bar for 0-recompositions performance optimization
                    AudioProgressBar(
                        progressFlow = viewModel.audioProgress,
                        durationState = viewModel.audioDuration.collectAsState(),
                        onSeek = { target -> viewModel.seekAudioTo(target) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. PureSonic Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Song Button
                        IconButton(
                            onClick = { viewModel.playPreviousAudio() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "السابق (Previous)",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play/Pause Big White Circle
                        IconButton(
                            onClick = { viewModel.toggleAudioPlayPause() },
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "تشغيل الكتم",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Next Song Button
                        IconButton(
                            onClick = { viewModel.playNextAudio() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "التالي (Next)",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
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
    progressFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onLineClicked: (LyricLine) -> Unit,
    onUserInteraction: () -> Unit,
    onTapBackground: () -> Unit
) {
    val progress by progressFlow.collectAsState()
    val activeIndex = remember(progress, lyrics) {
        val idx = lyrics.indexOfLast { progress >= it.timeMs }
        if (idx == -1) 0 else idx
    }
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val targetYOffsetPx = remember(containerHeightPx) { containerHeightPx * 0.33f } // Upper third (33% of the screen height)
        val scrollOffsetPx = remember(targetYOffsetPx) { -targetYOffsetPx }

        // Smooth scroll to align active lyric line perfectly at 33% height of list viewports
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0 && activeIndex < lyrics.size) {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = scrollOffsetPx.toInt()
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        onTapBackground()
                    }
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = maxHeight * 0.33f, bottom = maxHeight * 0.67f + 120.dp)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUserInteraction()
                            onLineClicked(line)
                        }
                        .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = line.text,
                        color = if (isActive) Color.White else Color.LightGray.copy(alpha = 0.8f),
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = if (isActive) 22.sp else 14.sp,
                        textAlign = TextAlign.Right, // RTL reading flow for Arabic lyrics
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                }
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
fun TrackAlbumArtCard(
    albumArtBitmap: android.graphics.Bitmap?,
    colors: AuroraColors,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(albumArtBitmap) { albumArtBitmap?.asImageBitmap() }

    Box(
        modifier = modifier
            .size(240.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Track Album Art",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(colors.c1, colors.c2, colors.c3, colors.c4)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music icon",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
fun AuroraBackground(
    colors: AuroraColors,
    albumArtBitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    // Smoothly animate each aurora color to create beautiful transition morphs when the track changes
    val animatedC1 by animateColorAsState(targetValue = colors.c1, animationSpec = tween(2000), label = "animated_c1")
    val animatedC2 by animateColorAsState(targetValue = colors.c2, animationSpec = tween(2000), label = "animated_c2")
    val animatedC3 by animateColorAsState(targetValue = colors.c3, animationSpec = tween(2000), label = "animated_c3")
    val animatedC4 by animateColorAsState(targetValue = colors.c4, animationSpec = tween(2000), label = "animated_c4")

    // Optimize: Instead of rendering heavy canvas with continuous floating coordinates and costly .blur(80.dp)
    // which drains battery and heats up the device by redrawing 60 frames per second on screen,
    // we use a beautifully blended gradient brush directly. It achieves a gorgeous, vibrant look in hardware rendering.
    val meshBrush = remember(animatedC1, animatedC2, animatedC3, animatedC4) {
        val dark1 = animatedC1.copy(red = animatedC1.red * 0.08f, green = animatedC1.green * 0.08f, blue = animatedC1.blue * 0.08f, alpha = 0.45f)
        val dark2 = animatedC2.copy(red = animatedC2.red * 0.06f, green = animatedC2.green * 0.06f, blue = animatedC2.blue * 0.06f, alpha = 0.40f)
        val dark3 = animatedC3.copy(red = animatedC3.red * 0.07f, green = animatedC3.green * 0.07f, blue = animatedC3.blue * 0.07f, alpha = 0.38f)
        val dark4 = animatedC4.copy(red = animatedC4.red * 0.07f, green = animatedC4.green * 0.07f, blue = animatedC4.blue * 0.07f, alpha = 0.42f)
        Brush.linearGradient(
            colors = listOf(dark1, dark2, dark3, dark4)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(meshBrush)
    ) {
        // Dynamic Blurred Album Art Background
        if (albumArtBitmap != null) {
            val imgBitmap = remember(albumArtBitmap) { albumArtBitmap.asImageBitmap() }
            Image(
                bitmap = imgBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .graphicsLayer { alpha = 0.28f },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun AudioProgressBar(
    progressFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    durationState: State<Long>,
    onSeek: (Long) -> Unit
) {
    val progress by progressFlow.collectAsState()
    val duration = durationState.value
    
    val progressSec = progress / 1000
    val durationSec = duration / 1000
    val progressStr = formatTimeToArabicIndic(progressSec)
    val durationStr = formatTimeToArabicIndic(durationSec)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = if (duration > 0) progress.toFloat() / duration else 0f,
            onValueChange = { onSeek((it * duration).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("lyrics_player_seek")
        )

        Spacer(modifier = Modifier.width(12.dp))

        // PureSonic iconic visual signature: separator lines + capsule split
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .background(Color.White)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .width(42.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White)
        )
    }

    // Time labels Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = progressStr,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = durationStr,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private val SineBehaviorEasing = Easing { fraction ->
    val sinVal = kotlin.math.sin(fraction * java.lang.Math.PI - java.lang.Math.PI / 2)
    ((sinVal + 1) / 2).toFloat()
}

data class AuroraColors(
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val c4: Color
)

fun getAuroraColors(track: MediaFile): AuroraColors {
    val title = track.title.lowercase()
    val artist = (track.artist ?: "").lowercase()
    return when {
        title.contains("أعطني") || title.contains("fairuz") || title.contains("ناي") || artist.contains("فيروز") -> {
            AuroraColors(
                c1 = Color(0xFF034F6A),
                c2 = Color(0xFF14C39C),
                c3 = Color(0xFF0F7A7D),
                c4 = Color(0xFF3CDC9F)
            )
        }
        title.contains("ألقاك") || title.contains("kulthum") || title.contains("غداً") || artist.contains("كلثوم") -> {
            AuroraColors(
                c1 = Color(0xFF7A071E),
                c2 = Color(0xFFDF9E21),
                c3 = Color(0xFFB52B3C),
                c4 = Color(0xFFFFD166)
            )
        }
        title.contains("فنجان") || title.contains("abdel") || title.contains("حليم") || artist.contains("حليم") -> {
            AuroraColors(
                c1 = Color(0xFF331B6A),
                c2 = Color(0xFF8B2EFF),
                c3 = Color(0xFF4A0072),
                c4 = Color(0xFFB388FF)
            )
        }
        else -> {
            val hash = (track.title + (track.artist ?: "")).hashCode()
            val hue1 = (hash.absoluteValue % 360).toFloat()
            val hue2 = (hue1 + 75f) % 360
            val hue3 = (hue1 + 150f) % 360
            val hue4 = (hue1 + 225f) % 360
            AuroraColors(
                c1 = Color.hsv(hue1, 0.70f, 0.35f),
                c2 = Color.hsv(hue2, 0.85f, 0.45f),
                c3 = Color.hsv(hue3, 0.65f, 0.40f),
                c4 = Color.hsv(hue4, 0.75f, 0.50f)
            )
        }
    }
}

fun extractAuroraColorsFromBitmap(bitmap: android.graphics.Bitmap): AuroraColors? {
    try {
        // Downscale bitmap to 24x24 for fast and lightweight pixel analysis
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 24, 24, false)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        
        val hsvList = ArrayList<Triple<Int, FloatArray, Float>>()
        
        for (color in pixels) {
            val alpha = (color shr 24) and 0xff
            if (alpha < 200) continue
            
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff
            
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val diff = max - min
            
            if (max < 30 || min > 225 || diff < 15) {
                continue
            }
            
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val weight = hsv[1] * hsv[2]
            hsvList.add(Triple(color, hsv, weight))
        }
        
        if (hsvList.size < 10) {
            hsvList.clear()
            for (color in pixels) {
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                hsvList.add(Triple(color, hsv, 1f))
            }
        }
        
        val buckets = Array(6) { ArrayList<Triple<Int, FloatArray, Float>>() }
        for (item in hsvList) {
            val hue = item.second[0]
            val bucketIdx = (hue / 60.0f).toInt().coerceIn(0, 5)
            buckets[bucketIdx].add(item)
        }
        
        val activeBuckets = buckets
            .mapIndexed { index, list -> index to list.sumOf { it.third.toDouble() } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            
        val extractedColors = ArrayList<Color>()
        
        for (bucketInfo in activeBuckets) {
            val bucketIdx = bucketInfo.first
            val bestColorItem = buckets[bucketIdx].maxByOrNull { it.third }
            if (bestColorItem != null) {
                extractedColors.add(Color(bestColorItem.first))
            }
            if (extractedColors.size >= 4) break
        }
        
        if (scaled != bitmap) {
            scaled.recycle()
        }
        
        if (extractedColors.size >= 2) {
            val main1 = extractedColors[0]
            val main2 = extractedColors[1]
            
            val hsv1 = FloatArray(3)
            android.graphics.Color.RGBToHSV((main1.red * 255).toInt(), (main1.green * 255).toInt(), (main1.blue * 255).toInt(), hsv1)
            val hsv2 = FloatArray(3)
            android.graphics.Color.RGBToHSV((main2.red * 255).toInt(), (main2.green * 255).toInt(), (main2.blue * 255).toInt(), hsv2)
            
            val averageHue = (hsv1[0] + hsv2[0]) / 2f
            val c3 = Color.hsv(averageHue, maxOf(hsv1[1], hsv2[1]) * 0.9f, maxOf(hsv1[2], hsv2[2]) * 0.8f)
            val c4 = Color.hsv((hsv1[0] + 180f) % 360f, hsv1[1] * 0.4f, maxOf(hsv1[2], hsv2[2]) * 0.3f + 0.15f)
            
            return AuroraColors(
                c1 = main1,
                c2 = main2,
                c3 = c3,
                c4 = c4
            )
        } else if (extractedColors.size == 1) {
            val main1 = extractedColors[0]
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((main1.red * 255).toInt(), (main1.green * 255).toInt(), (main1.blue * 255).toInt(), hsv)
            
            val c1 = main1
            val c2 = Color.hsv((hsv[0] + 60f) % 360f, hsv[1], hsv[2])
            val c3 = Color.hsv((hsv[0] + 120f) % 360f, hsv[1] * 0.9f, hsv[2] * 0.8f)
            val c4 = Color.hsv((hsv[0] + 240f) % 360f, hsv[1] * 0.8f, hsv[2] * 0.6f)
            return AuroraColors(c1, c2, c3, c4)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun formatTimeToArabicIndic(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    val englishStr = "%02d:%02d".format(m, s)
    return englishStr.map { char ->
        when (char) {
            '0' -> '٠'
            '1' -> '١'
            '2' -> '٢'
            '3' -> '٣'
            '4' -> '٤'
            '5' -> '٥'
            '6' -> '٦'
            '7' -> '٧'
            '8' -> '٨'
            '9' -> '٩'
            else -> char
        }
    }.joinToString("")
}
