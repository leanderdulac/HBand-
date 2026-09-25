package com.example.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.ingest.IngestReadingIdentity
import com.example.data.ingest.IngestReconciler
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Frozen v6 queue schema from 72e65f4. Other five entity schemas are unchanged. */
@Entity(tableName = "ingest_queue")
data class LegacyQueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "PENDING",
    val retries: Int = 0,
    val createdAt: Long = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
)
@Dao interface LegacyQueueDao { @Query("SELECT COUNT(*) FROM ingest_queue") fun count(): Int }
@Database(entities = [LegacyQueueRow::class, HBandSensorMetricEntity::class, HydrationLogEntity::class,
    BreathingSessionEntity::class, UserProfileEntity::class, AdvancedMeasurementEntity::class],
    version = 6, exportSchema = false)
abstract class LegacyV6Database : RoomDatabase() { abstract fun queue(): LegacyQueueDao }

/** Real Room validation + file-backed synthetic SQLite; not SQLCipher or an installed app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class IngestMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var current: RoomDatabase? = null
    private val otherTables = listOf("hband_sensor_metrics", "hydration_logs", "breathing_sessions",
        "user_profile", "advanced_measurements")
    private val oldColumns = "id,payloadJson,status,retries,createdAt,lastAttemptAt,errorMessage"

    @Before fun prepare() { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    @After fun cleanup() { current?.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }

    private fun legacy(): SupportSQLiteDatabase {
        val db = Room.databaseBuilder(context, LegacyV6Database::class.java, AppDatabase.DATABASE_NAME)
            .allowMainThreadQueries().build()
        current = db
        return db.openHelper.writableDatabase
    }
    private fun migrate(): AppDatabase {
        current?.close()
        return AppDatabase.schemaPreservingBuilder(context).allowMainThreadQueries().build().also { current = it }
    }
    private fun payload(id: String? = null) = JSONObject().put("patient_id", "SYNTHETIC-PATIENT")
        .put("device_id", "SYNTHETIC-WATCH").put("timestamp", "2026-09-24T12:00:00Z")
        .put("heart_rate", 72).apply { if (id != null) put("client_reading_id", id) }.toString()
    private fun insert(db: SupportSQLiteDatabase, id: Int, raw: String, status: String = "PENDING") {
        db.execSQL("INSERT INTO ingest_queue ($oldColumns) VALUES (?,?,?,?,?,?,?)",
            arrayOf<Any>(id, raw, status, 3, 1234L + id, 9000L, "synthetic-original-error"))
    }
    private fun snapshot(db: SupportSQLiteDatabase, table: String, columns: String = "*"): List<List<String?>> =
        db.query("SELECT $columns FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                if (cursor.isNull(it)) null else cursor.getString(it)
            }) }
        }

    @Test fun migration_preserves_every_table_and_queue_field_then_reopens_with_stable_ids() = runBlocking {
        val old = legacy()
        insert(old, 1, payload(), "PENDING")
        insert(old, 2, payload("existing-identity"), "SYNCED")
        insert(old, 3, "invalid legacy JSON retained verbatim", "FAILED")
        for (table in otherTables) {
            val values = mutableListOf<Any>()
            val names = mutableListOf<String>()
            old.query("PRAGMA table_info($table)").use { columns ->
                while (columns.moveToNext()) {
                    names += columns.getString(1)
                    values += when (columns.getString(2)) { "INTEGER" -> 1; "REAL" -> 1.25; else -> "SYNTHETIC" }
                }
            }
            old.execSQL("INSERT INTO $table (${names.joinToString()}) VALUES (${names.joinToString { "?" }})", values.toTypedArray())
        }
        val beforeQueue = snapshot(old, "ingest_queue", oldColumns)
        val beforeOther = otherTables.associateWith { snapshot(old, it) }
        val db = migrate()
        val migrated = db.openHelper.writableDatabase // triggers real Room migration AND schema validation
        assertEquals(7, migrated.version)
        assertEquals(beforeQueue, snapshot(migrated, "ingest_queue", oldColumns))
        for (table in otherTables) assertEquals(beforeOther[table], snapshot(migrated, table))
        val rows = db.ingestQueueDao().getAllItemsSync()
        assertEquals(3, rows.map { it.clientReadingId }.distinct().size)
        assertTrue(rows.all { IngestReadingIdentity.isValid(it.clientReadingId) })
        assertEquals("existing-identity", rows.single { it.id == 2L }.clientReadingId)
        val pending = rows.single { it.id == 1L }
        val request = IngestReconciler.prepare(pending).json
        db.close()
        val reopened = migrate()
        assertEquals(rows, reopened.ingestQueueDao().getAllItemsSync())
        assertEquals(request, IngestReconciler.prepare(reopened.ingestQueueDao().getPendingItems().single()).json)
        val conflict = pending.copy(id = 0, payloadJson = payload(), status = "FAILED")
        assertTrue(runCatching { reopened.ingestQueueDao().insertItem(conflict) }.isFailure)
        assertEquals(rows, reopened.ingestQueueDao().getAllItemsSync())
    }

    @Test fun conflicting_existing_client_ids_roll_back_entire_migration_without_data_loss() {
        val old = legacy()
        insert(old, 1, payload("shared-identity"))
        insert(old, 2, payload("shared-identity"))
        val db = migrate()
        assertTrue(runCatching { db.openHelper.writableDatabase }.isFailure)
        db.close()
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(6, raw.version)
            raw.rawQuery("SELECT $oldColumns FROM ingest_queue ORDER BY id", null).use { rows ->
                assertEquals(2, rows.count)
                while (rows.moveToNext()) assertEquals(payload("shared-identity"), rows.getString(1))
            }
            raw.rawQuery("PRAGMA table_info(ingest_queue)", null).use { columns ->
                assertEquals(7, columns.count)
            }
        }
    }
}
