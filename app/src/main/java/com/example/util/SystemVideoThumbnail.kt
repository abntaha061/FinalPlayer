package com.example.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * Fast Jetpack Compose UI component that fetches and displays video thumbnails using Coil
 * with VideoFrameDecoder.
 * 
 * Key Features:
 * 1. Fetches frame at 15 seconds (15,000 ms) to skip black/blank intros.
 * 2. No placeholders, error images, or fallbacks (displays pure image when ready).
 * 3. Enables Memory Cache and Disk Cache for smooth LazyColumn scrolling.
 * 4. Downscales to size(300) to keep memory footprint light and rendering fast.
 */
@Composable
fun FastVideoThumbnail(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(videoUri)
            .decoderFactory(VideoFrameDecoder.Factory())
            // Fetch frame at 15 seconds to skip black introductory screens
            .videoFrameMillis(15000L)
            .crossfade(true)
            .size(300) // Downscale target size for fast list rendering
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = "Video Thumbnail",
        modifier = modifier,
        contentScale = ContentScale.Crop
        // No placeholders or error images as requested
    )
}

/**
 * Overload for video file path string.
 */
@Composable
fun FastVideoThumbnail(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    FastVideoThumbnail(
        videoUri = Uri.fromFile(File(videoPath)),
        modifier = modifier
    )
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
