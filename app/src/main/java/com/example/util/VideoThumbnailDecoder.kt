package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custom Coil Fetcher & Video Frame Decoder that extracts video thumbnails on Dispatchers.IO.
 * It includes a smart solid/black frame detection strategy: if the frame at 0s/1s is mostly black,
 * it attempts to extract a representative frame from 30% into the video duration.
 *
 * All resources (MediaMetadataRetriever) are strictly released in a finally block to prevent memory leaks.
 */
class VideoThumbnailFetcher(
    private val videoFile: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        if (!videoFile.exists()) return@withContext null

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            // 1. Initial attempt: frame at 1s or 0s
            var bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            // 2. Black/Solid Frame Check Strategy:
            // If the initial frame is dark/black, extract from 30% of video duration
            if (bitmap != null && isBitmapMostlyBlack(bitmap)) {
                val targetTimeUs = if (durationMs > 2000L) {
                    (durationMs * 0.30f * 1000f).toLong()
                } else {
                    2_000_000L
                }
                val candidateFrame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (candidateFrame != null && !isBitmapMostlyBlack(candidateFrame)) {
                    bitmap = candidateFrame
                }
            }

            if (bitmap == null) return@withContext null

            // 3. Downscale bitmap to keep memory footprint low
            val scaledBitmap = downscaleBitmap(bitmap, maxDimension = 320)

            val drawable = BitmapDrawable(options.context.resources, scaledBitmap)

            DrawableResult(
                drawable = drawable,
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                // Suppress release exception on older API levels
            }
        }
    }

    /**
     * Measures average luminance across sample points on a grid.
     * Returns true if luminance < 15f (indicating a black or dark frame).
     */
    private fun isBitmapMostlyBlack(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true

        var totalLuminance = 0f
        var samples = 0
        val stepX = (width / 8).coerceAtLeast(1)
        val stepY = (height / 8).coerceAtLeast(1)

        for (x in stepX until width step stepX) {
            for (y in stepY until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                totalLuminance += luminance
                samples++
            }
        }

        if (samples == 0) return true
        val avgLuminance = totalLuminance / samples
        return avgLuminance < 15f
    }

    private fun downscaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            val extension = data.extension.lowercase()
            val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv", "m4v")
            if (extension in videoExtensions) {
                return VideoThumbnailFetcher(data, options)
            }
            return null
        }
    }
}
