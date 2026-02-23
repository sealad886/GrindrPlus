package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.persistence.model.IndexMetadataEntity
import com.grindrplus.persistence.model.IndexedMessageEntity
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.getObjectField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MessageIndexer : Hook(
    "Message indexer",
    "Index chat messages for search functionality"
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val chatMessageHandler = "fo.k"

    private fun dao() = GrindrPlus.database.messageIndexDao()

    override fun init() {
        hookOutgoingMessages()
        hookIncomingNotifications()
    }

    private fun hookOutgoingMessages() {
        findClass(chatMessageHandler).hook("l", HookStage.AFTER) { param ->
            try {
                val message = getObjectField(param.arg(0), "chatMessage")
                val content = getObjectField(message, "content")
                val sender = getObjectField(content, "sender") as String
                val recipient = getObjectField(content, "recipient") as String
                val body = getObjectField(content, "body") as String

                val bodyJson = JSONObject(body)
                if (!bodyJson.has("text")) return@hook

                val text = bodyJson.getString("text")
                val messageId = tryExtractMessageId(message) ?: "out_${System.nanoTime()}"
                val conversationId = buildConversationId(sender, recipient)

                indexMessage(messageId, conversationId, sender, recipient, text)
            } catch (e: Exception) {
                Logger.e("Failed to index outgoing message: ${e.message}", LogSource.MODULE)
            }
        }
    }

    private fun hookIncomingNotifications() {
        scope.launch {
            GrindrPlus.serverNotifications.collect { notification ->
                try {
                    when (notification.typeValue) {
                        "chat.v1.message.created" -> handleMessageCreated(notification.payload)
                        "chat.v1.message.updated" -> handleMessageUpdated(notification.payload)
                        "chat.v1.message.deleted" -> handleMessageDeleted(notification.payload)
                        "chat.v1.conversation.delete" -> handleConversationDeleted(notification.payload)
                    }
                } catch (e: Exception) {
                    Logger.e("Failed to process notification for indexing: ${e.message}", LogSource.MODULE)
                }
            }
        }
    }

    private fun handleMessageCreated(payload: JSONObject?) {
        payload ?: return
        val messageId = payload.optString("messageId", "").ifEmpty { return }
        val conversationId = payload.optString("conversationId", "").ifEmpty { return }
        val sender = payload.optString("senderId", "").ifEmpty { return }
        val recipient = extractRecipient(conversationId, sender)

        val bodyObj = payload.optJSONObject("body") ?: payload.optString("body", "").let {
            if (it.isNotEmpty()) try { JSONObject(it) } catch (_: Exception) { return } else return
        }

        val text = bodyObj.optString("text", "").ifEmpty { return }
        indexMessage(messageId, conversationId, sender, recipient, text)
    }

    private fun handleMessageUpdated(payload: JSONObject?) {
        payload ?: return
        val messageId = payload.optString("messageId", "").ifEmpty { return }
        val conversationId = payload.optString("conversationId", "").ifEmpty { return }
        val sender = payload.optString("senderId", "").ifEmpty { return }
        val recipient = extractRecipient(conversationId, sender)

        val bodyObj = payload.optJSONObject("body") ?: payload.optString("body", "").let {
            if (it.isNotEmpty()) try { JSONObject(it) } catch (_: Exception) { return } else return
        }

        val text = bodyObj.optString("text", "").ifEmpty { return }
        indexMessage(messageId, conversationId, sender, recipient, text)
    }

    private fun handleMessageDeleted(payload: JSONObject?) {
        payload ?: return
        val messageId = payload.optString("messageId", "").ifEmpty { return }
        scope.launch {
            try {
                val deleted = dao().deleteMessage(messageId)
                if (deleted > 0) {
                    Logger.d("Removed deleted message from index: $messageId", LogSource.MODULE)
                }
            } catch (e: Exception) {
                Logger.e("Failed to remove deleted message from index: ${e.message}", LogSource.MODULE)
            }
        }
    }

    private fun handleConversationDeleted(payload: JSONObject?) {
        payload ?: return
        val conversationIds = payload.optJSONArray("conversationIds") ?: return
        scope.launch {
            try {
                for (i in 0 until conversationIds.length()) {
                    val convId = conversationIds.getString(i)
                    val deleted = dao().deleteConversation(convId)
                    if (deleted > 0) {
                        Logger.d("Removed $deleted messages for deleted conversation $convId", LogSource.MODULE)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to handle conversation deletion for index: ${e.message}", LogSource.MODULE)
            }
        }
    }

    private fun indexMessage(
        messageId: String,
        conversationId: String,
        sender: String,
        recipient: String,
        text: String
    ) {
        scope.launch {
            try {
                val entity = IndexedMessageEntity(
                    messageId = messageId,
                    conversationId = conversationId,
                    sender = sender,
                    recipient = recipient,
                    body = text,
                    timestamp = System.currentTimeMillis()
                )
                dao().upsertMessage(entity)
                Logger.d("Indexed message $messageId in conversation $conversationId", LogSource.MODULE)
            } catch (e: Exception) {
                Logger.e("Failed to index message $messageId: ${e.message}", LogSource.MODULE)
            }
        }
    }

    private fun buildConversationId(sender: String, recipient: String): String {
        val ids = listOf(sender, recipient).sorted()
        return "${ids[0]}:${ids[1]}"
    }

    private fun extractRecipient(conversationId: String, sender: String): String {
        val parts = conversationId.split(":")
        return parts.firstOrNull { it != sender } ?: sender
    }

    private fun tryExtractMessageId(message: Any): String? {
        return try {
            getObjectField(message, "messageId") as? String
        } catch (_: Exception) {
            try {
                getObjectField(message, "id") as? String
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        suspend fun reindexFromDatabase(): ReindexResult {
            val startTime = System.currentTimeMillis()
            val dao = GrindrPlus.database.messageIndexDao()

            try {
                val chatMessages = com.grindrplus.core.DatabaseHelper.query(
                    """SELECT conversation_id, sender_profile_id, body, timestamp, message_id
                       FROM chat_messages
                       WHERE body IS NOT NULL AND body != ''
                       ORDER BY timestamp DESC""",
                    null
                )

                val messages = chatMessages.mapNotNull { row ->
                    try {
                        val bodyStr = row["body"]?.toString() ?: return@mapNotNull null
                        val bodyJson = try { JSONObject(bodyStr) } catch (_: Exception) { return@mapNotNull null }
                        val text = bodyJson.optString("text", "").ifEmpty { return@mapNotNull null }

                        val convId = row["conversation_id"]?.toString() ?: return@mapNotNull null
                        val sender = row["sender_profile_id"]?.toString() ?: return@mapNotNull null
                        val parts = convId.split(":")
                        val recipient = parts.firstOrNull { it != sender } ?: sender
                        val timestamp = when (val ts = row["timestamp"]) {
                            is Int -> ts.toLong()
                            is Long -> ts
                            is Number -> ts.toLong()
                            is String -> ts.toLongOrNull() ?: System.currentTimeMillis()
                            else -> System.currentTimeMillis()
                        }
                        val msgId = row["message_id"]?.toString() ?: "grindr_${System.nanoTime()}"

                        IndexedMessageEntity(
                            messageId = msgId,
                            conversationId = convId,
                            sender = sender,
                            recipient = recipient,
                            body = text,
                            timestamp = timestamp
                        )
                    } catch (e: Exception) {
                        Logger.e("Failed to parse message for reindex: ${e.message}", LogSource.MODULE)
                        null
                    }
                }

                dao.rebuildIndex(messages)

                val elapsed = System.currentTimeMillis() - startTime
                Logger.i("Reindex complete: ${messages.size} messages indexed in ${elapsed}ms", LogSource.MODULE)
                return ReindexResult(success = true, messageCount = messages.size, elapsedMs = elapsed)
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                Logger.e("Reindex failed after ${elapsed}ms: ${e.message}", LogSource.MODULE)
                return ReindexResult(success = false, messageCount = 0, elapsedMs = elapsed, error = e.message)
            }
        }

        suspend fun getIndexStats(): IndexStats {
            val dao = GrindrPlus.database.messageIndexDao()
            val count = dao.count()
            val lastReindex = dao.getMetadata("last_reindex")?.value?.toLongOrNull()
            val reindexCount = dao.getMetadata("reindex_count")?.value?.toIntOrNull()
            return IndexStats(
                totalMessages = count,
                lastReindexTimestamp = lastReindex,
                lastReindexCount = reindexCount
            )
        }
    }

    data class ReindexResult(
        val success: Boolean,
        val messageCount: Int,
        val elapsedMs: Long,
        val error: String? = null
    )

    data class IndexStats(
        val totalMessages: Int,
        val lastReindexTimestamp: Long?,
        val lastReindexCount: Int?
    )
}
