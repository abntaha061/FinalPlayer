package com.example.data.local

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.dao.MediaDao
import com.example.data.local.entities.MediaFile
import com.example.data.local.entities.ScannedFolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

class MediaScanner(private val mediaDao: MediaDao) {

    // highly robust MediaStore complete sync
    suspend fun scanMedia(context: Context, onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        Log.d("MediaScanner", "Initiating highly robust MediaStore complete sync.")
        onProgress("جاري فحص ملفات الوسائط على جهازك 🔍...")

        try {
            // 1. Fetch current items from MediaStore
            val mediaStoreFiles = queryAllMediaStoreItems(context)
            Log.d("MediaScanner", "Found ${mediaStoreFiles.size} items in MediaStore.")

            // 2. Fetch all existing paths from Room Database
            val existingFiles = mediaDao.getAllMediaFilesFlow().first()
            val existingMapByPath = existingFiles.associateBy { it.path }

            // 3. Diff: Identify additions/updates and deletions
            val toInsertOrUpdate = mutableListOf<MediaFile>()
            val scannedPathsSet = mediaStoreFiles.map { it.path }.toSet()

            for (mediaStoreFile in mediaStoreFiles) {
                val existing = existingMapByPath[mediaStoreFile.path]
                if (existing == null) {
                    toInsertOrUpdate.add(mediaStoreFile)
                } else if (existing.dateModified != mediaStoreFile.dateModified) {
                    // File modified, preserve favorite, private, etc.
                    toInsertOrUpdate.add(mediaStoreFile.copy(
                        id = existing.id,
                        isFavorite = existing.isFavorite,
                        isPrivate = existing.isPrivate,
                        lastPlayPosition = existing.lastPlayPosition,
                        thumbnailPath = existing.thumbnailPath
                    ))
                }
            }

            // Identify orphaned items in our database that are no longer in MediaStore
            val toDeletePaths = existingFiles.filter { dbFile ->
                // Only clean up local paths, don't delete external online playlist assets
                !dbFile.path.startsWith("http") && !scannedPathsSet.contains(dbFile.path)
            }.map { it.path }

            // 4. Perform database operations in batches
            if (toDeletePaths.isNotEmpty()) {
                Log.d("MediaScanner", "Deleting ${toDeletePaths.size} orphaned local files.")
                mediaDao.deleteMediaFilesByPaths(toDeletePaths)
            }

            if (toInsertOrUpdate.isNotEmpty()) {
                Log.d("MediaScanner", "Inserting/Updating ${toInsertOrUpdate.size} files.")
                insertBatchChunked(toInsertOrUpdate)
                onProgress("تم مزامنة ${toInsertOrUpdate.size} ملفات جديدة ✅")
            } else {
                onProgress("")
            }

            // 5. Update folder statistics based on what is currently in Room Database
            refreshFoldersFromDb()

            return@withContext toInsertOrUpdate.size
        } catch (e: Exception) {
            Log.e("MediaScanner", "Full sync failure", e)
            onProgress("فشل في مسح الملفات")
            return@withContext 0
        }
    }

    private suspend fun refreshFoldersFromDb() {
        try {
            // Recalculate directories according to the media files currently stored
            val currentMedia = mediaDao.getAllMediaFilesFlow().first().filter { !it.path.startsWith("http") }
            val foldersGrouped = currentMedia.groupBy { File(it.path).parent ?: "/" }
            
            // Clear existing folder mappings and replace with real, scanned ones
            mediaDao.clearFolders()
            
            for ((dirPath, files) in foldersGrouped) {
                val maxModified = files.maxOfOrNull { it.dateModified } ?: System.currentTimeMillis()
                mediaDao.insertFolder(
                    ScannedFolder(
                        folderPath = dirPath,
                        lastModifiedTs = maxModified,
                        fileCount = files.count { !it.isPrivate },
                        lastScannedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Failed to refresh folders structure", e)
        }
    }

    private suspend fun queryAllMediaStoreItems(context: Context): List<MediaFile> = withContext(Dispatchers.IO) {
        val foundFiles = mutableListOf<MediaFile>()

        // Query Videos
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

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null, null, null
            )?.use { cursor ->
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
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    foundFiles.add(
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
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying MediaStore videos", e)
        }

        // Query Audios
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

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null, null, null
            )?.use { cursor ->
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
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    foundFiles.add(
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
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying MediaStore audios", e)
        }

        foundFiles
    }

    private suspend fun insertBatchChunked(mediaList: List<MediaFile>) = supervisorScope {
        if (mediaList.isEmpty()) return@supervisorScope
        
        mediaList.chunked(50).forEach { chunk ->
            try {
                mediaDao.insertMediaFiles(chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MediaScanner", "Error storing chunk in database", e)
            }
            delay(10)
        }
    }
}
