package com.example.data.local

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.HealthCheckResponse
import com.example.data.model.IngestResponse
import com.example.data.remote.HealthTechApiService
import com.example.data.repository.WearableRepository
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
import retrofit2.Response
import java.security.MessageDigest

/** Real Android process/SQLCipher recovery, fake transport only. Never a backend deduplication test. */
@RunWith(AndroidJUnit4::class)
class ReceiptRecoveryAndroidLabTest {
    private lateinit var context: Context
    private lateinit var point: String
    private val opened = mutableListOf<AppDatabase>()
    private val ids = listOf(601L, 602L, 603L)
    private val name get() = "storage-lab-receipt-$point"
    private val evidence get() = context.getSharedPreferences("receipt_lab_$point", Context.MODE_PRIVATE)
    private val committedCount get() = if (point == "before-first") 0 else 1

    @Before fun requireIsolatedEmulator() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.aistudio.hbandhealthtech.pxq97m.storagelab")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("storageLab") == "synthetic-only")
        check(context.applicationContext.javaClass == android.app.Application::class.java)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET))
        point = checkNotNull(InstrumentationRegistry.getArguments().getString("receiptCutPoint"))
        check(point in setOf("before-first", "before-second"))
        AppDatabase.loadSqlCipherNativeLibrary()
        assertTrue(SqlCipherNative.isLoaded)
    }

    @After fun closeOnly() { opened.forEach { it.close() } } // Never delete databases or keys.

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabaseMigrations.MIGRATION_6_7)
        .openHelperFactory(SupportOpenHelperFactory(SqlCipherPassphrase.getPassphrase(context)))
        .build().also { opened += it }

    private fun metadataDigest(): String {
        val prefs = context.getSharedPreferences("hband_sqlcipher", Context.MODE_PRIVATE)
        val wrapped = checkNotNull(prefs.getString("wrapped_db_key", null))
        val iv = checkNotNull(prefs.getString("wrapped_db_key_iv", null))
        return MessageDigest.getInstance("SHA-256").digest("$wrapped|$iv".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun seedRow(id: Long) = IngestQueueEntity(id = id,
        payloadJson = """{"patient_id":"SYNTHETIC-RECEIPT","device_id":"LAB-ONLY","timestamp":"2026-09-25T12:00:0${id - 600}Z","heart_rate":72}""",
        retries = 2, createdAt = id, clientReadingId = "synthetic-receipt-$id")

    private suspend fun rows(db: AppDatabase) = db.ingestQueueDao().getAllItemsSync().sortedBy { it.id }
    private fun snapshot(rows: List<IngestQueueEntity>): String = JSONArray(rows.map { row ->
        JSONArray(listOf(row.id, row.payloadJson, row.status, row.retries, row.createdAt,
            row.lastAttemptAt ?: JSONObject.NULL, row.errorMessage ?: JSONObject.NULL, row.clientReadingId))
    }).toString()

    private fun service(db: AppDatabase, handler: (String?, String) -> String) = object : HealthTechApiService {
        override suspend fun checkHealth(): Response<HealthCheckResponse> = error("No health/network request in lab")
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> =
            error("Only correlated batch fixture permitted")
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
            assertFalse(db.inTransaction())
            val raw = Buffer().also { body.writeTo(it) }.readUtf8()
            return Response.success(handler(idempotencyKey, raw).toResponseBody())
        }
    }

    // Existing confirmed-contract fixture shape. There is no server or remote persistence here.
    private fun receipt(raw: String, status: (String) -> String): String {
        val request = JSONObject(raw)
        val readings = request.getJSONArray("readings")
        val results = JSONArray()
        for (index in 0 until readings.length()) {
            val row = readings.getJSONObject(index)
            val id = row.getString("client_reading_id")
            val outcome = status(id)
            assertEquals("SYNTHETIC-RECEIPT", row.getString("patient_id"))
            results.put(JSONObject().put("index", index).put("client_reading_id", id).put("status", outcome)
                .put("result", JSONObject().put("patient_id", row.getString("patient_id"))
                    .put("ingest_status", outcome).put("reading_id", "simulated-$id")))
        }
        return JSONObject().put("patient_id", request.getString("patient_id"))
            .put("status", "success").put("results", results).toString()
    }

    private fun repo(db: AppDatabase, dao: IngestQueueDao, api: HealthTechApiService) =
        WearableRepository(dao, db.sensorMetricDao(), api, db.localWriteTransaction(),
            db.advancedMeasurementDao(), maxBatchItems = 2)

    /** Intentionally killed after a parsed fake receipt; not a passing JUnit execution. */
    @Test fun interruptReceiptPersistence(): Unit = runBlocking {
        check(!context.getDatabasePath(name).exists()) { "Seed refuses existing receipt database" }
        check(!evidence.contains("cut_point")) { "Never reuse an interruption marker" }
        val db = open()
        ids.forEach { db.ingestQueueDao().insertItem(seedRow(it)) }
        assertEquals(ids.map(::seedRow), rows(db))
        var calls = 0
        var updates = 0
        var firstRequest = ""
        var firstKey: String? = null
        val api = service(db) { key, raw ->
            calls++
            assertEquals(1, calls) // Never reaches the next chunk before the cut.
            assertFalse(key.isNullOrBlank())
            val readings = JSONObject(raw).getJSONArray("readings")
            assertEquals(2, readings.length())
            assertEquals("synthetic-receipt-601", readings.getJSONObject(0).getString("client_reading_id"))
            assertEquals("synthetic-receipt-602", readings.getJSONObject(1).getString("client_reading_id"))
            firstRequest = raw; firstKey = key
            receipt(raw) { "accepted" }
        }
        val delegate = db.ingestQueueDao()
        val cuttingDao = object : IngestQueueDao by delegate {
            override suspend fun updateItem(item: IngestQueueEntity) {
                updates++
                assertEquals(1, calls)
                assertEquals("SYNCED", item.status) // Reconciled result, not merely an HTTP callback.
                assertEquals(seedRow(600L + updates).copy(status = "SYNCED", lastAttemptAt = item.lastAttemptAt), item)
                assertNotNull(item.lastAttemptAt)
                if (updates == committedCount + 1) {
                    assertFalse(db.inTransaction())
                    val existing = rows(db)
                    val firstTime = existing.first().lastAttemptAt
                    assertEquals(ids.map { id -> if (id == 601L && committedCount == 1)
                        seedRow(id).copy(status = "SYNCED", lastAttemptAt = firstTime) else seedRow(id) }, existing)
                    if (committedCount == 1) assertNotNull(firstTime)
                    check(evidence.edit().putString("cut_point", point).putInt("interrupted_pid", Process.myPid())
                        .putString("metadata_digest", metadataDigest()).putString("before_snapshot", snapshot(existing))
                        .putString("first_request", firstRequest).putString("first_key", firstKey)
                        .putInt("fixture_calls", calls).putInt("updates_entered", updates).commit())
                    Process.killProcess(Process.myPid())
                    error("Expected isolated process termination")
                }
                delegate.updateItem(item)
            }
        }
        repo(db, cuttingDao, api).processQueueDetailed()
        error("Cut point was not reached")
    }

    @Test fun recoverReceiptsInNewProcess() = runBlocking {
        check(context.getDatabasePath(name).exists()) { "Recovery never seeds a database" }
        check(!evidence.contains("recovered_snapshot")) { "Recovery already completed; use read-only reopen phase" }
        assertEquals(point, evidence.getString("cut_point", null))
        assertTrue(evidence.getInt("interrupted_pid", -1) > 0)
        assertNotEquals(evidence.getInt("interrupted_pid", -1), Process.myPid())
        assertEquals(1, evidence.getInt("fixture_calls", -1))
        assertEquals(committedCount + 1, evidence.getInt("updates_entered", -1))
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
        val db = open()
        AppDatabase.verifyOpen(db)
        val before = rows(db)
        assertEquals(evidence.getString("before_snapshot", null), snapshot(before))
        assertEquals(ids, before.map { it.id })
        assertEquals(ids.map(::seedRow), before.map { it.copy(status = "PENDING", lastAttemptAt = null) })
        assertEquals(List(committedCount) { "SYNCED" } + List(3 - committedCount) { "PENDING" }, before.map { it.status })
        val oldRequest = checkNotNull(evidence.getString("first_request", null))
        val originalReadings = JSONObject(oldRequest).getJSONArray("readings")
        val originalById = (0 until originalReadings.length()).associate {
            val reading = originalReadings.getJSONObject(it)
            reading.getString("client_reading_id") to reading.toString()
        }
        val replayed = mutableListOf<String>()
        var calls = 0
        val api = service(db) { key, raw ->
            calls++
            if (calls == 1 && committedCount == 0) {
                assertEquals(oldRequest, raw)
                assertEquals(evidence.getString("first_key", null), key)
            } else if (calls == 1) assertNotEquals(evidence.getString("first_key", null), key)
            val readings = JSONObject(raw).getJSONArray("readings")
            for (index in 0 until readings.length()) {
                val reading = readings.getJSONObject(index)
                val id = reading.getString("client_reading_id")
                replayed += id
                originalById[id]?.let { assertEquals(it, reading.toString()) }
            }
            // Fixture assumes the first chunk was accepted before the process died.
            // This models a response; it does not prove server deduplication/durability.
            receipt(raw) { id -> if (id in originalById) "duplicate" else "accepted" }
        }
        val repository = repo(db, db.ingestQueueDao(), api)
        assertFalse(repository.isSyncing.value)
        val result = repository.processQueueDetailed()
        assertEquals(3 - committedCount, result.syncedCount)
        assertFalse(result.hasIncompleteItems)
        assertEquals(if (committedCount == 0) 2 else 1, calls)
        assertEquals(ids.drop(committedCount).map { "synthetic-receipt-$it" }, replayed)
        val after = rows(db)
        assertTrue(after.all { it.status == "SYNCED" && it.lastAttemptAt != null })
        assertEquals(ids.map(::seedRow), after.map { it.copy(status = "PENDING", lastAttemptAt = null) })
        if (committedCount == 1) assertEquals(before.first(), after.first()) // Saved receipt never resent/rewritten.
        assertEquals(0, repository.processQueueDetailed().syncedCount)
        assertEquals(if (committedCount == 0) 2 else 1, calls) // Empty queue never calls the fixture.
        db.close()
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(context.getDatabasePath(name)))
        assertEquals(after, rows(open()))
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
        check(evidence.edit().putString("recovered_snapshot", snapshot(after))
            .putInt("recovered_pid", Process.myPid()).commit())
    }

    @Test fun reopenRecoveredReceipts() = runBlocking {
        check(context.getDatabasePath(name).exists())
        check(evidence.contains("recovered_snapshot"))
        assertTrue(evidence.getInt("recovered_pid", -1) > 0)
        assertNotEquals(evidence.getInt("recovered_pid", -1), Process.myPid())
        assertEquals(evidence.getString("metadata_digest", null), metadataDigest())
        val db = open()
        AppDatabase.verifyOpen(db)
        assertEquals(evidence.getString("recovered_snapshot", null), snapshot(rows(db)))
        assertEquals(ids, rows(db).map { it.id })
        assertTrue(rows(db).all { it.status == "SYNCED" })
    }
}
