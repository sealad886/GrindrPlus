# Correctness & Safety Audit — GrindrPlus

**Branch:** `feat/message-search-inbox`  
**Commit:** `243563a`  
**Date:** 2025-07-17  
**Prior audits:** `DUPLICATION_AUDIT.md`, `PERFORMANCE_AUDIT.md`

---

## Findings Summary

| ID | Severity | Component | Status |
|--------|----------|------------------------------|---------|
| ERR-01 | P1 | AntiBlock | FIXED |
| ERR-02 | P1 | Database command | FIXED |
| ERR-03 | P2 | Search command | FIXED |
| ERR-04 | P2 | MessageIndexer | FIXED |
| ERR-05 | P2 | MessageIndexer | FIXED |

---

## ERR-01 — AntiBlock `shouldTriggerAntiblock` permanently stuck false

**Severity:** P1 (silent feature breakage)  
**File:** `app/src/main/java/com/grindrplus/hooks/AntiBlock.kt`  
**Lines:** 75–79

### Evidence

When `inboxFragmentV2DeleteConversations.b` fires with an empty conversation list
(`numberOfChatsToDelete == 0`), the BEFORE hook already set
`shouldTriggerAntiblock = false` and `blockCaller = "inboxFragmentV2DeleteConversations"`.
The AFTER hook's early return exited **without resetting either flag**, leaving
anti-block detection permanently disabled until the next app restart.

### Reproduction

1. Open Grindr with GrindrPlus active.
2. Trigger any navigation path that fires the delete-conversations flow with an
   empty list (e.g., entering the inbox when no conversations are selected).
3. Have another user block you — no notification is generated.

### Fix

Reset both `shouldTriggerAntiblock = true` and `blockCaller = ""` before the
early return when `numberOfChatsToDelete == 0`.

### Before/After

- **Before:** Early return with no flag reset → anti-block stuck off.
- **After:** Flags reset before return → anti-block resumes immediately.

### Regression Risk

Low. The reset logic mirrors the coroutine-delayed reset used for non-zero
deletion counts.

---

## ERR-02 — SQL injection in `/list_table` command

**Severity:** P1 (arbitrary SQL execution)  
**File:** `app/src/main/java/com/grindrplus/commands/Database.kt`  
**Lines:** 37–42

### Evidence

The `/list_table` command interpolated user input directly into a SQL query:

```kotlin
val query = "SELECT * FROM $tableName;"
```

A malicious table name like `sqlite_master; DROP TABLE messages; --` would execute
arbitrary SQL against Grindr's host database.

### Fix

1. Added regex validation: `tableName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))`.
2. Backtick-quoted the table name in the query: `` SELECT * FROM `$tableName` ``.

### Before/After

- **Before:** Arbitrary SQL execution possible via crafted table name.
- **After:** Only valid identifier names accepted; SQL injection blocked.

### Regression Risk

None. Legitimate table names all match the identifier pattern.

---

## ERR-03 — FTS MATCH crash on malformed query input

**Severity:** P2 (user-facing crash)  
**File:** `app/src/main/java/com/grindrplus/commands/Search.kt`  
**Lines:** 38, 71, 205–210

### Evidence

FTS4 `MATCH` queries fail with `SQLiteException` when the input contains
special characters like `"`, `(`, `)`, `*`, `{`, `}`, `:`, `^`, `~`.
For example, `/search hello "world` throws an unmatched-quote syntax error.
The error propagated to the user as a toast with a cryptic SQLite stack trace.

### Fix

Added `sanitizeFtsQuery()` that strips FTS special characters to spaces, applied
to both `search()` and `searchChat()` before passing to the DAO:

```kotlin
private fun sanitizeFtsQuery(input: String): String {
    val cleaned = input.replace(Regex("""["(){}\[\]*:^~]"""), " ").trim()
    if (cleaned.isBlank()) return input.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
    return cleaned
}
```

### Before/After

- **Before:** `SQLiteException` crash on certain query characters.
- **After:** Special characters stripped; graceful degradation to keyword search.

### Regression Risk

Low. Users lose the ability to use FTS operators intentionally, but this is
acceptable since the UI provides no documentation of FTS syntax.

---

## ERR-04 — Slash commands indexed as chat messages

**Severity:** P2 (data pollution)  
**File:** `app/src/main/java/com/grindrplus/hooks/MessageIndexer.kt`  
**Lines:** 55–56

### Evidence

When a user types `/search hello`, ChatTerminal intercepts it in the BEFORE hook,
but MessageIndexer's AFTER hook still sees the outgoing message payload and indexes
it. This pollutes search results with command invocations.

### Fix

Added command prefix check before indexing:

```kotlin
val commandPrefix = Config.get("command_prefix", "/") as String
if (text.startsWith(commandPrefix)) return@hook
```

### Before/After

- **Before:** Commands like `/search`, `/reindex`, `/tp` appear in search results.
- **After:** Messages starting with the command prefix are skipped during indexing.

### Regression Risk

Minimal. Legitimate messages rarely start with `/` (or the configured prefix).
The check mirrors ChatTerminal's own prefix detection logic.

---

## ERR-05 — Unsafe `as String` casts in MessageIndexer

**Severity:** P2 (NPE crash path)  
**File:** `app/src/main/java/com/grindrplus/hooks/MessageIndexer.kt`  
**Lines:** 44–47

### Evidence

`getObjectField()` returns `Any?`, but the original code used hard casts
(`as String`) for `sender`, `recipient`, and `body`. If any of these fields
are null in the Xposed-reflected object (e.g., system messages, media-only
messages), a `NullPointerException` crashes the hook.

### Fix

Changed to safe casts with early return: `as? String ?: return@hook`.

### Before/After

- **Before:** NPE on null fields kills the entire hook callback.
- **After:** Null fields cause a silent skip for that single message.

### Regression Risk

None. The early return is strictly more permissive than crashing.

---

## Areas Reviewed Without Findings

| Area | Notes |
|------|-------|
| Room migration v5→v6 | FTS4 triggers are auto-generated by Room's `onPostMigrate()` — not a bug |
| DatabaseHelper thread safety | Correct double-check locking with `@Volatile` + `synchronized` |
| EventManager SharedFlow config | `extraBufferCapacity=100`, `replay=0` — appropriate for notification fan-out |
| HookManager lifecycle | `cleanup()` properly cancels SupervisorJob scopes in MessageIndexer and AntiBlock |
| Config thread safety | `SharedPreferences`-backed, thread-safe by Android contract |
| HTTP client usage | OkHttp calls in `Location.kt` are wrapped in `withContext(Dispatchers.IO)` |
| Coroutine scope management | Both MessageIndexer and AntiBlock use SupervisorJob with cancel-on-cleanup |

---

## Residual Risks

1. **`/shell` command** (Utils.kt): Executes arbitrary shell commands via `Runtime.getRuntime().exec()`.
   This is an intentional power-user feature but represents a significant attack surface if the
   Xposed module's command channel is ever exposed. No fix applied — this is a design decision.

2. **Obfuscated class names**: All hooks reference obfuscated class names (e.g., `fo.k`, `tn.c`).
   These will break on Grindr app updates. This is an inherent limitation of the Xposed hooking
   approach and is mitigated by the project's version-pinning (see `spline.json`).

3. **`runBlocking` in LocalSavedPhrases**: Multiple `runBlocking` calls on the main thread in
   Retrofit proxy handlers. This is a performance concern (documented in PERFORMANCE_AUDIT.md)
   rather than a correctness issue, since the DB operations are fast Room queries.
