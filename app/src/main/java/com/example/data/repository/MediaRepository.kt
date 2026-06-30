package com.example.data.repository

import android.content.Context
import com.example.data.local.MediaDatabase
import com.example.data.local.MediaScanner
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import java.io.File

class MediaRepository(private val context: Context) {
    private val database = MediaDatabase.getDatabase(context)
    private val mediaDao = database.mediaDao()
    private val scanner = MediaScanner(mediaDao)

    val videosFlow: Flow<List<MediaFile>> = mediaDao.getVideosFlow()
    val audioFlow: Flow<List<MediaFile>> = mediaDao.getAudioFlow()
    val favoritesFlow: Flow<List<MediaFile>> = mediaDao.getFavoritesFlow()
    val foldersFlow: Flow<List<ScannedFolder>> = mediaDao.getAllFoldersFlow()
    val playlistsFlow: Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylistsFlow()
    val historyFlow: Flow<List<HistoryEntity>> = mediaDao.getHistoryFlow()
    val privateFilesFlow: Flow<List<MediaFile>> = mediaDao.getPrivateFilesFlow()

    suspend fun getMediaByPath(path: String): MediaFile? {
        return mediaDao.getMediaFileByPath(path)
    }

    suspend fun triggerScan(context: Context, onProgress: (String) -> Unit = {}): Int {
        return scanner.scanMedia(context, onProgress) ?: 0
    }

    suspend fun updatePlaybackPosition(path: String, position: Long) {
        mediaDao.updatePlaybackPosition(path, position)
    }

    suspend fun markAsPlayed(path: String) {
        mediaDao.markAsPlayed(path)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        mediaDao.updateFavorite(id, isFavorite)
    }

    private fun getContentUriForPath(context: Context, path: String): android.net.Uri? {
        val file = File(path)
        val volumeName = "external"
        val isAudio = path.lowercase().endsWith(".mp3") || path.lowercase().endsWith(".wav") || path.lowercase().endsWith(".m4a") || path.lowercase().endsWith(".ogg") || path.lowercase().endsWith(".flac")
        val collection = if (isAudio) {
            android.provider.MediaStore.Audio.Media.getContentUri(volumeName)
        } else {
            android.provider.MediaStore.Video.Media.getContentUri(volumeName)
        }
        
        val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
        
        // Strategy 1: Multi-path variant exact matching
        val pathsToTry = mutableListOf(path)
        try {
            val cp = file.canonicalPath
            if (cp != path) pathsToTry.add(cp)
        } catch (e: Exception) {}
        try {
            val ap = file.absolutePath
            if (ap != path && !pathsToTry.contains(ap)) pathsToTry.add(ap)
        } catch (e: Exception) {}

        for (p in pathsToTry) {
            val selection = "${android.provider.MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(p)
            try {
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                        return android.content.ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Strategy 2: Fallback query by display name and file size (highly robust backup)
        if (file.exists()) {
            val selection = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.SIZE} = ?"
            val selectionArgs = arrayOf(file.name, file.length().toString())
            try {
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                        return android.content.ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return null
    }

    private fun copyFileViaContentResolver(context: Context, srcUri: android.net.Uri, destFile: File): Boolean {
        try {
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun deleteFileViaContentResolver(context: Context, path: String): Boolean {
        val uri = getContentUriForPath(context, path) ?: return false
        try {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            return deletedRows > 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun copyFileToExternalStorage(context: Context, srcFile: File, originalPath: String): String? {
        val file = File(originalPath)
        val resolver = context.contentResolver
        val isVideo = originalPath.lowercase().endsWith(".mp4") || originalPath.lowercase().endsWith(".mkv") || originalPath.lowercase().endsWith(".webm") || originalPath.lowercase().endsWith(".avi") || originalPath.lowercase().endsWith(".3gp") || originalPath.lowercase().endsWith(".flv") || originalPath.lowercase().endsWith(".ts")
        
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/*" else "audio/*")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val relPath = if (isVideo) "Movies/FinalPlayerVault" else "Music/FinalPlayerVault"
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
        }
        
        val collection = if (isVideo) {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        try {
            val uri = resolver.insert(collection, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                srcFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA))
                }
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun setPrivateStatus(id: Long, isPrivate: Boolean) {
        scanner.scanMutex.withLock {
            try {
                val mediaFile = mediaDao.getMediaFileById(id) ?: return
                val secureDir = File(context.filesDir, "SecureVault")
                if (!secureDir.exists()) {
                    secureDir.mkdirs()
                    try {
                        File(secureDir, ".nomedia").createNewFile()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (isPrivate) {
                    val originalFile = File(mediaFile.path)
                    val destFile = File(secureDir, "${mediaFile.id}_${originalFile.name}")
                    var copiedSuccessfully = false
                    var deletedSuccessfully = false
                    
                    // Method 1: Try direct File API
                    if (originalFile.exists() && !originalFile.absolutePath.contains("SecureVault")) {
                        try {
                            originalFile.copyTo(destFile, overwrite = true)
                            copiedSuccessfully = destFile.exists() && destFile.length() > 0
                            if (copiedSuccessfully) {
                                deletedSuccessfully = originalFile.delete()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    // Method 2: Fallback to ContentResolver (essential for Scoped Storage / missing raw path write permission)
                    if (!copiedSuccessfully && !originalFile.absolutePath.contains("SecureVault")) {
                        try {
                            val srcUri = getContentUriForPath(context, mediaFile.path)
                            if (srcUri != null) {
                                copiedSuccessfully = copyFileViaContentResolver(context, srcUri, destFile)
                                if (copiedSuccessfully) {
                                    deletedSuccessfully = deleteFileViaContentResolver(context, mediaFile.path)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    if (copiedSuccessfully) {
                        // Save original path in preferences
                        val prefs = context.getSharedPreferences("secure_original_paths", Context.MODE_PRIVATE)
                        prefs.edit().putString("path_${mediaFile.id}", mediaFile.path).apply()
                        
                        // Update in DB with the exact local private vault path
                        mediaDao.updatePathAndPrivateStatus(id, destFile.absolutePath, true)
                    } else {
                        // Fallback: at least flag it in the DB
                        mediaDao.updatePrivateStatus(id, true)
                    }
                } else {
                    val currentFile = File(mediaFile.path)
                    val prefs = context.getSharedPreferences("secure_original_paths", Context.MODE_PRIVATE)
                    val originalPath = prefs.getString("path_${mediaFile.id}", null)
                    if (originalPath != null) {
                        val destFile = File(originalPath)
                        var restoredSuccessfully = false
                        var restoredPath = originalPath
                        
                        // Method 1: Try direct File API
                        if (currentFile.exists() && currentFile.absolutePath.contains("SecureVault")) {
                            try {
                                destFile.parentFile?.mkdirs()
                                currentFile.copyTo(destFile, overwrite = true)
                                restoredSuccessfully = destFile.exists() && destFile.length() > 0
                                if (restoredSuccessfully) {
                                    currentFile.delete()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        // Method 2: Fallback to ContentResolver MediaStore Insertion
                        if (!restoredSuccessfully && currentFile.exists() && currentFile.absolutePath.contains("SecureVault")) {
                            val insertedPath = copyFileToExternalStorage(context, currentFile, originalPath)
                            if (insertedPath != null) {
                                restoredSuccessfully = true
                                restoredPath = insertedPath
                                currentFile.delete()
                            }
                        }
                        
                        if (restoredSuccessfully) {
                            mediaDao.updatePathAndPrivateStatus(id, restoredPath, false)
                            prefs.edit().remove("path_${mediaFile.id}").apply()
                        } else {
                            mediaDao.updatePathAndPrivateStatus(id, originalPath, false)
                            prefs.edit().remove("path_${mediaFile.id}").apply()
                        }
                    } else {
                        // Fallback if original path is somehow unknown
                        mediaDao.updatePrivateStatus(id, false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mediaDao.updatePrivateStatus(id, isPrivate)
            }
        }
    }

    suspend fun deleteFile(path: String) {
        try {
            val file = File(path)
            if (file.exists() && file.delete()) {
                mediaDao.deleteMediaFileByPath(path)
            } else {
                // If physical file can't be deleted because of modern permissions, we can still hide or remove from database
                mediaDao.deleteMediaFileByPath(path)
            }
        } catch (e: Exception) {
            mediaDao.deleteMediaFileByPath(path)
        }
    }

    suspend fun renameFile(oldPath: String, newName: String): String? {
        try {
            val file = File(oldPath)
            if (file.exists()) {
                val parent = file.parentFile ?: return null
                val newFile = File(parent, newName)
                if (file.renameTo(newFile)) {
                    val oldMedia = mediaDao.getMediaFileByPath(oldPath)
                    if (oldMedia != null) {
                        mediaDao.deleteMediaFileByPath(oldPath)
                        mediaDao.insertMediaFile(
                            oldMedia.copy(
                                id = 0,
                                path = newFile.absolutePath,
                                title = newName
                            )
                        )
                        return newFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // --- PLAYLIST SECTIONS ---
    suspend fun createPlaylist(name: String): Long {
        return mediaDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        mediaDao.deletePlaylist(playlistId)
    }

    suspend fun addToPlaylist(playlistId: Long, path: String) {
        mediaDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaFilePath = path,
                orderIndex = 0
            )
        )
    }

    suspend fun removeFromPlaylist(playlistId: Long, path: String) {
        mediaDao.deletePlaylistItem(playlistId, path)
    }

    fun getPlaylistMediaFlow(playlistId: Long): Flow<List<MediaFile>> {
        return mediaDao.getPlaylistMediaFlow(playlistId)
    }

    // --- HISTORY SECTIONS ---
    suspend fun addHistory(path: String, position: Long) {
        mediaDao.insertHistory(
            HistoryEntity(
                mediaFilePath = path,
                viewedAt = System.currentTimeMillis(),
                playbackPosition = position
            )
        )
    }

    suspend fun clearHistory() {
        mediaDao.clearHistory()
    }

    suspend fun insertMediaFiles(files: List<MediaFile>) {
        mediaDao.insertMediaFiles(files)
    }
}
