package com.example.data.repository

import android.app.Application
import androidx.room.Room
import com.example.data.hband.VeepooSportReading
import com.example.data.hband.VeepooHistoryMapper
import com.example.data.ingest.QueueAuthorization
import com.example.data.ingest.SingleOnlyTestApi
import com.example.data.local.*
import com.example.ui.components.buildSavedWeek
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

/** File SQLite/Room and synthetic rows, never the installed app, SQLCipher or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WearableSportRecordingTest {
    private val context = RuntimeEnvironment.getApplication()
    // Robolectric already isolates each test; keep the native SQLite path below Windows MAX_PATH.
    private val name = "sport.db"
    private lateinit var db: AppDatabase
    private val observed = VeepooHistoryMapper.parseIsoToMillis("2026-09-23T23:59:59Z")
    private val reading = VeepooSportReading(observed, 2400, 82.5f, 1250f)
    // Any accidental HTTP call fails the test, even if production catches Exception.
    private val api = object : SingleOnlyTestApi() {
        override suspend fun checkHealth(): retrofit2.Response<com.example.data.model.HealthCheckResponse> =
            throw AssertionError("Sport recording must not check the backend")
        override suspend fun ingestWearableData(body: okhttp3.RequestBody, idempotencyKey: String?): retrofit2.Response<com.example.data.model.IngestResponse> =
            throw AssertionError("Sport recording must not flush the queue")
    }

    private fun open() { db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build() }
    @Before fun setup() = open()
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private fun repo() = WearableRepository(db.ingestQueueDao(), db.sensorMetricDao(), api, db.localWriteTransaction())

    @Test fun reopen_preserves_time_vitals_and_queue() = runBlocking {
        val pending = IngestQueueEntity(payloadJson = "synthetic-pending")
        val blocked = IngestQueueEntity(payloadJson = "synthetic-blocked", status = "FAILED",
            errorMessage = QueueAuthorization.UNAUTHORIZED_PREFIX, retries = 3, lastAttemptAt = observed - 1000)
        db.ingestQueueDao().insertItem(pending)
        db.ingestQueueDao().insertItem(blocked)
        val before = db.ingestQueueDao().getAllItemsSync()
        val savedId = repo().persistSportReading("TEST-WATCH", reading)
        val saved = db.sensorMetricDao().getAllMetricsList().single()
        assertEquals(savedId, saved.id)
        assertEquals(observed, saved.timestampMillis)
        assertEquals("2026-09-23T23:59:59Z", saved.timestamp)
        assertEquals("TEST-WATCH", saved.deviceId)
        assertEquals(2400, saved.steps)
        assertEquals(1250f, saved.distanceMeters, 0f)
        assertEquals(82.5f, saved.calories, 0f)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0), listOf(saved.heartRate, saved.systolicBp,
            saved.diastolicBp, saved.spO2, saved.hrvScore, saved.deepSleepMinutes, saved.lightSleepMinutes, saved.awakeMinutes))
        assertEquals(0f, saved.temperatureCelsius, 0f)
        assertTrue(com.example.data.remote.clinicalInsightRecords(listOf(saved)).isEmpty())
        val clinical = saved.copy(id = saved.id + 1, heartRate = 72)
        assertEquals(listOf(clinical), com.example.data.remote.clinicalInsightRecords(listOf(saved, clinical)))
        db.close(); open()
        assertEquals(listOf(saved), db.sensorMetricDao().getAllMetricsList())
        assertEquals(before, db.ingestQueueDao().getAllItemsSync())
        // Saving after midnight does not move an earlier callback to today's card.
        val week = buildSavedWeek(listOf(saved), observed + 2000, TimeZone.getTimeZone("UTC"))
        assertEquals(0, week.last().recordCount)
        assertEquals(2400, week[5].highestSteps)
        assertNull(week[5].averageHeartRate)
    }

    @Test fun lower_counters_preserve_old_rows() = runBlocking {
        repo().persistSportReading("TEST-WATCH", reading)
        repo().persistSportReading("TEST-WATCH", reading.copy(observedAtMillis = observed + 1000, steps = 0, calories = 0f, distanceMeters = 0f))
        val rows = db.sensorMetricDao().getAllMetricsList()
        assertEquals(2, rows.size)
        assertEquals(setOf(2400, 0), rows.map { it.steps }.toSet())
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
    }

    @Test fun failed_insert_preserves_and_can_retry() = runBlocking {
        repo().persistSportReading("TEST-WATCH", reading)
        val original = db.sensorMetricDao().getAllMetricsList()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_sport BEFORE INSERT ON hband_sensor_metrics
            BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END
        """)
        assertTrue(runCatching { repo().persistSportReading("TEST-WATCH", reading.copy(steps = 2500)) }.isFailure)
        assertEquals(original, db.sensorMetricDao().getAllMetricsList())
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_sport")
        repo().persistSportReading("TEST-WATCH", reading.copy(steps = 2500))
        assertEquals(2, db.sensorMetricDao().getAllMetricsList().size)
    }

    @Test fun missing_device_is_not_replaced_with_default_identity() = runBlocking {
        assertTrue(runCatching { repo().persistSportReading(" ", reading) }.isFailure)
        assertTrue(db.sensorMetricDao().getAllMetricsList().isEmpty())
        assertTrue(db.ingestQueueDao().getAllItemsSync().isEmpty())
    }

    @Test fun sport_does_not_trigger_clinical_analysis() = runBlocking {
        repo().persistSportReading("TEST-WATCH", reading)
        val sport = db.sensorMetricDao().getAllMetricsList().single()
        val clinical = sport.copy(id = sport.id + 1, heartRate = 72)
        val emissions = kotlinx.coroutines.flow.flowOf(listOf(clinical), listOf(clinical, sport), listOf(clinical))
            .map { com.example.data.remote.clinicalInsightRecords(it) }.distinctUntilChanged().toList()
        assertEquals(listOf(listOf(clinical)), emissions)
        val result = com.example.data.remote.GeminiHealthAnalyzer.generateSevenDayInsight(listOf(sport))
        assertTrue(result.contains("não contêm medições clínicas"))
        assertFalse(result.contains("0 BPM"))
    }
}
