package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.ingest.QueueAuthorization
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.HealthTechApiService
import kotlinx.coroutines.*
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

/** Real local Room with synthetic transport receipts; does not assert real backend deduplication. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableBatchIngestTest {
    private lateinit var db: AppDatabase
    private val requests = mutableListOf<Pair<String?, String>>()
    private val stored = mutableSetOf<String>()
    private var singles = 0
    private var batchCalls = 0
    private var batchHandler: suspend (JSONObject) -> Response<ResponseBody> = { success(it) }
    private val api = object : HealthTechApiService {
        override suspend fun checkHealth() = Response.success(HealthCheckResponse())
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
            assertFalse(db.inTransaction())
            singles++
            error("Repository must use the correlated batch envelope even for one reading")
        }
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
            assertFalse(db.inTransaction())
            batchCalls++
            val text = Buffer().also { body.writeTo(it) }.readUtf8()
            requests += idempotencyKey to text
            return batchHandler(JSONObject(text))
        }
    }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun repo(queue: IngestQueueDao = db.ingestQueueDao(), batchSize: Int = 50) =
        WearableRepository(queue, db.sensorMetricDao(), api, db.localWriteTransaction(),
            db.advancedMeasurementDao(), maxBatchItems = batchSize)

    private suspend fun insert(id: Int, patient: String = "PATIENT-A", raw: String? = null) {
        val payload = raw ?: JSONObject().put("patient_id", patient).put("device_id", "WATCH-A")
            .put("timestamp", "2026-09-24T12:00:00Z").put("heart_rate", 72).toString()
        db.ingestQueueDao().insertItem(IngestQueueEntity(id = id.toLong(), payloadJson = payload,
            createdAt = id.toLong(), clientReadingId = "synthetic-$id"))
    }
    private fun receipt(request: JSONObject): JSONObject {
        val readings = request.getJSONArray("readings")
        val results = JSONArray()
        for (i in 0 until readings.length()) {
            val row = readings.getJSONObject(i)
            assertEquals(request.getString("patient_id"), row.getString("patient_id"))
            val id = row.getString("client_reading_id")
            val status = if (stored.add(id)) "accepted" else "duplicate"
            results.put(JSONObject().put("index", i).put("client_reading_id", id).put("status", status)
                .put("result", JSONObject().put("patient_id", row.getString("patient_id"))
                    .put("ingest_status", status).put("reading_id", "stored-$id")))
        }
        return JSONObject().put("patient_id", request.getString("patient_id"))
            .put("status", "success").put("results", results)
    }
    private fun success(request: JSONObject) = Response.success(receipt(request).toString().toResponseBody())

    @Test fun mixed_patients_are_sent_in_separate_batches_with_exact_original_ids() = runBlocking {
        insert(1); insert(2, "PATIENT-B"); insert(3); insert(4, "PATIENT-B")
        val original = db.ingestQueueDao().getAllItemsSync().associate { it.id to it.payloadJson }
        assertEquals(4, repo().processQueueDetailed().syncedCount)
        assertEquals(2, batchCalls)
        assertEquals(0, singles)
        assertEquals(setOf("PATIENT-A", "PATIENT-B"), requests.map { JSONObject(it.second).getString("patient_id") }.toSet())
        assertEquals(original, db.ingestQueueDao().getAllItemsSync().associate { it.id to it.payloadJson })
    }

    @Test fun partial_receipt_does_not_confirm_neighbor_and_retry_recovers_duplicate() = runBlocking {
        insert(1); insert(2)
        batchHandler = { request ->
            val body = receipt(request)
            val second = body.getJSONArray("results").getJSONObject(1)
            Response.success(body.put("results", JSONArray().put(second)).toString().toResponseBody())
        }
        val result = repo().processQueueDetailed()
        assertEquals(1, result.syncedCount)
        assertEquals(1, result.pendingCount)
        val rows = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        assertEquals("PENDING", rows[1L]!!.status)
        assertEquals("SYNCED", rows[2L]!!.status)
        batchHandler = { success(it) }
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(2, stored.size)
    }

    @Test fun lost_response_replays_same_payload_and_key_without_new_fixture_readings() = runBlocking {
        insert(1); insert(2)
        batchHandler = { request -> receipt(request); throw IOException("Synthetic lost receipt") }
        assertEquals(0, repo().processQueueDetailed().syncedCount)
        val before = db.ingestQueueDao().getAllItemsSync()
        assertTrue(before.all { it.status == "PENDING" })
        batchHandler = { success(it) }
        assertEquals(2, repo().processQueueDetailed().syncedCount)
        assertEquals(requests[0], requests[1])
        assertEquals(2, stored.size)
        assertEquals(before.map { it.clientReadingId }, db.ingestQueueDao().getAllItemsSync().map { it.clientReadingId })
    }

    @Test fun batch_auth_pause_survives_new_repository_and_inconclusive_manual_retry() = runBlocking {
        insert(1); insert(2)
        batchHandler = { Response.error(403, "synthetic-private-response".toResponseBody()) }
        assertNotNull(repo().processQueueDetailed().authError)
        assertTrue(db.ingestQueueDao().getAllItemsSync().all(QueueAuthorization::isBlocked))
        insert(3)
        assertNotNull(repo().processQueueDetailed().authError)
        assertEquals(1, batchCalls)
        batchHandler = { Response.error(503, "synthetic-failure".toResponseBody()) }
        repo().retryAllFailed()
        assertEquals(2, batchCalls)
        assertNotNull(repo().processQueueDetailed().authError)
        assertEquals(2, batchCalls)
        assertFalse(db.ingestQueueDao().getAllItemsSync().any { it.errorMessage?.contains("private-response") == true })
    }

    @Test fun cancellation_during_batch_preserves_queue_and_releases_shared_gate() = runBlocking {
        insert(1); insert(2)
        val entered = CompletableDeferred<Unit>()
        batchHandler = { entered.complete(Unit); awaitCancellation() }
        val before = db.ingestQueueDao().getAllItemsSync()
        val job = launch(Dispatchers.IO) { repo().processQueueDetailed() }
        withTimeout(5000) { entered.await() }
        job.cancelAndJoin()
        assertEquals(before, db.ingestQueueDao().getAllItemsSync())
        batchHandler = { success(it) }
        assertEquals(2, repo().processQueueDetailed().syncedCount)
    }

    @Test fun local_receipt_write_failure_aborts_before_next_chunk_and_can_recover() = runBlocking {
        insert(1); insert(2); insert(3)
        val failing = object : IngestQueueDao by db.ingestQueueDao() {
            override suspend fun updateItem(item: IngestQueueEntity) { error("Synthetic local write failure") }
        }
        val failure = runCatching { repo(failing, 2).processQueueDetailed() }.exceptionOrNull()
        assertTrue(failure is QueuePersistenceException)
        assertEquals(1, batchCalls)
        assertEquals(0, singles)
        assertTrue(db.ingestQueueDao().getAllItemsSync().all { it.status == "PENDING" })
        assertEquals(3, repo(batchSize = 2).processQueueDetailed().syncedCount)
        assertEquals(3, stored.size)
    }

    @Test fun partial_receipt_write_reopens_safely() = runBlocking {
        // File-backed synthetic SQLite; closing/reopening Room is not process death or SQLCipher.
        db.close()
        // Each Robolectric case has its own sandbox; keep the Windows SQLite path short.
        val name = "receipt.db"
        fun reopen() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        db = reopen()
        insert(1); insert(2); insert(3)
        val before = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        val dao = db.ingestQueueDao()
        val failing = object : IngestQueueDao by dao {
            override suspend fun updateItem(item: IngestQueueEntity) {
                if (item.id == 2L) throw IOException("Synthetic receipt storage interruption")
                dao.updateItem(item)
            }
        }
        assertTrue(runCatching { repo(failing, 2).processQueueDetailed() }.exceptionOrNull() is QueuePersistenceException)
        assertEquals(1, batchCalls)
        assertEquals(setOf("synthetic-1", "synthetic-2"), stored)
        db.close()
        db = reopen()
        val partial = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        assertEquals("SYNCED", partial[1L]!!.status)
        assertEquals(before[2L], partial[2L])
        assertEquals(before[3L], partial[3L])
        assertFalse(repo().isSyncing.value)
        assertEquals(2, repo(batchSize = 2).processQueueDetailed().syncedCount)
        val replay = JSONObject(requests[1].second).getJSONArray("readings")
        assertEquals(listOf("synthetic-2", "synthetic-3"),
            (0 until replay.length()).map { replay.getJSONObject(it).getString("client_reading_id") })
        val first = JSONObject(requests[0].second).getJSONArray("readings").getJSONObject(1)
        assertEquals(first.toString(), replay.getJSONObject(0).toString())
        val after = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        assertEquals(partial[1L], after[1L]) // Already persisted receipt is not rewritten.
        assertEquals(before.mapValues { it.value.payloadJson }, after.mapValues { it.value.payloadJson })
        assertEquals(before.mapValues { it.value.clientReadingId }, after.mapValues { it.value.clientReadingId })
        assertTrue(after.values.all { it.status == "SYNCED" })
        assertEquals(3, stored.size) // Synthetic receiver only, not proof of backend deduplication.
    }

    @Test fun cancellation_between_receipt_writes_keeps_remaining_rows_and_releases_gate() = runBlocking {
        insert(1); insert(2)
        val before = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        val committed = CompletableDeferred<Unit>()
        val dao = db.ingestQueueDao()
        val pausing = object : IngestQueueDao by dao {
            override suspend fun updateItem(item: IngestQueueEntity) {
                dao.updateItem(item)
                committed.complete(Unit)
                awaitCancellation()
            }
        }
        val job = launch(Dispatchers.IO) { repo(pausing).processQueueDetailed() }
        try {
            withTimeout(5000) { committed.await() }
        } finally { job.cancelAndJoin() }
        val partial = db.ingestQueueDao().getAllItemsSync().associateBy { it.id }
        assertEquals("SYNCED", partial[1L]!!.status)
        assertEquals(before[2L], partial[2L])
        assertFalse(repo().isSyncing.value)
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(partial[1L], db.ingestQueueDao().getAllItemsSync().single { it.id == 1L })
        assertEquals("synthetic-2", JSONObject(requests[1].second).getJSONArray("readings")
            .getJSONObject(0).getString("client_reading_id"))
        assertEquals(2, stored.size)
    }

    @Test fun second_repository_cannot_reset_failed_rows_while_batch_is_in_flight() = runBlocking {
        insert(1); insert(2); insert(3)
        val failed = db.ingestQueueDao().getAllItemsSync().single { it.id == 3L }.copy(
            status = "FAILED", retries = 4, lastAttemptAt = 1234L, errorMessage = "Synthetic client rejection")
        db.ingestQueueDao().updateItem(failed)
        val before = db.ingestQueueDao().getAllItemsSync()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        batchHandler = { request -> entered.complete(Unit); release.await(); success(request) }
        val first = repo()
        val second = repo()
        val job = async(Dispatchers.IO) { first.processQueueDetailed() }
        try {
            withTimeout(5000) { entered.await() }
            assertTrue(second.isSyncing.value)
            assertEquals("Sincronização já em andamento.", second.retryAllFailed().message)
            assertEquals(before, db.ingestQueueDao().getAllItemsSync())
            assertEquals(1, batchCalls)
            release.complete(Unit)
            assertEquals(2, withTimeout(5000) { job.await() }.syncedCount)
        } finally { job.cancelAndJoin() }
        assertFalse(second.isSyncing.value)
        assertEquals(failed, db.ingestQueueDao().getAllItemsSync().single { it.id == 3L })
        batchHandler = { success(it) }
        assertEquals(1, second.retryAllFailed().syncedCount)
        assertEquals(2, batchCalls)
    }

    @Test fun unavailable_batch_endpoint_does_not_fallback_or_claim_success() = runBlocking {
        insert(1); insert(2)
        batchHandler = { Response.error(404, "missing".toResponseBody()) }
        assertEquals(0, repo().processQueueDetailed().syncedCount)
        assertEquals(1, batchCalls)
        assertEquals(0, singles)
        assertEquals(2, db.ingestQueueDao().getAllItemsSync().size)
    }

    @Test fun missing_identity_time_or_conflicting_id_stays_local_unchanged() = runBlocking {
        insert(1, raw = """{"heart_rate":72}""")
        insert(2, raw = """{"patient_id":"A","device_id":"D","heart_rate":72}""")
        insert(3, raw = """{"patient_id":"A","device_id":"D","timestamp":"T","heart_rate":72,"client_reading_id":"conflict"}""")
        insert(4, raw = """{"patient_id":"A","device_id":"D","timestamp":"T","heart_rate":72}""")
        val original = db.ingestQueueDao().getAllItemsSync().map { it.payloadJson }
        assertEquals(0, repo().processQueueDetailed().syncedCount)
        assertEquals(0, batchCalls + singles)
        assertEquals(original, db.ingestQueueDao().getAllItemsSync().map { it.payloadJson })
    }

    @Test fun batch_limit_is_fifty_and_remaining_item_keeps_batch_correlation() = runBlocking {
        for (i in 1..51) insert(i)
        assertEquals(51, repo().processQueueDetailed().syncedCount)
        assertEquals(2, batchCalls)
        assertEquals(0, singles)
        assertEquals(50, JSONObject(requests[0].second).getJSONArray("readings").length())
        assertEquals("synthetic-51", JSONObject(requests[1].second).getJSONArray("readings")
            .getJSONObject(0).getString("client_reading_id"))
    }

    @Test fun empty_receipt_for_one_item_is_not_success() = runBlocking {
        insert(1)
        batchHandler = { Response.success(null) }
        assertEquals(0, repo().processQueueDetailed().syncedCount)
        assertEquals("PENDING", db.ingestQueueDao().getAllItemsSync().single().status)
    }

    @Test fun natural_duplicate_with_older_frame_identity_uses_current_envelope_identity() = runBlocking {
        insert(1)
        batchHandler = { request ->
            stored.add("synthetic-1")
            val body = receipt(request)
            val entry = body.getJSONArray("results").getJSONObject(0)
            entry.getJSONObject("result").put("client_reading_id", "older-client-id")
            Response.success(body.toString().toResponseBody())
        }
        assertEquals(1, repo().processQueueDetailed().syncedCount)
        assertEquals(0, singles)
        assertEquals(1, batchCalls)
    }
}
