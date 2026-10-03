package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedTimestamp DESC")
    suspend fun getAllBookmarksList(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getBookmarkById(mediaId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}
