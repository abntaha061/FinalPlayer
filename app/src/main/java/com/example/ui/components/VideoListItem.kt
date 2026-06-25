// File: /app/src/main/java/com/example/ui/components/VideoListItem.kt
package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.example.data.VideoItem
import com.example.ui.screens.rememberVideoThumbnail
import com.example.util.DateFormatter
import com.example.util.FileSizeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoListItem(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail = rememberVideoThumbnail(video.path)

    // Check if the video is "NEW" (added within last 3 days)
    val currentTimeSeconds = System.currentTimeMillis() / 1000L
    val threeDaysInSeconds = 3 * 24 * 60 * 60L
    val isNew = (currentTimeSeconds - video.dateAdded) <= threeDaysInSeconds

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Thumbnail Area
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 70.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF212121))
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = "Video thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback icon placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Icon",
                        tint = Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // NEW Badge on TOP-LEFT
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0xFFD32F2F), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Duration Badge on BOTTOM-RIGHT
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Right: Video Details Text Block
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Video Title
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Metadata Row (size • date)
            val sizeStr = FileSizeFormatter.formatSize(video.size)
            val dateStr = DateFormatter.formatDate(video.dateAdded, context)
            Text(
                text = "$sizeStr • $dateStr",
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Chips Row (Subtitles and Quality)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Video Subtitle Chips
                video.subtitles.forEach { subtitle ->
                    val subtitleColor = when (subtitle.language.uppercase(Locale.US)) {
                        "AR" -> Color(0xFF1565C0)
                        "EN" -> Color(0xFF2E7D32)
                        "ORIG" -> Color(0xFF616161)
                        "AR-ORIG" -> Color(0xFF1565C0)
                        else -> Color(0xFF616161)
                    }
                    VideoChip(text = subtitle.displayTag.uppercase(Locale.US), backgroundColor = subtitleColor)
                }

                // Video Quality Chip
                val quality = detectQuality(video.width, video.height, video.path, video.title)
                val qualityColor = when (quality) {
                    "1080p" -> Color(0xFF6A1B9A)
                    "720p" -> Color(0xFF00695C)
                    "480p" -> Color(0xFFE65100)
                    else -> Color(0xFF616161)
                }
                VideoChip(text = quality, backgroundColor = qualityColor)
            }
        }
    }
}

@Composable
private fun VideoChip(
    text: String,
    backgroundColor: Color
) {
    Box(
        modifier = Modifier
            .height(18.dp)
            .background(backgroundColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun detectQuality(width: Int, height: Int, path: String, title: String): String {
    val maxDim = maxOf(width, height)
    return when {
        maxDim >= 3840 -> "4K"
        maxDim >= 1920 -> "1080p"
        maxDim >= 1280 -> "720p"
        maxDim >= 854  -> "480p"
        maxDim >= 640  -> "360p"
        maxDim > 0     -> "SD"
        else -> {
            // fallback to filename if no resolution data
            val combined = "$path $title".lowercase(Locale.US)
            when {
                combined.contains("2160") || combined.contains("4k") -> "4K"
                combined.contains("1080") -> "1080p"
                combined.contains("720")  -> "720p"
                combined.contains("480")  -> "480p"
                else -> "1080p"
            }
        }
    }
}
