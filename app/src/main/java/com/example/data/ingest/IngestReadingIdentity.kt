package com.example.data.ingest

import org.json.JSONObject
import java.util.UUID

/** Core PR14 identity syntax; generated only at insert/migration, never during flush. */
object IngestReadingIdentity {
    fun isValid(value: String): Boolean = Regex("^[A-Za-z0-9._:-]{1,128}$").matches(value)

    fun forPayload(payload: String): String {
        val existing = runCatching { JSONObject(payload).opt("client_reading_id") }.getOrNull()
        return (existing as? String)?.takeIf(::isValid) ?: UUID.randomUUID().toString()
    }
}
