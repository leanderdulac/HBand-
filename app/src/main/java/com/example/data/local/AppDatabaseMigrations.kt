package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Additive Room migrations. Version 6 → 7 adds a stable [IngestQueueEntity.clientReadingId]
 * and backfills every existing queued row with its own UUID so retries stay idempotent.
 */
object AppDatabaseMigrations {
    const val VERSION_BEFORE_CLIENT_READING_ID = 6
    const val VERSION_WITH_CLIENT_READING_ID = 7

    val MIGRATION_6_7: Migration = object : Migration(
        VERSION_BEFORE_CLIENT_READING_ID,
        VERSION_WITH_CLIENT_READING_ID,
    ) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE ingest_queue ADD COLUMN clientReadingId TEXT NOT NULL DEFAULT ''"
            )
            val cursor = db.query("SELECT id FROM ingest_queue")
            cursor.use { rows ->
                while (rows.moveToNext()) {
                    val rowId = rows.getLong(0)
                    val uuid = UUID.randomUUID().toString()
                    db.execSQL(
                        "UPDATE ingest_queue SET clientReadingId = ? WHERE id = ?",
                        arrayOf<Any>(uuid, rowId),
                    )
                }
            }
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_ingest_queue_clientReadingId " +
                    "ON ingest_queue (clientReadingId)"
            )
        }
    }
}
