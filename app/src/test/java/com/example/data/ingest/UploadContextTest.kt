package com.example.data.ingest

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.IngestQueueEntity
import com.example.data.local.localWriteTransaction
import com.example.data.remote.HealthTechApiService
import com.example.data.repository.WearableRepository
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UploadContextTest {
    private fun item(id: String = "A") = IngestQueueEntity(
        payloadJson = """{"patient_id":"TEST-PATIENT","device_id":"TEST-WATCH","timestamp":"2026-10-17T12:00:00Z","heart_rate":72}""",
        clientReadingId = id,
    )

    @Test fun app_state_is_foreground_or_background_and_battery_is_optional() {
        val shown = UploadContext.of(inForeground = true, screenLocked = false, watchBatteryPct = 81)
        assertEquals(UploadContext.FOREGROUND, shown.appState)
        assertFalse(shown.screenLocked)
        assertEquals(81, shown.watchBatteryPct)

        val locked = UploadContext.of(inForeground = false, screenLocked = true, watchBatteryPct = null)
        assertEquals(UploadContext.BACKGROUND, locked.appState)
        assertTrue(locked.screenLocked)
        assertNull(locked.watchBatteryPct)

        assertNull(UploadContext.of(false, false, watchBatteryPct = -1).watchBatteryPct)
        assertNull(UploadContext.of(false, false, watchBatteryPct = 101).watchBatteryPct)
        assertNull(UploadContext.of(false, false, watchBatteryPct = 40, batterySimulated = true).watchBatteryPct)
        assertEquals(0, UploadContext.of(false, false, watchBatteryPct = 0).watchBatteryPct)
        assertEquals(100, UploadContext.of(false, false, watchBatteryPct = 100).watchBatteryPct)
    }

    @Test fun started_activities_are_foreground_even_without_process_importance() {
        AppVisibility.resetForTests()
        try {
            assertFalse(AppVisibility.isForeground())
            AppVisibility.noteStarted()
            assertTrue(AppVisibility.isForeground())
            AppVisibility.noteStarted()
            AppVisibility.noteStopped()
            assertTrue(AppVisibility.isForeground())
            AppVisibility.noteStopped()
            AppVisibility.noteStopped()
            assertFalse(AppVisibility.isForeground())
        } finally {
            AppVisibility.resetForTests()
        }
    }

    @Test fun keyguard_locked_is_screen_locked() {
        val app = RuntimeEnvironment.getApplication()
        val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setKeyguardLocked(true)
        assertTrue(UploadContextReader.isScreenLocked(app))
        shadowOf(keyguard).setKeyguardLocked(false)
        assertFalse(UploadContextReader.isScreenLocked(app))
        assertNull(UploadContextReader.lastWatchBatteryPercent(app))
    }

    @Test fun prepare_adds_optional_keys_without_changing_identity_or_omitting_them_when_absent() {
        val plain = IngestReconciler.prepare(item())
        val plainJson = JSONObject(plain.json)
        assertFalse(plainJson.has(UploadContext.KEY_APP_STATE))
        assertFalse(plainJson.has(UploadContext.KEY_SCREEN_LOCKED))
        assertFalse(plainJson.has(UploadContext.KEY_WATCH_BATTERY))

        val stamped = IngestReconciler.prepare(
            item(),
            upload = UploadContext.of(inForeground = false, screenLocked = true, watchBatteryPct = null),
        )
        val json = JSONObject(stamped.json)
        assertEquals("background", json.getString(UploadContext.KEY_APP_STATE))
        assertTrue(json.getBoolean(UploadContext.KEY_SCREEN_LOCKED))
        assertTrue(json.isNull(UploadContext.KEY_WATCH_BATTERY))
        assertEquals("TEST-WATCH", json.getString("device_id"))
        assertEquals("A", json.getString("client_reading_id"))
        assertEquals(
            IngestReconciler.flushIdempotencyKey(listOf(plain)),
            IngestReconciler.flushIdempotencyKey(listOf(stamped)),
        )

        val withBattery = JSONObject(
            IngestReconciler.prepare(item(), upload = UploadContext.of(true, false, 64)).json,
        )
        assertEquals("foreground", withBattery.getString(UploadContext.KEY_APP_STATE))
        assertFalse(withBattery.getBoolean(UploadContext.KEY_SCREEN_LOCKED))
        assertEquals(64, withBattery.getInt(UploadContext.KEY_WATCH_BATTERY))
    }

    @Test fun send_time_context_is_on_every_reading_and_the_queue_row_stays_unchanged() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val requests = mutableListOf<String>()
        val api = object : HealthTechApiService {
            override suspend fun checkHealth() = error("no health")
            override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?) = error("batch only")
            override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): Response<ResponseBody> {
                val text = Buffer().also { body.writeTo(it) }.readUtf8()
                requests += text
                val row = JSONObject(text).getJSONArray("readings").getJSONObject(0)
                val receipt = JSONObject()
                    .put("patient_id", row.getString("patient_id"))
                    .put("status", "success")
                    .put("results", org.json.JSONArray().put(JSONObject()
                        .put("index", 0)
                        .put("client_reading_id", row.getString("client_reading_id"))
                        .put("status", "accepted")
                        .put("result", JSONObject()
                            .put("patient_id", row.getString("patient_id"))
                            .put("ingest_status", "accepted")
                            .put("reading_id", "stored"))))
                return Response.success(receipt.toString().toResponseBody())
            }
        }
        try {
            val original = """{"patient_id":"TEST-PATIENT","device_id":"TEST-WATCH","timestamp":"2026-10-17T12:00:00Z","heart_rate":72}"""
            db.ingestQueueDao().insertItem(IngestQueueEntity(payloadJson = original, clientReadingId = "synthetic-ctx"))
            var context: UploadContext? = UploadContext.of(false, true, 42)
            val repository = WearableRepository(
                db.ingestQueueDao(), db.sensorMetricDao(), api, db.localWriteTransaction(),
                uploadContextProvider = { context },
            )
            repository.processQueueDetailed()
            val sent = JSONObject(requests.single()).getJSONArray("readings").getJSONObject(0)
            assertEquals("background", sent.getString("app_state"))
            assertTrue(sent.getBoolean("screen_locked"))
            assertEquals(42, sent.getInt("watch_battery_pct"))
            assertEquals(original, db.ingestQueueDao().getAllItemsSync().single().payloadJson)

            context = null
            db.ingestQueueDao().insertItem(IngestQueueEntity(
                id = 2, payloadJson = original, clientReadingId = "synthetic-ctx-2", createdAt = 2,
            ))
            repository.processQueueDetailed()
            val second = JSONObject(requests.last()).getJSONArray("readings").getJSONObject(0)
            assertFalse(second.has("app_state"))
            assertFalse(second.has("screen_locked"))
            assertFalse(second.has("watch_battery_pct"))
        } finally {
            db.close()
        }
    }

}
