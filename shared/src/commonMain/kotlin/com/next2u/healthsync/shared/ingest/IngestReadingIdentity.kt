package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.platform.randomUuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Core PR14 identity syntax; generated only at insert/migration, never during flush. */
object IngestReadingIdentity {
    private val syntax = Regex("^[A-Za-z0-9._:-]{1,128}$")
    private val metricChars = Regex("[^A-Za-z0-9_]")
    private val idChars = Regex("[^A-Za-z0-9._:-]")

    fun isValid(value: String): Boolean = syntax.matches(value)

    fun forPayload(payload: String, randomId: () -> String = { randomUuid() }): String {
        val existing = existingId(payload)
        return existing?.takeIf(::isValid) ?: randomId()
    }

    /**
     * Stable id for a watch history sample so reconnect/replay is the same
     * `client_reading_id` the Core already stored (device + metric + timestamp).
     */
    fun historyId(deviceId: String, metricType: String, timestampMillis: Long): String {
        val device = deviceId.trim().ifEmpty { "device" }
        val metric = metricType.trim().ifEmpty { "metric" }
            .replace(metricChars, "")
            .ifEmpty { "metric" }
        val raw = "$device-$metric-$timestampMillis"
        val cleaned = raw.replace(idChars, "").take(128)
        return cleaned.takeIf(::isValid) ?: "hist-$metric-$timestampMillis".replace(idChars, "").take(128)
    }

    private fun existingId(payload: String): String? {
        val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val value = root["client_reading_id"] as? JsonPrimitive ?: return null
        return value.contentOrNull
    }
}
