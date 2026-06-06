package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "scanned_folders")
data class ScannedFolder(
    @PrimaryKey val folderPath: String,
    val lastModifiedTs: Long,
    val fileCount: Int,
    val lastScannedAt: Long
) : Serializable

@Entity(tableName = "media_files")
data class MediaFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val title: String,
    val duration: Long,
    val size: Long,
    val dateModified: Long,
    val isVideo: Boolean,
    val thumbnailPath: String? = null,
    val lastPlayPosition: Long = 0,
    val isFavorite: Boolean = false,
    val artist: String? = null,
    val album: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val isPrivate: Boolean = false
) : Serializable

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaFilePath: String,
    val orderIndex: Int
) : Serializable

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaFilePath: String,
    val viewedAt: Long,
    val playbackPosition: Long
) : Serializable
