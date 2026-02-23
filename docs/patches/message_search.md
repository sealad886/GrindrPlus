# Message Search & Indexing

GrindrPlus includes a persistent message search index that enables full-text search across your chat messages. Messages are indexed incrementally as they are sent and received, and can be searched using slash commands.

## Architecture

- **Storage**: Room database with FTS4 (Full-Text Search) virtual table
- **Indexing**: Incremental via Xposed hooks on outgoing/incoming messages
- **Persistence**: Survives process restarts; data stored in `grindrplus.db`
- **Safety**: Index is separate from Grindr's own database; operations only affect GrindrPlus data

## Commands

All commands use the configured command prefix (default: `/`).

### `/search <query>` (aliases: `s`, `find`)

Search all indexed messages across all conversations.

```
/search meeting tomorrow
/s dinner plans
/find phone number
```

Results are displayed in a dialog with timestamp, sender, and message body. Supports FTS4 query syntax including:
- Simple words: `/search hello`
- Phrases: `/search "exact phrase"`
- Boolean: `/search hello OR world`
- Prefix: `/search hel*`

### `/search_chat <query>` (alias: `sc`)

Search messages only within the current conversation.

```
/search_chat restaurant
/sc address
```

### `/reindex` (alias: `rebuild_index`)

Rebuild the search index from Grindr's native database. Use this when:
- First installing the search feature (to index existing messages)
- You suspect the index is out of sync
- After a database corruption

```
/reindex
```

Reports the number of messages indexed and elapsed time on completion.

### `/db_reset` (alias: `reset_index`)

Reset (clear) the message search index. This removes all indexed messages from GrindrPlus's search database.

**Does NOT affect Grindr's own message storage.**

Requires explicit confirmation:
```
/db_reset confirm    # Direct confirmation via command argument
/db_reset            # Shows confirmation dialog with cancel/reset buttons
```

After reset, use `/reindex` to rebuild the index.

### `/index_stats` (alias: `istats`)

Display statistics about the current search index:
- Total indexed messages
- Last reindex timestamp
- Messages in last reindex

```
/index_stats
```

## Ops Runbook

### Initial Setup
1. Install/update GrindrPlus with the search feature
2. Open any chat in Grindr
3. Run `/reindex` to index existing messages from Grindr's database
4. New messages are automatically indexed going forward

### When to Reindex
- After first install of the search feature
- If search results seem incomplete or stale
- After restoring from backup
- Run `/index_stats` to check index health

### When to Reset
- If the index becomes corrupted (search crashes or returns wrong results)
- To free up storage space
- Workflow: `/db_reset confirm` → `/reindex`

### Confirming Healthy State
1. Run `/index_stats` — verify message count is reasonable
2. Run `/search <known-keyword>` — verify results appear
3. Check logs for any indexing errors

### Recovery from Corruption
1. Run `/db_reset confirm` to clear the index
2. Run `/reindex` to rebuild from Grindr's database
3. Verify with `/index_stats`

### Crash Safety
- If the app crashes during reindex, simply re-run `/reindex`
- The reindex operation is atomic (uses database transactions)
- Partial reindex states are impossible due to the rebuild strategy (delete-all then insert-all inside a transaction)

## Technical Details

### Schema (v6)

**IndexedMessageEntity**
| Column | Type | Description |
|--------|------|-------------|
| messageId | TEXT (PK) | Unique message identifier |
| conversationId | TEXT | Conversation ID (format: `profileId1:profileId2`) |
| sender | TEXT | Sender's profile ID |
| recipient | TEXT | Recipient's profile ID |
| body | TEXT | Message text content |
| timestamp | INTEGER | Message timestamp (epoch ms) |
| indexedAt | INTEGER | When the message was indexed (epoch ms) |

**IndexedMessageFts** — FTS4 virtual table on `body` column for full-text search

**IndexMetadataEntity**
| Column | Type | Description |
|--------|------|-------------|
| key | TEXT (PK) | Metadata key |
| value | TEXT | Metadata value |
| updatedAt | INTEGER | Last update timestamp |

### Incremental Indexing

Messages are indexed via two mechanisms:
1. **Outgoing messages**: Hooked at the chat message handler (`ChatTerminal`-adjacent hook)
2. **Incoming messages**: Via WebSocket server notifications (`chat.v1.message.created`, `.updated`, `.deleted`, `chat.v1.conversation.delete`)

### Migration

Database migration from v5 to v6 is handled automatically. The migration creates new tables without affecting existing data (albums, saved phrases, teleport locations).
