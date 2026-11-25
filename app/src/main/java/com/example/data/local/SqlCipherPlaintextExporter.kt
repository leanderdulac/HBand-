package com.example.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase as AndroidSqlite
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Copies a plaintext SQLite file into a SQLCipher database opened with the
 * same raw passphrase Room passes to [net.zetetic.database.sqlcipher.SupportOpenHelperFactory].
 * The source file is only read. The caller deletes it after the snapshot matches.
 */
internal object SqlCipherPlaintextExporter : EncryptedDatabaseCodec {
    override fun export(source: File, destination: File, passphrase: ByteArray) {
        if (destination.exists()) destination.delete()
        var plain: AndroidSqlite? = null
        var encrypted: SQLiteDatabase? = null
        try {
            plain = AndroidSqlite.openDatabase(source.path, null, AndroidSqlite.OPEN_READONLY)
            encrypted = SQLiteDatabase.openOrCreateDatabase(destination, passphrase, null, null)
            encrypted.execSQL("PRAGMA foreign_keys = OFF")
            copySchema(plain, encrypted)
            copyRows(plain, encrypted)
            copySequence(plain, encrypted)
            encrypted.version = plain.version
        } catch (error: Exception) {
            encrypted?.close()
            encrypted = null
            destination.delete()
            throw error
        } finally {
            encrypted?.close()
            plain?.close()
        }
    }

    override fun readEncrypted(file: File, passphrase: ByteArray): DatabaseSnapshot {
        val db = SQLiteDatabase.openDatabase(
            file.path,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null,
        )
        db.use { encrypted ->
            val tables = mutableListOf<String>()
            encrypted.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            encrypted.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) tables += "sqlite_sequence"
            }
            val rows = linkedMapOf<String, List<List<String>>>()
            for (table in tables) {
                quoteSqlIdent(table)
                val columns = mutableListOf<String>()
                encrypted.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    while (cursor.moveToNext()) columns += cursor.getString(1)
                }
                val tableRows = mutableListOf<List<String>>()
                if (columns.isNotEmpty()) {
                    val sql = "SELECT ${columns.joinToString { quoteSqlIdent(it) }} FROM ${quoteSqlIdent(table)}"
                    encrypted.rawQuery(sql, null).use { cursor ->
                        while (cursor.moveToNext()) tableRows += SnapshotCells.encode(cursor)
                    }
                }
                rows[table] = tableRows
            }
            return DatabaseSnapshot(userVersion = encrypted.version, rows = rows)
        }
    }

    private fun copySchema(plain: AndroidSqlite, encrypted: SQLiteDatabase) {
        plain.rawQuery(
            """
            SELECT sql FROM sqlite_master
            WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'
            ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 WHEN 'trigger' THEN 2 ELSE 3 END, name
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val sql = cursor.getString(0) ?: continue
                encrypted.execSQL(sql)
            }
        }
    }

    private fun copyRows(plain: AndroidSqlite, encrypted: SQLiteDatabase) {
        val tables = mutableListOf<String>()
        plain.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        encrypted.beginTransaction()
        try {
            for (table in tables) {
                quoteSqlIdent(table)
                val columns = mutableListOf<String>()
                plain.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    while (cursor.moveToNext()) columns += cursor.getString(1)
                }
                if (columns.isEmpty()) continue
                val select = "SELECT ${columns.joinToString { quoteSqlIdent(it) }} FROM ${quoteSqlIdent(table)}"
                val insert = "INSERT INTO ${quoteSqlIdent(table)} (${columns.joinToString { quoteSqlIdent(it) }}) " +
                    "VALUES (${columns.joinToString { "?" }})"
                val statement = encrypted.compileStatement(insert)
                try {
                    plain.rawQuery(select, null).use { cursor ->
                        while (cursor.moveToNext()) {
                            statement.clearBindings()
                            bindRow(statement, cursor)
                            statement.executeInsert()
                        }
                    }
                } finally {
                    statement.close()
                }
            }
            encrypted.setTransactionSuccessful()
        } finally {
            encrypted.endTransaction()
        }
    }

    private fun copySequence(plain: AndroidSqlite, encrypted: SQLiteDatabase) {
        val source = mutableListOf<Pair<String, Long>>()
        plain.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return
        }
        plain.rawQuery("SELECT name, seq FROM sqlite_sequence", null).use { cursor ->
            while (cursor.moveToNext()) source += cursor.getString(0) to cursor.getLong(1)
        }
        encrypted.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return
        }
        encrypted.execSQL("DELETE FROM sqlite_sequence")
        for ((name, seq) in source) {
            encrypted.execSQL(
                "INSERT INTO sqlite_sequence(name, seq) VALUES (?, ?)",
                arrayOf<Any>(name, seq),
            )
        }
    }

    private fun bindRow(statement: net.zetetic.database.sqlcipher.SQLiteStatement, cursor: Cursor) {
        for (index in 0 until cursor.columnCount) {
            val bind = index + 1
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> statement.bindNull(bind)
                Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(bind, cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(bind, cursor.getDouble(index))
                Cursor.FIELD_TYPE_STRING -> statement.bindString(bind, cursor.getString(index))
                Cursor.FIELD_TYPE_BLOB -> statement.bindBlob(bind, cursor.getBlob(index))
                else -> statement.bindNull(bind)
            }
        }
    }
}
