package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class IndexMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
