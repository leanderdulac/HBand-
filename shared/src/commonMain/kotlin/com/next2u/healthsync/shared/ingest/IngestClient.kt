package com.next2u.healthsync.shared.ingest

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent

data class IngestHttpCall(
    val statusCode: Int,
    val body: String?,
    val kind: IngestHttpKind,
)

/**
 * HealthTech ingest client. Does not log URLs, headers or clinical payloads.
 * Redirects are disabled; HTTPS and key usability are enforced by [IngestConfiguration].
 */
class IngestClient(
    private val http: HttpClient,
    private val transport: IngestTransport,
) {
    init {
        require(transport.configurationError == null && !transport.baseUrl.isNullOrBlank()) {
            transport.configurationError ?: IngestApiKey.CONFIGURATION_ERROR
        }
    }

    private val baseUrl: String = transport.baseUrl!!

    suspend fun checkHealth(): IngestHttpCall =
        execute(HttpMethod.Get, "api/health", body = null, idempotencyKey = null)

    suspend fun ingestWearableData(body: String, idempotencyKey: String? = null): IngestHttpCall =
        execute(HttpMethod.Post, "api/v1/wearables/ingest", body, idempotencyKey)

    suspend fun batchIngestWearableData(body: String, idempotencyKey: String? = null): IngestHttpCall =
        execute(HttpMethod.Post, "api/v1/wearables/batch-ingest", body, idempotencyKey)

    private suspend fun execute(
        method: HttpMethod,
        path: String,
        body: String?,
        idempotencyKey: String?,
    ): IngestHttpCall {
        return try {
            val response = http.request(baseUrl + path) {
                this.method = method
                header("X-API-Key", transport.apiKey)
                if (!idempotencyKey.isNullOrBlank()) header("Idempotency-Key", idempotencyKey)
                if (body != null) {
                    setBody(TextContent(body, ContentType.Application.Json))
                }
            }
            val text = response.bodyAsText().ifBlank { null }
            IngestHttpCall(response.status.value, text, IngestPayload.classifyHttp(response.status.value))
        } catch (_: Exception) {
            IngestHttpCall(0, null, IngestHttpKind.NETWORK)
        }
    }

    companion object {
        fun create(configuration: IngestConfiguration, engine: HttpClientEngine = defaultIngestEngine()): IngestClient {
            val transport = configuration.transport()
            check(transport.configurationError == null) { transport.configurationError.orEmpty() }
            return IngestClient(ingestHttpClient(engine), transport)
        }

        fun ingestHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 12_000
                socketTimeoutMillis = 12_000
            }
        }
    }
}

internal expect fun defaultIngestEngine(): HttpClientEngine
