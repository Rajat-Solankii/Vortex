package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bookmark(
    @SerialName("id") val id: String? = null, // Supabase UUID
    @SerialName("user_id") val userId: String? = null, // Set automatically if we use auth.uid() or manually
    @SerialName("media_id") val mediaId: String,
    @SerialName("media_type") val mediaType: String, // 'movie' or 'tv'
    @SerialName("title") val title: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
