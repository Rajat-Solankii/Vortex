package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_providers")
data class CachedProvider(
    @PrimaryKey val providerId: Int,
    val providerName: String,
    val logoPath: String?,
    val displayPriority: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
