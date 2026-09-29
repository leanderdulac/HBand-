package com.example.data.ingest

import android.app.Application
import androidx.room.Room
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.repository.WearableRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

/** Real synthetic Room transactions; no BLE, native SQLCipher or real HTTP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LiveSyncStorageTest {
    private lateinit var db: AppDatabase
    private fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        "live-sync.db").allowMainThreadQueries().build()
    @Before fun prepare() { db = open() }
    @After fun close() { db.close() }
    private fun reading(hr: Int) = HBandTelemetry(
        deviceId = "TEST-WATCH", deviceModel = "Test", timestamp = "2026-09-25T12:00:00Z",
        heartRate = hr, bloodPressure = BloodPressure(), spO2 = 0,
        temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, sleepSummary = SleepSummary(), isRealSensorData = true,
    )
    private fun repository(send: suspend () -> Response<IngestResponse>) = WearableRepository(
        db.ingestQueueDao(), db.sensorMetricDao(), object : SingleOnlyTestApi() {
            override suspend fun checkHealth() = Response.success(HealthCheckResponse())
            override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): Response<IngestResponse> {
                assertFalse(db.inTransaction())
                return send()
            }
        }, db.localWriteTransaction(), maxBatchItems = 1,
    )

    @Test fun local_pairs_continue_during_blocked_http_and_survive_cancellation_and_reopen() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val repo = repository { entered.complete(Unit); awaitCancellation() }
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        val saved = Channel<Unit>(Channel.UNLIMITED)
        val recorder = recordLiveReadingsWithSync(readings, { true }, {
            repo.persistTelemetry(it, "TEST-PATIENT"); saved.send(Unit)
        }, { repo.processQueueDetailed() }, { throw it }, { throw it })
        try {
            readings.value = reading(72)
            withTimeout(5000) { saved.receive(); entered.await() }
            readings.value = reading(76)
            withTimeout(5000) { saved.receive() }
            readings.value = reading(80)
            withTimeout(5000) { saved.receive() }
            assertEquals(3, db.ingestQueueDao().getAllItemsSync().size)
        } finally { recorder.cancelAndJoin(); saved.cancel() }
        val rows = db.ingestQueueDao().getAllItemsSync()
        val history = db.sensorMetricDao().getAllMetricsList()
        assertEquals(3, rows.size); assertEquals(3, history.size)
        assertTrue(rows.all { it.status == "PENDING" && it.retries == 0 })
        assertEquals(setOf(72, 76, 80), history.map { it.heartRate }.toSet())
        assertEquals(3, rows.map { it.clientReadingId }.distinct().size)
        assertFalse(repo.isSyncing.value)
        db.close(); db = open()
        assertEquals(rows, db.ingestQueueDao().getAllItemsSync())
        assertEquals(history, db.sensorMetricDao().getAllMetricsList())
    }

    @Test fun authorization_pause_blocks_new_sends_but_keeps_saving_new_readings() = runBlocking {
        val calls = AtomicInteger()
        val paused = CompletableDeferred<Unit>()
        val repo = repository { calls.incrementAndGet(); Response.error(401, "Synthetic refusal".toResponseBody()) }
        val readings = MutableStateFlow<HBandTelemetry?>(null)
        val saved = Channel<Unit>(Channel.UNLIMITED)
        val recorder = recordLiveReadingsWithSync(readings, { true }, {
            repo.persistTelemetry(it, "TEST-PATIENT"); saved.send(Unit)
        }, {
            if (repo.processQueueDetailed().authError != null) paused.complete(Unit)
        }, { throw it }, { throw it })
        try {
            readings.value = reading(72)
            withTimeout(5000) { saved.receive(); paused.await() }
            readings.value = reading(76); withTimeout(5000) { saved.receive() }
            readings.value = reading(80); withTimeout(5000) { saved.receive() }
        } finally { recorder.cancelAndJoin(); saved.cancel() }
        val rows = db.ingestQueueDao().getAllItemsSync()
        assertEquals(3, rows.size)
        assertEquals(1, rows.count(QueueAuthorization::isBlocked))
        assertEquals(2, rows.count { it.status == "PENDING" })
        assertEquals(3, db.sensorMetricDao().getAllMetricsList().size)
        assertEquals(1, calls.get())
        db.close(); db = open()
        assertEquals(rows, db.ingestQueueDao().getAllItemsSync())
        assertTrue(db.ingestQueueDao().hasAuthorizationBlock(
            QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
    }
}
