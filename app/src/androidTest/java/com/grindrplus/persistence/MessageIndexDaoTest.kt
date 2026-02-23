package com.grindrplus.persistence

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grindrplus.persistence.model.IndexMetadataEntity
import com.grindrplus.persistence.model.IndexedMessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageIndexDaoTest {

    private lateinit var database: GPDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, GPDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsertAndRetrieveMessage() = runTest {
        val dao = database.messageIndexDao()
        val message = IndexedMessageEntity(
            messageId = "msg-001",
            conversationId = "100:200",
            sender = "100",
            recipient = "200",
            body = "Hello world",
            timestamp = System.currentTimeMillis()
        )

        dao.upsertMessage(message)
        val retrieved = dao.getMessage("msg-001")

        assertNotNull(retrieved)
        assertEquals("Hello world", retrieved!!.body)
        assertEquals("100:200", retrieved.conversationId)
    }

    @Test
    fun upsertIsIdempotent() = runTest {
        val dao = database.messageIndexDao()
        val message = IndexedMessageEntity(
            messageId = "msg-002",
            conversationId = "100:200",
            sender = "100",
            recipient = "200",
            body = "First version",
            timestamp = 1000L
        )

        dao.upsertMessage(message)
        dao.upsertMessage(message.copy(body = "Updated version"))

        val count = dao.count()
        assertEquals(1, count)

        val retrieved = dao.getMessage("msg-002")
        assertEquals("Updated version", retrieved!!.body)
    }

    @Test
    fun searchByFullTextQuery() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "c1", "s1", "r1", "The quick brown fox", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "c1", "s1", "r1", "Lazy dog sleeping", 2000L))
        dao.upsertMessage(IndexedMessageEntity("m3", "c1", "s1", "r1", "Fox jumped over", 3000L))

        val results = dao.search("fox")
        assertEquals(2, results.size)
        assertTrue(results.all { it.body.contains("fox", ignoreCase = true) || it.body.contains("Fox") })
    }

    @Test
    fun searchInConversationFilters() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "100:200", "100", "200", "Hello from conv1", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "100:300", "100", "300", "Hello from conv2", 2000L))

        val resultsConv1 = dao.searchInConversation("Hello", "100:200")
        assertEquals(1, resultsConv1.size)
        assertEquals("100:200", resultsConv1[0].conversationId)

        val resultsConv2 = dao.searchInConversation("Hello", "100:300")
        assertEquals(1, resultsConv2.size)
        assertEquals("100:300", resultsConv2[0].conversationId)
    }

    @Test
    fun deleteMessage() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "c1", "s1", "r1", "Test message", 1000L))
        assertEquals(1, dao.count())

        val deleted = dao.deleteMessage("m1")
        assertEquals(1, deleted)
        assertEquals(0, dao.count())
        assertNull(dao.getMessage("m1"))
    }

    @Test
    fun deleteMessageIsIdempotent() = runTest {
        val dao = database.messageIndexDao()

        val deleted = dao.deleteMessage("nonexistent")
        assertEquals(0, deleted)
    }

    @Test
    fun deleteConversation() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "100:200", "100", "200", "Msg 1", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "100:200", "200", "100", "Msg 2", 2000L))
        dao.upsertMessage(IndexedMessageEntity("m3", "100:300", "100", "300", "Msg 3", 3000L))

        val deleted = dao.deleteConversation("100:200")
        assertEquals(2, deleted)
        assertEquals(1, dao.count())
    }

    @Test
    fun resetIndexClearsAll() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "c1", "s1", "r1", "Test", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "c1", "s1", "r1", "Test2", 2000L))
        dao.upsertMetadata(IndexMetadataEntity("last_reindex", "12345"))

        dao.resetIndex()

        assertEquals(0, dao.count())
        assertNull(dao.getMetadata("last_reindex"))
    }

    @Test
    fun resetIndexIsIdempotent() = runTest {
        val dao = database.messageIndexDao()

        dao.resetIndex()
        dao.resetIndex()

        assertEquals(0, dao.count())
    }

    @Test
    fun rebuildIndexReplacesData() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("old1", "c1", "s1", "r1", "Old message", 1000L))
        assertEquals(1, dao.count())

        val newMessages = listOf(
            IndexedMessageEntity("new1", "c1", "s1", "r1", "New msg 1", 2000L),
            IndexedMessageEntity("new2", "c2", "s2", "r2", "New msg 2", 3000L),
            IndexedMessageEntity("new3", "c2", "s2", "r2", "New msg 3", 4000L)
        )

        dao.rebuildIndex(newMessages)

        assertEquals(3, dao.count())
        assertNull(dao.getMessage("old1"))
        assertNotNull(dao.getMessage("new1"))
        assertNotNull(dao.getMetadata("last_reindex"))
        assertEquals("3", dao.getMetadata("reindex_count")?.value)
    }

    @Test
    fun rebuildIndexIsIdempotent() = runTest {
        val dao = database.messageIndexDao()
        val messages = listOf(
            IndexedMessageEntity("m1", "c1", "s1", "r1", "Msg 1", 1000L),
            IndexedMessageEntity("m2", "c1", "s1", "r1", "Msg 2", 2000L)
        )

        dao.rebuildIndex(messages)
        val countAfterFirst = dao.count()

        dao.rebuildIndex(messages)
        val countAfterSecond = dao.count()

        assertEquals(countAfterFirst, countAfterSecond)
        assertEquals(2, countAfterSecond)
    }

    @Test
    fun getConversationMessages() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "100:200", "100", "200", "Msg 1", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "100:200", "200", "100", "Msg 2", 2000L))
        dao.upsertMessage(IndexedMessageEntity("m3", "100:300", "100", "300", "Msg 3", 3000L))

        val convMessages = dao.getConversationMessages("100:200")
        assertEquals(2, convMessages.size)
        assertEquals("Msg 2", convMessages[0].body)
    }

    @Test
    fun getLatestTimestamp() = runTest {
        val dao = database.messageIndexDao()

        assertNull(dao.getLatestTimestamp())

        dao.upsertMessage(IndexedMessageEntity("m1", "c1", "s1", "r1", "Msg 1", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "c1", "s1", "r1", "Msg 2", 5000L))
        dao.upsertMessage(IndexedMessageEntity("m3", "c1", "s1", "r1", "Msg 3", 3000L))

        assertEquals(5000L, dao.getLatestTimestamp())
    }

    @Test
    fun metadataOperations() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMetadata(IndexMetadataEntity("version", "1"))
        val meta = dao.getMetadata("version")
        assertNotNull(meta)
        assertEquals("1", meta!!.value)

        dao.upsertMetadata(IndexMetadataEntity("version", "2"))
        val updated = dao.getMetadata("version")
        assertEquals("2", updated!!.value)
    }

    @Test
    fun emptySearchReturnsEmpty() = runTest {
        val dao = database.messageIndexDao()
        val results = dao.search("nonexistent query")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchResultsOrderedByTimestampDesc() = runTest {
        val dao = database.messageIndexDao()

        dao.upsertMessage(IndexedMessageEntity("m1", "c1", "s1", "r1", "common word", 1000L))
        dao.upsertMessage(IndexedMessageEntity("m2", "c1", "s1", "r1", "common word again", 3000L))
        dao.upsertMessage(IndexedMessageEntity("m3", "c1", "s1", "r1", "common word third", 2000L))

        val results = dao.search("common")
        assertEquals(3, results.size)
        assertEquals(3000L, results[0].timestamp)
        assertEquals(2000L, results[1].timestamp)
        assertEquals(1000L, results[2].timestamp)
    }
}
