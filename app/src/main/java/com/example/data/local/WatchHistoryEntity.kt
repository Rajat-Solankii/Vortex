package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val id: Int, // TMDB media ID
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String, // "movie" or "tv"
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)
