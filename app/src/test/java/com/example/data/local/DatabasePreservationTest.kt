package com.example.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.example.data.ingest.QueueAuthorization
import com.example.data.model.*
import com.example.data.remote.HealthTechApiService
import com.example.data.repository.WearableRepository
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Synthetic file-backed SQLite only: no installed database, SQLCipher or real HTTP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DatabasePreservationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var db: AppDatabase? = null
    private var calls = 0
    private var code = 503
    private val api = object : com.example.data.ingest.SingleOnlyTestApi() {
        override suspend fun checkHealth() = retrofit2.Response.success(HealthCheckResponse())
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): retrofit2.Response<IngestResponse> {
            calls++
            return retrofit2.Response.error(code, "Synthetic failure".toResponseBody())
        }
    }

    @Before fun prepare() { context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun open(): AppDatabase = AppDatabase.schemaPreservingBuilder(context)
        .allowMainThreadQueries().build().also { db = it }

    private fun repository(database: AppDatabase) = WearableRepository(
        database.ingestQueueDao(), database.sensorMetricDao(), api,
        database.localWriteTransaction(), database.advancedMeasurementDao(), maxBatchItems = 1,
    )

    private fun sample() = HBandTelemetry(
        deviceId = "SYNTHETIC-WATCH", deviceModel = "TEST", timestamp = "2026-09-24T12:00:00Z",
        heartRate = 72, bloodPressure = BloodPressure(), spO2 = 0, temperatureCelsius = 0f,
        steps = 0, calories = 0f, distanceMeters = 0f, hrvScore = 0, sleepSummary = SleepSummary(),
        isRealSensorData = true,
    )

    @Test fun failed_send_keeps_exact_queue_and_history_after_database_reopen() = runBlocking {
        val first = open()
        repository(first).enqueueTelemetry(sample(), "SYNTHETIC-PATIENT")
        val queue = first.ingestQueueDao().getAllItemsSync()
        val history = first.sensorMetricDao().getAllMetricsList()
        assertEquals(1, queue.size)
        assertEquals(1, history.size)
        assertEquals(QueueStatus.PENDING.name, queue.single().status)
        first.close()
        val reopened = open()
        assertEquals(queue, reopened.ingestQueueDao().getAllItemsSync())
        assertEquals(history, reopened.sensorMetricDao().getAllMetricsList())
        assertEquals(1, calls)
    }

    @Test fun authorization_pause_survives_database_reopen_and_inconclusive_manual_retry() = runBlocking {
        code = 401
        val first = open()
        repository(first).enqueueTelemetry(sample(), "SYNTHETIC-PATIENT")
        first.close()
        val second = open()
        assertNotNull(repository(second).processQueueDetailed().authError)
        assertEquals(1, calls)
        code = 503
        repository(second).retryAllFailed()
        assertEquals(2, calls)
        val queue = second.ingestQueueDao().getAllItemsSync()
        assertTrue(QueueAuthorization.isBlocked(queue.single()))
        second.close()
        val third = open()
        assertEquals(queue, third.ingestQueueDao().getAllItemsSync())
        assertNotNull(repository(third).processQueueDetailed().authError)
        assertEquals(2, calls)
        assertEquals(1, third.sensorMetricDao().getAllMetricsList().size)
    }

    private fun assertUnknownSchemaPreserved(version: Int) {
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL("CREATE TABLE preserved_reading (id INTEGER PRIMARY KEY, payload TEXT NOT NULL)")
            raw.execSQL("INSERT INTO preserved_reading VALUES (1, 'synthetic-original')")
            raw.version = version
        }
        val candidate = open()
        assertTrue(runCatching { candidate.openHelper.writableDatabase }.isFailure)
        candidate.close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertEquals(version, raw.version)
            raw.rawQuery("SELECT payload FROM preserved_reading WHERE id = 1", null).use { rows ->
                assertTrue(rows.moveToFirst())
                assertEquals("synthetic-original", rows.getString(0))
            }
        }
    }

    @Test fun unsupported_upgrade_preserves_original_database() = assertUnknownSchemaPreserved(5)
    @Test fun unsupported_downgrade_preserves_original_database() = assertUnknownSchemaPreserved(9)

    @Test fun legacy_plaintext_is_blocked_without_removing_database_or_sidecars() {
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        file.parentFile!!.mkdirs()
        val original = "SQLite format 3\u0000synthetic-record".toByteArray()
        file.writeBytes(original)
        val sidecars = listOf("-wal", "-shm", "-journal").map { File(file.path + it) }
        sidecars.forEach { it.writeText("synthetic-sidecar") }
        assertTrue(runCatching { SqliteFileInspector.requireEncryptedInput(file) }.isFailure)
        assertArrayEquals(original, file.readBytes())
        sidecars.forEach { assertEquals("synthetic-sidecar", it.readText()) }
    }
}
