package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // --- SCANNED FOLDERS ---
    @Query("SELECT * FROM scanned_folders")
    fun getAllFoldersFlow(): Flow<List<ScannedFolder>>

    @Query("SELECT * FROM scanned_folders")
    suspend fun getAllFolders(): List<ScannedFolder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: ScannedFolder)

    @Query("DELETE FROM scanned_folders WHERE folderPath = :folderPath")
    suspend fun deleteFolder(folderPath: String)

    @Query("DELETE FROM scanned_folders")
    suspend fun clearFolders()


    // --- MEDIA FILES ---
    @Query("SELECT * FROM media_files ORDER BY title ASC")
    fun getAllMediaFilesFlow(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE isVideo = 1 AND isPrivate = 0 ORDER BY dateModified DESC")
    fun getVideosFlow(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE isVideo = 0 AND isPrivate = 0 ORDER BY dateModified DESC")
    fun getAudioFlow(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE isFavorite = 1 AND isPrivate = 0")
    fun getFavoritesFlow(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE isPrivate = 1")
    fun getPrivateFilesFlow(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE path = :path LIMIT 1")
    suspend fun getMediaFileByPath(path: String): MediaFile?

    @Query("SELECT * FROM media_files WHERE id = :id LIMIT 1")
    suspend fun getMediaFileById(id: Long): MediaFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFile(file: MediaFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFiles(files: List<MediaFile>)

    @Query("DELETE FROM media_files WHERE path = :path")
    suspend fun deleteMediaFileByPath(path: String)

    @Query("DELETE FROM media_files WHERE path IN (:paths)")
    suspend fun deleteMediaFilesByPaths(paths: List<String>)

    @Query("UPDATE media_files SET lastPlayPosition = :position, isNew = 0 WHERE path = :path")
    suspend fun updatePlaybackPosition(path: String, position: Long)

    @Query("UPDATE media_files SET isNew = 0 WHERE path = :path")
    suspend fun markAsPlayed(path: String)

    @Query("UPDATE media_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE media_files SET isPrivate = :isPrivate WHERE id = :id")
    suspend fun updatePrivateStatus(id: Long, isPrivate: Boolean)

    @Query("UPDATE media_files SET path = :newPath, isPrivate = :isPrivate WHERE id = :id")
    suspend fun updatePathAndPrivateStatus(id: Long, newPath: String, isPrivate: Boolean)

    @Query("UPDATE media_files SET thumbnailPath = :thumbPath WHERE id = :id")
    suspend fun updateThumbnail(id: Long, thumbPath: String)


    // --- PLAYLISTS ---
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaFilePath = :path")
    suspend fun deletePlaylistItem(playlistId: Long, path: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistItemsFlow(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM media_files WHERE path IN (SELECT mediaFilePath FROM playlist_items WHERE playlistId = :playlistId) AND isPrivate = 0")
    fun getPlaylistMediaFlow(playlistId: Long): Flow<List<MediaFile>>


    // --- HISTORY ---
    @Query("SELECT * FROM playback_history ORDER BY viewedAt DESC LIMIT 50")
    fun getHistoryFlow(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE mediaFilePath = :path")
    suspend fun deleteHistoryByPath(path: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
