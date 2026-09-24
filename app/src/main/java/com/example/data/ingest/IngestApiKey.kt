package com.example.data.ingest

/**
 * Detects missing / placeholder HealthTech ingest keys.
 *
 * Never log the key value — only yes/no and a user-facing configuration error.
 */
object IngestApiKey {
    const val PLACEHOLDER = "YOUR_HEALTHTECH_API_KEY_HERE"

    fun isPlaceholder(key: String?): Boolean {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        if (trimmed.equals(PLACEHOLDER, ignoreCase = true)) return true
        // Patient id was historically stuffed into the key field by mistake.
        if (trimmed.equals(IngestPayloadMapper.DEFAULT_PATIENT_ID, ignoreCase = true)) return true
        return false
    }

    fun isUsable(key: String?): Boolean = !isPlaceholder(key)

    fun configurationError(): String = IngestReconciler.AUTH_INVALID_MESSAGE
}
