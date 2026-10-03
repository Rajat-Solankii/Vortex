package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("watch_history") val watchHistory: List<WatchHistoryDto>? = null
)

@Serializable
data class WatchHistoryDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("media_type") val mediaType: String,
    @SerialName("timestamp") val timestamp: Long = 0L,
    @SerialName("season") val season: Int? = null,
    @SerialName("episode") val episode: Int? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("last_position_ms") val lastPositionMs: Long? = null
)
