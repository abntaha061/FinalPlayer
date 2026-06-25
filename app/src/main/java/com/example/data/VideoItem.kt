// File: /app/src/main/java/com/example/data/VideoItem.kt
package com.example.data

import java.io.Serializable

data class SubtitleInfo(
    val path: String,
    val language: String, // e.g. "AR", "EN", "ORIG"
    val displayTag: String // e.g. "AR-AR", "AR-EN", "EN", "ORIG"
) : Serializable

data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val duration: Long,
    val size: Long, // bytes
    val dateAdded: Long, // unix timestamp
    val subtitles: List<SubtitleInfo>,
    val width: Int = 0,
    val height: Int = 0
) : Serializable
