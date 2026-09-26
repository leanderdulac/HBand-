package com.example.data.local

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ingest.QueueAuthorization
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Storage-only fixtures; no transport, BLE, real identity or production startup. */
@RunWith(AndroidJUnit4::class)
class UpgradePreservationAndroidLabTest {
    private lateinit var context: Context
    private val tables = listOf("advanced_measurements", "breathing_sessions", "hband_sensor_metrics",
        "hydration_logs", "ingest_queue", "user_profile")
    private val baseline get() = File(context.filesDir, "upgrade-synthetic-baseline.json")

    @Before fun requireLab() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.aistudio.hbandhealthtech.pxq97m.storagelab")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("storageLab") == "synthetic-only")
        check(context.applicationContext.javaClass == android.app.Application::class.java)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun keyMetadataDigest(): String {
        val prefs = context.getSharedPreferences("hband_sqlcipher", Context.MODE_PRIVATE)
        val wrapped = checkNotNull(prefs.getString("wrapped_db_key", null))
        val iv = checkNotNull(prefs.getString("wrapped_db_key_iv", null))
        return digest("$wrapped|$iv".toByteArray())
    }

    private fun queueRows() = listOf(
        IngestQueueEntity(701, "{\"patient_id\":\"SYNTHETIC-UPGRADE\",\"value\":71}", "PENDING", 0, 1700000000701, null, null, "synthetic-upgrade-701"),
        IngestQueueEntity(702, "{\"patient_id\":\"SYNTHETIC-UPGRADE\",\"value\":72}", "FAILED", 2, 1700000000702, 1700000001002,
            QueueAuthorization.UNAUTHORIZED_PREFIX + ": synthetic-401", "synthetic-upgrade-702"),
        IngestQueueEntity(703, "{\"patient_id\":\"SYNTHETIC-UPGRADE\",\"value\":73}", "FAILED", 3, 1700000000703, 1700000001003,
            QueueAuthorization.FORBIDDEN_PREFIX + ": synthetic-403", "synthetic-upgrade-703"),
        // SYNCED is the existing local receipt representation, not evidence of server acceptance.
        IngestQueueEntity(704, "{\"patient_id\":\"SYNTHETIC-UPGRADE\",\"value\":74}", "SYNCED", 1, 1700000000704, 1700000001004, null, "synthetic-upgrade-704"),
    )

    private fun snapshot(sql: SupportSQLiteDatabase): String = JSONObject().apply {
        put("schema", sql.version)
        tables.forEach { table ->
            put(table, JSONArray().apply {
                sql.query("SELECT * FROM $table ORDER BY id").use { c ->
                    while (c.moveToNext()) put(JSONObject().apply {
                        for (i in 0 until c.columnCount) put(c.getColumnName(i),
                            if (c.isNull(i)) JSONObject.NULL else JSONObject().put("type", c.getType(i)).put("value", c.getString(i)))
                    })
                }
            })
        }
    }.toString()

    private suspend fun assertState(db: AppDatabase) {
        assertEquals(8, db.openHelper.writableDatabase.version)
        assertEquals(queueRows().reversed(), db.ingestQueueDao().getAllItemsSync())
        assertTrue(db.ingestQueueDao().hasAuthorizationBlock(QueueAuthorization.UNAUTHORIZED_PREFIX, QueueAuthorization.FORBIDDEN_PREFIX))
        assertTrue(QueueAuthorization.isBlocked(queueRows()[1]))
        assertTrue(QueueAuthorization.isBlocked(queueRows()[2]))
        assertFalse(SqliteFileInspector.looksLikeUnencryptedSqlite(context.getDatabasePath(AppDatabase.DATABASE_NAME)))
    }

    private fun report(state: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("upgrade_pid", Process.myPid().toString())
            putString("upgrade_snapshot_sha256", digest(state.toByteArray()))
            putString("upgrade_key_metadata_sha256", keyMetadataDigest())
            putString("upgrade_snapshot", state)
        })
    }

    @Test fun seedPreviousVersion(): Unit = runBlocking {
        check(!baseline.exists()) { "Never replace baseline" }
        check(!context.getDatabasePath(AppDatabase.DATABASE_NAME).exists()) { "Fresh lab database required" }
        val db = AppDatabase.openVerified(context)
        try {
            db.localWriteTransaction().run {
                queueRows().forEach { db.ingestQueueDao().insertItem(it) }
                // Synthetic records use the existing entities/DAOs, never a clinical API contract.
                db.userProfileDao().saveUserProfile(UserProfileEntity(
                    patientId = "SYNTHETIC-UPGRADE", fullName = "Pessoa Sintetica Upgrade",
                    age = 40, gender = "Nao informado", heightCm = 170f, weightKg = 70f,
                    dailyStepGoal = 6000, targetWaterMl = 2000, emergencyContact = "",
                    medicalNotes = "SYNTHETIC LAB ONLY"))
                for (row in 1L..2L) {
                    val time = 1700000000000L + row
                    db.sensorMetricDao().insertMetric(HBandSensorMetricEntity(
                        id = row, deviceId = "SYNTHETIC-DEVICE", timestamp = "2023-11-14T22:13:20Z",
                        timestampMillis = time, heartRate = 70 + row.toInt(), systolicBp = 120,
                        diastolicBp = 80, spO2 = 98, temperatureCelsius = 36.5f, steps = row.toInt() * 100,
                        calories = 1.25f, distanceMeters = 20.5f, hrvScore = 40, deepSleepMinutes = 60,
                        lightSleepMinutes = 120, awakeMinutes = 10))
                    db.hydrationDao().insertLog(HydrationLogEntity(row, 200 + row.toInt(), time, "2023-11-14"))
                    db.breathingDao().insertSession(BreathingSessionEntity(row, 60 + row.toInt(), "SYNTHETIC", time, "2023-11-14"))
                    db.advancedMeasurementDao().insert(AdvancedMeasurementEntity(
                        id = row, deviceId = "SYNTHETIC-DEVICE", kind = AdvancedMeasurementKind.BREATH,
                        timestamp = "2023-11-14T22:13:20Z", timestampMillis = time,
                        summary = "SYNTHETIC LAB ONLY", numericValue = row.toFloat(), secondaryValue = 0.25f,
                        sampleCount = 1, payloadJson = "{\"synthetic\":true,\"sample\":$row}", isReal = false))
                }
            }
            assertState(db)
            val state = snapshot(db.openHelper.writableDatabase)
            baseline.writeText(JSONObject().put("snapshot", state).put("seed_pid", Process.myPid())
                .put("key_digest", keyMetadataDigest()).toString())
            report(state)
        } finally { db.close() }
    }

    @Test fun verifyExistingStateInNewProcess(): Unit = runBlocking {
        check(baseline.isFile) { "Baseline required; verification never seeds or repairs" }
        check(context.getDatabasePath(AppDatabase.DATABASE_NAME).isFile)
        val before = baseline.readBytes()
        val expected = JSONObject(before.toString(Charsets.UTF_8))
        assertNotEquals(expected.getInt("seed_pid"), Process.myPid())
        assertEquals(expected.getString("key_digest"), keyMetadataDigest())
        val db = AppDatabase.openVerified(context)
        try {
            assertState(db)
            val state = snapshot(db.openHelper.writableDatabase)
            assertEquals(expected.getString("snapshot"), state)
            assertEquals(expected.getString("key_digest"), keyMetadataDigest())
            assertArrayEquals(before, baseline.readBytes())
            report(state)
        } finally { db.close() }
    }
}
