package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.ingest.IngestReconciler
import com.example.data.ingest.QueueAuthorization
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.HealthTechApiService
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

/** Synthetic file-backed Room and transport; no native SQLCipher, device or Core HTTP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableFilterPreservationTest {
    private lateinit var db: AppDatabase
    private val sent = mutableListOf<JSONObject>()
    private var calls = 0
    private val allowed = listOf(null, JSONObject.NULL, "", "BMO", "Wavelet", "Butterworth", "Raw", "Adaptive")
    private val invalid = listOf("bmo", " Raw", "Raw ", " ", "unknown", 0, false, JSONObject(), JSONArray())
    private fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        "filter.db").allowMainThreadQueries().build()
    @Before fun prepare() { db = open() }
    @After fun close() { db.close() }

    private fun compatible(row: JSONObject): Boolean {
        val value = row.opt("filter_type")
        // Core 75e5e02 schemas.py: Optional[str], exact enum; null/empty retained, not defaulted.
        return !row.has("filter_type") || value == JSONObject.NULL ||
            (value is String && value in listOf("", "BMO", "Wavelet", "Butterworth", "Raw", "Adaptive"))
    }
    private val api = object : HealthTechApiService {
        override suspend fun checkHealth(): Response<HealthCheckResponse> = error("No health probe expected")
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> =
            error("No single-ingest fallback")
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
            calls++
            assertFalse(db.inTransaction())
            val request = JSONObject(Buffer().also { body.writeTo(it) }.readUtf8())
            val rows = request.getJSONArray("readings")
            val items = (0 until rows.length()).map { rows.getJSONObject(it) }
            sent.addAll(items)
            if (!items.all(::compatible)) return Response.error(422, "synthetic-filter-rejection".toResponseBody())
            val results = JSONArray()
            items.forEachIndexed { index, row ->
                results.put(JSONObject().put("index", index).put("status", "accepted")
                    .put("client_reading_id", row.getString("client_reading_id"))
                    .put("result", JSONObject().put("patient_id", "TEST-PATIENT")
                        .put("reading_id", "stored-${row.getString("client_reading_id")}")
                        .put("ingest_status", "accepted").put("duplicate", false)))
            }
            return Response.success(JSONObject().put("patient_id", "TEST-PATIENT")
                .put("status", "success").put("results", results).toString().toResponseBody())
        }
    }
    private fun repo() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(), api,
        db.localWriteTransaction(), maxBatchItems = 3)
    private fun payload(value: Any?, legacy: Boolean) = JSONObject().put("patient_id", "TEST-PATIENT")
        .put(if (legacy) "deviceId" else "device_id", "TEST-WATCH")
        .put("timestamp", "2026-09-26T12:00:00Z").put("ingest_source", "http")
        .apply {
            if (legacy) put("metrics", JSONObject().put("heartRate", 72)) else put("heart_rate", 72)
            if (value != null) put("filter_type", value) // Kotlin null means absent, JSONObject.NULL explicit.
        }.toString()
    private suspend fun insert(id: Int, value: Any?, legacy: Boolean) = db.ingestQueueDao().insertItem(
        IngestQueueEntity(id = id.toLong(), createdAt = id.toLong(), clientReadingId = "filter-$id",
            payloadJson = payload(value, legacy)))

    @Test fun normalization_preserves_explicit_filter_and_never_invents_absent_filter() {
        for (legacy in listOf(false, true)) for (value in allowed + invalid) {
            val raw = JSONObject(payload(value, legacy))
            val normalized = JSONObject(IngestPayloadMapper.normalizeQueuePayload(raw.toString()))
            assertEquals("Presence for legacy=$legacy value=$value", raw.has("filter_type"), normalized.has("filter_type"))
            assertEquals(raw.opt("filter_type")?.toString(), normalized.opt("filter_type")?.toString())
            assertEquals("http", normalized.getString("ingest_source"))
        }
    }

    @Test fun incompatible_filters_do_not_block_valid_neighbors_and_survive_reopen_and_retry() = runBlocking {
        var id = 0
        val expected = mutableMapOf<String, JSONObject>()
        // Interleave to exercise selection before chunking, not merely a trailing bad record.
        for (legacy in listOf(false, true)) for (index in invalid.indices) {
            insert(++id, invalid[index], legacy)
            if (index < allowed.size) {
                insert(++id, allowed[index], legacy)
                expected["filter-$id"] = JSONObject(payload(allowed[index], legacy))
            }
        }
        val before = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        val result = repo().processQueueDetailed()
        assertEquals(expected.size, result.syncedCount)
        assertEquals(invalid.size * 2, result.failedCount)
        assertEquals(invalid.size * 2, result.skippedCount)
        assertEquals(expected.keys, sent.map { it.getString("client_reading_id") }.toSet())
        sent.forEach { row ->
            val original = expected.getValue(row.getString("client_reading_id"))
            assertEquals(original.has("filter_type"), row.has("filter_type"))
            assertEquals(original.opt("filter_type"), row.opt("filter_type"))
            assertEquals(original.get("ingest_source"), row.get("ingest_source"))
        }
        val after = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        for ((key, row) in after) {
            val original = before.getValue(key)
            assertEquals(original.payloadJson, row.payloadJson)
            assertEquals(original.clientReadingId, row.clientReadingId)
            assertEquals(original.createdAt, row.createdAt)
            assertEquals(original.retries, row.retries)
            assertEquals(IngestReconciler.prepare(original), IngestReconciler.prepare(row).copy(item = original))
            assertEquals(IngestReconciler.flushIdempotencyKey(listOf(IngestReconciler.prepare(original))),
                IngestReconciler.flushIdempotencyKey(listOf(IngestReconciler.prepare(row))))
        }
        db.close(); db = open()
        assertEquals(after, db.ingestQueueDao().getAllItemsSync().associateBy { it.id })
        val previousCalls = calls
        val retry = repo().retryAllFailed()
        assertEquals(0, retry.syncedCount)
        assertEquals(invalid.size * 2, retry.skippedCount)
        assertEquals(previousCalls, calls)
        val retried = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        assertEquals(before.mapValues { it.value.payloadJson }, retried.mapValues { it.value.payloadJson })
        assertEquals(before.mapValues { it.value.clientReadingId }, retried.mapValues { it.value.clientReadingId })
        insert(++id, "Raw", true)
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(previousCalls + 1, calls)
    }

    @Test fun invalid_filter_does_not_remove_persisted_authorization_pause() = runBlocking {
        insert(1, "unknown", false)
        val original = db.ingestQueueDao().getAllItemsSync().single().copy(status = "FAILED",
            errorMessage = QueueAuthorization.FORBIDDEN_PREFIX)
        db.ingestQueueDao().updateItem(original)
        assertEquals(1, repo().retryAllFailed().skippedCount)
        val held = db.ingestQueueDao().getAllItemsSync().single()
        assertTrue(QueueAuthorization.isBlocked(held))
        assertEquals(original.payloadJson, held.payloadJson)
        assertEquals(original.clientReadingId, held.clientReadingId)
        assertNotNull(repo().processQueueDetailed().authError)
        assertEquals(0, calls)
    }
}
