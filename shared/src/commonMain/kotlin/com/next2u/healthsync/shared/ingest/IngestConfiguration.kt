package com.next2u.healthsync.shared.ingest

data class IngestConfigurationStatus(
    val origin: String = "Indisponível",
    val keyConfigured: Boolean = false,
    val usingSettingsOverride: Boolean = false,
    val configurationError: String? = IngestApiKey.CONFIGURATION_ERROR,
)

data class IngestTransport(
    val baseUrl: String? = null,
    val apiKey: String = "",
    val configurationError: String? = null,
) {
    init {
        require((baseUrl != null) == (configurationError == null))
    }
}

/** Never a data class: credentials must not appear in generated toString/copy diagnostics. */
class IngestConfiguration private constructor(
    private val baseUrl: String?,
    private val key: String,
    val status: IngestConfigurationStatus,
) {
    override fun toString(): String = "IngestConfiguration(status=$status)"

    fun transport(): IngestTransport =
        if (status.configurationError != null) IngestTransport(configurationError = status.configurationError)
        else IngestTransport(baseUrl = baseUrl, apiKey = key)

    companion object {
        const val INVALID_URL =
            "O endereço do serviço está inválido. Use HTTPS sem credenciais, consulta ou fragmento."
        const val DEFAULT_BASE_URL = "https://healthtech-secure-api-5794833455.us-central1.run.app/"

        fun resolve(rawUrl: String, overrideKey: String?, builtInKey: String): IngestConfiguration {
            val parsed = parseHttpsBase(rawUrl)
            val candidate = (overrideKey ?: builtInKey).trim()
            val key = candidate.takeIf(IngestApiKey::isUsable).orEmpty()
            return IngestConfiguration(
                parsed?.normalizedBase,
                key,
                IngestConfigurationStatus(
                    origin = parsed?.origin ?: "Indisponível",
                    keyConfigured = key.isNotEmpty(),
                    usingSettingsOverride = overrideKey != null,
                    configurationError = when {
                        parsed == null -> INVALID_URL
                        key.isEmpty() -> IngestApiKey.CONFIGURATION_ERROR
                        else -> null
                    },
                ),
            )
        }
    }
}

internal data class ParsedHttpsUrl(val origin: String, val normalizedBase: String)

/**
 * Mirrors the Android OkHttp checks: HTTPS, no userinfo, no query, no fragment.
 * Path is forced to end with `/` so Retrofit/Ktor join `api/...` correctly.
 */
internal fun parseHttpsBase(raw: String): ParsedHttpsUrl? {
    val value = raw.trim()
    if (!value.startsWith("https://", ignoreCase = true)) return null
    if (value.contains('?') || value.contains('#')) return null
    val rest = value.substring(8)
    if (rest.contains('@')) return null
    val slash = rest.indexOf('/')
    val authority = if (slash < 0) rest else rest.substring(0, slash)
    if (authority.isEmpty() || authority.startsWith('[') || ':' == authority.lastOrNull()) return null
    val host = if (authority.startsWith('[')) return null
    else authority.substringBefore(':').lowercase()
    if (host.isEmpty() || host.startsWith('.') || host.endsWith('.') || ' ' in host) return null
    val portPart = if (authority.contains(':')) authority.substringAfter(':') else ""
    if (portPart.isNotEmpty() && portPart.toIntOrNull()?.let { it in 1..65535 } != true) return null
    val path = if (slash < 0) "/" else rest.substring(slash).ifEmpty { "/" }
    val normalizedPath = if (path.endsWith('/')) path else "$path/"
    val origin = "https://" + authority
    return ParsedHttpsUrl(origin = origin, normalizedBase = origin + normalizedPath)
}
