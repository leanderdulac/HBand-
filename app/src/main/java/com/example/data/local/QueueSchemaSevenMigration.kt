package com.example.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/** Runs inside Room's upgrade transaction; only the two recorded v7 schemas are supported. */
internal object QueueSchemaSevenMigration {
    private const val COLUMNS = "id,payloadJson,status,retries,createdAt,lastAttemptAt,errorMessage,clientReadingId"
    private const val FIELDS = "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "payloadJson TEXT NOT NULL, status TEXT NOT NULL, retries INTEGER NOT NULL, " +
        "createdAt INTEGER NOT NULL, lastAttemptAt INTEGER, errorMessage TEXT, clientReadingId TEXT NOT NULL"
    private const val INDEX = "CREATE UNIQUE INDEX index_ingest_queue_clientReadingId ON ingest_queue (clientReadingId)"

    // Room creates quoted identifiers, while ALTER TABLE 6→7 appends an unquoted
    // column. Ignore that formatting only; unknown columns/constraints/defaults fail closed.
    private fun comparable(sql: String): String = sql
        .replace(Regex("`([A-Za-z_][A-Za-z0-9_]*)`|\"([A-Za-z_][A-Za-z0-9_]*)\"")) {
            it.groups[1]?.value ?: it.groups[2]!!.value
        }
        .replace(Regex("\\s+"), " ").trim()
        .replace(Regex("^(CREATE (?:UNIQUE INDEX|TABLE)) IF NOT EXISTS "), "$1 ")
        .replace(Regex("\\s*([(),])\\s*"), "$1")

    fun migrate(db: SupportSQLiteDatabase) {
        val definitions = db.query(
            "SELECT type, name, sql FROM sqlite_master WHERE tbl_name = 'ingest_queue' ORDER BY type, name"
        ).use { rows ->
            buildList {
                while (rows.moveToNext()) add(Triple(rows.getString(0), rows.getString(1), rows.getString(2)))
            }
        }
        val oldSql = "CREATE TABLE ingest_queue ($FIELDS)"
        val localSql = "CREATE TABLE ingest_queue ($FIELDS DEFAULT '')"
        val table = definitions.singleOrNull { it.first == "table" && it.second == "ingest_queue" }
        check(definitions.size == 2 && table != null && definitions.any {
            it.first == "index" && it.second == "index_ingest_queue_clientReadingId" &&
                comparable(it.third) == comparable(INDEX)
        }) { "Unsupported version 7 queue objects; original database retained." }
        val actual = comparable(table.third)
        check(actual == comparable(oldSql) || actual == comparable(localSql)) {
            "Unsupported version 7 queue schema; original database retained."
        }

        // The local v7 already has the exact desired schema. Room still performs
        // its normal validation and identity/version update for this migration.
        if (actual == comparable(localSql)) return

        // Neither supported application schema has foreign keys. Do not drop a
        // referenced table in an unexpected database, even with foreign_keys off.
        val tableNames = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
            buildList { while (rows.moveToNext()) add(rows.getString(0)) }
        }
        for (name in tableNames) {
            val quoted = name.replace("\"", "\"\"")
            db.query("PRAGMA foreign_key_list(\"$quoted\")").use { keys ->
                while (keys.moveToNext()) check(keys.getString(2) != "ingest_queue") {
                    "Unexpected queue reference; original database retained."
                }
            }
        }
        val sequence = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'ingest_queue'").use { rows ->
            if (rows.moveToFirst()) {
                check(rows.getType(0) == android.database.Cursor.FIELD_TYPE_INTEGER) {
                    "Invalid queue sequence; original database retained."
                }
                rows.getLong(0).also {
                    check(it >= 0 && !rows.moveToNext()) { "Invalid queue sequence; original database retained." }
                }
            } else null
        }
        // CREATE without IF NOT EXISTS deliberately refuses a conflicting staging table.
        db.execSQL("CREATE TABLE ingest_queue_v8 ($FIELDS DEFAULT '')")
        db.execSQL("INSERT INTO ingest_queue_v8 ($COLUMNS) SELECT $COLUMNS FROM ingest_queue")
        db.execSQL("DROP TABLE ingest_queue")
        db.execSQL("ALTER TABLE ingest_queue_v8 RENAME TO ingest_queue")
        db.execSQL(INDEX)
        // Copying surviving rows alone would lose the watermark of deleted rows,
        // allowing future local IDs to be reused. Preserve an empty queue's watermark too.
        if (sequence != null) {
            db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = 'ingest_queue'", arrayOf(sequence))
            db.execSQL("INSERT INTO sqlite_sequence(name, seq) SELECT 'ingest_queue', ? " +
                "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'ingest_queue')", arrayOf(sequence))
        }
    }
}
