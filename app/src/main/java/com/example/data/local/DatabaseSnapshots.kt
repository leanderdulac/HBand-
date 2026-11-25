package com.example.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Comparable picture of a SQLite file: schema version, table names and every
 * cell. Used to prove a ciphertext copy still holds the plaintext rows.
 */
internal data class DatabaseSnapshot(
    val userVersion: Int,
    val rows: Map<String, List<List<String>>>,
)

internal object SnapshotCells {
    fun encode(cursor: Cursor): List<String> = (0 until cursor.columnCount).map { index ->
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> "N"
            Cursor.FIELD_TYPE_INTEGER -> "I:" + cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> "R:" + cursor.getDouble(index).toRawBits().toString()
            Cursor.FIELD_TYPE_STRING -> "T:" + cursor.getString(index)
            Cursor.FIELD_TYPE_BLOB -> "B:" + cursor.getBlob(index).joinToString("") { "%02x".format(it) }
            else -> "U"
        }
    }
}

internal fun quoteSqlIdent(name: String): String {
    require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Nome de tabela recusado." }
    return "\"$name\""
}

internal object AndroidPlaintextSnapshots {
    fun checkpoint(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                    "Checkpoint do banco em texto claro não concluiu."
                }
            }
        }
    }

    fun read(file: File): DatabaseSnapshot =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = tableNames(db)
            val rows = linkedMapOf<String, List<List<String>>>()
            for (table in tables) {
                rows[table] = readTable(db, table)
            }
            DatabaseSnapshot(userVersion = db.version, rows = rows)
        }

    private fun tableNames(db: SQLiteDatabase): List<String> {
        val names = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) names += "sqlite_sequence"
        }
        return names
    }

    private fun readTable(db: SQLiteDatabase, table: String): List<List<String>> {
        val columns = columns(db, table)
        if (columns.isEmpty()) return emptyList()
        val sql = "SELECT ${columns.joinToString { quoteSqlIdent(it) }} FROM ${quoteSqlIdent(table)}"
        val rows = mutableListOf<List<String>>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) rows += SnapshotCells.encode(cursor)
        }
        return rows
    }

    private fun columns(db: SQLiteDatabase, table: String): List<String> {
        quoteSqlIdent(table)
        val names = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(1)
        }
        return names
    }
}
