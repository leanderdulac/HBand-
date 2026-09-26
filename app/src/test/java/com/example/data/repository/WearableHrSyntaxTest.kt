package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.IngestPayloadMapper
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

/** Real synthetic Room file and fake HTTP; Core float examples verified separately offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableHrSyntaxTest {
    private lateinit var db: AppDatabase
    private val requests = mutableListOf<String>()
    private fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        "hr.db").allowMainThreadQueries().build()
    @Before fun prepare() { db = open() }
    @After fun close() { db.close() }
    private fun payload(value: Any, legacy: Boolean = false) = JSONObject()
        .put("patient_id", "SYNTHETIC-PATIENT").put(if (legacy) "deviceId" else "device_id", "SYNTHETIC-WATCH")
        .put("timestamp", "2026-09-26T12:00:00Z").apply {
            if (legacy) put("metrics", JSONObject().put("heartRate", value)) else put("heart_rate", value)
        }.toString()

    private val api = object : HealthTechApiService {
        override suspend fun checkHealth(): Response<HealthCheckResponse> = error("No health probe")
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> =
            error("No single fallback")
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
            val raw = Buffer().also { body.writeTo(it) }.readUtf8()
            requests += raw
            val rows = JSONObject(raw).getJSONArray("readings")
            val results = JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                // Independent narrow oracle: these explicit Java float syntaxes fail Core/Pydantic.
                if (row.opt("heart_rate") in listOf("0x1.2p6", "72f", "72d"))
                    return Response.error(422, "synthetic-whole-batch-float-rejection".toResponseBody())
                val id = row.getString("client_reading_id")
                results.put(JSONObject().put("index", i).put("client_reading_id", id).put("status", "accepted")
                    .put("result", JSONObject().put("patient_id", "SYNTHETIC-PATIENT")
                        .put("reading_id", "stored-$id").put("ingest_status", "accepted").put("duplicate", false)))
            }
            return Response.success(JSONObject().put("patient_id", "SYNTHETIC-PATIENT").put("results", results)
                .toString().toResponseBody())
        }
    }
    private fun repo() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(), api, db.localWriteTransaction())

    @Test fun incompatibleFlatTextDoesNotBlockNeighbor() = runBlocking {
        for ((id, value) in listOf(1 to 72, 2 to "0x1.2p6")) {
            db.ingestQueueDao().insertItem(IngestQueueEntity(id=id.toLong(), createdAt=id.toLong(),
                clientReadingId="synthetic-hr-$id", payloadJson=payload(value)))
        }
        val before = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        val result = repo().processQueueDetailed()
        assertEquals(1, result.syncedCount)
        assertEquals(1, result.skippedCount)
        val after = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        for ((id, original) in before) {
            val row = after.getValue(id)
            assertEquals(original.payloadJson, row.payloadJson)
            assertEquals(original.clientReadingId, row.clientReadingId)
            assertEquals(original.createdAt, row.createdAt)
            assertEquals(if (id == 1L) "SYNCED" else "FAILED", row.status)
        }
        assertEquals(1, JSONObject(requests.single()).getJSONArray("readings").length())
        db.close(); db = open()
        assertEquals(after, db.ingestQueueDao().getAllItemsSync().associateBy { it.id })
        assertEquals(1, repo().retryAllFailed().skippedCount)
        assertEquals(1, requests.size)
    }

    @Test fun explicitMalformedLegacyTextIsNeverConvertedToValidNumber() {
        for (value in listOf("0x1.2p6", "72f", "72d", "", "NaN", true, JSONObject(), JSONArray())) {
            val normalized = IngestPayloadMapper.normalizeQueuePayload(payload(value, true))
            assertEquals(value.toString(), JSONObject(normalized).get("heart_rate").toString())
            assertFalse(IngestPayloadMapper.isIngestibleJson(normalized))
        }
    }

    @Test fun compatibleCoreDecimalsKeepExistingLegacyNumericNormalization() {
        val values = listOf<Any>(20, 72.25, 250, "72", "7_2", "7.2e_1", "72_._0", "+_72", " 72 ", "\u008572\u0085")
        for (value in values) {
            val flat = IngestPayloadMapper.normalizeQueuePayload(payload(value))
            assertEquals(value.toString(), JSONObject(flat).get("heart_rate").toString())
            assertTrue("Core permits $value", IngestPayloadMapper.isIngestibleJson(flat))
            val legacy = IngestPayloadMapper.normalizeQueuePayload(payload(value, true))
            assertTrue(JSONObject(legacy).get("heart_rate") is Number)
            assertTrue(IngestPayloadMapper.isIngestibleJson(legacy))
        }
        for (value in listOf<Any>(19.9, 250.1, " 7_2 ", "_72", "72_", "7__2", "\u001c72\u001c")) {
            assertFalse(IngestPayloadMapper.isIngestibleJson(IngestPayloadMapper.normalizeQueuePayload(payload(value))))
        }
        val fallback = JSONObject(payload(JSONObject.NULL, true)).put("heart_rate", 73.5)
        assertEquals(73.5, JSONObject(IngestPayloadMapper.normalizeQueuePayload(fallback.toString())).getDouble("heart_rate"), 0.0)
        val explicit = JSONObject(payload("0x1.2p6", true)).put("heart_rate", 73.5)
        assertEquals("0x1.2p6", JSONObject(IngestPayloadMapper.normalizeQueuePayload(explicit.toString())).get("heart_rate"))
    }
}
