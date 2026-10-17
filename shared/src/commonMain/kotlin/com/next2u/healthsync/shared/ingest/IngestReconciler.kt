package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.model.IngestResponse
import com.next2u.healthsync.shared.model.QueuedReading
import com.next2u.healthsync.shared.platform.sha256Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class IngestItemOutcome { ACCEPTED, DUPLICATE, REJECTED, AUTH, CLIENT_ERROR, TRANSIENT }

data class IngestItemDecision(val outcome: IngestItemOutcome, val message: String? = null) {
    val confirmed get() = outcome == IngestItemOutcome.ACCEPTED || outcome == IngestItemOutcome.DUPLICATE
}

data class PreparedReading(val item: QueuedReading, val patientId: String, val json: String)

/**
 * Selective reconciliation of HBand PR5 against Core PR14.
 * Never infer receipt from HTTP alone, counters, array position or another item's ID.
 */
object IngestReconciler {
    const val BATCH_MAX_ITEMS = 50
    const val UNCONFIRMED = "Resposta sem confirmação válida deste registro. Leitura mantida na fila."
    const val INVALID_LOCAL = "Identidade ou instante da leitura não confirmado. Registro preservado para revisão."

    private val isoInstant =
        Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$")
    private val compact = Json

    private fun string(obj: JsonObject, name: String): String? {
        val value = (obj[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it == it.trim() }
        return value
    }

    fun validMeasurementTime(value: String): Boolean {
        if (!isoInstant.matches(value)) return false
        val seconds = value.replace(Regex("\\.\\d+(?=Z|[+-])"), "")
        val date = seconds.substring(0, 10).split('-')
        val time = seconds.substring(11, 19).split(':')
        if (date.size != 3 || time.size != 3) return false
        val year = date[0].toIntOrNull() ?: return false
        val month = date[1].toIntOrNull() ?: return false
        val day = date[2].toIntOrNull() ?: return false
        val hour = time[0].toIntOrNull() ?: return false
        val minute = time[1].toIntOrNull() ?: return false
        val second = time[2].toIntOrNull() ?: return false
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return false
        val maxDay = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        }
        return day <= maxDay
    }

    fun prepare(item: QueuedReading): PreparedReading {
        val original = Json.parseToJsonElement(item.payloadJson).jsonObject
        val patient = requireNotNull(string(original, "patient_id")) { INVALID_LOCAL }
        require(string(original, "timestamp")?.let(::validMeasurementTime) == true) { INVALID_LOCAL }
        val device = requireNotNull(
            if (original.containsKey("device_id")) string(original, "device_id")
            else string(original, "deviceId"),
        ) { INVALID_LOCAL }
        if (original.containsKey("deviceId")) require(string(original, "deviceId") == device) { INVALID_LOCAL }
        require(IngestReadingIdentity.isValid(item.clientReadingId)) { INVALID_LOCAL }
        if (original.containsKey("client_reading_id")) {
            require(string(original, "client_reading_id") == item.clientReadingId) { INVALID_LOCAL }
        }
        val normalized = Json.parseToJsonElement(
            IngestPayload.withDeviceLabel(IngestPayload.normalizeQueuePayload(item.payloadJson), IngestRuntime.deviceLabel),
        ).jsonObject
        val withIdentity = JsonObject(
            normalized + mapOf(
                "patient_id" to JsonPrimitive(patient),
                "device_id" to JsonPrimitive(device),
                "timestamp" to JsonPrimitive(original.getValue("timestamp").jsonObjectOrString()),
                "client_reading_id" to JsonPrimitive(item.clientReadingId),
            ),
        )
        return PreparedReading(item, patient, compact.encodeToString(JsonObject.serializer(), withIdentity))
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
        val body = buildJsonObject {
            put("patient_id", patient)
            put(
                "readings",
                buildJsonArray {
                    readings.forEach { add(Json.parseToJsonElement(it.json)) }
                },
            )
        }
        return compact.encodeToString(JsonObject.serializer(), body)
    }

    fun flushIdempotencyKey(readings: List<PreparedReading>): String {
        val material = buildJsonArray {
            add(JsonPrimitive(readings.first().patientId))
            readings.forEach { add(JsonPrimitive(it.item.clientReadingId)) }
        }
        return sha256Hex(compact.encodeToString(JsonArray.serializer(), material))
    }

    fun httpFailure(code: Int): IngestItemDecision = when (code) {
        401, 403 -> IngestItemDecision(IngestItemOutcome.AUTH, IngestPayload.authErrorMessage(code, null))
        in 400..499 -> IngestItemDecision(IngestItemOutcome.CLIENT_ERROR, "HTTP $code. Registro preservado para revisão.")
        else -> IngestItemDecision(IngestItemOutcome.TRANSIENT, "Envio não confirmado (HTTP $code). Leitura mantida na fila.")
    }

    fun unconfirmed() = IngestItemDecision(IngestItemOutcome.TRANSIENT, UNCONFIRMED)

    fun single(reading: PreparedReading, code: Int, body: IngestResponse?): IngestItemDecision {
        if (code != 200) return httpFailure(code)
        if (body == null || !body.success || body.clientReadingId != reading.item.clientReadingId ||
            body.patientId != reading.patientId || body.readingId.isNullOrBlank()
        ) return unconfirmed()
        if (body.duplicate != null && body.duplicate != (body.ingestStatus == "duplicate")) return unconfirmed()
        return when (body.ingestStatus) {
            "accepted" -> IngestItemDecision(IngestItemOutcome.ACCEPTED)
            "duplicate" -> IngestItemDecision(IngestItemOutcome.DUPLICATE)
            else -> unconfirmed()
        }
    }

    fun batch(readings: List<PreparedReading>, code: Int, body: String?): List<IngestItemDecision> {
        if (code != 200) return readings.map { httpFailure(code) }
        val unknown = readings.map { unconfirmed() }
        val root = runCatching { Json.parseToJsonElement(body ?: "").jsonObject }.getOrNull() ?: return unknown
        if (string(root, "patient_id") != readings.first().patientId) return unknown
        val results = root["results"] as? JsonArray ?: return unknown
        val decisions = unknown.toMutableList()
        val seen = mutableSetOf<Int>()
        for (position in 0 until results.size) {
            val entry = results[position] as? JsonObject ?: return unknown
            val rawIndex = entry["index"]
            val indexLong = when (rawIndex) {
                is JsonPrimitive -> rawIndex.content.toLongOrNull()
                else -> null
            } ?: return unknown
            if (indexLong < 0 || indexLong >= readings.size || !seen.add(indexLong.toInt())) return unknown
            val expected = readings[indexLong.toInt()]
            if (string(entry, "client_reading_id") != expected.item.clientReadingId) return unknown
            val status = (entry["status"] as? JsonPrimitive)?.content
            decisions[indexLong.toInt()] = when (status) {
                "accepted", "duplicate" -> {
                    val result = entry["result"] as? JsonObject ?: return unknown
                    if (string(result, "patient_id") != expected.patientId ||
                        (result["ingest_status"] as? JsonPrimitive)?.content != status ||
                        string(result, "reading_id") == null
                    ) return unknown
                    if (result.containsKey("duplicate") && result["duplicate"] !is JsonNull) {
                        val dup = (result["duplicate"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                        if (dup != (status == "duplicate")) return unknown
                    }
                    IngestItemDecision(
                        if (status == "duplicate") IngestItemOutcome.DUPLICATE else IngestItemOutcome.ACCEPTED,
                    )
                }
                "rejected" -> IngestItemDecision(
                    IngestItemOutcome.REJECTED,
                    "Registro rejeitado pelo servidor. Dados preservados para revisão.",
                )
                else -> unconfirmed()
            }
        }
        return decisions
    }
}

private fun JsonElement.jsonObjectOrString(): String =
    (this as? JsonPrimitive)?.content ?: error(IngestReconciler.INVALID_LOCAL)
