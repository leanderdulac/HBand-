package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.store.PatientStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IngestRuntimeTest {
    @BeforeTest
    fun reset() {
        PatientStore.resetForTests()
        IngestRuntime.useStore(MemoryQueueStore())
        IngestRuntime.useEngine(null)
        IngestRuntime.configure("https://service.invalid/", "synthetic-key", "")
    }

    @Test
    fun record_assigns_durable_id_and_flush_uses_it() = runTest {
        val first = IngestRuntime.recordLive(
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
            patientId = "PAT-1",
        )
        assertTrue(first.queued)
        assertNotNull(first.clientReadingId)
        assertEquals(1, IngestRuntime.pendingCount())

        var sentBody = ""
        var sentIdem = ""
        IngestRuntime.useEngine(
            MockEngine { request ->
                sentIdem = request.headers["Idempotency-Key"].orEmpty()
                sentBody = request.body.toString()
                val id = first.clientReadingId
                respond(
                    content = """{"patient_id":"PAT-1","results":[{"index":0,"client_reading_id":"$id","status":"accepted","result":{"patient_id":"PAT-1","ingest_status":"accepted","reading_id":"R1"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        val flush = IngestRuntime.flush()
        assertEquals(200, flush.lastHttpCode)
        assertEquals(1, flush.syncedCount)
        assertEquals(0, IngestRuntime.pendingCount())
        assertTrue(sentIdem.isNotEmpty())
        assertTrue(first.clientReadingId!! in sentBody || flush.message.contains("concluídos 1"))
    }

    @Test
    fun placeholder_key_blocks_flush_but_keeps_the_row() = runTest {
        IngestRuntime.configure("https://service.invalid/", "YOUR_HEALTHTECH_API_KEY_HERE", "")
        val recorded = IngestRuntime.recordLive(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            heartRate = 72,
            spo2 = 0,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 0,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            patientId = "PAT-1",
        )
        assertTrue(recorded.queued)
        val flush = IngestRuntime.flush()
        assertEquals(IngestApiKey.CONFIGURATION_ERROR, flush.message)
        assertEquals(1, IngestRuntime.pendingCount())
    }

    @Test
    fun missing_hr_is_not_queued_and_does_not_need_the_store() = runTest {
        val recorded = IngestRuntime.recordLive(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            heartRate = 0,
            spo2 = 98,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 0,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            patientId = "PAT-1",
        )
        assertFalse(recorded.queued)
        assertEquals(IngestPayload.MISSING_HR_ERROR, recorded.message)
        assertEquals(0, IngestRuntime.pendingCount())
        assertEquals(0, PatientStore.metricCount())
    }
}
