package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.SingleOnlyTestApi
import com.example.data.ingest.withSyntheticReceipt
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.*
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

/** Real Room/SQLite with synthetic history and receipts, not installed data or Core. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableHistoryReplayTest {
    private lateinit var db: AppDatabase
    private var offline = false
    private val api = object : SingleOnlyTestApi() {
        override suspend fun checkHealth() = Response.success(HealthCheckResponse())
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
            assertFalse(db.inTransaction())
            if (offline) throw java.io.IOException("Synthetic offline")
            return withSyntheticReceipt(Response.success(IngestResponse(ingest_status = "accepted")), body)
        }
    }
    private fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        "history-replay.db").allowMainThreadQueries().build()
    @Before fun prepare() { db = open() }
    @After fun close() { db.close() }
    private fun repository() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(), api,
        db.localWriteTransaction(), maxBatchItems = 1)
    private fun reading(hr: Int = 72) = HBandTelemetry(
        deviceId = "TEST-WATCH", deviceModel = "TEST", timestamp = "2026-09-30T12:00:00Z",
        heartRate = hr, bloodPressure = BloodPressure(), spO2 = 0, temperatureCelsius = 0f,
        steps = 0, calories = 0f, distanceMeters = 0f, hrvScore = 0,
        sleepSummary = SleepSummary(), isRealSensorData = true,
    )

    @Test fun replay_after_database_reopen_preserves_row_ids_and_synced_receipts() = runBlocking {
        val samples = listOf(reading(), reading().copy(timestamp = "2026-09-30T13:00:00Z"))
        assertEquals(2, repository().persistHistorySamples(samples, "TEST-PATIENT"))
        val beforeMetrics = db.sensorMetricDao().getAllMetricsList()
        val beforeQueue = db.ingestQueueDao().getAllItemsSync()
        assertTrue(beforeQueue.all { it.status == "SYNCED" })
        db.close()
        db = open()
        assertEquals(0, repository().persistHistorySamples(samples, "TEST-PATIENT"))
        assertEquals(beforeMetrics, db.sensorMetricDao().getAllMetricsList())
        assertEquals(beforeQueue, db.ingestQueueDao().getAllItemsSync())
    }

    @Test fun duplicate_callbacks_in_one_batch_insert_once() = runBlocking {
        assertEquals(1, repository().persistHistorySamples(List(4) { reading() }, "TEST-PATIENT"))
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(1, db.ingestQueueDao().getAllItemsSync().size)
    }

    @Test fun concurrent_repositories_admit_one_copy_in_the_same_database() = runBlocking {
        val counts = coroutineScope {
            List(4) { async(Dispatchers.IO) { repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT") } }.awaitAll()
        }
        assertEquals(1, counts.sum())
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(1, db.ingestQueueDao().getAllItemsSync().size)
    }

    @Test fun each_changed_field_and_other_device_or_time_survives() = runBlocking {
        val original = reading()
        val samples = listOf(original,
            original.copy(deviceId = "OTHER-WATCH"), original.copy(timestamp = "2026-09-30T12:01:00Z"),
            original.copy(heartRate = 73), original.copy(bloodPressure = BloodPressure(120, 0)),
            original.copy(bloodPressure = BloodPressure(0, 80)), original.copy(spO2 = 97),
            original.copy(temperatureCelsius = 36.5f), original.copy(steps = 42),
            original.copy(calories = 3.5f), original.copy(distanceMeters = 12.5f), original.copy(hrvScore = 34),
            original.copy(sleepSummary = SleepSummary(20, 0, 0)),
            original.copy(sleepSummary = SleepSummary(0, 30, 0)),
            original.copy(sleepSummary = SleepSummary(0, 0, 5)),
        )
        assertEquals(samples.size, repository().persistHistorySamples(samples, "TEST-PATIENT"))
        val before = db.sensorMetricDao().getAllMetricsList().sortedBy { it.id }
        assertEquals(samples.size, before.size)
        assertEquals(0, repository().persistHistorySamples(samples.reversed(), "TEST-PATIENT"))
        assertEquals(before, db.sensorMetricDao().getAllMetricsList().sortedBy { it.id })
    }

    @Test fun later_queue_failure_rolls_back_new_history_and_allows_retry() = runBlocking {
        repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT")
        val before = db.sensorMetricDao().getAllMetricsList()
        val queueBefore = db.ingestQueueDao().getAllItemsSync()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_replay_queue BEFORE INSERT ON ingest_queue
            BEGIN SELECT RAISE(ABORT, 'Synthetic failure'); END
        """)
        val newReading = reading().copy(timestamp = "2026-09-30T13:00:00Z")
        assertTrue(runCatching { repository().persistHistorySamples(listOf(reading(), newReading), "TEST-PATIENT") }.isFailure)
        assertEquals(before, db.sensorMetricDao().getAllMetricsList())
        assertEquals(queueBefore, db.ingestQueueDao().getAllItemsSync())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_replay_queue")
        assertEquals(1, repository().persistHistorySamples(listOf(reading(), newReading), "TEST-PATIENT"))
        assertEquals(2, db.sensorMetricDao().getAllMetricsList().size)
    }

    @Test fun existing_duplicates_are_preserved_but_no_third_copy_is_created() = runBlocking {
        repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT")
        db.sensorMetricDao().insertMetric(db.sensorMetricDao().getAllMetricsList().single().copy(id = 0))
        val before = db.sensorMetricDao().getAllMetricsList().sortedBy { it.id }
        assertEquals(2, before.size)
        assertEquals(0, repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT"))
        assertEquals(before, db.sensorMetricDao().getAllMetricsList().sortedBy { it.id })
    }

    @Test fun replay_keeps_pending_identity_payload_and_existing_retry_path() = runBlocking {
        offline = true
        repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT")
        val before = db.ingestQueueDao().getAllItemsSync().single()
        assertEquals("PENDING", before.status)
        assertEquals(0, repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT"))
        val after = db.ingestQueueDao().getAllItemsSync().single()
        assertEquals(before.id, after.id)
        assertEquals(before.clientReadingId, after.clientReadingId)
        assertEquals(before.payloadJson, after.payloadJson)
        assertEquals("PENDING", after.status)
    }

    @Test fun new_lower_sample_does_not_change_hourly_selection_or_requeue_old_maximum() = runBlocking {
        repository().persistHistorySamples(listOf(reading(90)), "TEST-PATIENT")
        val beforeQueue = db.ingestQueueDao().getAllItemsSync()
        assertEquals(1, repository().persistHistorySamples(listOf(reading(90), reading(70)), "TEST-PATIENT"))
        assertEquals(2, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(beforeQueue, db.ingestQueueDao().getAllItemsSync())
    }

    @Test fun removed_synced_queue_is_not_reconstructed_from_a_history_replay() = runBlocking {
        repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT")
        val before = db.sensorMetricDao().getAllMetricsList()
        db.ingestQueueDao().clearSyncedItems()
        assertEquals(0, repository().persistHistorySamples(listOf(reading()), "TEST-PATIENT"))
        assertEquals(before, db.sensorMetricDao().getAllMetricsList())
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
    }

    @Test fun invalid_observation_time_does_not_use_fallback_as_replay_identity() = runBlocking {
        val unknownTime = reading(0).copy(timestamp = "unknown")
        assertEquals(2, repository().persistHistorySamples(listOf(unknownTime, unknownTime), "TEST-PATIENT"))
        assertEquals(2, db.sensorMetricDao().getAllMetricsList().size)
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
    }
}
