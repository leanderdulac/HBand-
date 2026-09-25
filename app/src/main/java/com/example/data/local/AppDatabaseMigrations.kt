package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.ingest.IngestReadingIdentity

/**
 * Additive Room migrations. Version 6 → 7 adds a stable [IngestQueueEntity.clientReadingId]
 * and backfills existing queued rows once, preserving valid IDs already in their payloads.
 * Raw payloads and every existing field stay untouched.
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
            val cursor = db.query("SELECT id, payloadJson FROM ingest_queue")
            cursor.use { rows ->
                while (rows.moveToNext()) {
                    val rowId = rows.getLong(0)
                    // Preserve an existing valid identity; never silently rewrite a replay.
                    // Duplicate identities fail the unique index and roll back the migration.
                    val uuid = IngestReadingIdentity.forPayload(rows.getString(1))
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
