package com.next2u.healthsync.shared.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Decides whether a snapshot file that failed to decrypt is legacy plaintext
 * JSON worth re-encrypting. Ciphertext that is not JSON is left untouched.
 */
object SnapshotAtRest {
    private val json = Json { ignoreUnknownKeys = true }

    fun shouldMigratePlaintext(utf8: String?): Boolean {
        val text = utf8?.trim() ?: return false
        if (text.length > 32_000_000) return false
        if (!text.startsWith("{") || !text.endsWith("}")) return false
        if (!text.contains("\"schemaVersion\"")) return false
        return try {
            val element = json.parseToJsonElement(text)
            element is JsonObject && element.jsonObject.containsKey("schemaVersion")
        } catch (_: Exception) {
            false
        }
    }
}
