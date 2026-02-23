package com.grindrplus.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GPDatabase::class.java
    )

    @Test
    fun migrate5To6_createsNewTables() {
        helper.createDatabase("test_migration", 5).apply {
            close()
        }

        val db = helper.runMigrationsAndValidate("test_migration", 6, true, GPDatabase.MIGRATION_5_6)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
        val tables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue("IndexedMessageEntity table should exist", tables.contains("IndexedMessageEntity"))
        assertTrue("IndexedMessageFts table should exist", tables.contains("IndexedMessageFts"))
        assertTrue("IndexMetadataEntity table should exist", tables.contains("IndexMetadataEntity"))

        db.close()
    }

    @Test
    fun migrate5To6_canInsertData() {
        helper.createDatabase("test_migration_insert", 5).apply {
            close()
        }

        val db = helper.runMigrationsAndValidate("test_migration_insert", 6, true, GPDatabase.MIGRATION_5_6)

        db.execSQL("""
            INSERT INTO IndexedMessageEntity (messageId, conversationId, sender, recipient, body, timestamp, indexedAt)
            VALUES ('test-msg', '100:200', '100', '200', 'Hello test', 1234567890, 1234567890)
        """.trimIndent())

        val cursor = db.query("SELECT * FROM IndexedMessageEntity WHERE messageId = 'test-msg'")
        assertEquals(1, cursor.count)
        cursor.close()

        db.execSQL("""
            INSERT INTO IndexMetadataEntity (`key`, value, updatedAt)
            VALUES ('version', '1', 1234567890)
        """.trimIndent())

        val metaCursor = db.query("SELECT * FROM IndexMetadataEntity WHERE `key` = 'version'")
        assertEquals(1, metaCursor.count)
        metaCursor.close()

        db.close()
    }

    @Test
    fun migrate5To6_preservesExistingData() {
        val dbV5 = helper.createDatabase("test_migration_preserve", 5)

        dbV5.execSQL("""
            INSERT INTO TeleportLocationEntity (name, latitude, longitude)
            VALUES ('test_location', 40.7128, -74.0060)
        """.trimIndent())

        dbV5.close()

        val dbV6 = helper.runMigrationsAndValidate("test_migration_preserve", 6, true, GPDatabase.MIGRATION_5_6)

        val cursor = dbV6.query("SELECT * FROM TeleportLocationEntity WHERE name = 'test_location'")
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        val lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"))
        val lon = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"))
        assertEquals(40.7128, lat, 0.0001)
        assertEquals(-74.0060, lon, 0.0001)
        cursor.close()

        dbV6.close()
    }
}
