package com.example.data.ingest

import com.example.data.local.IngestQueueEntity
import com.example.data.model.IngestResponse
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

enum class IngestItemOutcome { ACCEPTED, DUPLICATE, REJECTED, AUTH, CLIENT_ERROR, TRANSIENT }

data class IngestItemDecision(val outcome: IngestItemOutcome, val message: String? = null) {
    val confirmed get() = outcome == IngestItemOutcome.ACCEPTED || outcome == IngestItemOutcome.DUPLICATE
}

data class PreparedReading(val item: IngestQueueEntity, val patientId: String, val json: String)

/**
 * Selective reconciliation of HBand PR5 against Core PR14 (90c3a1d...).
 * Never infer receipt from HTTP alone, counters, array position or another item's ID.
 * Transport and backend deployment are verified separately from this pure policy.
 */
object IngestReconciler {
    const val BATCH_MAX_ITEMS = 50
    const val UNCONFIRMED = "Resposta sem confirmação válida deste registro. Leitura mantida na fila."
    const val INVALID_LOCAL = "Identidade ou instante da leitura não confirmado. Registro preservado para revisão."

    private fun string(obj: JSONObject, name: String): String? =
        (obj.opt(name) as? String)?.takeIf { it.isNotBlank() && it == it.trim() }

    private fun validMeasurementTime(value: String): Boolean {
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$").matches(value)) return false
        // Validate calendar/offset independently of fractional precision; never rewrite the time.
        val seconds = value.replace(Regex("\\.\\d+(?=Z|[+-])"), "")
        val position = ParsePosition(0)
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }
            .parse(seconds, position)
        return parsed != null && position.index == seconds.length
    }

    fun prepare(item: IngestQueueEntity): PreparedReading {
        val original = JSONObject(item.payloadJson)
        val patient = requireNotNull(string(original, "patient_id")) { INVALID_LOCAL }
        require(string(original, "timestamp")?.let(::validMeasurementTime) == true) { INVALID_LOCAL }
        val device = requireNotNull(if (original.has("device_id")) string(original, "device_id")
            else string(original, "deviceId")) { INVALID_LOCAL }
        if (original.has("deviceId")) require(string(original, "deviceId") == device) { INVALID_LOCAL }
        require(IngestReadingIdentity.isValid(item.clientReadingId)) { INVALID_LOCAL }
        if (original.has("client_reading_id")) {
            require(string(original, "client_reading_id") == item.clientReadingId) { INVALID_LOCAL }
        }
        val normalized = JSONObject(IngestPayloadMapper.normalizeQueuePayload(item.payloadJson))
        // These fields are sourced from durable data, never the clock or an inferred patient.
        normalized.put("patient_id", patient)
        normalized.put("device_id", device)
        normalized.put("timestamp", original.getString("timestamp"))
        normalized.put("client_reading_id", item.clientReadingId)
        return PreparedReading(item, patient, normalized.toString())
    }

    fun chunks(readings: List<PreparedReading>, maxItems: Int = BATCH_MAX_ITEMS): List<List<PreparedReading>> {
        require(maxItems in 1..BATCH_MAX_ITEMS)
        return readings.groupBy { it.patientId }.values.flatMap { it.chunked(maxItems) }
    }

    fun batchBody(readings: List<PreparedReading>): String {
        require(readings.isNotEmpty())
        val patient = readings.first().patientId
        require(readings.all { it.patientId == patient })
        require(readings.map { it.item.clientReadingId }.distinct().size == readings.size)
        return JSONObject().put("patient_id", patient)
            .put("readings", JSONArray().apply { readings.forEach { put(JSONObject(it.json)) } }).toString()
    }

    fun flushIdempotencyKey(readings: List<PreparedReading>): String {
        // Order matters because the server caches results with request-relative indices.
        val material = JSONArray().put(readings.first().patientId)
        readings.forEach { material.put(it.item.clientReadingId) }
        return MessageDigest.getInstance("SHA-256").digest(material.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun httpFailure(code: Int): IngestItemDecision = when (code) {
        401, 403 -> IngestItemDecision(IngestItemOutcome.AUTH, IngestPayloadMapper.authErrorMessage(code, null))
        in 400..499 -> IngestItemDecision(IngestItemOutcome.CLIENT_ERROR, "HTTP $code. Registro preservado para revisão.")
        else -> IngestItemDecision(IngestItemOutcome.TRANSIENT, "Envio não confirmado (HTTP $code). Leitura mantida na fila.")
    }

    fun unconfirmed() = IngestItemDecision(IngestItemOutcome.TRANSIENT, UNCONFIRMED)

    fun single(reading: PreparedReading, code: Int, body: IngestResponse?): IngestItemDecision {
        if (code != 200) return httpFailure(code)
        if (body == null || !body.success || body.client_reading_id != reading.item.clientReadingId ||
            body.patient_id != reading.patientId || body.reading_id.isNullOrBlank()) return unconfirmed()
        if (body.duplicate != null && body.duplicate != (body.ingest_status == "duplicate")) return unconfirmed()
        return when (body.ingest_status) {
            "accepted" -> IngestItemDecision(IngestItemOutcome.ACCEPTED)
            "duplicate" -> IngestItemDecision(IngestItemOutcome.DUPLICATE)
            else -> unconfirmed()
        }
    }

    fun batch(readings: List<PreparedReading>, code: Int, body: String?): List<IngestItemDecision> {
        if (code != 200) return readings.map { httpFailure(code) }
        val unknown = readings.map { unconfirmed() }
        val root = runCatching { JSONObject(body ?: "") }.getOrNull() ?: return unknown
        if (string(root, "patient_id") != readings.first().patientId) return unknown
        val results = root.optJSONArray("results") ?: return unknown
        val decisions = unknown.toMutableList()
        val seen = mutableSetOf<Int>()
        for (position in 0 until results.length()) {
            val entry = results.optJSONObject(position) ?: return unknown
            val rawIndex = entry.opt("index")
            if (rawIndex !is Int && rawIndex !is Long) return unknown
            val index = (rawIndex as Number).toLong()
            if (index < 0 || index >= readings.size || !seen.add(index.toInt())) return unknown
            val expected = readings[index.toInt()]
            if (string(entry, "client_reading_id") != expected.item.clientReadingId) return unknown
            decisions[index.toInt()] = when (entry.opt("status")) {
                "accepted", "duplicate" -> {
                    val result = entry.optJSONObject("result") ?: return unknown
                    if (string(result, "patient_id") != expected.patientId ||
                        result.opt("ingest_status") != entry.opt("status") ||
                        string(result, "reading_id") == null) return unknown
                    IngestItemDecision(if (entry.getString("status") == "duplicate")
                        IngestItemOutcome.DUPLICATE else IngestItemOutcome.ACCEPTED)
                }
                "rejected" -> IngestItemDecision(IngestItemOutcome.REJECTED,
                    "Registro rejeitado pelo servidor. Dados preservados para revisão.")
                else -> unconfirmed()
            }
        }
        return decisions
    }
}
