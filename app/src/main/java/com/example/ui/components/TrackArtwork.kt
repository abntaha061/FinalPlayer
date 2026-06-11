package com.example.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artworkCache = android.util.LruCache<String, android.graphics.Bitmap>(150)

@Composable
fun TrackArtwork(
    filePath: String,
    modifier: Modifier = Modifier,
    fallbackColor: Color = MaterialTheme.colorScheme.primary
) {
    var bitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(artworkCache.get(filePath)) }

    LaunchedEffect(filePath) {
        if (bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                // Safely read track album art using FileInputStream to be compatible across APIs
                val file = java.io.File(filePath)
                if (file.exists()) {
                    java.io.FileInputStream(file).use { fis ->
                        retriever.setDataSource(fis.fd)
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = 2 // Downgrade size to 1/2 for thumbnail level to save heap and memory footprint
                            }
                            val decoded = BitmapFactory.decodeByteArray(art, 0, art.size, options)
                            if (decoded != null) {
                                artworkCache.put(filePath, decoded)
                                bitmap = decoded
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Track Artwork Cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = "Generic audio icon",
                    tint = fallbackColor,
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }
    }
}
