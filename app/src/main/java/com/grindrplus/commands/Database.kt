package com.grindrplus.commands

import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.DatabaseHelper

class Database(
    recipient: String,
    sender: String
) : CommandModule("Database", recipient, sender) {
    @Command("list_tables", aliases = ["lts"], help = "List all tables in the database")
    fun listTables(args: List<String>) {
        try {
            val query = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"
            val tables = DatabaseHelper.query(query).map { it["name"].toString() }
            val tableList = if (tables.isEmpty()) "No tables found."
                else tables.joinToString("\n")

            CommandDialogs.showTextDialog(
                title = "Database Tables",
                content = tableList,
                copyLabel = "Database Tables"
            )
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("list_table", aliases = ["lt"], help = "List all rows from a specific table")
    fun listTable(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a table name.")
            return
        }

        val tableName = args[0]
        try {
            val query = "SELECT * FROM $tableName;"
            val rows = DatabaseHelper.query(query)
            val tableContent = if (rows.isEmpty()) {
                "No rows found in table $tableName."
            } else {
                rows.joinToString("\n\n") { row ->
                    row.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                }
            }

            CommandDialogs.showTextDialog(
                title = "Table Content: $tableName",
                content = tableContent,
                copyLabel = "Table Content: $tableName"
            )
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("list_databases", aliases = ["ldbs"], help = "List all database files in the app's files directory")
    fun listDatabases(args: List<String>) {
        try {
            val context = GrindrPlus.context
            val databases = context.databaseList()
            val dbList = if (databases.isEmpty()) "No databases found." else databases.joinToString("\n")

            CommandDialogs.showTextDialog(
                title = "Database Files",
                content = dbList,
                copyLabel = "Database Files"
            )
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }
}
