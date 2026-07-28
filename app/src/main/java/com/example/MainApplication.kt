package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.util.VideoThumbnailFetcher

/**
 * Main Application class that configures Coil's global ImageLoader instance.
 * Registers custom VideoThumbnailFetcher with DiskCache (100MB) and MemoryCache (25% RAM).
 */
class MainApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoThumbnailFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Allocate 25% of RAM for bitmap caching
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("video_thumbnails_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB disk cache limit
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
