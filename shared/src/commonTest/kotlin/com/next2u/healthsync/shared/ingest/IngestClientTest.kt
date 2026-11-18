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

    @Test
    fun health_and_batch_send_ios_version_with_the_build() = runTest {
        val seen = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            seen += request.url.encodedPath to request.headers[AppVersionHeader.NAME]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val config = IngestConfiguration.resolve("https://service.invalid/", "synthetic-key", "")
        val client = IngestClient(
            IngestClient.ingestHttpClient(engine),
            config.transport(),
            appVersion = "ios-0.1.0+21",
        )
        client.checkHealth()
        client.batchIngestWearableData("""{"patient_id":"P","readings":[]}""")
        assertEquals(listOf("/api/health", "/api/v1/wearables/batch-ingest"), seen.map { it.first })
        assertEquals(listOf("ios-0.1.0+21", "ios-0.1.0+21"), seen.map { it.second })
    }

    @Test
    fun ktor_path_uses_the_bundle_header_when_the_explicit_version_has_no_build() = runTest {
        var sent: String? = null
        val engine = MockEngine { request ->
            sent = request.headers[AppVersionHeader.NAME]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val config = IngestConfiguration.resolve("https://service.invalid/", "synthetic-key", "")
        val client = IngestClient(
            IngestClient.ingestHttpClient(engine),
            config.transport(),
            appVersion = "ios-0.1.0",
            platformVersion = "ios-0.1.0+21",
        )
        client.ingestWearableData("""{"patient_id":"P","heart_rate":72}""")
        assertEquals("ios-0.1.0+21", sent)
    }
}
