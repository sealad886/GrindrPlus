package com.grindrplus.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grindrplus.persistence.converters.DateConverter
import com.grindrplus.persistence.dao.AlbumDao
import com.grindrplus.persistence.dao.MessageIndexDao
import com.grindrplus.persistence.dao.SavedPhraseDao
import com.grindrplus.persistence.dao.TeleportLocationDao
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.persistence.model.IndexMetadataEntity
import com.grindrplus.persistence.model.IndexedMessageEntity
import com.grindrplus.persistence.model.IndexedMessageFts
import com.grindrplus.persistence.model.SavedPhraseEntity
import com.grindrplus.persistence.model.TeleportLocationEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        IndexedMessageEntity::class,
        IndexedMessageFts::class,
        IndexMetadataEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun messageIndexDao(): MessageIndexDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `IndexedMessageEntity` (
                        `messageId` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `recipient` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `indexedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_IndexedMessageEntity_conversationId` ON `IndexedMessageEntity` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_IndexedMessageEntity_sender` ON `IndexedMessageEntity` (`sender`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_IndexedMessageEntity_timestamp` ON `IndexedMessageEntity` (`timestamp`)")

                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `IndexedMessageFts`
                    USING FTS4(`body` TEXT, content=`IndexedMessageEntity`, tokenize=unicode61)
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `IndexMetadataEntity` (
                        `key` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                """.trimIndent())
            }
        }

        fun create(context: Context): GPDatabase {
            return Room.databaseBuilder(context, GPDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration(false)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
