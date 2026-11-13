package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedHomeItemDao {
    @Query("SELECT * FROM cached_home_items WHERE category = :category ORDER BY localId ASC")
    suspend fun getItemsByCategory(category: String): List<CachedHomeItem>

    @Query("SELECT * FROM cached_home_items WHERE category = :category ORDER BY localId ASC LIMIT 1")
    suspend fun getFirstItemInCategory(category: String): CachedHomeItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedHomeItem>)

    @Query("DELETE FROM cached_home_items WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM cached_home_items")
    suspend fun clearAll()
}
