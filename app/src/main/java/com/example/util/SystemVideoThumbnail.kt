package com.example.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches pre-generated native Android system thumbnails from MediaStore.
 * Highly efficient and runs strictly on Dispatchers.IO.
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
                // Android 10+ (API 29+) native fast thumbnail loader
                context.contentResolver.loadThumbnail(
                    videoUri,
                    Size(512, 512),
                    null
                )
            } else {
                // Android 9 and lower legacy thumbnail loader
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

/**
 * Fast Jetpack Compose UI component that asynchronously fetches and displays
 * the Android system thumbnail for local videos without blocking the UI thread.
 */
@Composable
fun FastVideoThumbnail(
    videoId: Long, 
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var thumbnailBitmap by remember(videoId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(videoId) {
        val bitmap = getSystemVideoThumbnail(context, videoId)
        if (bitmap != null) {
            thumbnailBitmap = bitmap.asImageBitmap()
        }
    }

    if (thumbnailBitmap != null) {
        Image(
            bitmap = thumbnailBitmap!!,
            contentDescription = "Video Thumbnail",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(Color.DarkGray)
        )
    }
}
