package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.IngestApiKey
import com.example.data.ingest.QueueAuthorization
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.*
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

/** File-backed synthetic Room only. Never connects to a backend or installed app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableIngestAccessTest {
    private lateinit var db: AppDatabase
    private val calls = mutableListOf<String>()
    private var healthFailure = false
    private var transport = IngestTransport(configurationError = IngestApiKey.CONFIGURATION_ERROR)
    private var captures = 0
    private var firstBatch: () -> Unit = {}
    private fun openDb() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, "access.db")
        .allowMainThreadQueries().build()
    @Before fun open() {
        RuntimeEnvironment.getApplication().deleteDatabase("access.db")
        db = openDb()
    }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("access.db") }
    private fun api(name: String) = object : HealthTechApiService {
        override suspend fun checkHealth(): Response<HealthCheckResponse> {
            calls += "health"
            if (healthFailure) throw IOException("synthetic-private-key-and-url")
            return Response.success(HealthCheckResponse())
        }
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> =
            throw AssertionError("Single ingest must not be used")
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
            calls += name
            firstBatch.also { firstBatch = {} }.invoke()
            val request = JSONObject(Buffer().also { body.writeTo(it) }.readUtf8())
            val rows = request.getJSONArray("readings")
            val results = JSONArray()
            for (i in 0 until rows.length()) {
                val id = rows.getJSONObject(i).getString("client_reading_id")
                results.put(JSONObject().put("index", i).put("client_reading_id", id).put("status", "accepted")
                    .put("result", JSONObject().put("patient_id", request.getString("patient_id"))
                        .put("ingest_status", "accepted").put("reading_id", "stored-$id")))
            }
            return Response.success(JSONObject().put("patient_id", request.getString("patient_id"))
                .put("status", "success").put("results", results).toString().toResponseBody())
        }
    }
    private fun repo() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(), api("forbidden-facade"),
        db.localWriteTransaction(), db.advancedMeasurementDao(), maxBatchItems = 1,
        ingestTransportProvider = { captures++; transport })
    private suspend fun insert(id: Long, status: String = "PENDING", error: String? = null) {
        db.ingestQueueDao().insertItem(IngestQueueEntity(id = id, payloadJson =
            """{"patient_id":"SYNTHETIC-A","device_id":"SYNTHETIC-D","timestamp":"2026-09-24T12:00:00Z","heart_rate":72}""",
            status = status, retries = 2, createdAt = id, lastAttemptAt = 20, errorMessage = error,
            clientReadingId = "synthetic-$id"))
    }

    @Test fun missing_access_keeps_all_fields() = runBlocking {
        insert(1); insert(2, "FAILED", "synthetic failure"); insert(3, "SYNCED")
        val before = db.ingestQueueDao().getAllItemsSync()
        assertNotNull(repo().processQueueDetailed().configurationError)
        assertNotNull(repo().retryAllFailed().configurationError)
        assertNotNull(repo().retryFailedItem(2).configurationError)
        assertEquals(before, db.ingestQueueDao().getAllItemsSync())
        assertTrue(calls.isEmpty())
    }

    @Test fun restart_keeps_pause_until_explicit_retry() = runBlocking {
        insert(1, "FAILED", QueueAuthorization.UNAUTHORIZED_PREFIX + " legacy private error")
        insert(2)
        val before = db.ingestQueueDao().getAllItemsSync()
        db.close(); db = openDb()
        assertNotNull(repo().processQueueDetailed().authError)
        assertEquals(0, captures)
        assertNotNull(repo().retryAllFailed().configurationError)
        assertEquals(before, db.ingestQueueDao().getAllItemsSync())
        transport = IngestTransport(service = api("corrected"))
        assertNotNull(repo().processQueueDetailed().authError)
        assertTrue(calls.isEmpty())
        assertEquals(before, db.ingestQueueDao().getAllItemsSync())
        assertEquals(2, repo().retryAllFailed().syncedCount)
        assertEquals(listOf("corrected", "corrected"), calls)
    }

    @Test fun rotation_keeps_one_transport_per_attempt() = runBlocking {
        insert(1); insert(2)
        transport = IngestTransport(service = api("old"))
        firstBatch = { transport = IngestTransport(service = api("new")) }
        assertEquals(2, repo().processQueueDetailed().syncedCount)
        assertEquals(1, captures)
        assertEquals(listOf("old", "old"), calls)
        insert(3)
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(listOf("old", "old", "new"), calls)
        assertEquals(2, captures)
    }

    @Test fun health_never_sends_or_clears_queue() = runBlocking {
        insert(1, "FAILED", QueueAuthorization.FORBIDDEN_PREFIX)
        val before = db.ingestQueueDao().getAllItemsSync()
        assertTrue(repo().checkApiHealth().isOnline)
        healthFailure = true
        val failed = repo().checkApiHealth()
        assertFalse(failed.isOnline)
        assertFalse(failed.message.contains("synthetic-private"))
        assertEquals(listOf("health", "health"), calls)
        assertEquals(before, db.ingestQueueDao().getAllItemsSync())
        assertEquals(0, captures)
    }
}
