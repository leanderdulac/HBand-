package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.HealthTechApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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

/** Real Room/SQLite transactions with synthetic records; never the installed database or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableAtomicRecordingTest {
    private lateinit var db: AppDatabase
    private var networkCalls = 0
    private var failNetwork = false
    private var healthCalls = 0
    private val api = object : com.example.data.ingest.SingleOnlyTestApi() {
        override suspend fun checkHealth(): Response<HealthCheckResponse> {
            assertFalse(db.inTransaction())
            healthCalls++
            return Response.success(null)
        }
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
            assertFalse("Network must run outside the local write transaction", db.inTransaction())
            networkCalls++
            if (failNetwork) throw java.io.IOException("Synthetic network failure after commit")
            return com.example.data.ingest.withSyntheticReceipt(Response.success(IngestResponse(ingest_status = "accepted")), body)
        }
    }

    @Before fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun closeDatabase() { db.close() }

    private fun repository(queue: IngestQueueDao = db.ingestQueueDao()) = WearableRepository(
        queue, db.sensorMetricDao(), api, advancedMeasurementDao = db.advancedMeasurementDao(),
        localWriteTransaction = db.localWriteTransaction(), maxBatchItems = 1,
    )

    private fun sample(hour: Int = 12, hr: Int = 72) = HBandTelemetry(
        deviceId = "TEST-WATCH", deviceModel = "TEST", timestamp = "2026-09-24T${hour}:00:00Z",
        heartRate = hr, bloodPressure = BloodPressure(), spO2 = 0, temperatureCelsius = 0f,
        steps = 0, calories = 0f, distanceMeters = 0f, hrvScore = 0, sleepSummary = SleepSummary(),
        isRealSensorData = true,
    )

    private fun rejectQueueWrites() {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_queue BEFORE INSERT ON ingest_queue
            BEGIN SELECT RAISE(ABORT, 'Synthetic queue write failure'); END
        """)
    }

    private suspend fun assertEmpty() {
        assertTrue(db.sensorMetricDao().getAllMetricsList().isEmpty())
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
        assertTrue(db.advancedMeasurementDao().getAll().first().isEmpty())
        assertEquals(0, networkCalls)
        assertEquals(0, healthCalls)
    }

    @Test fun queue_failure_rolls_back_live_metric() = runBlocking {
        rejectQueueWrites()
        assertTrue(runCatching { repository().enqueueTelemetry(sample(), "TEST-PATIENT") }.isFailure)
        assertEmpty()
    }

    @Test fun queue_failure_rolls_back_manual_capture_before_health_check_or_send() = runBlocking {
        rejectQueueWrites()
        assertTrue(runCatching { repository().enqueueAndProcessTelemetry(sample(), "TEST-PATIENT") }.isFailure)
        assertEmpty()
    }

    @Test fun later_hourly_queue_failure_rolls_back_entire_history_batch() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_second_queue BEFORE INSERT ON ingest_queue
            WHEN (SELECT COUNT(*) FROM ingest_queue) = 1
            BEGIN SELECT RAISE(ABORT, 'Synthetic second queue write failure'); END
        """)
        assertTrue(runCatching {
            repository().persistHistorySamples(listOf(sample(12), sample(13)), "TEST-PATIENT")
        }.isFailure)
        assertEmpty()
    }

    @Test fun queue_failure_rolls_back_advanced_ecg_record() = runBlocking {
        rejectQueueWrites()
        val ecg = AdvancedMeasurementEntity(deviceId = "TEST-WATCH", kind = AdvancedMeasurementKind.ECG,
            timestamp = "2026-09-24T12:00:00Z", summary = "Synthetic", numericValue = 72f)
        assertTrue(runCatching { repository().persistAdvancedSample(ecg, "TEST-PATIENT") }.isFailure)
        assertEmpty()
    }

    @Test fun cancellation_between_writes_rolls_back_and_allows_a_later_capture() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val blockingQueue = object : IngestQueueDao by db.ingestQueueDao() {
            override suspend fun insertItem(item: IngestQueueEntity): Long {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val job = launch(Dispatchers.IO) { repository(blockingQueue).enqueueTelemetry(sample(), "TEST-PATIENT") }
        withTimeout(5_000) { entered.await() }
        job.cancelAndJoin()
        assertEmpty()
        repository().enqueueTelemetry(sample(), "TEST-PATIENT")
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(1, db.ingestQueueDao().getAllItemsSync().size)
        assertEquals(1, networkCalls)
    }

    @Test fun successful_capture_keeps_both_records_and_sends_after_commit() = runBlocking {
        repository().enqueueTelemetry(sample(), "TEST-PATIENT")
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals("SYNCED", db.ingestQueueDao().getAllItemsSync().single().status)
        assertEquals(1, networkCalls)
    }

    @Test fun history_without_ingestible_heart_rate_still_saves_locally_only() = runBlocking {
        repository().persistHistorySamples(listOf(sample(hr = 0)), "TEST-PATIENT")
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
        assertEquals(0, networkCalls)
    }

    @Test fun first_metric_write_failure_leaves_no_queue_item() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_metric BEFORE INSERT ON hband_sensor_metrics
            BEGIN SELECT RAISE(ABORT, 'Synthetic metric write failure'); END
        """)
        assertTrue(runCatching { repository().enqueueTelemetry(sample(), "TEST-PATIENT") }.isFailure)
        assertEmpty()
    }

    @Test fun successful_history_keeps_all_metrics_and_existing_hourly_selection() = runBlocking {
        val saved = repository().persistHistorySamples(listOf(sample(12), sample(12, 80), sample(13)), "TEST-PATIENT")
        assertEquals(3, saved)
        assertEquals(3, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(2, db.ingestQueueDao().getAllItemsSync().size)
        assertEquals(2, networkCalls)
    }

    @Test fun non_ecg_advanced_reading_remains_local_without_fabricated_heart_rate() = runBlocking {
        repository().persistAdvancedSample(AdvancedMeasurementEntity(deviceId = "TEST-WATCH",
            kind = AdvancedMeasurementKind.GLUCOSE, timestamp = "2026-09-24T12:00:00Z",
            summary = "Synthetic", numericValue = 90f), "TEST-PATIENT")
        assertEquals(1, db.advancedMeasurementDao().getAll().first().size)
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
        assertEquals(0, networkCalls)
    }

    @Test fun network_failure_after_commit_keeps_both_records_for_later_delivery() = runBlocking {
        failNetwork = true
        repository().enqueueTelemetry(sample(), "TEST-PATIENT")
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        val row = db.ingestQueueDao().getAllItemsSync().single()
        assertEquals("PENDING", row.status)
        assertEquals(1, row.retries)
        assertEquals(1, networkCalls)
    }
}
