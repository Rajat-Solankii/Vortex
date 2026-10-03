package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedProviderDao {
    @Query("SELECT * FROM cached_providers ORDER BY displayPriority ASC")
    suspend fun getAllProviders(): List<CachedProvider>

    @Query("SELECT * FROM cached_providers LIMIT 1")
    suspend fun getFirstProvider(): CachedProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<CachedProvider>)

    @Query("DELETE FROM cached_providers")
    suspend fun clearAll()
}
