package com.example.data.ingest

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class IngestItemOutcome {
    ACCEPTED,
    DUPLICATE,
    REJECTED,
    AUTH_INVALID,
    AUTH_FORBIDDEN,
    CLIENT_ERROR,
    TRANSIENT,
    CONFIG_ERROR,
}

data class IngestItemDecision(
    val clientReadingId: String,
    val outcome: IngestItemOutcome,
    val markSynced: Boolean,
    val keepQueued: Boolean,
    val errorMessage: String? = null,
    val httpStatus: Int? = null,
)

data class AuthBackoffState(
    val lastAuthHttp: Int? = null,
    val lastAuthAtMs: Long = 0L,
    val keyFingerprint: String? = null,
)

/**
 * Pure sync reconciliation for HealthTech wearable ingest.
 *
 * Contract: docs/contracts/WEARABLE_INGEST_IDEMPOTENCY.md (Core PR #14)
 * - 200 + accepted|duplicate = synced
 * - rejected stays local with reason
 * - 5xx / network stays queued
 * - 401 / 403 stop the retry storm (caller must not keep POSTing)
 */
object IngestReconciler {
    const val AUTH_INVALID_MESSAGE = "chave de ingestão inválida ou ausente"
    const val AUTH_FORBIDDEN_MESSAGE = "chave sem permissão de escrita"
    const val AUTH_BACKOFF_MS = 15 * 60 * 1000L
    const val BATCH_MAX_ITEMS = 50
    const val CLIENT_READING_ID_MAX = 128

    private val CLIENT_READING_ID_PATTERN = Regex("^[A-Za-z0-9._:-]{1,$CLIENT_READING_ID_MAX}$")

    fun isValidClientReadingId(id: String): Boolean = CLIENT_READING_ID_PATTERN.matches(id)

    fun authMessage(httpCode: Int): String = when (httpCode) {
        403 -> AUTH_FORBIDDEN_MESSAGE
        else -> AUTH_INVALID_MESSAGE
    }

    fun keyFingerprint(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    fun shouldSkipServerCall(
        nowMs: Long,
        keyFingerprint: String?,
        backoff: AuthBackoffState?,
        backoffMs: Long = AUTH_BACKOFF_MS,
    ): Boolean {
        if (backoff == null) return false
        val code = backoff.lastAuthHttp ?: return false
        if (code != 401 && code != 403) return false
        if (!keyFingerprint.isNullOrBlank() &&
            !backoff.keyFingerprint.isNullOrBlank() &&
            keyFingerprint != backoff.keyFingerprint
        ) {
            return false
        }
        return nowMs - backoff.lastAuthAtMs < backoffMs
    }

    fun flushIdempotencyKey(clientReadingIds: List<String>): String {
        val material = clientReadingIds.sorted().joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(64)
    }

    fun utcNowIso(nowMs: Long = System.currentTimeMillis()): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(nowMs))
    }

    /**
     * Guarantees `client_reading_id`, UTC `timestamp` and `device_id` on the
     * JSON that will be POSTed. Existing values are never replaced on retry.
     */
    fun ensureReadingPayload(
        rawJson: String,
        clientReadingId: String,
        fallbackDeviceId: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val json = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val existingId = json.optString("client_reading_id", "").trim()
        if (!isValidClientReadingId(existingId)) {
            json.put("client_reading_id", clientReadingId)
        }
        val timestamp = json.optString("timestamp", "").trim()
        if (timestamp.isEmpty()) {
            json.put("timestamp", utcNowIso(nowMs))
        }
        val deviceId = IngestPayloadMapper.resolveDeviceId(
            json.optString("device_id", "").ifBlank { json.optString("deviceId", "") },
            fallbackDeviceId,
        )
        if (deviceId.isNotBlank()) {
            json.put("device_id", deviceId)
        }
        if (!json.has("service")) {
            json.put("service", IngestPayloadMapper.SERVICE_NAME)
        }
        return json.toString(2)
    }

    fun buildBatchBody(patientId: String, readingJsons: List<String>): String {
        val envelope = JSONObject()
        envelope.put("patient_id", IngestPayloadMapper.resolvePatientId(patientId))
        val readings = JSONArray()
        readingJsons.forEach { raw ->
            readings.put(JSONObject(raw))
        }
        envelope.put("readings", readings)
        return envelope.toString(2)
    }

    fun patientIdFrom(readingJson: String): String {
        return try {
            IngestPayloadMapper.resolvePatientId(JSONObject(readingJson).optString("patient_id", ""))
        } catch (_: Exception) {
            IngestPayloadMapper.DEFAULT_PATIENT_ID
        }
    }

    fun decisionsForHttp(
        clientReadingIds: List<String>,
        httpCode: Int,
        responseBody: String?,
        networkError: Boolean = false,
    ): List<IngestItemDecision> {
        if (networkError) {
            return clientReadingIds.map { id ->
                IngestItemDecision(
                    clientReadingId = id,
                    outcome = IngestItemOutcome.TRANSIENT,
                    markSynced = false,
                    keepQueued = true,
                    errorMessage = "Falha de rede. Leitura mantida na fila.",
                    httpStatus = null,
                )
            }
        }

        return when (IngestPayloadMapper.classifyHttp(httpCode)) {
            IngestHttpKind.AUTH -> {
                val outcome = if (httpCode == 403) {
                    IngestItemOutcome.AUTH_FORBIDDEN
                } else {
                    IngestItemOutcome.AUTH_INVALID
                }
                val message = authMessage(httpCode)
                clientReadingIds.map { id ->
                    IngestItemDecision(
                        clientReadingId = id,
                        outcome = outcome,
                        markSynced = false,
                        keepQueued = true,
                        errorMessage = message,
                        httpStatus = httpCode,
                    )
                }
            }
            IngestHttpKind.SERVER -> clientReadingIds.map { id ->
                IngestItemDecision(
                    clientReadingId = id,
                    outcome = IngestItemOutcome.TRANSIENT,
                    markSynced = false,
                    keepQueued = true,
                    errorMessage = "Servidor indisponível (HTTP $httpCode). Leitura mantida na fila.",
                    httpStatus = httpCode,
                )
            }
            IngestHttpKind.CLIENT -> clientReadingIds.map { id ->
                IngestItemDecision(
                    clientReadingId = id,
                    outcome = IngestItemOutcome.CLIENT_ERROR,
                    markSynced = false,
                    keepQueued = false,
                    errorMessage = "HTTP $httpCode: ${safeErrorDetail(responseBody)}",
                    httpStatus = httpCode,
                )
            }
            IngestHttpKind.SUCCESS -> reconcileSuccess(clientReadingIds, httpCode, responseBody)
            IngestHttpKind.NETWORK -> clientReadingIds.map { id ->
                IngestItemDecision(
                    clientReadingId = id,
                    outcome = IngestItemOutcome.TRANSIENT,
                    markSynced = false,
                    keepQueued = true,
                    errorMessage = "Falha de rede. Leitura mantida na fila.",
                    httpStatus = httpCode,
                )
            }
        }
    }

    fun decisionsForConfigError(clientReadingIds: List<String>): List<IngestItemDecision> {
        return clientReadingIds.map { id ->
            IngestItemDecision(
                clientReadingId = id,
                outcome = IngestItemOutcome.CONFIG_ERROR,
                markSynced = false,
                keepQueued = true,
                errorMessage = AUTH_INVALID_MESSAGE,
                httpStatus = null,
            )
        }
    }

    private fun reconcileSuccess(
        clientReadingIds: List<String>,
        httpCode: Int,
        responseBody: String?,
    ): List<IngestItemDecision> {
        val root = parseObject(responseBody)
        val results = root?.optJSONArray("results")
        if (results != null && results.length() > 0) {
            return reconcileResultsArray(clientReadingIds, httpCode, results)
        }

        val ingestStatus = root?.optString("ingest_status", "")?.trim()?.lowercase()
        if (ingestStatus == "accepted" || ingestStatus == "duplicate") {
            val outcome = if (ingestStatus == "duplicate") {
                IngestItemOutcome.DUPLICATE
            } else {
                IngestItemOutcome.ACCEPTED
            }
            return clientReadingIds.map { id ->
                syncedDecision(id, outcome, httpCode)
            }
        }

        // Legacy 200 without per-item results: processed_count == accepted+duplicate.
        val processed = root?.optInt("processed_count", -1) ?: -1
        if (processed >= clientReadingIds.size && clientReadingIds.isNotEmpty()) {
            return clientReadingIds.map { id -> syncedDecision(id, IngestItemOutcome.ACCEPTED, httpCode) }
        }
        if (root?.optBoolean("duplicate", false) == true) {
            return clientReadingIds.map { id -> syncedDecision(id, IngestItemOutcome.DUPLICATE, httpCode) }
        }

        // HTTP 200 with no usable body — treat as accepted (old Core).
        return clientReadingIds.map { id -> syncedDecision(id, IngestItemOutcome.ACCEPTED, httpCode) }
    }

    private fun reconcileResultsArray(
        clientReadingIds: List<String>,
        httpCode: Int,
        results: JSONArray,
    ): List<IngestItemDecision> {
        val byId = LinkedHashMap<String, IngestItemDecision>()
        val byIndex = ArrayList<IngestItemDecision>(results.length())

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val status = item.optString("status", "").trim().lowercase()
            val echoedId = item.optString("client_reading_id", "").trim()
            val error = item.optString("error", "").trim().ifBlank { null }
            val decision = when (status) {
                "accepted" -> syncedDecision(echoedId.ifBlank { indexId(clientReadingIds, i) }, IngestItemOutcome.ACCEPTED, httpCode)
                "duplicate" -> syncedDecision(echoedId.ifBlank { indexId(clientReadingIds, i) }, IngestItemOutcome.DUPLICATE, httpCode)
                "rejected" -> IngestItemDecision(
                    clientReadingId = echoedId.ifBlank { indexId(clientReadingIds, i) },
                    outcome = IngestItemOutcome.REJECTED,
                    markSynced = false,
                    keepQueued = false,
                    errorMessage = error ?: "rejected",
                    httpStatus = httpCode,
                )
                else -> IngestItemDecision(
                    clientReadingId = echoedId.ifBlank { indexId(clientReadingIds, i) },
                    outcome = IngestItemOutcome.TRANSIENT,
                    markSynced = false,
                    keepQueued = true,
                    errorMessage = "status desconhecido: $status",
                    httpStatus = httpCode,
                )
            }
            byIndex += decision
            if (decision.clientReadingId.isNotBlank()) {
                byId[decision.clientReadingId] = decision
            }
        }

        return clientReadingIds.mapIndexed { index, id ->
            byId[id] ?: byIndex.getOrNull(index)?.copy(clientReadingId = id) ?: IngestItemDecision(
                clientReadingId = id,
                outcome = IngestItemOutcome.TRANSIENT,
                markSynced = false,
                keepQueued = true,
                errorMessage = "Item ausente em results[]",
                httpStatus = httpCode,
            )
        }
    }

    private fun syncedDecision(
        id: String,
        outcome: IngestItemOutcome,
        httpCode: Int,
    ) = IngestItemDecision(
        clientReadingId = id,
        outcome = outcome,
        markSynced = true,
        keepQueued = false,
        errorMessage = null,
        httpStatus = httpCode,
    )

    private fun indexId(ids: List<String>, index: Int): String =
        ids.getOrNull(index).orEmpty()

    private fun parseObject(body: String?): JSONObject? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    /** Never echo a value that could be the API key. */
    private fun safeErrorDetail(body: String?): String {
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) return "erro do cliente"
        if (trimmed.length > 240) return trimmed.take(240)
        if (IngestApiKey.isUsable(trimmed) && trimmed.length >= 24 && !trimmed.contains(' ')) {
            return "erro do cliente"
        }
        return trimmed
    }
}
