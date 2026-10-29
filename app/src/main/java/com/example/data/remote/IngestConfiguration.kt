package com.example.data.remote

import com.example.data.ingest.IngestApiKey
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class IngestConfigurationStatus(
    val origin: String = "Indisponível",
    val keyConfigured: Boolean = false,
    val usingSettingsOverride: Boolean = false,
    val configurationError: String? = IngestApiKey.CONFIGURATION_ERROR,
)

data class IngestTransport(val service: HealthTechApiService? = null, val configurationError: String? = null) {
    init { require((service != null) == (configurationError == null)) }
}

/** Never a data class: credentials must not appear in generated toString/copy diagnostics. */
internal class IngestConfiguration private constructor(
    private val baseUrl: String?, private val key: String, val status: IngestConfigurationStatus,
) {
    fun transport(factory: (String, String) -> HealthTechApiService): IngestTransport =
        if (status.configurationError != null) IngestTransport(configurationError = status.configurationError)
        else IngestTransport(service = factory(baseUrl!!, key))

    fun healthService(factory: (String, String) -> HealthTechApiService): HealthTechApiService {
        check(baseUrl != null) { INVALID_URL }
        return factory(baseUrl, key)
    }

    companion object {
        const val INVALID_URL = "O endereço do serviço está inválido. Use HTTPS sem credenciais, consulta ou fragmento."
        fun resolve(rawUrl: String, overrideKey: String?, builtInKey: String): IngestConfiguration {
            val parsed = rawUrl.trim().toHttpUrlOrNull()?.takeIf {
                it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
            }
            val base = parsed?.let { if (it.encodedPath.endsWith('/')) it.toString() else it.newBuilder().addPathSegment("").build().toString() }
            // An explicitly cleared override must not silently reactivate a build key.
            val candidate = (overrideKey ?: builtInKey).trim()
            val key = candidate.takeIf(IngestApiKey::isUsable).orEmpty()
            val origin = parsed?.newBuilder()?.encodedPath("/")?.build()?.toString()?.removeSuffix("/") ?: "Indisponível"
            return IngestConfiguration(base, key, IngestConfigurationStatus(
                origin = origin, keyConfigured = key.isNotEmpty(), usingSettingsOverride = overrideKey != null,
                configurationError = if (base == null) INVALID_URL else if (key.isEmpty()) IngestApiKey.CONFIGURATION_ERROR else null,
            ))
        }
    }
}
