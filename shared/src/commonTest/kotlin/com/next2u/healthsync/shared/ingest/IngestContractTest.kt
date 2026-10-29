package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.model.BloodPressure
import com.next2u.healthsync.shared.model.HBandTelemetry
import com.next2u.healthsync.shared.model.IngestResponse
import com.next2u.healthsync.shared.model.QueuedReading
import com.next2u.healthsync.shared.model.SleepSummary
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class IngestApiKeyTest {
    @Test
    fun unusable_keys_are_rejected() {
        for (key in listOf("", "  ", "YOUR_HEALTHTECH_API_KEY_HERE", "your_healthtech_api_key_here", "PAT-HBAND-001", "pat-hband-001", "key\r\nInjected: value", "bad key")) {
            assertFalse(IngestApiKey.isUsable(key), key)
        }
        assertTrue(IngestApiKey.isUsable(" synthetic-key "))
    }
}

class IngestReadingIdentityTest {
    @Test
    fun syntax_and_reuse() {
        assertTrue(IngestReadingIdentity.isValid("8f3a2c1e-4b0d-4a11-9c22-111111111111"))
        assertTrue(IngestReadingIdentity.isValid("A.b_c:1-2"))
        assertFalse(IngestReadingIdentity.isValid(""))
        assertFalse(IngestReadingIdentity.isValid("id with space"))
        assertFalse(IngestReadingIdentity.isValid("x".repeat(129)))
        val existing = """{"heart_rate":72,"client_reading_id":"keep-me"}"""
        assertEquals("keep-me", IngestReadingIdentity.forPayload(existing) { fail("must reuse") })
        val generated = IngestReadingIdentity.forPayload("""{"heart_rate":72}""") { "new-id" }
        assertEquals("new-id", generated)
    }

    @Test
    fun history_id_is_deterministic_and_core_syntax() {
        val first = IngestReadingIdentity.historyId("C4:E3:42:AA:30:A4", "origin", 1_700_000_000_000L)
        val second = IngestReadingIdentity.historyId("C4:E3:42:AA:30:A4", "origin", 1_700_000_000_000L)
        val spo2 = IngestReadingIdentity.historyId("C4:E3:42:AA:30:A4", "spo2", 1_700_000_000_000L)
        assertEquals(first, second)
        assertTrue(IngestReadingIdentity.isValid(first))
        assertTrue(first.contains("origin"))
        assertTrue(first.contains("1700000000000"))
        assertTrue(first.contains("C4:E3:42:AA:30:A4"))
        assertTrue(IngestReadingIdentity.isValid(spo2))
        assertTrue(first != spo2)
        val messy = IngestReadingIdentity.historyId("dev id!", "hr/v", 12L)
        assertTrue(IngestReadingIdentity.isValid(messy), messy)
        assertFalse(messy.contains(" "))
        assertFalse(messy.contains("/"))
    }
}

class IngestConfigurationTest {
    @Test
    fun unusable_keys_never_open_transport() {
        for (key in listOf("", "  ", "your_healthtech_api_key_here", "pat-hband-001", "key\r\nInjected: value", "bad key")) {
            val config = IngestConfiguration.resolve("https://service.invalid", key, "synthetic-build-key")
            assertNotNull(config.transport().configurationError)
            assertFalse(config.status.keyConfigured)
        }
    }

    @Test
    fun cleared_override_never_reactivates_build_key() {
        assertTrue(IngestConfiguration.resolve("https://service.invalid", null, "synthetic-build").status.keyConfigured)
        val cleared = IngestConfiguration.resolve("https://service.invalid", "", "synthetic-build")
        assertTrue(cleared.status.usingSettingsOverride)
        assertFalse(cleared.status.keyConfigured)
    }

    @Test
    fun unsafe_url_blocks_and_diagnostics_omit_secrets() {
        for (url in listOf(
            "http://service.invalid/",
            "https://user:secret@service.invalid/",
            "https://service.invalid/?key=secret",
            "https://service.invalid/#secret",
            "not a url",
        )) {
            val config = IngestConfiguration.resolve(url, "synthetic-key", "")
            assertNotNull(config.transport().configurationError)
            assertFalse(config.status.toString().contains("secret"))
        }
        val config = IngestConfiguration.resolve("https://service.invalid:8443/private-path", "synthetic-key", "")
        assertEquals("https://service.invalid:8443", config.status.origin)
        assertFalse(config.status.toString().contains("private-path"))
        assertFalse(config.toString().contains("synthetic-key"))
        assertEquals("https://old.invalid/path/", IngestConfiguration.resolve("https://old.invalid/path", "k", "").transport().baseUrl)
        assertEquals("https://new.invalid/", IngestConfiguration.resolve("https://new.invalid/", "k", "").transport().baseUrl)
    }
}

class IngestPayloadTest {
    private fun telemetry(
        heartRate: Int = 76,
        systolic: Int = 0,
        diastolic: Int = 0,
        spO2: Int = 0,
        temp: Float = 0f,
        steps: Int = 1200,
        hrv: Int = 0,
        deviceId: String = "C4:E3:42:AA:30:A4",
        isReal: Boolean = true,
    ) = HBandTelemetry(
        deviceId = deviceId,
        deviceModel = "VE30",
        timestamp = "2026-09-14T12:00:00Z",
        heartRate = heartRate,
        bloodPressure = BloodPressure(systolic, diastolic),
        spO2 = spO2,
        temperatureCelsius = temp,
        steps = steps,
        calories = 50.4f,
        distanceMeters = 0f,
        hrvScore = hrv,
        sleepSummary = SleepSummary(),
        isRealSensorData = isReal,
    )

    @Test
    fun telemetry_json_omits_unmeasured_vitals() {
        val json = Json.parseToJsonElement(IngestPayload.telemetryToJson(telemetry(heartRate = 81), " PAT-42 ")).jsonObject
        assertEquals("PAT-42", json.getValue("patient_id").let { (it as JsonPrimitive).content })
        assertEquals("C4:E3:42:AA:30:A4", (json.getValue("device_id") as JsonPrimitive).content)
        assertEquals(81, (json.getValue("heart_rate") as JsonPrimitive).content.toInt())
        assertFalse(json.containsKey("blood_pressure"))
        assertFalse(json.containsKey("spo2"))
        assertFalse(json.containsKey("temperature"))
        assertFalse(json.containsKey("hrv_score"))
        assertEquals(1200, (json.getValue("steps") as JsonPrimitive).content.toInt())
        assertEquals(true, (json.getValue("is_real_sensor_data") as JsonPrimitive).content.toBoolean())
        assertEquals("healthtech-secure-api", (json.getValue("service") as JsonPrimitive).content)
    }

    @Test
    fun unmeasured_heart_rate_stays_zero() {
        val json = Json.parseToJsonElement(IngestPayload.telemetryToJson(telemetry(heartRate = 0), "")).jsonObject
        assertEquals(0, (json.getValue("heart_rate") as JsonPrimitive).content.toInt())
        assertEquals(IngestPayload.DEFAULT_PATIENT_ID, (json.getValue("patient_id") as JsonPrimitive).content)
        assertFalse(IngestPayload.isIngestibleJson(json.toString()))
    }

    @Test
    fun queued_heart_rate_obeys_core_bounds() {
        for (value in listOf(20.0, 72.5, 250.0)) {
            assertTrue(IngestPayload.isIngestibleJson(buildJsonObject { put("heart_rate", value) }.toString()))
        }
        for (value in listOf(19.9, 250.1, 999.0)) {
            assertFalse(IngestPayload.isIngestibleJson(buildJsonObject { put("heart_rate", value) }.toString()))
        }
        for (raw in listOf("{}", """{"heart_rate":null}""", """{"heart_rate":"NaN"}""", """{"heart_rate":"Infinity"}""")) {
            assertFalse(IngestPayload.isIngestibleJson(raw))
        }
    }

    @Test
    fun out_of_contract_reading_is_preserved_locally() = runTest {
        val reading = telemetry(heartRate = 999)
        val saved = mutableListOf<HBandTelemetry>()
        IngestDeduper().saveIfNeeded(reading) { saved += it }
        assertEquals(listOf(reading), saved)
        val payload = IngestPayload.telemetryToJson(reading, "SYNTHETIC")
        assertEquals(999, (Json.parseToJsonElement(payload).jsonObject.getValue("heart_rate") as JsonPrimitive).content.toInt())
        assertFalse(IngestPayload.isIngestibleJson(payload))
    }

    @Test
    fun normalization_preserves_explicit_source() {
        for (value in listOf(
            JsonPrimitive("companion_manual"),
            JsonPrimitive("ble_sim"),
            JsonPrimitive("ble_hband"),
            JsonPrimitive("http"),
            JsonPrimitive(""),
            JsonPrimitive("unknown-source"),
            JsonNull,
        )) {
            val raw = buildJsonObject {
                put("patient_id", "SYNTHETIC")
                put("ingest_source", value)
                put("heart_rate", 72)
            }.toString()
            val normalized = Json.parseToJsonElement(IngestPayload.normalizeQueuePayload(raw)).jsonObject
            assertTrue(normalized.containsKey("ingest_source"))
            assertEquals(value, normalized.getValue("ingest_source"))
        }
    }

    @Test
    fun normalization_never_infers_absent_source() {
        for (isReal in listOf(false, true)) {
            val raw = buildJsonObject {
                put("patient_id", "SYNTHETIC")
                put("is_real_sensor_data", isReal)
                put("heart_rate", 72)
            }.toString()
            val normalized = Json.parseToJsonElement(IngestPayload.normalizeQueuePayload(raw)).jsonObject
            assertFalse(normalized.containsKey("ingest_source"))
            assertEquals(isReal, (normalized.getValue("is_real_sensor_data") as JsonPrimitive).content.toBoolean())
        }
    }

    @Test
    fun auth_message_never_echoes_server_body() {
        val message = IngestPayload.authErrorMessage(403, "private server detail")
        assertTrue(message.contains("403"))
        assertTrue(message.contains("Ajustes"))
        assertFalse(message.contains("private server detail"))
    }
}

class IngestReconcilerTest {
    @Test
    fun prepare_stamps_durable_client_reading_id() {
        val payload = IngestPayload.telemetryToJson(
            HBandTelemetry(
                deviceId = "C4:E3:42:AA:30:A4",
                deviceModel = "VE30",
                timestamp = "2026-09-14T12:00:00Z",
                heartRate = 80,
                isRealSensorData = true,
            ),
            "PAT-1",
        )
        val row = QueuedReading(clientReadingId = "8f3a2c1e-4b0d-4a11-9c22-111111111111", payloadJson = payload)
        val first = IngestReconciler.prepare(row).json
        val json = Json.parseToJsonElement(first).jsonObject
        assertEquals("8f3a2c1e-4b0d-4a11-9c22-111111111111", (json.getValue("client_reading_id") as JsonPrimitive).content)
        assertEquals("2026-09-14T12:00:00Z", (json.getValue("timestamp") as JsonPrimitive).content)
        assertEquals(
            first,
            IngestReconciler.prepare(row).json,
        )
    }

    @Test
    fun single_receipt_requires_matching_ids() {
        val reading = IngestReconciler.prepare(
            QueuedReading(
                clientReadingId = "A",
                payloadJson = """{"patient_id":"P","device_id":"D","timestamp":"2026-09-14T12:00:00Z","heart_rate":72}""",
            ),
        )
        val valid = IngestResponse(
            success = true,
            ingestStatus = "accepted",
            clientReadingId = "A",
            readingId = "R",
            patientId = "P",
        )
        assertEquals(IngestItemOutcome.ACCEPTED, IngestReconciler.single(reading, 200, valid).outcome)
        assertEquals(IngestItemOutcome.TRANSIENT, IngestReconciler.single(reading, 200, valid.copy(clientReadingId = "B")).outcome)
        assertEquals(IngestItemOutcome.AUTH, IngestReconciler.single(reading, 401, valid).outcome)
    }

    @Test
    fun idempotency_key_is_stable_for_the_same_order() {
        val a = IngestReconciler.prepare(
            QueuedReading("id-1", """{"patient_id":"P","device_id":"D","timestamp":"2026-09-14T12:00:00Z","heart_rate":70}"""),
        )
        val b = IngestReconciler.prepare(
            QueuedReading("id-2", """{"patient_id":"P","device_id":"D","timestamp":"2026-09-14T12:01:00Z","heart_rate":71}"""),
        )
        assertEquals(IngestReconciler.flushIdempotencyKey(listOf(a, b)), IngestReconciler.flushIdempotencyKey(listOf(a, b)))
        assertTrue(IngestReconciler.flushIdempotencyKey(listOf(a, b)) != IngestReconciler.flushIdempotencyKey(listOf(b, a)))
    }
}

class IngestDeduperTest {
    private fun sample(hr: Int) = HBandTelemetry(
        deviceId = "C4:E3:42:AA:30:A4",
        deviceModel = "VE30",
        timestamp = "2026-09-14T12:00:00Z",
        heartRate = hr,
        isRealSensorData = true,
        steps = 10,
    )

    @Test
    fun optional_measurement_changes_are_not_duplicates() {
        val original = sample(72)
        val changed = original.copy(temperatureCelsius = 36.5f)
        val deduper = IngestDeduper()
        assertTrue(deduper.shouldEnqueue(original, nowMs = 1_000L))
        assertTrue(deduper.shouldEnqueue(changed, nowMs = 1_001L))
        assertFalse(deduper.shouldEnqueue(changed.copy(timestamp = "2026-09-14T12:00:01Z"), nowMs = 1_002L))
        assertTrue(deduper.shouldEnqueue(changed, nowMs = 31_001L))
    }
}
