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
    private val scanMutex = kotlinx.coroutines.sync.Mutex()

    // Comprehensive real-time scan merging MediaStore with direct Filesystem crawling
    suspend fun scanMedia(context: Context, onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) {
            Log.d("MediaScanner", "MediaScanner: scan already in progress, skipping duplicate trigger.")
            return@withContext 0
        }
        try {
            Log.d("MediaScanner", "Initiating highly robust local file and MediaStore complete sync.")
            onProgress("جاري فحص جميع ملفات ومجلدات الوسائط على جهازك 🔍...")
            // 1. Fetch from standard Android MediaStore database
            val mediaStoreFiles = queryAllMediaStoreItems(context)
            Log.d("MediaScanner", "Found ${mediaStoreFiles.size} items in MediaStore.")

            // 2. Comprehensive directly crawler for files that haven't been indexed by the OS yet
            val filesystemFiles = mutableListOf<MediaFile>()
            try {
                val rootStorage = android.os.Environment.getExternalStorageDirectory()
                if (rootStorage != null && rootStorage.exists()) {
                    scanDirectoryFiles(rootStorage, filesystemFiles)
                }
            } catch (e: Exception) {
                Log.e("MediaScanner", "Failed to walk physical filesystem storage", e)
            }
            Log.d("MediaScanner", "Found ${filesystemFiles.size} items via direct filesystem crawl.")

            // 3. Merge results to achieve absolute 100% detection rate. 
            // Filesystem files take precedence as they are fresher, unique by path.
            val allScannedFiles = (mediaStoreFiles + filesystemFiles).associateBy { it.path }.values.toList()
            Log.d("MediaScanner", "Merged absolute unique items for DB sync: ${allScannedFiles.size}")

            if (allScannedFiles.isEmpty()) {
                onProgress("")
                return@withContext 0
            }

            // 4. Fetch existing DB entries to compare differences
            val existingFiles = mediaDao.getAllMediaFilesFlow().first()
            val existingMapByPath = existingFiles.associateBy { it.path }

            val toInsertOrUpdate = mutableListOf<MediaFile>()
            val scannedPathsSet = allScannedFiles.map { it.path }.toSet()

            for (scannedFile in allScannedFiles) {
                val existing = existingMapByPath[scannedFile.path]
                if (existing == null) {
                    toInsertOrUpdate.add(scannedFile)
                } else if (existing.dateModified != scannedFile.dateModified || existing.size != scannedFile.size) {
                    // File modified, preserve user states: favorite, private, playback history progress, thumbnails, isNew
                    toInsertOrUpdate.add(scannedFile.copy(
                        id = existing.id,
                        isFavorite = existing.isFavorite,
                        isPrivate = existing.isPrivate,
                        lastPlayPosition = existing.lastPlayPosition,
                        thumbnailPath = existing.thumbnailPath,
                        isNew = existing.isNew
                    ))
                }
            }

            // Detect and clear cached records of files that have been physically deleted by the user
            val toDeletePaths = existingFiles.filter { dbFile ->
                // Clean up local paths that do not exist physically on storage anymore
                !dbFile.path.startsWith("http") && !scannedPathsSet.contains(dbFile.path)
            }.map { it.path }

            // Apply DB sync transactions
            if (toDeletePaths.isNotEmpty()) {
                Log.d("MediaScanner", "Deleting ${toDeletePaths.size} orphaned local player files.")
                mediaDao.deleteMediaFilesByPaths(toDeletePaths)
            }

            if (toInsertOrUpdate.isNotEmpty()) {
                Log.d("MediaScanner", "Storing/updating ${toInsertOrUpdate.size} scanned items.")
                insertBatchChunked(toInsertOrUpdate)
                onProgress("تم مزامنة ${toInsertOrUpdate.size} ملفات جديدة ✅")
            } else {
                onProgress("")
            }

            // Re-catalog the folders based on the actual synchronized DB states
            refreshFoldersFromDb()

            return@withContext toInsertOrUpdate.size
        } catch (e: Exception) {
            Log.e("MediaScanner", "Storage comprehensive sync failure", e)
            onProgress("فشل في مسح مجلدات التخزين!")
            return@withContext 0
        } finally {
            scanMutex.unlock()
        }
    }

    private fun scanDirectoryFiles(dir: File, foundFiles: MutableList<MediaFile>, visitedDirs: MutableSet<String> = mutableSetOf()) {
        if (!dir.exists() || !dir.isDirectory) return

        // Prevent symlink cycles or repeating folder runs
        val canonicalPath = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
        if (visitedDirs.contains(canonicalPath)) return
        visitedDirs.add(canonicalPath)

        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return

        // Skip folders marked with .nomedia to respect hidden cache / app assets files
        val hasNoMedia = files.any { it.name.equals(".nomedia", ignoreCase = true) }
        if (hasNoMedia) return

        for (file in files) {
            val name = file.name
            if (file.isDirectory) {
                // EXTREMELY CRITICAL: Ignore System paths and app caches to prevent heavy background lags or UI lockouts
                if (name.equals("Android", ignoreCase = true) ||
                    name.startsWith(".") ||
                    name.equals("cache", ignoreCase = true) ||
                    name.equals("temp", ignoreCase = true) ||
                    name.equals("databases", ignoreCase = true)
                ) {
                    continue
                }
                scanDirectoryFiles(file, foundFiles, visitedDirs)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                val path = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                val size = file.length()

                if (size < 1024) continue // ignore small corrupt assets less than 1KB

                if (ext == "mp4" || ext == "mkv" || ext == "webm" || ext == "avi" || ext == "3gp" || ext == "flv" || ext == "ts") {
                    if (shouldExcludeVideoFile(path)) continue
                    val dateModified = file.lastModified()
                    val title = file.nameWithoutExtension

                    var duration = 0L
                    var width = 0
                    var height = 0
                    try {
                        android.media.MediaMetadataRetriever().use { retriever ->
                            retriever.setDataSource(path)
                            duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                            height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        }
                    } catch (e: Exception) {
                        Log.w("MediaScanner", "Error querying metadata for video file: $path", e)
                    }

                    foundFiles.add(
                        MediaFile(
                            path = path,
                            title = title,
                            duration = duration,
                            size = size,
                            dateModified = dateModified,
                            isVideo = true,
                            width = width,
                            height = height
                        )
                    )
                } else if (ext == "mp3" || ext == "wav" || ext == "m4a" || ext == "ogg" || ext == "flac") {
                    val dateModified = file.lastModified()
                    val title = file.nameWithoutExtension

                    if (shouldExcludeAudioFile(path, title)) continue

                    var duration = 0L
                    var artist: String? = null
                    var album: String? = null
                    try {
                        android.media.MediaMetadataRetriever().use { retriever ->
                            retriever.setDataSource(path)
                            duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        }
                    } catch (e: Exception) {
                        Log.w("MediaScanner", "Error querying metadata for audio file: $path", e)
                    }

                    foundFiles.add(
                        MediaFile(
                            path = path,
                            title = title,
                            duration = duration,
                            size = size,
                            dateModified = dateModified,
                            isVideo = false,
                            artist = artist,
                            album = album
                        )
                    )
                }
            }
        }
    }

    private fun shouldExcludeAudioFile(path: String, title: String): Boolean {
        val name = title.lowercase()
        val lowerPath = path.lowercase()
        return name.startsWith("aud-") || 
               name.startsWith("aud_") || 
               (name.startsWith("aud") && name.substring(3).all { it.isDigit() || it == '-' || it == '_' || it.isWhitespace() }) ||
               lowerPath.contains("whatsapp") || 
               (lowerPath.contains("voice") && lowerPath.contains("note")) ||
               lowerPath.contains("recording")
    }

    private fun shouldExcludeVideoFile(path: String): Boolean {
        if (path.contains("SecureVault")) return false // do not filter vault files
        val lowerPath = path.lowercase()
        return lowerPath.contains("_temp_") || 
               lowerPath.contains("temp_") || 
               lowerPath.contains("temp_segments") || 
               lowerPath.contains("temp-segments") ||
               lowerPath.contains(".mp4_temp_") ||
               lowerPath.contains("segment") ||
               lowerPath.contains(".cache") ||
               lowerPath.contains("/cache/") ||
               (lowerPath.contains(".ts") && (lowerPath.contains("seg") || lowerPath.contains("chunk"))) ||
               (lowerPath.contains("idm") && lowerPath.contains("temp"))
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
                    val rawPath = cursor.getString(dataCol) ?: continue
                    if (shouldExcludeVideoFile(rawPath)) continue
                    val file = File(rawPath)
                    val path = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
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
                    val rawPath = cursor.getString(dataCol) ?: continue
                    val file = File(rawPath)
                    val path = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                    val title = cursor.getString(nameCol) ?: file.name
                    if (shouldExcludeAudioFile(path, title)) continue
                    val dateMod = cursor.getLong(dateModCol) * 1000L
                    foundFiles.add(
                        MediaFile(
                            path = path,
                            title = title,
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
