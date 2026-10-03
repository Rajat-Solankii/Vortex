package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    suspend fun getAllWatchHistoryList(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE id = :id LIMIT 1")
    suspend fun getWatchHistoryById(id: Int): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
