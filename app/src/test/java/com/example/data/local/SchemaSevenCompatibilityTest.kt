package com.example.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.ingest.QueueAuthorization
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Frozen queue schema at published PR5 e9a80ef; its other five entities are unchanged. */
@Entity(tableName = "ingest_queue", indices = [Index(value = ["clientReadingId"], unique = true)])
data class PublishedV7QueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "PENDING",
    val retries: Int = 0,
    val createdAt: Long = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    val clientReadingId: String,
)

/** Frozen queue schema at local ffe5210; SQL DEFAULT differs from the published v7. */
@Entity(tableName = "ingest_queue", indices = [Index(value = ["clientReadingId"], unique = true)])
data class LocalV7QueueRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "PENDING",
    val retries: Int = 0,
    val createdAt: Long = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "''") val clientReadingId: String,
)

@Dao interface V7QueueCountDao { @Query("SELECT COUNT(*) FROM ingest_queue") fun count(): Int }

@Database(entities = [PublishedV7QueueRow::class, HBandSensorMetricEntity::class,
    HydrationLogEntity::class, BreathingSessionEntity::class, UserProfileEntity::class,
    AdvancedMeasurementEntity::class], version = 7, exportSchema = false)
abstract class PublishedV7Database : RoomDatabase() { abstract fun queue(): V7QueueCountDao }

@Database(entities = [LocalV7QueueRow::class, HBandSensorMetricEntity::class,
    HydrationLogEntity::class, BreathingSessionEntity::class, UserProfileEntity::class,
    AdvancedMeasurementEntity::class], version = 7, exportSchema = false)
abstract class LocalV7Database : RoomDatabase() { abstract fun queue(): V7QueueCountDao }

/** Actual Room file opening/validation, synthetic SQLite only; no app startup or transport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SchemaSevenCompatibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var database: RoomDatabase? = null
    private val tables = listOf("ingest_queue", "hband_sensor_metrics", "hydration_logs",
        "breathing_sessions", "user_profile", "advanced_measurements")

    @After fun cleanup() {
        database?.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun old(published: Boolean): SupportSQLiteDatabase {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        database = if (published) {
            Room.databaseBuilder(context, PublishedV7Database::class.java, AppDatabase.DATABASE_NAME)
                .allowMainThreadQueries().build()
        } else {
            Room.databaseBuilder(context, LocalV7Database::class.java, AppDatabase.DATABASE_NAME)
                .allowMainThreadQueries().build()
        }
        return database!!.openHelper.writableDatabase
    }

    private fun seed(sql: SupportSQLiteDatabase) {
        for ((index, status) in listOf("PENDING", "FAILED", "FAILED", "SYNCED").withIndex()) {
            sql.execSQL("INSERT INTO ingest_queue VALUES (?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                index + 1, "original-payload-$index", status, index, 1000L + index,
                if (index == 0) null else 2000L + index,
                when (index) { 1 -> QueueAuthorization.UNAUTHORIZED_PREFIX; 2 -> QueueAuthorization.FORBIDDEN_PREFIX; else -> null }, "stable-id-$index",
            ))
        }
        // A deleted high row establishes an AUTOINCREMENT watermark above all live rows.
        sql.execSQL("INSERT INTO ingest_queue VALUES (900, 'removed-synthetic', 'PENDING', 0, 0, NULL, NULL, 'removed-id')")
        sql.execSQL("DELETE FROM ingest_queue WHERE id = 900")
        for (table in tables.drop(1)) {
            val names = mutableListOf<String>()
            val values = mutableListOf<Any>()
            sql.query("PRAGMA table_info($table)").use { columns ->
                while (columns.moveToNext()) {
                    names += columns.getString(1)
                    values += when (columns.getString(2)) { "INTEGER" -> 1; "REAL" -> 1.25; else -> "SYNTHETIC" }
                }
            }
            sql.execSQL("INSERT INTO $table (${names.joinToString()}) VALUES (${names.joinToString { "?" }})", values.toTypedArray())
        }
    }

    private fun snapshot(sql: SupportSQLiteDatabase): Map<String, List<List<Pair<Int, String?>>>> =
        tables.associateWith { table ->
            sql.query("SELECT * FROM $table ORDER BY id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                        cursor.getType(it) to if (cursor.isNull(it)) null else cursor.getString(it)
                    })
                }
            }
        }

    private fun current(): SupportSQLiteDatabase {
        database?.close()
        database = AppDatabase.schemaPreservingBuilder(context).allowMainThreadQueries().build()
        return database!!.openHelper.writableDatabase
    }

    private fun preservesOrigin(published: Boolean) {
        val before = old(published)
        seed(before)
        val expected = snapshot(before)
        val opened = current()
        assertEquals(8, opened.version)
        assertEquals(expected, snapshot(opened))
        opened.query("SELECT seq FROM sqlite_sequence WHERE name = 'ingest_queue'").use {
            assertTrue(it.moveToFirst())
            assertEquals(900L, it.getLong(0))
        }
        val reopened = current()
        assertEquals(expected, snapshot(reopened))
        runBlocking {
            val dao = (database as AppDatabase).ingestQueueDao()
            assertTrue(dao.hasAuthorizationBlock(QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
            val rows = dao.getAllItemsSync()
            assertTrue(runCatching { dao.insertItem(rows.first().copy(id = 0)) }.isFailure)
            assertEquals(rows, dao.getAllItemsSync())
            assertEquals(901L, dao.insertItem(rows.first().copy(id = 0, clientReadingId = "new-reading")))
        }
    }

    @Test fun published_v7_preserves_all_rows_and_reopens() = preservesOrigin(published = true)
    @Test fun local_v7_preserves_all_rows_and_reopens() = preservesOrigin(published = false)

    @Test fun published_v7_empty_queue_preserves_deleted_row_watermark() {
        val before = old(published = true)
        seed(before)
        before.execSQL("DELETE FROM ingest_queue")
        val expected = snapshot(before)
        val after = current()
        assertEquals(expected, snapshot(after))
        after.execSQL("INSERT INTO ingest_queue (payloadJson,status,retries,createdAt,clientReadingId) " +
            "VALUES ('new-synthetic','PENDING',0,0,'new-reading')")
        after.query("SELECT id FROM ingest_queue").use {
            assertTrue(it.moveToFirst())
            assertEquals(901L, it.getLong(0))
        }
    }

    @Test fun published_v7_never_used_queue_opens_without_inventing_rows() {
        old(published = true)
        val after = current()
        assertEquals(8, after.version)
        assertTrue(snapshot(after).values.all { it.isEmpty() })
    }

    private fun rejectedMutationPreservesOriginal(mutate: (SupportSQLiteDatabase) -> Unit) {
        val before = old(published = true)
        seed(before)
        mutate(before)
        val expected = snapshot(before)
        val schema = before.query("SELECT type,name,sql FROM sqlite_master ORDER BY type,name").use { rows ->
            buildList { while (rows.moveToNext()) add((0..2).map { rows.getString(it) }) }
        }
        assertTrue(runCatching { current() }.isFailure)
        database?.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(AppDatabase.DATABASE_NAME).path,
            null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(7, raw.version)
            for (table in tables) raw.rawQuery("SELECT * FROM $table ORDER BY id", null).use { rows ->
                val actual = buildList {
                    while (rows.moveToNext()) add((0 until rows.columnCount).map {
                        rows.getType(it) to if (rows.isNull(it)) null else rows.getString(it)
                    })
                }
                assertEquals(expected[table], actual)
            }
            raw.rawQuery("SELECT type,name,sql FROM sqlite_master ORDER BY type,name", null).use { rows ->
                val actual = buildList { while (rows.moveToNext()) add((0..2).map { rows.getString(it) }) }
                assertEquals(schema, actual)
            }
            raw.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='ingest_queue'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(900L, it.getLong(0))
            }
        }
    }

    @Test fun unknown_queue_column_is_refused_without_discarding_data() = rejectedMutationPreservesOriginal {
        it.execSQL("ALTER TABLE ingest_queue ADD COLUMN unknown_field TEXT DEFAULT 'preserved'")
    }

    @Test fun unknown_trigger_is_refused_without_discarding_data() = rejectedMutationPreservesOriginal {
        it.execSQL("CREATE TRIGGER unknown_queue_trigger AFTER INSERT ON ingest_queue BEGIN SELECT 1; END")
    }

    @Test fun unknown_index_is_refused_without_discarding_data() = rejectedMutationPreservesOriginal {
        it.execSQL("CREATE INDEX unknown_queue_index ON ingest_queue (status)")
    }

    @Test fun unknown_sql_default_is_refused_without_discarding_data() = rejectedMutationPreservesOriginal {
        val ddl = it.query("SELECT sql FROM sqlite_master WHERE name='ingest_queue'").use { rows ->
            assertTrue(rows.moveToFirst()); rows.getString(0)
        }
        it.execSQL(ddl.replace("`ingest_queue`", "`unexpected_queue`").dropLast(1) + " DEFAULT 'bogus')")
        it.execSQL("INSERT INTO unexpected_queue SELECT * FROM ingest_queue")
        it.execSQL("DROP TABLE ingest_queue")
        it.execSQL("ALTER TABLE unexpected_queue RENAME TO ingest_queue")
        it.execSQL("CREATE UNIQUE INDEX index_ingest_queue_clientReadingId ON ingest_queue(clientReadingId)")
        it.execSQL("UPDATE sqlite_sequence SET seq=900 WHERE name='ingest_queue'")
    }

    @Test fun staging_name_collision_is_refused_without_discarding_data() = rejectedMutationPreservesOriginal {
        it.execSQL("CREATE TABLE ingest_queue_v8 (evidence TEXT)")
        it.execSQL("INSERT INTO ingest_queue_v8 VALUES ('preserved')")
    }

    @Test fun room_validation_failure_rolls_back_queue_rebuild_and_version() = rejectedMutationPreservesOriginal {
        it.execSQL("ALTER TABLE hydration_logs ADD COLUMN unknown_field TEXT DEFAULT 'preserved'")
    }

    @Test fun referenced_queue_is_refused_before_copying_or_cascading() = rejectedMutationPreservesOriginal {
        it.execSQL("CREATE TABLE external_reference (queue_id INTEGER REFERENCES ingest_queue(id) ON DELETE CASCADE)")
        it.execSQL("INSERT INTO external_reference VALUES (1)")
    }
}
