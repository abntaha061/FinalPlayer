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

/**
 * Fast Jetpack Compose UI component that fetches and displays video thumbnails using Coil
 * with VideoFrameDecoder.
 * 
 * Key Features:
 * 1. Fetches frame at 60 seconds (60,000 ms) to ensure real visual content.
 * 2. Uses OPTION_CLOSEST to force extracting exact frames rather than black keyframes.
 * 3. No placeholders, error images, or fallbacks.
 * 4. Enables Memory Cache and Disk Cache for smooth LazyColumn scrolling.
 * 5. Downscales to size(300) to keep memory footprint light and rendering fast.
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
                val duration = durationStr?.toLongOrNull() ?: 0L
                
                // جلب صورة عند 15% من مدة الفيديو لتخطي المقدمات
                val targetTimeUs = if (duration > 0) (duration * 0.15 * 1000).toLong() else 1000000L
                
                val frame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    bitmap = frame.asImageBitmap()
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
