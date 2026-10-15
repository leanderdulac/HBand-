package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.platform.randomUuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Core PR14 identity syntax; generated only at insert/migration, never during flush. */
object IngestReadingIdentity {
    private val syntax = Regex("^[A-Za-z0-9._:-]{1,128}$")

    fun isValid(value: String): Boolean = syntax.matches(value)

    fun forPayload(payload: String, randomId: () -> String = { randomUuid() }): String {
        val existing = existingId(payload)
        return existing?.takeIf(::isValid) ?: randomId()
    }

    private fun existingId(payload: String): String? {
        val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        val value = root["client_reading_id"] as? JsonPrimitive ?: return null
        return value.contentOrNull
    }
}
