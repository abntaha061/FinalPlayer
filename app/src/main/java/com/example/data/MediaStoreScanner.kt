// File: /app/src/main/java/com/example/data/MediaStoreScanner.kt
package com.example.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreScanner {
    suspend fun scanVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val videoItems = mutableListOf<VideoItem>()
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        // Query external storage for videos sorted by modification date descending
        val contentResolver = context.contentResolver
        val cursor = try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )
        } catch (e: Exception) {
            null
        }
        
        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = c.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            
            while (c.moveToNext()) {
                val id = if (idCol != -1) c.getLong(idCol) else 0L
                val title = if (nameCol != -1) c.getString(nameCol) ?: "Unknown" else "Unknown"
                val path = if (dataCol != -1) c.getString(dataCol) ?: "" else ""
                val duration = if (durationCol != -1) c.getLong(durationCol) else 0L
                val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                val dateAdded = if (dateCol != -1) c.getLong(dateCol) else 0L
                
                if (path.isNotEmpty() && File(path).exists()) {
                    val subtitles = scanSubtitlesForVideo(path)
                    videoItems.add(
                        VideoItem(
                            id = id,
                            title = title,
                            path = path,
                            duration = duration,
                            size = size,
                            dateAdded = dateAdded,
                            subtitles = subtitles
                        )
                    )
                }
            }
        }
        
        videoItems
    }
    
    private fun scanSubtitlesForVideo(videoPath: String): List<SubtitleInfo> {
        val videoFile = File(videoPath)
        val videoNameNoExt = videoFile.nameWithoutExtension.lowercase()
        val parentFolder = videoFile.parentFile
        val subtitleExtensions = setOf("srt", "ass", "ssa", "sub")
        val matchingSubtitles = mutableListOf<SubtitleInfo>()
        
        if (parentFolder != null && parentFolder.exists() && parentFolder.isDirectory) {
            val files = parentFolder.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (ext in subtitleExtensions) {
                            val fileName = file.name.lowercase()
                            // Match subtitles associated with this specific video file
                            if (fileName.contains(videoNameNoExt)) {
                                val language: String
                                val displayTag: String
                                
                                when {
                                    fileName.contains("ar-ar") -> {
                                        language = "AR"
                                        displayTag = "AR-AR"
                                    }
                                    fileName.contains("ar-en") -> {
                                        language = "AR"
                                        displayTag = "AR-EN"
                                    }
                                    fileName.contains("ar") || fileName.contains("arabic") -> {
                                        language = "AR"
                                        displayTag = "AR"
                                    }
                                    fileName.contains("en") || fileName.contains("english") -> {
                                        language = "EN"
                                        displayTag = "EN"
                                    }
                                    fileName.contains("orig") || fileName.contains("original") -> {
                                        language = "ORIG"
                                        displayTag = "ORIG"
                                    }
                                    else -> {
                                        language = "ORIG"
                                        displayTag = "ORIG"
                                    }
                                }
                                
                                matchingSubtitles.add(
                                    SubtitleInfo(
                                        path = file.absolutePath,
                                        language = language,
                                        displayTag = displayTag
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return matchingSubtitles
    }
}
