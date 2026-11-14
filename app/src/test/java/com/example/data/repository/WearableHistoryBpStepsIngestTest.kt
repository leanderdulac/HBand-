package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.SingleOnlyTestApi
import com.example.data.ingest.withSyntheticReceipt
import com.example.data.local.AppDatabase
import com.example.data.local.localWriteTransaction
import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.HealthCheckResponse
import com.example.data.model.IngestResponse
import com.example.data.model.SleepSummary
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

/** Real Room/SQLite with synthetic history. Asserts queued Core payloads, not network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableHistoryBpStepsIngestTest {
    private lateinit var db: AppDatabase
    private val api = object : SingleOnlyTestApi() {
        override suspend fun checkHealth() = Response.success(HealthCheckResponse())
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
            return withSyntheticReceipt(Response.success(IngestResponse(ingest_status = "accepted")), body)
        }
    }

    @Before fun open() {
        db = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, "history-bp.db")
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun repository() = WearableRepository(
        db.ingestQueueDao(), db.sensorMetricDao(), api, db.localWriteTransaction(), maxBatchItems = 1,
    )

    @Test fun hourly_hr_only_winner_still_uploads_origin_bp_and_steps() = runBlocking {
        val origin = sample("2026-09-30T12:05:00Z", hr = 72, systolic = 126, diastolic = 82, steps = 340)
        val laterHr = sample("2026-09-30T12:40:00Z", hr = 90)
        assertEquals(2, repository().persistHistorySamples(listOf(origin, laterHr), "TEST-PATIENT"))
        assertEquals(2, db.sensorMetricDao().getAllMetricsList().size)
        val queued = db.ingestQueueDao().getAllItemsSync()
        assertEquals(1, queued.size)
        val json = JSONObject(queued.single().payloadJson)
        assertEquals(90, json.getInt("heart_rate"))
        assertEquals(126, json.getJSONObject("blood_pressure").getInt("systolic"))
        assertEquals(82, json.getJSONObject("blood_pressure").getInt("diastolic"))
        assertEquals(340, json.getInt("steps"))
    }

    @Test fun origin_bp_without_hr_is_not_queued_alone_and_does_not_invent_heart_rate() = runBlocking {
        val origin = sample("2026-09-30T12:05:00Z", hr = 0, systolic = 126, diastolic = 82, steps = 340)
        repository().persistHistorySamples(listOf(origin), "TEST-PATIENT")
        assertEquals(1, db.sensorMetricDao().getAllMetricsList().size)
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
    }

    @Test fun later_origin_bp_is_queued_when_the_hourly_hr_winner_was_already_stored() = runBlocking {
        val winner = sample("2026-09-30T12:00:00Z", hr = 90)
        repository().persistHistorySamples(listOf(winner), "TEST-PATIENT")
        val before = db.ingestQueueDao().getAllItemsSync()
        assertEquals(1, before.size)
        assertFalse(JSONObject(before.single().payloadJson).has("blood_pressure"))

        val origin = sample("2026-09-30T12:08:00Z", hr = 70, systolic = 119, diastolic = 77, steps = 80)
        assertEquals(1, repository().persistHistorySamples(listOf(winner, origin), "TEST-PATIENT"))
        val queued = db.ingestQueueDao().getAllItemsSync()
        assertEquals(2, queued.size)
        val uploaded = queued.map { JSONObject(it.payloadJson) }
        assertTrue(uploaded.any { it.optJSONObject("blood_pressure")?.optInt("systolic") == 119 && it.getInt("steps") == 80 })
        assertEquals(before.single().id, queued.minByOrNull { it.id }!!.id)
    }

    private fun sample(
        timestamp: String,
        hr: Int,
        systolic: Int = 0,
        diastolic: Int = 0,
        steps: Int = 0,
    ) = HBandTelemetry(
        deviceId = "TEST-WATCH",
        deviceModel = "TEST",
        timestamp = timestamp,
        heartRate = hr,
        bloodPressure = BloodPressure(systolic, diastolic),
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = steps,
        calories = 0f,
        distanceMeters = 0f,
        hrvScore = 0,
        sleepSummary = SleepSummary(),
        isRealSensorData = true,
    )
}
