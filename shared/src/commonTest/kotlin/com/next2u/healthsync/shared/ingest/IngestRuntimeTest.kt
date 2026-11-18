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

    @Test
    fun retry_and_delete_keep_patient_copy() = runTest {
        val recorded = IngestRuntime.recordLive(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            heartRate = 74,
            spo2 = 97,
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
        val id = recorded.clientReadingId!!
        IngestRuntime.retryFailed(id)
        assertEquals(1, IngestRuntime.pendingCount())
        assertEquals("Aguardando envio", IngestRuntime.statusLabel("PENDING"))
        assertEquals("Falha no envio", IngestRuntime.statusLabel("FAILED"))
        val visible = IngestRuntime.visibleItems()
        assertEquals(1, visible.size)
        assertEquals(id, visible.single().clientReadingId)
        assertTrue(visible.single().queuedAtMillis > 0)
        IngestRuntime.deleteItem(id)
        assertEquals(0, IngestRuntime.pendingCount())
        assertTrue(IngestRuntime.visibleItems().isEmpty())
    }

    @Test
    fun unauthorized_rows_return_to_queue_when_a_new_key_is_configured() = runTest {
        IngestRuntime.recordLive(
            deviceId = "C4:E3:42:AA:30:A4", deviceModel = "VE30", heartRate = 81, spo2 = 0,
            systolic = 0, diastolic = 0, temperatureCelsius = 0f, steps = 0, calories = 0f,
            distanceMeters = 0f, hrvScore = 0, patientId = "PAT-1",
        )
        IngestRuntime.useEngine(MockEngine { respond("""{"detail":"unauthorized"}""", HttpStatusCode.Unauthorized) })
        IngestRuntime.flush()
        assertEquals(1, IngestRuntime.failedCount())
        assertEquals(1, IngestRuntime.waitingCount())
        // Same key again: nothing changes (no retry storm on every configure call).
        IngestRuntime.configure("https://service.invalid/", "synthetic-key", "")
        assertEquals(1, IngestRuntime.failedCount())
        // New key: unauthorized rows go back to PENDING.
        IngestRuntime.configure("https://service.invalid/", "synthetic-key-2", "")
        assertEquals(0, IngestRuntime.failedCount())
        assertEquals(1, IngestRuntime.pendingCount())
        assertEquals(1, IngestRuntime.waitingCount())
    }

    @Test
    fun device_label_is_sent_with_every_reading_including_older_queue_rows() = runTest {
        IngestRuntime.setDeviceLabel(null)
        IngestRuntime.recordLive(
            deviceId = "C4:E3:42:AA:30:A4", deviceModel = "VE30", heartRate = 70, spo2 = 0,
            systolic = 0, diastolic = 0, temperatureCelsius = 0f, steps = 0, calories = 0f,
            distanceMeters = 0f, hrvScore = 0, patientId = "PAT-1",
        )
        IngestRuntime.setDeviceLabel("  iPhone   de LFM ")
        assertEquals("iPhone de LFM", IngestRuntime.deviceLabel)
        var body = ""
        IngestRuntime.useEngine(MockEngine { request ->
            body = (request.body as io.ktor.http.content.TextContent).text
            respond("""{"detail":"x"}""", HttpStatusCode.ServiceUnavailable)
        })
        IngestRuntime.flush()
        assertTrue(body.contains("\"device_label\":\"iPhone de LFM\""), body)
        IngestRuntime.setDeviceLabel(null)
    }

    @Test
    fun history_sample_uses_deterministic_id_and_skips_live_deduper() = runTest {
        val first = IngestRuntime.recordHistorySample(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            timestampIso = "2026-10-29T12:00:00Z",
            timestampMillis = 1_700_000_000_000L,
            metricType = "origin",
            heartRate = 72,
            spo2 = 0,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 10,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            deepSleepMinutes = 0,
            lightSleepMinutes = 0,
            awakeMinutes = 0,
            patientId = "PAT-1",
        )
        val sameAgain = IngestRuntime.recordHistorySample(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            timestampIso = "2026-10-29T12:00:00Z",
            timestampMillis = 1_700_000_000_000L,
            metricType = "origin",
            heartRate = 72,
            spo2 = 0,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 10,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            deepSleepMinutes = 0,
            lightSleepMinutes = 0,
            awakeMinutes = 0,
            patientId = "PAT-1",
        )
        val laterSameHr = IngestRuntime.recordHistorySample(
            deviceId = "C4:E3:42:AA:30:A4",
            deviceModel = "VE30",
            timestampIso = "2026-10-29T12:05:00Z",
            timestampMillis = 1_700_000_300_000L,
            metricType = "origin",
            heartRate = 72,
            spo2 = 0,
            systolic = 0,
            diastolic = 0,
            temperatureCelsius = 0f,
            steps = 12,
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            deepSleepMinutes = 0,
            lightSleepMinutes = 0,
            awakeMinutes = 0,
            patientId = "PAT-1",
        )
        assertTrue(first.queued)
        assertEquals("C4:E3:42:AA:30:A4-origin-1700000000000", first.clientReadingId)
        assertFalse(sameAgain.queued)
        assertEquals(first.clientReadingId, sameAgain.clientReadingId)
        assertTrue(laterSameHr.queued, "histórico não usa a janela de 30s das leituras ao vivo")
        assertEquals(2, IngestRuntime.pendingCount())
        assertTrue(laterSameHr.clientReadingId!!.contains("1700000300000"))
    }
}
