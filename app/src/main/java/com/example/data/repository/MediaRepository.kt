package com.example.data.repository

import android.content.Context
import com.example.data.local.MediaDatabase
import com.example.data.local.MediaScanner
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow
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

    suspend fun setPrivateStatus(id: Long, isPrivate: Boolean) {
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
                if (originalFile.exists() && !originalFile.absolutePath.contains("SecureVault")) {
                    val destFile = File(secureDir, "${mediaFile.id}_${originalFile.name}")
                    try {
                        originalFile.copyTo(destFile, overwrite = true)
                        originalFile.delete()
                        
                        // Save original path in preferences
                        val prefs = context.getSharedPreferences("secure_original_paths", Context.MODE_PRIVATE)
                        prefs.edit().putString("path_${mediaFile.id}", mediaFile.path).apply()
                        
                        // Update in DB
                        mediaDao.updatePathAndPrivateStatus(id, destFile.absolutePath, true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback: at least update the private status in DB
                        mediaDao.updatePrivateStatus(id, true)
                    }
                } else {
                    mediaDao.updatePrivateStatus(id, true)
                }
            } else {
                val currentFile = File(mediaFile.path)
                val prefs = context.getSharedPreferences("secure_original_paths", Context.MODE_PRIVATE)
                val originalPath = prefs.getString("path_${mediaFile.id}", null)
                if (originalPath != null) {
                    val destFile = File(originalPath)
                    if (currentFile.exists() && currentFile.absolutePath.contains("SecureVault")) {
                        try {
                            destFile.parentFile?.mkdirs()
                            currentFile.copyTo(destFile, overwrite = true)
                            currentFile.delete()
                            
                            mediaDao.updatePathAndPrivateStatus(id, originalPath, false)
                            prefs.edit().remove("path_${mediaFile.id}").apply()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            mediaDao.updatePrivateStatus(id, false)
                        }
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
