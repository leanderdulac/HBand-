package com.example.data.remote

import android.app.Application
import android.content.Context
import com.example.data.ingest.IngestApiKey
import com.example.data.ingest.IngestDiagnostics
import com.example.data.ingest.QueueAuthorization
import com.example.data.local.IngestQueueEntity
import com.example.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class IngestConfigurationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun resetSyntheticPreferences() {
        context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE).edit().clear().commit()
        RetrofitClient.initialize(context)
    }
    private val noNetwork = object : HealthTechApiService {
        override suspend fun checkHealth(): retrofit2.Response<HealthCheckResponse> = throw AssertionError("No network")
        override suspend fun ingestWearableData(body: RequestBody, idempotencyKey: String?): retrofit2.Response<IngestResponse> = throw AssertionError("No network")
        override suspend fun batchIngestWearableData(body: RequestBody, idempotencyKey: String?): retrofit2.Response<ResponseBody> = throw AssertionError("No network")
    }

    @Test fun unusable_keys_never_construct_ingest_service() {
        for (key in listOf("", "  ", "your_healthtech_api_key_here", "pat-hband-001", "key\r\nInjected: value", "bad key")) {
            val config = IngestConfiguration.resolve("https://service.invalid", key, "synthetic-build-key")
            assertNotNull(config.transport { _, _ -> throw AssertionError("Factory must not run") }.configurationError)
            assertFalse(config.status.keyConfigured)
        }
        assertTrue(IngestApiKey.isUsable(" synthetic-key "))
    }

    @Test fun cleared_override_never_reactivates_build_key() {
        assertTrue(IngestConfiguration.resolve("https://service.invalid", null, "synthetic-build").status.keyConfigured)
        val cleared = IngestConfiguration.resolve("https://service.invalid", "", "synthetic-build")
        assertTrue(cleared.status.usingSettingsOverride)
        assertFalse(cleared.status.keyConfigured)
    }

    @Test fun unsafe_url_blocks_requests_and_diagnostics_never_show_sensitive_parts() {
        for (url in listOf("http://service.invalid/", "https://user:secret@service.invalid/", "https://service.invalid/?key=secret", "https://service.invalid/#secret", "not a url")) {
            val config = IngestConfiguration.resolve(url, "synthetic-key", "")
            assertNotNull(config.transport { _, _ -> throw AssertionError("Factory must not run") }.configurationError)
            assertFalse(config.status.toString().contains("secret"))
        }
        val config = IngestConfiguration.resolve("https://service.invalid:8443/private-path", "synthetic-key", "")
        assertEquals("https://service.invalid:8443", config.status.origin)
        assertFalse(config.status.toString().contains("private-path"))
        assertFalse(config.toString().contains("synthetic-key"))
    }

    @Test fun service_factory_receives_coherent_immutable_pairs() {
        val before = IngestConfiguration.resolve("https://old.invalid/path", "old-synthetic", "")
        val after = IngestConfiguration.resolve("https://new.invalid/", "new-synthetic", "")
        val pairs = mutableListOf<Pair<String, String>>()
        val factory = { url: String, key: String -> pairs += url to key; noNetwork }
        before.transport(factory); after.transport(factory); before.transport(factory)
        assertEquals(listOf("https://old.invalid/path/" to "old-synthetic", "https://new.invalid/" to "new-synthetic", "https://old.invalid/path/" to "old-synthetic"), pairs)
    }

    @Test fun client_keeps_captured_key_replaces_request_header_and_does_not_follow_redirects() {
        val client = RetrofitClient.createClient("synthetic-captured")
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects)
        var sent: Request? = null
        val isolated = client.newBuilder().addInterceptor { chain ->
            sent = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("Synthetic redirect")
                .header("Location", "https://other.invalid/").body("".toResponseBody()).build()
        }.build()
        isolated.newCall(Request.Builder().url("https://synthetic.invalid/").header("x-api-key", "old-header").build()).execute().use {
            assertEquals(302, it.code)
        }
        assertEquals(listOf("synthetic-captured"), sent!!.headers.values("X-API-Key"))
        assertTrue(client.interceptors.none { it is okhttp3.logging.HttpLoggingInterceptor })
    }

    @Test fun settings_reload_clear_and_invalid_update_preserve_expected_configuration() = runBlocking {
        RetrofitClient.updateConfig(context, "https://approved.invalid/", "synthetic-key")
        val expected = RetrofitClient.configurationState.first()
        RetrofitClient.initialize(context)
        assertEquals(expected, RetrofitClient.configurationState.first())
        assertTrue(runCatching { RetrofitClient.updateConfig(context, "https://user:secret@other.invalid/", "new") }.isFailure)
        assertEquals(expected, RetrofitClient.configurationState.first())
        RetrofitClient.initialize(context)
        assertEquals(expected, RetrofitClient.configurationState.first())
        RetrofitClient.updateConfig(context, "https://approved.invalid/", "")
        RetrofitClient.initialize(context)
        assertNotNull(RetrofitClient.captureIngestTransport().configurationError)
    }

    @Test fun diagnostics_count_persisted_states_without_exposing_record_contents() {
        val rows = listOf(
            IngestQueueEntity(payloadJson = "private-patient", status = "PENDING"),
            IngestQueueEntity(payloadJson = "private-reading", status = "FAILED", errorMessage = QueueAuthorization.UNAUTHORIZED_PREFIX + " secret-raw-error"),
            IngestQueueEntity(payloadJson = "private-done", status = "SYNCED"),
            IngestQueueEntity(payloadJson = "private-other", status = "UNKNOWN"),
        )
        val diag = IngestDiagnostics.from(rows, IngestConfiguration.resolve("https://service.invalid/path", "synthetic-key", "").status)
        assertTrue(diag.loaded); assertTrue(diag.authorizationPaused)
        assertEquals(listOf(1, 1, 1, 1), listOf(diag.pending, diag.failed, diag.synced, diag.unknown))
        assertFalse(diag.toString().contains("private")); assertFalse(diag.toString().contains("secret"))
        assertFalse(diag.toString().contains("synthetic-key"))
        assertFalse(IngestDiagnostics().loaded)
    }
}
