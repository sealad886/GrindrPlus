package com.grindrplus.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.grindrplus.GrindrPlus

object DatabaseHelper {
    @Volatile
    private var cachedDb: SQLiteDatabase? = null
    @Volatile
    private var cachedDbName: String? = null
    private val lock = Any()

    private fun getDatabase(): SQLiteDatabase {
        cachedDb?.let { if (it.isOpen) return it }

        synchronized(lock) {
            cachedDb?.let { if (it.isOpen) return it }

            val context = GrindrPlus.context
            val dbName = cachedDbName ?: run {
                val databases = context.databaseList()
                val name = databases.firstOrNull {
                    it.contains("grindr_user") && it.endsWith(".db")
                } ?: throw IllegalStateException("No Grindr user database found!").also {
                    Logger.apply {
                        e(it.message!!)
                        writeRaw(
                            "Available databases:\n" +
                                    "${databases.joinToString("\n") { "  $it" }}\n"
                        )
                    }
                }
                cachedDbName = name
                Logger.d("Using database: $name")
                name
            }

            val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
            cachedDb = db
            return db
        }
    }

    fun query(query: String, args: Array<String>? = null): List<Map<String, Any>> {
        val database = getDatabase()
        val cursor = database.rawQuery(query, args)
        val results = mutableListOf<Map<String, Any>>()

        try {
            val columnIndices = mutableMapOf<String, Int>()
            if (cursor.moveToFirst()) {
                cursor.columnNames.forEach { column ->
                    columnIndices[column] = cursor.getColumnIndexOrThrow(column)
                }
                do {
                    val row = mutableMapOf<String, Any>()
                    for ((column, idx) in columnIndices) {
                        row[column] = when (cursor.getType(idx)) {
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(idx)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(idx)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(idx)
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(idx)
                            Cursor.FIELD_TYPE_NULL -> "NULL"
                            else -> "UNKNOWN"
                        }
                    }
                    results.add(row)
                } while (cursor.moveToNext())
            }
        } finally {
            cursor.close()
        }

        return results
    }

    fun insert(table: String, values: ContentValues): Long {
        val database = getDatabase()
        return database.insert(table, null, values)
    }

    fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int {
        val database = getDatabase()
        return database.update(table, values, whereClause, whereArgs)
    }

    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int {
        val database = getDatabase()
        return database.delete(table, whereClause, whereArgs)
    }

    fun execute(sql: String) {
        val database = getDatabase()
        database.execSQL(sql)
    }
}
