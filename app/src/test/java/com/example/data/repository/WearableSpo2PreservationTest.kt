package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.ingest.QueueAuthorization
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.HealthTechApiService
import java.io.IOException
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

/** Synthetic Room file and fake batch transport; no Core, BLE, SQLCipher or device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableSpo2PreservationTest {
    private lateinit var db: AppDatabase
    private lateinit var databaseName: String
    private val bodies = mutableListOf<String>()
    private val keys = mutableListOf<String?>()
    private val received = mutableSetOf<String>()
    private var loseNextReceipt = false
    private fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        databaseName).allowMainThreadQueries().build()
    @Before fun prepare() { databaseName = "s.db"; db = open() }
    @After fun close() { db.close() }

    private fun compatible(row: JSONObject): Boolean {
        // Core 75e5e02: Optional[float], inclusive [50,100]; missing/null are allowed.
        if (!row.has("spo2") || row.isNull("spo2")) return true
        val number = when (val value = row.get("spo2")) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
        return number != null && number.isFinite() && number in 50.0..100.0
    }
    private val api = object : HealthTechApiService {
        override suspend fun checkHealth(): Response<HealthCheckResponse> = error("No health probe")
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> =
            error("No single fallback")
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
            assertFalse(db.inTransaction())
            val raw = Buffer().also { body.writeTo(it) }.readUtf8()
            bodies += raw; keys += idempotencyKey
            val rows = JSONObject(raw).getJSONArray("readings")
            val items = (0 until rows.length()).map { rows.getJSONObject(it) }
            if (!items.all(::compatible)) return Response.error(422, "synthetic-spo2-rejection".toResponseBody())
            val results = JSONArray()
            items.forEachIndexed { index, row ->
                val id = row.getString("client_reading_id")
                val duplicate = !received.add(id)
                val status = if (duplicate) "duplicate" else "accepted"
                results.put(JSONObject().put("index", index).put("client_reading_id", id).put("status", status)
                    .put("result", JSONObject().put("patient_id", "TEST-PATIENT")
                        .put("reading_id", "stored-$id").put("ingest_status", status).put("duplicate", duplicate)))
            }
            if (loseNextReceipt) { loseNextReceipt = false; throw IOException("Synthetic lost receipt") }
            return Response.success(JSONObject().put("patient_id", "TEST-PATIENT")
                .put("results", results).toString().toResponseBody())
        }
    }
    private fun repo() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(), api,
        db.localWriteTransaction(), maxBatchItems = 3)
    private fun payload(value: Any?, legacy: Boolean): String = JSONObject()
        .put("patient_id", "TEST-PATIENT").put(if (legacy) "deviceId" else "device_id", "TEST-WATCH")
        .put("timestamp", "2026-09-26T12:00:00Z").put("ingest_source", "http")
        .apply {
            if (legacy) put("metrics", JSONObject().put("heartRate", 72).apply {
                if (value != null) put("spO2", value)
            }) else { put("heart_rate", 72); if (value != null) put("spo2", value) }
        }.toString()
    private suspend fun insert(id: Int, value: Any?, legacy: Boolean) = db.ingestQueueDao().insertItem(
        IngestQueueEntity(id = id.toLong(), createdAt = id.toLong(), clientReadingId = "spo2-$id",
            payloadJson = payload(value, legacy)))

    @Test fun legacyFractionPreserved() {
        for (value in listOf(49.9, 50.0, 98.75, 100.0, 100.5, 255.0)) {
            val normalized = JSONObject(IngestPayloadMapper.normalizeQueuePayload(payload(value, true)))
            assertEquals("Preserve the positive legacy value $value", value, normalized.getDouble("spo2"), 0.0)
        }
        // Existing legacy missing-reading convention is outside this change.
        for (value in listOf(null, JSONObject.NULL, 0, -1)) {
            assertFalse(JSONObject(IngestPayloadMapper.normalizeQueuePayload(payload(value, true))).has("spo2"))
        }
        assertTrue(JSONObject(IngestPayloadMapper.normalizeQueuePayload(payload(JSONObject.NULL, false))).isNull("spo2"))
        assertFalse(JSONObject(IngestPayloadMapper.normalizeQueuePayload(payload(null, false))).has("spo2"))
    }

    @Test fun invalidNeighborsRecovery() = runBlocking {
        val cases = listOf(
            Triple(255, false, false), Triple(98.75, false, true), Triple(100.5, true, false),
            Triple(98.75, true, true), Triple(49.9, true, false), Triple(null, false, true),
            Triple(JSONObject.NULL, false, true), Triple(0, false, false), Triple(50, false, true),
            Triple(100, true, true), Triple("97.5", false, true), Triple("NaN", false, false),
            Triple("", false, false), Triple(JSONObject(), false, false), Triple(null, true, true),
            Triple(0, true, true), Triple(JSONObject.NULL, true, true), Triple(-1, false, false),
        )
        cases.forEachIndexed { index, (value, legacy, _) -> insert(index + 1, value, legacy) }
        val before = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        val good = cases.mapIndexedNotNull { index, row -> if (row.third) "spo2-${index + 1}" else null }.toSet()
        val result = repo().processQueueDetailed()
        assertEquals(good.size, result.syncedCount)
        assertEquals(cases.size - good.size, result.skippedCount)
        assertEquals(cases.size - good.size, result.failedCount)
        assertEquals(good, received)
        val after = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        after.forEach { (id, row) ->
            val original = before.getValue(id)
            assertEquals(original.payloadJson, row.payloadJson)
            assertEquals(original.clientReadingId, row.clientReadingId)
            assertEquals(original.createdAt, row.createdAt)
            assertEquals(original.retries, row.retries)
            assertEquals(if (row.clientReadingId in good) "SYNCED" else "FAILED", row.status)
        }
        db.close(); db = open()
        assertEquals(after, db.ingestQueueDao().getAllItemsSync().associateBy { it.id })
        val calls = bodies.size
        assertEquals(cases.size - good.size, repo().retryAllFailed().skippedCount)
        assertEquals(calls, bodies.size)
        insert(100, 97, false)
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(before.mapValues { it.value.payloadJson }, db.ingestQueueDao().getAllItemsSync()
            .filter { it.id != 100L }.associate { it.id to it.payloadJson })
    }

    @Test fun fractionLostReceipt() = runBlocking {
        insert(1, 98.75, true)
        val original = db.ingestQueueDao().getAllItemsSync().single()
        loseNextReceipt = true
        assertTrue(repo().processQueueDetailed().hadTransientFailure)
        assertEquals(98.75, JSONObject(bodies.single()).getJSONArray("readings").getJSONObject(0).getDouble("spo2"), 0.0)
        db.close(); db = open()
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1]); assertNotNull(keys[0]); assertEquals(keys[0], keys[1])
        val after = db.ingestQueueDao().getAllItemsSync().single()
        assertEquals(original.payloadJson, after.payloadJson)
        assertEquals(original.clientReadingId, after.clientReadingId)
        assertEquals("SYNCED", after.status)
    }

    @Test fun authPausePreserved() = runBlocking {
        insert(1, 255, false)
        val original = db.ingestQueueDao().getAllItemsSync().single().copy(status = "FAILED",
            errorMessage = QueueAuthorization.FORBIDDEN_PREFIX)
        db.ingestQueueDao().updateItem(original)
        assertEquals(1, repo().retryAllFailed().skippedCount)
        val held = db.ingestQueueDao().getAllItemsSync().single()
        assertTrue(QueueAuthorization.isBlocked(held))
        assertEquals(original.payloadJson, held.payloadJson)
        assertEquals(original.clientReadingId, held.clientReadingId)
        assertNotNull(repo().processQueueDetailed().authError)
        assertTrue(bodies.isEmpty())
    }
}
