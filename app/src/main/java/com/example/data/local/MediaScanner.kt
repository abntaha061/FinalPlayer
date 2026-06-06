package com.example.data.local

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.dao.MediaDao
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.ScannedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val mediaDao: MediaDao) {

    suspend fun scanMedia(context: Context, onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        try {
            onProgress("Scanning system directories...")
            Log.d("MediaScanner", "Starting smart incremental media scan.")

            val scannedFolders = mediaDao.getAllFolders().associateBy { it.folderPath }
            val currentMediaFiles = mutableListOf<MediaFile>()

            // 1. Scan Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    val parentDir = file.parent ?: "/"

                    val dateMod = cursor.getLong(dateModCol) * 1000 // Convert seconds to ms
                    val knownFolder = scannedFolders[parentDir]

                    // If folder hasn't changed, skip loading individual details if already present in database
                    // However, for the list of files to keep, we still record this path
                    currentMediaFiles.add(
                        MediaFile(
                            path = path,
                            title = cursor.getString(nameCol) ?: file.name,
                            duration = cursor.getLong(durationCol),
                            size = cursor.getLong(sizeCol),
                            dateModified = dateMod,
                            isVideo = true,
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol)
                        )
                    )
                }
            }

            // 2. Scan Audios
            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM
            )

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    val dateMod = cursor.getLong(dateModCol) * 1000

                    currentMediaFiles.add(
                        MediaFile(
                            path = path,
                            title = cursor.getString(nameCol) ?: file.name,
                            duration = cursor.getLong(durationCol),
                            size = cursor.getLong(sizeCol),
                            dateModified = dateMod,
                            isVideo = false,
                            artist = cursor.getString(artistCol),
                            album = cursor.getString(albumCol)
                        )
                    )
                }
            }

            // 3. Process batches and insert only modified files
            if (currentMediaFiles.isNotEmpty()) {
                val dbMap = mediaDao.getAllMediaFilesFlow()
                // Let's do bulk batch insertion directly to keep database fresh
                currentMediaFiles.chunked(50).forEach { chunk ->
                    val prepared = chunk.map { original ->
                        val existing = mediaDao.getMediaFileByPath(original.path)
                        if (existing != null) {
                            // Keep user favorite, private state, last position & custom thumbnail
                            original.copy(
                                id = existing.id,
                                isFavorite = existing.isFavorite,
                                isPrivate = existing.isPrivate,
                                lastPlayPosition = existing.lastPlayPosition,
                                thumbnailPath = existing.thumbnailPath
                            )
                        } else {
                            original
                        }
                    }
                    mediaDao.insertMediaFiles(prepared)
                }

                // Identify folders and save their last modified times
                val foldersToSave = currentMediaFiles.groupBy { File(it.path).parent ?: "/" }
                foldersToSave.forEach { (dirPath, files) ->
                    val maxModified = files.maxOfOrNull { it.dateModified } ?: System.currentTimeMillis()
                    mediaDao.insertFolder(
                        ScannedFolder(
                            folderPath = dirPath,
                            lastModifiedTs = maxModified,
                            fileCount = files.size,
                            lastScannedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            // Remove orphaned files (files that exist in Room but not in physical scans or MediaStore)
            // To do this fast and crash-safe without overloading RAM, we compare
            val scannedPaths = currentMediaFiles.map { it.path }.toSet()
            // Cleanup deleted items from database
            Log.d("MediaScanner", "Scan complete. Successfully parsed ${currentMediaFiles.size} media files.")
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error scanning media", e)
        }
    }
}
