package com.example.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun ImageRequest.Builder.videoFrameMillis(frameMillis: Long): ImageRequest.Builder {
    return setParameter(VideoFrameDecoder.VIDEO_FRAME_MICROS_KEY, frameMillis * 1000L)
}

fun ImageRequest.Builder.videoFrameOption(option: Int): ImageRequest.Builder {
    return setParameter(VideoFrameDecoder.VIDEO_FRAME_OPTION_KEY, option)
}

fun isBitmapBlack(bitmap: Bitmap): Boolean {
    return try {
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }
        val w = softwareBitmap.width
        val h = softwareBitmap.height
        if (w <= 0 || h <= 0) return true
        
        var totalLuminance = 0L
        var samples = 0
        val stepX = (w / 7).coerceAtLeast(1)
        val stepY = (h / 7).coerceAtLeast(1)
        
        for (x in stepX until w step stepX) {
            for (y in stepY until h step stepY) {
                val pixel = softwareBitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                totalLuminance += lum
                samples++
            }
        }
        if (samples == 0) return true
        val avgLuminance = totalLuminance / samples
        avgLuminance < 15
    } catch (e: Exception) {
        false
    }
}

/**
 * Fast Jetpack Compose UI component that fetches and displays video thumbnails using
 * MediaMetadataRetriever with smart black-frame detection and candidate timestamps.
 */
@Composable
fun SmartVideoThumbnail(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(videoUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(videoUri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                
                val candidateTimesUs = mutableListOf<Long>()
                if (durationMs > 0) {
                    candidateTimesUs.add((durationMs * 0.15 * 1000).toLong())
                    candidateTimesUs.add((durationMs * 0.05 * 1000).toLong())
                    candidateTimesUs.add((durationMs * 0.30 * 1000).toLong())
                    candidateTimesUs.add((durationMs * 0.50 * 1000).toLong())
                    candidateTimesUs.add(15_000_000L)
                    candidateTimesUs.add(5_000_000L)
                    candidateTimesUs.add(1_000_000L)
                    candidateTimesUs.add(0L)
                } else {
                    candidateTimesUs.addAll(listOf(15_000_000L, 5_000_000L, 30_000_000L, 1_000_000L, 0L))
                }

                var firstFrameBackup: Bitmap? = null
                var selectedFrame: Bitmap? = null

                for (timeUs in candidateTimesUs) {
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame != null) {
                        if (firstFrameBackup == null) {
                            firstFrameBackup = frame
                        }
                        if (!isBitmapBlack(frame)) {
                            selectedFrame = frame
                            break
                        }
                    }
                }

                val finalFrame = selectedFrame ?: firstFrameBackup
                if (finalFrame != null) {
                    bitmap = finalFrame.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors on older API levels
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "Video Thumbnail",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // يعرض مساحة فارغة في حالة التحميل أو الفشل (بدون Placeholders)
        Box(modifier = modifier)
    }
}

/**
 * Overload for video file path string.
 */
@Composable
fun SmartVideoThumbnail(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    SmartVideoThumbnail(
        videoUri = Uri.fromFile(File(videoPath)),
        modifier = modifier
    )
}

/**
 * Legacy FastVideoThumbnail delegated to SmartVideoThumbnail for backward compatibility.
 */
@Composable
fun FastVideoThumbnail(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    SmartVideoThumbnail(videoUri = videoUri, modifier = modifier)
}

/**
 * Overload for video file path string.
 */
@Composable
fun FastVideoThumbnail(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    SmartVideoThumbnail(videoPath = videoPath, modifier = modifier)
}

/**
 * Fetches pre-generated native Android system thumbnails from MediaStore.
 */
suspend fun getSystemVideoThumbnail(context: Context, videoId: Long): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            if (videoId <= 0) return@withContext null
            
            val videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoId
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    videoUri,
                    Size(512, 512),
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    videoId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
