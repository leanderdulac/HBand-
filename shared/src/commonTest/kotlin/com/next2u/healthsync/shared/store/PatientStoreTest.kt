package com.next2u.healthsync.shared.store

import com.next2u.healthsync.shared.ingest.IngestRuntime
import com.next2u.healthsync.shared.ingest.MemoryQueueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatientStoreTest {
    private lateinit var codec: MemorySnapshotCodec

    @BeforeTest
    fun reset() {
        codec = MemorySnapshotCodec()
        PatientStore.resetForTests(codec)
        IngestRuntime.useStore(MemoryQueueStore())
        IngestRuntime.useEngine(null)
        IngestRuntime.configure("https://service.invalid/", "synthetic-key", "")
    }

    @Test
    fun open_missing_file_creates_android_default_profile() {
        assertEquals(StoreStatus.READY, PatientStore.open())
        val profile = PatientStore.profile()
        assertEquals("CURRENT_USER", profile.id)
        assertEquals("PAT-HBAND-001", profile.patientId)
        assertEquals("Alex Rivera", profile.fullName)
        assertEquals(32, profile.age)
        assertEquals("Masculino", profile.gender)
        assertEquals(178f, profile.heightCm)
        assertEquals(74f, profile.weightKg)
        assertEquals(8000, profile.dailyStepGoal)
        assertEquals(2500, profile.targetWaterMl)
        assertEquals("+55 11 98765-4321", profile.emergencyContact)
        assertEquals("Monitoramento cardiovascular preventivo com HBand VE30.", profile.medicalNotes)
        assertEquals(PatientStore.SCHEMA_VERSION, 1)
        assertTrue(codec.writeCount >= 1)
        assertTrue(codec.plaintext.orEmpty().contains("\"schemaVersion\":1"))
    }

    @Test
    fun metric_survives_reopen() = runTest {
        assertEquals(StoreStatus.READY, PatientStore.open())
        PatientStore.attachQueue()
        val recorded = IngestRuntime.recordLive(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            heartRate = 76,
            spo2 = 98,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 12,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            patientId = "PAT-HBAND-001",
        )
        assertTrue(recorded.queued)
        assertEquals(1, PatientStore.metricCount())
        val persisted = codec.plaintext
        PatientStore.useCodec(MemorySnapshotCodec(persisted))
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertEquals(1, PatientStore.metricCount())
        val metric = PatientStore.metrics().single()
        assertEquals("C4:E3:42:AA:30:A4", metric.deviceId)
        assertEquals(76, metric.heartRate)
        assertEquals(98, metric.spO2)
        assertEquals(1, PatientStore.queue().size)
        assertEquals("PAT-HBAND-001", PatientStore.profile().patientId)
    }

    @Test
    fun real_reading_without_hr_is_stored_but_not_queued() = runTest {
        assertEquals(StoreStatus.READY, PatientStore.open())
        PatientStore.attachQueue()
        val recorded = IngestRuntime.recordLive(
            deviceId = "AA:BB",
            deviceModel = "VE30",
            heartRate = 0,
            spo2 = 97,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 0,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            patientId = "PAT-HBAND-001",
        )
        assertFalse(recorded.queued)
        assertEquals(0, IngestRuntime.pendingCount())
        assertEquals(1, PatientStore.metricCount())
        assertEquals(97, PatientStore.metrics().single().spO2)
    }

    @Test
    fun thirty_second_dedup_still_persists_the_metric() = runTest {
        assertEquals(StoreStatus.READY, PatientStore.open())
        PatientStore.attachQueue()
        suspend fun once() = IngestRuntime.recordLive(
            deviceId = "AA:BB",
            deviceModel = "VE30",
            heartRate = 80,
            spo2 = 96,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 0,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            patientId = "PAT-1",
        )
        assertTrue(once().queued)
        val second = once()
        assertFalse(second.queued)
        assertTrue(second.message.contains("gravada localmente"))
        assertEquals(1, IngestRuntime.pendingCount())
        assertEquals(2, PatientStore.metricCount())
    }

    @Test
    fun unknown_schema_fails_without_writing() {
        val raw = """{"schemaVersion":99,"metrics":[],"queue":[],"profile":{"id":"CURRENT_USER"}}"""
        codec = MemorySnapshotCodec(raw)
        PatientStore.useCodec(codec)
        assertEquals(StoreStatus.FAILED, PatientStore.open())
        assertEquals(0, codec.writeCount)
        assertEquals(raw, codec.plaintext)
        assertTrue(PatientStore.detail.contains("esquema"))
        assertFalse(PatientStore.isReady)
    }

    @Test
    fun corrupt_plaintext_fails_without_writing() {
        codec = MemorySnapshotCodec("not-json")
        PatientStore.useCodec(codec)
        assertEquals(StoreStatus.FAILED, PatientStore.open())
        assertEquals(0, codec.writeCount)
        assertEquals("not-json", codec.plaintext)
    }

    @Test
    fun profile_and_wellness_roundtrip() {
        assertEquals(StoreStatus.READY, PatientStore.open())
        val updated = UserProfile(patientId = "PAT-TEST", fullName = "Maria")
        assertTrue(PatientStore.saveProfile(updated))
        assertTrue(PatientStore.appendHydration(HydrationLog(amountMl = 250, timestampMillis = 1, dateString = "2026-10-15")))
        assertTrue(PatientStore.appendBreathing(BreathingSession(durationSeconds = 60, timestampMillis = 2, dateString = "2026-10-15")))
        val persisted = codec.plaintext
        PatientStore.useCodec(MemorySnapshotCodec(persisted))
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertEquals("PAT-TEST", PatientStore.profile().patientId)
        assertEquals("Maria", PatientStore.profile().fullName)
        assertEquals(250, PatientStore.hydration().single().amountMl)
        assertEquals(60, PatientStore.breathing().single().durationSeconds)
    }
}
