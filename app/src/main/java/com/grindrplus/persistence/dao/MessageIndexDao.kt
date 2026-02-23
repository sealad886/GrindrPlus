package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.grindrplus.persistence.model.IndexMetadataEntity
import com.grindrplus.persistence.model.IndexedMessageEntity

@Dao
interface MessageIndexDao {

    @Query("""
        SELECT m.* FROM IndexedMessageEntity m
        JOIN IndexedMessageFts fts ON m.rowid = fts.rowid
        WHERE IndexedMessageFts MATCH :query
        ORDER BY m.timestamp DESC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<IndexedMessageEntity>

    @Query("""
        SELECT m.* FROM IndexedMessageEntity m
        JOIN IndexedMessageFts fts ON m.rowid = fts.rowid
        WHERE IndexedMessageFts MATCH :query AND m.conversationId = :conversationId
        ORDER BY m.timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchInConversation(
        query: String,
        conversationId: String,
        limit: Int = 50
    ): List<IndexedMessageEntity>

    @Upsert
    suspend fun upsertMessage(message: IndexedMessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<IndexedMessageEntity>)

    @Query("DELETE FROM IndexedMessageEntity WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String): Int

    @Query("DELETE FROM IndexedMessageEntity WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String): Int

    @Query("DELETE FROM IndexedMessageEntity")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM IndexedMessageEntity")
    suspend fun count(): Int

    @Query("SELECT * FROM IndexedMessageEntity WHERE messageId = :messageId")
    suspend fun getMessage(messageId: String): IndexedMessageEntity?

    @Query("SELECT * FROM IndexedMessageEntity WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getConversationMessages(conversationId: String, limit: Int = 100): List<IndexedMessageEntity>

    @Query("SELECT MAX(timestamp) FROM IndexedMessageEntity")
    suspend fun getLatestTimestamp(): Long?

    @Upsert
    suspend fun upsertMetadata(metadata: IndexMetadataEntity)

    @Query("SELECT * FROM IndexMetadataEntity WHERE `key` = :key")
    suspend fun getMetadata(key: String): IndexMetadataEntity?

    @Query("DELETE FROM IndexMetadataEntity")
    suspend fun deleteAllMetadata()

    @Transaction
    suspend fun resetIndex() {
        deleteAll()
        deleteAllMetadata()
    }

    @Transaction
    suspend fun rebuildIndex(messages: List<IndexedMessageEntity>) {
        deleteAll()
        upsertMessages(messages)
        upsertMetadata(
            IndexMetadataEntity(
                key = "last_reindex",
                value = System.currentTimeMillis().toString()
            )
        )
        upsertMetadata(
            IndexMetadataEntity(
                key = "reindex_count",
                value = messages.size.toString()
            )
        )
    }
}
