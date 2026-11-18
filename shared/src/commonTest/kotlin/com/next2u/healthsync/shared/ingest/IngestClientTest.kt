package com.next2u.healthsync.shared.ingest

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngestClientTest {
    @Test
    fun posts_ingest_with_api_key_and_idempotency() = runTest {
        var capturedPath = ""
        var capturedApiKey = ""
        var capturedIdem = ""
        var capturedMethod = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedApiKey = request.headers["X-API-Key"].orEmpty()
            capturedIdem = request.headers["Idempotency-Key"].orEmpty()
            capturedMethod = request.method.value
            respond(
                content = """{"success":true,"ingest_status":"accepted"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = IngestConfiguration.resolve("https://service.invalid/core", "synthetic-key", "")
        val client = IngestClient(IngestClient.ingestHttpClient(engine), config.transport())
        val call = client.ingestWearableData("""{"patient_id":"P","heart_rate":72}""", "idem-1")
        assertEquals(200, call.statusCode)
        assertEquals(IngestHttpKind.SUCCESS, call.kind)
        assertEquals("POST", capturedMethod)
        assertEquals("/core/api/v1/wearables/ingest", capturedPath)
        assertEquals("synthetic-key", capturedApiKey)
        assertEquals("idem-1", capturedIdem)
        assertTrue(call.body.orEmpty().contains("accepted"))
    }

    @Test
    fun network_failure_is_classified_without_throwing() = runTest {
        val engine = MockEngine { error("synthetic offline") }
        val config = IngestConfiguration.resolve("https://service.invalid/", "synthetic-key", "")
        val client = IngestClient(IngestClient.ingestHttpClient(engine), config.transport())
        val call = client.checkHealth()
        assertEquals(0, call.statusCode)
        assertEquals(IngestHttpKind.NETWORK, call.kind)
        assertNull(call.body)
    }

    @Test
    fun create_refuses_placeholder_key() {
        val config = IngestConfiguration.resolve("https://service.invalid/", "YOUR_HEALTHTECH_API_KEY_HERE", "")
        try {
            IngestClient.create(config, MockEngine { error("unused") })
            error("must refuse")
        } catch (error: IllegalStateException) {
            assertEquals(IngestApiKey.CONFIGURATION_ERROR, error.message)
        }
    }
}
