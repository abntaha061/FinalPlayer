package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history WHERE filePath = :path LIMIT 1")
    suspend fun getHistory(path: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE filePath = :path")
    suspend fun deleteHistory(path: String)
}
