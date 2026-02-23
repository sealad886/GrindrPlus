package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index("conversationId"),
        Index("sender"),
        Index("timestamp")
    ]
)
data class IndexedMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val sender: String,
    val recipient: String,
    val body: String,
    val timestamp: Long,
    val indexedAt: Long = System.currentTimeMillis()
)
