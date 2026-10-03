package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_home_items")
data class CachedHomeItem(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val mediaId: Int,
    val category: String, // e.g. "anime", "bollywood", "movies"
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String,
    val rating: Double,
    val releaseDate: String,
    val updatedAt: Long = System.currentTimeMillis()
)
