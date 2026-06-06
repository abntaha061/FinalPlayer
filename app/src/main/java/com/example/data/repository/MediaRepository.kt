package com.example.data.repository

import android.content.Context
import com.example.data.local.MediaDatabase
import com.example.data.local.MediaScanner
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import java.io.File

class MediaRepository(context: Context) {
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

    suspend fun triggerScan(context: Context, onProgress: (String) -> Unit = {}) {
        scanner.scanMedia(context, onProgress)
    }

    suspend fun updatePlaybackPosition(path: String, position: Long) {
        mediaDao.updatePlaybackPosition(path, position)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        mediaDao.updateFavorite(id, isFavorite)
    }

    suspend fun setPrivateStatus(id: Long, isPrivate: Boolean) {
        mediaDao.updatePrivateStatus(id, isPrivate)
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
