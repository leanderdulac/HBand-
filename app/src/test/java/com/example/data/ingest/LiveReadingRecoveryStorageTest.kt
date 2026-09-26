package com.example.data.ingest

import android.app.Application
import androidx.room.Room
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.WearableRepository
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

/** Synthetic Room/SQLite transaction plus collector, not native SQLCipher/BLE/backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LiveReadingRecoveryStorageTest {
    private lateinit var db: AppDatabase
    private var calls = 0
    private fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        "live-recovery.db").allowMainThreadQueries().build()
    @Before fun prepare() { db = open() }
    @After fun close() { db.close() }
    private fun repository() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(),
        object : SingleOnlyTestApi() {
            override suspend fun checkHealth() = Response.success(HealthCheckResponse())
            override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
                calls++
                throw IOException("Synthetic offline transport")
            }
        }, db.localWriteTransaction())
    private fun reading(second: Int) = HBandTelemetry(
        deviceId = "TEST-WATCH", deviceModel = "Test", timestamp = "2026-09-24T12:00:0${second}Z",
        heartRate = 72, bloodPressure = BloodPressure(0, 0), spO2 = 0,
        temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, sleepSummary = SleepSummary(0, 0, 0), isRealSensorData = true,
    )

    @Test fun optional_measurement_changes_survive_local_capture_and_database_reopen() = runBlocking {
        val original = reading(0)
        val samples = listOf(original,
            original.copy(temperatureCelsius = 36.5f), original.copy(hrvScore = 42),
            original.copy(calories = 3.5f), original.copy(distanceMeters = 12.5f),
            original.copy(sleepSummary = SleepSummary(20, 0, 0)),
            original.copy(sleepSummary = SleepSummary(0, 30, 0)),
            original.copy(sleepSummary = SleepSummary(0, 0, 5)),
        ).mapIndexed { index, value -> value.copy(timestamp = reading(index).timestamp) }
        val repo = repository()
        // Exercise the same dedup gate and local transaction with deterministic elapsed time.
        val deduper = IngestDeduper(elapsedMs = { 1_000L })
        for (sample in samples) {
            repeat(2) { deduper.saveIfNeeded(sample) { repo.persistTelemetry(it, "TEST-PATIENT") } }
        }
        val rows = db.ingestQueueDao().getAllItemsSync()
        val history = db.sensorMetricDao().getAllMetricsList()
        assertEquals("Only exact repeats may be coalesced", samples.size, rows.size)
        assertEquals(samples.size, history.size)
        val ordered = history.sortedBy { it.timestamp }
        assertEquals(samples.map { it.timestamp }, ordered.map { it.timestamp })
        assertEquals(samples.map { it.temperatureCelsius }, ordered.map { it.temperatureCelsius })
        assertEquals(samples.map { it.hrvScore }, ordered.map { it.hrvScore })
        assertEquals(samples.map { it.calories }, ordered.map { it.calories })
        assertEquals(samples.map { it.distanceMeters }, ordered.map { it.distanceMeters })
        assertEquals(samples.map { it.sleepSummary.deepSleepMinutes }, ordered.map { it.deepSleepMinutes })
        assertEquals(samples.map { it.sleepSummary.lightSleepMinutes }, ordered.map { it.lightSleepMinutes })
        assertEquals(samples.map { it.sleepSummary.awakeMinutes }, ordered.map { it.awakeMinutes })
        assertEquals(samples.size, rows.map { it.clientReadingId }.distinct().size)
        assertTrue(rows.all { it.status == "PENDING" })
        assertEquals(0, calls)
        db.close()
        db = open()
        assertEquals(rows, db.ingestQueueDao().getAllItemsSync())
        assertEquals(history, db.sensorMetricDao().getAllMetricsList())
    }

    @Test fun rolled_back_capture_allows_next_sample_and_preserves_committed_pair_on_reopen() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_capture BEFORE INSERT ON ingest_queue
            BEGIN SELECT RAISE(ABORT, 'Synthetic queue failure'); END
        """)
        val repo = repository()
        var failures = 0
        recordLiveReadings(flowOf(reading(0), reading(1), reading(2)), { true },
            { repo.enqueueTelemetry(it, "TEST-PATIENT") }, {
                failures++
                // Save has failed and Room has already rolled back before this callback.
                db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM hband_sensor_metrics").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
                }
                db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_capture")
            }).join()
        assertEquals(1, failures)
        val rows = db.ingestQueueDao().getAllItemsSync()
        val history = db.sensorMetricDao().getAllMetricsList()
        assertEquals(1, rows.size)
        assertEquals(1, history.size)
        assertEquals(reading(1).timestamp, history.single().timestamp)
        assertEquals(reading(1).timestamp, org.json.JSONObject(rows.single().payloadJson).getString("timestamp"))
        assertEquals("PENDING", rows.single().status)
        assertEquals(1, calls)
        db.close()
        db = open()
        assertEquals(rows, db.ingestQueueDao().getAllItemsSync())
        assertEquals(history, db.sensorMetricDao().getAllMetricsList())
    }

    @Test fun network_failure_after_local_commit_does_not_duplicate_capture() = runBlocking {
        val repo = repository()
        val failures = mutableListOf<Exception>()
        recordLiveReadings(flowOf(reading(0), reading(1)), { true },
            { repo.enqueueTelemetry(it, "TEST-PATIENT") }, failures::add).join()
        assertTrue(failures.isEmpty())
        assertEquals(1, db.ingestQueueDao().getAllItemsSync().size)
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(1, calls)
        assertEquals("PENDING", db.ingestQueueDao().getAllItemsSync().single().status)
    }
}
