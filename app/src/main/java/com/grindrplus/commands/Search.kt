package com.grindrplus.commands

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.hooks.MessageIndexer
import com.grindrplus.ui.Utils.copyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Search(
    recipient: String,
    sender: String
) : CommandModule("Search", recipient, sender) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @Command("search", aliases = ["s", "find"], help = "Search indexed messages (usage: search <query>)")
    fun search(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Usage: /search <query>")
            return
        }

        val query = args.joinToString(" ")
        coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    GrindrPlus.database.messageIndexDao().search(query, 50)
                }

                if (results.isEmpty()) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "No messages found for: $query")
                    return@launch
                }

                val resultText = results.mapIndexed { index, msg ->
                    val time = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", java.util.Locale.US
                    ).format(java.util.Date(msg.timestamp))
                    "${index + 1}. [$time] ${msg.sender}: ${msg.body.take(100)}${if (msg.body.length > 100) "..." else ""}"
                }.joinToString("\n\n")

                GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                    val scrollView = ScrollView(activity).apply {
                        setPadding(60, 40, 60, 40)
                    }

                    val textView = AppCompatTextView(activity).apply {
                        text = resultText
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        setPadding(20, 20, 20, 20)
                    }

                    scrollView.addView(textView)

                    AlertDialog.Builder(activity)
                        .setTitle("Search results (${results.size})")
                        .setView(scrollView)
                        .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                        .setNegativeButton("Copy") { _, _ ->
                            copyToClipboard("Search Results", resultText)
                        }
                        .create()
                        .show()
                }
            } catch (e: Exception) {
                Logger.e("Search failed: ${e.message}", LogSource.MODULE)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Search error: ${e.message}")
            }
        }
    }

    @Command("search_chat", aliases = ["sc"], help = "Search messages in current conversation")
    fun searchChat(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Usage: /search_chat <query>")
            return
        }

        val query = args.joinToString(" ")
        val conversationId = buildConversationId(recipient, sender)

        coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    GrindrPlus.database.messageIndexDao()
                        .searchInConversation(query, conversationId, 50)
                }

                if (results.isEmpty()) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "No messages found in this chat for: $query")
                    return@launch
                }

                val resultText = results.mapIndexed { index, msg ->
                    val time = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", java.util.Locale.US
                    ).format(java.util.Date(msg.timestamp))
                    "${index + 1}. [$time] ${msg.sender}: ${msg.body.take(100)}${if (msg.body.length > 100) "..." else ""}"
                }.joinToString("\n\n")

                GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                    val scrollView = ScrollView(activity).apply {
                        setPadding(60, 40, 60, 40)
                    }

                    val textView = AppCompatTextView(activity).apply {
                        text = resultText
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        setPadding(20, 20, 20, 20)
                    }

                    scrollView.addView(textView)

                    AlertDialog.Builder(activity)
                        .setTitle("Chat search (${results.size})")
                        .setView(scrollView)
                        .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                        .setNegativeButton("Copy") { _, _ ->
                            copyToClipboard("Chat Search Results", resultText)
                        }
                        .create()
                        .show()
                }
            } catch (e: Exception) {
                Logger.e("Chat search failed: ${e.message}", LogSource.MODULE)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Search error: ${e.message}")
            }
        }
    }

    @Command("reindex", aliases = ["rebuild_index"], help = "Rebuild the message search index from Grindr's database")
    fun reindex(args: List<String>) {
        GrindrPlus.showToast(Toast.LENGTH_LONG, "Starting reindex... This may take a moment.")

        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    MessageIndexer.reindexFromDatabase()
                }

                if (result.success) {
                    val message = "Reindex complete: ${result.messageCount} messages indexed in ${result.elapsedMs}ms"
                    GrindrPlus.showToast(Toast.LENGTH_LONG, message)
                    Logger.i(message, LogSource.MODULE)
                } else {
                    val message = "Reindex failed: ${result.error ?: "Unknown error"}"
                    GrindrPlus.showToast(Toast.LENGTH_LONG, message)
                    Logger.e(message, LogSource.MODULE)
                }
            } catch (e: Exception) {
                Logger.e("Reindex command failed: ${e.message}", LogSource.MODULE)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Reindex error: ${e.message}")
            }
        }
    }

    @Command("db_reset", aliases = ["reset_index"], help = "Reset the message index database (requires 'confirm' argument)")
    fun dbReset(args: List<String>) {
        if (args.firstOrNull() != "confirm") {
            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                AlertDialog.Builder(activity)
                    .setTitle("Reset Message Index")
                    .setMessage(
                        "This will delete ALL indexed messages from the GrindrPlus search database.\n\n" +
                        "This does NOT affect Grindr's own message storage.\n\n" +
                        "After reset, use /reindex to rebuild the index.\n\n" +
                        "To proceed, run: /db_reset confirm"
                    )
                    .setPositiveButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton("Reset Now") { _, _ ->
                        performReset()
                    }
                    .create()
                    .show()
            }
            return
        }

        performReset()
    }

    @Command("index_stats", aliases = ["istats"], help = "Show message index statistics")
    fun indexStats(args: List<String>) {
        coroutineScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    MessageIndexer.getIndexStats()
                }

                val lastReindex = stats.lastReindexTimestamp?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(it))
                } ?: "Never"

                val statsText = buildString {
                    appendLine("Total indexed messages: ${stats.totalMessages}")
                    appendLine("Last reindex: $lastReindex")
                    appendLine("Messages in last reindex: ${stats.lastReindexCount ?: "N/A"}")
                }

                GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                    val dialogView = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(60, 40, 60, 40)
                    }

                    val textView = AppCompatTextView(activity).apply {
                        text = statsText
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.NORMAL)
                        setPadding(20, 20, 20, 20)
                    }

                    dialogView.addView(textView)

                    AlertDialog.Builder(activity)
                        .setTitle("Index Statistics")
                        .setView(dialogView)
                        .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                        .create()
                        .show()
                }
            } catch (e: Exception) {
                Logger.e("Failed to fetch index stats: ${e.message}", LogSource.MODULE)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
            }
        }
    }

    private fun performReset() {
        coroutineScope.launch {
            try {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Resetting message index...")

                withContext(Dispatchers.IO) {
                    GrindrPlus.database.messageIndexDao().resetIndex()
                }

                GrindrPlus.showToast(Toast.LENGTH_LONG, "Message index reset successfully. Use /reindex to rebuild.")
                Logger.i("Message index reset completed", LogSource.MODULE)
            } catch (e: Exception) {
                Logger.e("Index reset failed: ${e.message}", LogSource.MODULE)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Reset failed: ${e.message}")
            }
        }
    }

    private fun buildConversationId(id1: String, id2: String): String {
        val ids = listOf(id1, id2).sorted()
        return "${ids[0]}:${ids[1]}"
    }
}
