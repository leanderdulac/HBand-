package com.example.data.ingest

data class IngestDiagnostics(
    val baseUrl: String = "",
    val keyConfigured: Boolean = false,
    val usingSettingsOverride: Boolean = false,
    val lastHttpStatus: Int? = null,
    val queuedCount: Int = 0,
    val lastError: String? = null,
    val configurationError: String? = null,
)
