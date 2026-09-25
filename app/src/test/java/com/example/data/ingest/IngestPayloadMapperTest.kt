package com.example.data.ingest

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import com.example.data.local.IngestQueueEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestPayloadMapperTest {

    @Test fun `normalization preserves explicitly stored source without reclassification`() {
        for (value in listOf("companion_manual", "ble_sim", "ble_hband", "http", "", "unknown-source", JSONObject.NULL)) {
            for (legacy in listOf(false, true)) {
                val raw = JSONObject().put("patient_id", "SYNTHETIC")
                    .put("ingest_source", value)
                if (legacy) raw.put("metrics", JSONObject().put("heartRate", 72))
                else raw.put("heart_rate", 72)
                val normalized = JSONObject(IngestPayloadMapper.normalizeQueuePayload(raw.toString()))
                assertTrue(normalized.has("ingest_source"))
                assertEquals(value, normalized.get("ingest_source"))
            }
        }
    }

    @Test fun `normalization never infers an absent source from real sensor marker`() {
        for (legacy in listOf(false, true)) {
            for (isReal in listOf(false, true)) {
                val raw = JSONObject().put("patient_id", "SYNTHETIC").put("is_real_sensor_data", isReal)
                if (legacy) raw.put("metrics", JSONObject().put("heartRate", 72))
                else raw.put("heart_rate", 72)
                val normalized = JSONObject(IngestPayloadMapper.normalizeQueuePayload(raw.toString()))
                assertFalse(normalized.has("ingest_source"))
                assertEquals(isReal, normalized.getBoolean("is_real_sensor_data"))
            }
        }
    }

    @Test fun `queued heart rate obeys inclusive Core bounds without integer truncation`() {
        for (value in listOf(20.0, 72.5, 250.0)) {
            assertTrue(IngestPayloadMapper.isIngestibleJson(JSONObject().put("heart_rate", value).toString()))
        }
        for (value in listOf(19.9, 250.1, 999.0)) {
            assertFalse(IngestPayloadMapper.isIngestibleJson(JSONObject().put("heart_rate", value).toString()))
        }
        for (raw in listOf("{}", "{\"heart_rate\":null}", "{\"heart_rate\":\"NaN\"}", "{\"heart_rate\":\"Infinity\"}")) {
            assertFalse(IngestPayloadMapper.isIngestibleJson(raw))
        }
    }

    @Test fun `out of contract reading remains eligible for durable local capture`() = kotlinx.coroutines.runBlocking {
        val reading = telemetry(heartRate = 999)
        val saved = mutableListOf<HBandTelemetry>()
        IngestDeduper().saveIfNeeded(reading) { saved += it }
        assertEquals(listOf(reading), saved)
        val payload = IngestPayloadMapper.telemetryToJson(reading, "SYNTHETIC")
        assertEquals(999, JSONObject(payload).getInt("heart_rate"))
        assertFalse(IngestPayloadMapper.isIngestibleJson(payload))
    }

    @Test fun `legacy heart rate preserves numeric fractions before transport validation`() {
        for (value in listOf(72.5, 250.1)) {
            val raw = JSONObject().put("patient_id", "SYNTHETIC")
                .put("metrics", JSONObject().put("heartRate", value)).toString()
            val normalized = IngestPayloadMapper.normalizeQueuePayload(raw)
            assertEquals(value, JSONObject(normalized).getDouble("heart_rate"), 0.0)
            assertEquals(value <= 250.0, IngestPayloadMapper.isIngestibleJson(normalized))
        }
    }

    private fun telemetry(
        heartRate: Int = 76,
        systolic: Int = 0,
        diastolic: Int = 0,
        spO2: Int = 0,
        temp: Float = 0f,
        steps: Int = 1200,
        hrv: Int = 0,
        deviceId: String = "C4:E3:42:AA:30:A4",
        isReal: Boolean = true
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
        sleepSummary = SleepSummary(0, 0, 0),
        isRealSensorData = isReal
    )

    @Test
    fun `telemetry JSON omits unmeasured vitals and never fabricates HR`() {
        val json = JSONObject(IngestPayloadMapper.telemetryToJson(telemetry(heartRate = 81), " PAT-42 "))
        assertEquals("PAT-42", json.getString("patient_id"))
        assertEquals("C4:E3:42:AA:30:A4", json.getString("device_id"))
        assertEquals(81, json.getInt("heart_rate"))
        assertFalse(json.has("blood_pressure"))
        assertFalse(json.has("spo2"))
        assertFalse(json.has("temperature"))
        assertFalse(json.has("hrv_score"))
        assertEquals(1200, json.getInt("steps"))
        assertTrue(json.getBoolean("is_real_sensor_data"))
        assertEquals("healthtech-secure-api", json.getString("service"))
    }

    @Test
    fun `unmeasured heart rate stays zero instead of the old 72 fallback`() {
        val json = JSONObject(IngestPayloadMapper.telemetryToJson(telemetry(heartRate = 0), ""))
        assertEquals(0, json.getInt("heart_rate"))
        assertEquals(IngestPayloadMapper.DEFAULT_PATIENT_ID, json.getString("patient_id"))
        assertFalse(IngestPayloadMapper.isIngestibleJson(json.toString()))
    }

    @Test
    fun `measured BP and SpO2 are included when present`() {
        val json = JSONObject(
            IngestPayloadMapper.telemetryToJson(
                telemetry(heartRate = 70, systolic = 124, diastolic = 82, spO2 = 97, hrv = 64),
                "PAT-HBAND-001"
            )
        )
        assertEquals(124, json.getJSONObject("blood_pressure").getInt("systolic"))
        assertEquals(82, json.getJSONObject("blood_pressure").getInt("diastolic"))
        assertEquals(97, json.getInt("spo2"))
        assertEquals(64, json.getInt("hrv_score"))
    }

    @Test
    fun `legacy metrics wrapper is flattened without demo defaults`() {
        val raw = """
            {
              "deviceId": "AA:BB:CC:DD:EE:FF",
              "timestamp": "2026-09-14T12:01:00Z",
              "metrics": { "heartRate": 88, "steps": 40 }
            }
        """.trimIndent()
        val json = JSONObject(IngestPayloadMapper.normalizeQueuePayload(raw))
        assertEquals(IngestPayloadMapper.DEFAULT_PATIENT_ID, json.getString("patient_id"))
        assertEquals("AA:BB:CC:DD:EE:FF", json.getString("device_id"))
        assertEquals(88, json.getInt("heart_rate"))
        assertEquals(40, json.getInt("steps"))
        assertFalse(json.has("spo2"))
        assertFalse(json.has("blood_pressure"))
        assertFalse(json.has("temperature"))
    }

    @Test
    fun `already-flat payloads are not rewritten with invented vitals`() {
        val raw = """
            {
              "patient_id": "PAT-9",
              "device_id": "11:22:33:44:55:66",
              "timestamp": "2026-09-14T12:02:00Z",
              "heart_rate": 0
            }
        """.trimIndent()
        val json = JSONObject(IngestPayloadMapper.normalizeQueuePayload(raw))
        assertEquals(0, json.getInt("heart_rate"))
        assertEquals("PAT-9", json.getString("patient_id"))
        assertFalse(IngestPayloadMapper.isIngestibleJson(json.toString()))
    }

    @Test
    fun `placeholder device ids are discarded in favor of a real MAC`() {
        assertEquals(
            "C4:E3:42:AA:30:A4",
            IngestPayloadMapper.resolveDeviceId("HBAND-B57-89A4", "C4:E3:42:AA:30:A4")
        )
    }

    @Test
    fun `HTTP 401 and 403 are classified as auth failures`() {
        assertEquals(IngestHttpKind.AUTH, IngestPayloadMapper.classifyHttp(401))
        assertEquals(IngestHttpKind.AUTH, IngestPayloadMapper.classifyHttp(403))
        assertEquals(IngestHttpKind.CLIENT, IngestPayloadMapper.classifyHttp(422))
        assertEquals(IngestHttpKind.SERVER, IngestPayloadMapper.classifyHttp(503))
        val message = IngestPayloadMapper.authErrorMessage(401, "invalid api key")
        assertTrue(message.contains("401"))
        assertTrue(message.contains("Ajustes"))
        assertFalse(message.contains("invalid api key"))
        val message403 = IngestPayloadMapper.authErrorMessage(403, "private server detail")
        assertTrue(message403.contains("403"))
        assertTrue(message403.contains("Ajustes"))
        assertFalse(message403.contains("private server detail"))
    }

    @Test
    fun `queued telemetry JSON includes durable client_reading_id on every attempt`() {
        val row = IngestQueueEntity(
            payloadJson = IngestPayloadMapper.telemetryToJson(telemetry(heartRate = 80), "PAT-1"),
            clientReadingId = "8f3a2c1e-4b0d-4a11-9c22-111111111111",
        )
        val first = IngestReconciler.prepare(row).json
        val json = JSONObject(first)
        assertEquals("8f3a2c1e-4b0d-4a11-9c22-111111111111", json.getString("client_reading_id"))
        assertEquals("2026-09-14T12:00:00Z", json.getString("timestamp"))
        assertEquals("C4:E3:42:AA:30:A4", json.getString("device_id"))
        assertEquals(first, IngestReconciler.prepare(row.copy(retries = 3, lastAttemptAt = 99)).json)
    }
}

class IngestDeduperTest {

    @Test
    fun `measurement date changes do not change the elapsed recording interval`() {
        var elapsed = 1_000L
        val deduper = IngestDeduper(elapsedMs = { elapsed })
        val original = sample(72)
        assertTrue(deduper.shouldEnqueue(original))
        elapsed = 2_000L
        assertFalse(deduper.shouldEnqueue(original.copy(timestamp = "2026-09-15T12:00:00Z")))
        elapsed = 30_999L
        assertFalse(deduper.shouldEnqueue(original.copy(timestamp = "2026-09-13T12:00:00Z")))
        elapsed = 31_000L
        val corrected = original.copy(timestamp = "2026-09-13T12:00:30Z")
        assertTrue(deduper.shouldEnqueue(corrected))
        assertEquals("2026-09-13T12:00:30Z", corrected.timestamp)
    }

    @Test
    fun `changed measurements bypass the interval but invalid data do not reset it`() {
        var elapsed = 100L
        val deduper = IngestDeduper(elapsedMs = { elapsed })
        assertTrue(deduper.shouldEnqueue(sample(72)))
        elapsed = 200L
        assertTrue(deduper.shouldEnqueue(sample(75)))
        elapsed = 10_000L
        assertFalse(deduper.shouldEnqueue(sample(0)))
        assertFalse(deduper.shouldEnqueue(sample(80, real = false)))
        elapsed = 30_199L
        assertFalse(deduper.shouldEnqueue(sample(75)))
        elapsed = 30_200L
        assertTrue(deduper.shouldEnqueue(sample(75)))
    }

    @Test
    fun `monotonic clock may have a negative origin and repeated callbacks do not postpone acceptance`() {
        var elapsed = -90_000L
        val deduper = IngestDeduper(elapsedMs = { elapsed })
        assertTrue(deduper.shouldEnqueue(sample(72)))
        for (at in listOf(-90_000L, -80_000L, -70_000L, -60_001L)) {
            elapsed = at
            assertFalse(deduper.shouldEnqueue(sample(72)))
        }
        elapsed = -60_000L
        assertTrue(deduper.shouldEnqueue(sample(72)))
    }

    @Test
    fun `backwards interval clock does not suppress repeated readings indefinitely`() {
        val deduper = IngestDeduper()
        assertTrue(deduper.shouldEnqueue(sample(72), nowMs = 1_000_000L))
        assertTrue(deduper.shouldEnqueue(sample(72), nowMs = 10_000L))
        assertFalse(deduper.shouldEnqueue(sample(72), nowMs = 39_999L))
        assertTrue(deduper.shouldEnqueue(sample(72), nowMs = 40_000L))
    }

    private fun sample(hr: Int, steps: Int = 100, real: Boolean = true) = HBandTelemetry(
        deviceId = "AA:BB:CC:DD:EE:FF",
        deviceModel = "VE30",
        timestamp = "2026-09-14T12:00:00Z",
        heartRate = hr,
        bloodPressure = BloodPressure(0, 0),
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = steps,
        calories = 0f,
        distanceMeters = 0f,
        hrvScore = 0,
        sleepSummary = SleepSummary(0, 0, 0),
        isRealSensorData = real
    )

    @Test
    fun `demo snapshots and invalid HR are never auto-enqueued`() {
        val deduper = IngestDeduper(minIntervalMs = 30_000L)
        assertFalse(deduper.shouldEnqueue(sample(hr = 72, real = false), nowMs = 1_000L))
        assertFalse(deduper.shouldEnqueue(sample(hr = 0, real = true), nowMs = 2_000L))
    }

    @Test
    fun `identical live readings are coalesced within the interval`() {
        val deduper = IngestDeduper(minIntervalMs = 30_000L)
        assertTrue(deduper.shouldEnqueue(sample(hr = 80), nowMs = 1_000L))
        assertFalse(deduper.shouldEnqueue(sample(hr = 80), nowMs = 10_000L))
        assertTrue(deduper.shouldEnqueue(sample(hr = 80), nowMs = 32_000L))
        assertTrue(deduper.shouldEnqueue(sample(hr = 91), nowMs = 33_000L))
    }
}
