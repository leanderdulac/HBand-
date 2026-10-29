package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.model.HBandTelemetry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class IngestHttpKind {
    SUCCESS,
    AUTH,
    CLIENT,
    SERVER,
    NETWORK,
}

/**
 * Builds and normalizes HealthTech wearable ingest payloads.
 *
 * Same contract as `com.example.data.ingest.IngestPayloadMapper`. This copy is
 * the KMP source of truth going forward; Android still uses its own class until
 * a later migration.
 */
object IngestPayload {
    const val DEFAULT_PATIENT_ID = "PAT-HBAND-001"
    const val SERVICE_NAME = "healthtech-secure-api"
    const val MIN_HEART_RATE = 20
    const val MAX_HEART_RATE = 250
    const val PLACEHOLDER_DEVICE_ID = "HBAND-B57-89A4"
    const val PLACEHOLDER_MAC = "E4:A8:B6:12:89:A4"

    const val MISSING_HR_ERROR =
        "FC ausente ou fora do intervalo aceito pela API ($MIN_HEART_RATE a $MAX_HEART_RATE). Registro preservado para revisão, sem envio ou alteração do valor."
    const val INVALID_SOURCE_ERROR =
        "Origem da leitura incompatível com a API. Registro preservado para revisão, sem envio ou alteração da origem."
    const val INVALID_FILTER_ERROR =
        "Filtro da leitura incompatível com a API. Registro preservado para revisão, sem envio ou alteração do filtro."
    const val INVALID_SPO2_ERROR =
        "SpO₂ fora do intervalo aceito pela API (50 a 100) ou inválida. Registro preservado para revisão, sem envio ou alteração do valor."

    private val ingestSources = setOf("companion_manual", "ble_sim", "ble_hband", "http")
    private val filterTypes = setOf("BMO", "Wavelet", "Butterworth", "Raw", "Adaptive")
    private val coreFloatDecimal = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun resolvePatientId(raw: String?): String = raw?.trim().orEmpty().ifEmpty { DEFAULT_PATIENT_ID }

    fun resolveDeviceId(deviceId: String?, fallbackMac: String? = null): String {
        val primary = deviceId?.trim().orEmpty()
        if (primary.isNotEmpty() && !isPlaceholderDeviceId(primary)) return primary
        val mac = fallbackMac?.trim().orEmpty()
        if (mac.isNotEmpty() && !isPlaceholderDeviceId(mac)) return mac
        return primary.ifEmpty { mac }
    }

    fun isPlaceholderDeviceId(id: String): Boolean {
        val value = id.trim()
        return value.equals(PLACEHOLDER_DEVICE_ID, ignoreCase = true) ||
            value.equals(PLACEHOLDER_MAC, ignoreCase = true)
    }

    fun isIngestibleHeartRate(heartRate: Int): Boolean = heartRate >= MIN_HEART_RATE

    fun isIngestible(telemetry: HBandTelemetry): Boolean = isIngestibleHeartRate(telemetry.heartRate)

    fun coreFloatNumber(value: JsonElement?): Double? {
        if (value == null || value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive.isString) return parseCoreDecimal(primitive.content)
        return primitive.content.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun coreFloatNumber(raw: Any?): Double? = when (raw) {
        null -> null
        is Number -> raw.toDouble()
        is String -> parseCoreDecimal(raw)
        else -> null
    }

    private fun parseCoreDecimal(value: String): Double? {
        val decimal = if ('_' in value) {
            if (value.startsWith('_') || value.endsWith('_') || "__" in value) return null
            value.replace("_", "")
        } else value.trim { it !in '\u001c'..'\u001f' && (it.isWhitespace() || it == '\u0085') }
        return decimal.takeIf { coreFloatDecimal.matches(it) }?.toDoubleOrNull()
    }

    fun isIngestibleJson(json: String): Boolean = try {
        val heartRate = coreFloatNumber(Json.parseToJsonElement(json).jsonObject["heart_rate"])
        heartRate != null && heartRate.isFinite() &&
            heartRate in MIN_HEART_RATE.toDouble()..MAX_HEART_RATE.toDouble()
    } catch (_: Exception) {
        false
    }

    fun isCompatibleSpo2Json(json: String): Boolean = try {
        val payload = Json.parseToJsonElement(json).jsonObject
        if (!payload.containsKey("spo2") || payload["spo2"] is JsonNull) true
        else {
            val value = coreFloatNumber(payload["spo2"])
            value != null && value.isFinite() && value in 50.0..100.0
        }
    } catch (_: Exception) {
        false
    }

    fun isCompatibleFilterTypeJson(json: String): Boolean = try {
        val payload = Json.parseToJsonElement(json).jsonObject
        val filter = payload["filter_type"]
        !payload.containsKey("filter_type") || filter is JsonNull ||
            (filter is JsonPrimitive && filter.isString &&
                (filter.content.isEmpty() || filter.content in filterTypes))
    } catch (_: Exception) {
        false
    }

    fun isCompatibleIngestSourceJson(json: String): Boolean = try {
        val payload = Json.parseToJsonElement(json).jsonObject
        val source = payload["ingest_source"]
        !payload.containsKey("ingest_source") || source is JsonNull ||
            (source is JsonPrimitive && source.isString &&
                (source.content.isEmpty() || source.content in ingestSources))
    } catch (_: Exception) {
        false
    }

    /** Acrescenta `device_label` se ainda não houver (leituras antigas da fila também ganham). */
    fun withDeviceLabel(json: String, label: String?): String {
        if (label.isNullOrBlank()) return json
        val obj = Json.parseToJsonElement(json).jsonObject
        if (obj.containsKey("device_label")) return json
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(obj + ("device_label" to JsonPrimitive(label))))
    }

    /** Stamps a durable `client_reading_id` at insert so reconnect/history replay keeps the same key. */
    fun withClientReadingId(json: String, clientReadingId: String): String {
        if (clientReadingId.isBlank()) return json
        val obj = Json.parseToJsonElement(json).jsonObject
        if (obj.containsKey("client_reading_id")) return json
        return pretty.encodeToString(
            JsonObject.serializer(),
            JsonObject(obj + ("client_reading_id" to JsonPrimitive(clientReadingId))),
        )
    }

    fun telemetryToJson(telemetry: HBandTelemetry, patientId: String): String {
        val obj = buildJsonObject {
            put("patient_id", resolvePatientId(patientId))
            put("device_id", resolveDeviceId(telemetry.deviceId))
            put("device_model", telemetry.deviceModel)
            put("timestamp", telemetry.timestamp)
            put("heart_rate", telemetry.heartRate)
            val sys = telemetry.bloodPressure.systolic
            val dia = telemetry.bloodPressure.diastolic
            if (sys > 0 && dia > 0) {
                put(
                    "blood_pressure",
                    buildJsonObject {
                        put("systolic", sys)
                        put("diastolic", dia)
                    },
                )
            }
            if (telemetry.spO2 > 0) put("spo2", telemetry.spO2)
            if (telemetry.temperatureCelsius > 0f) put("temperature", telemetry.temperatureCelsius.toDouble())
            put("steps", telemetry.steps)
            put("calories", telemetry.calories.toDouble())
            if (telemetry.distanceMeters > 0f) put("distance", telemetry.distanceMeters.toDouble())
            if (telemetry.hrvScore > 0) put("hrv_score", telemetry.hrvScore)
            put("is_real_sensor_data", telemetry.isRealSensorData)
            put("service", SERVICE_NAME)
        }
        return pretty.encodeToString(JsonObject.serializer(), obj)
    }

    fun normalizeQueuePayload(raw: String): String {
        val jsonObj = Json.parseToJsonElement(raw).jsonObject
        val alreadyFlat = jsonObj.containsKey("patient_id") && !jsonObj.containsKey("metrics")
        if (alreadyFlat) {
            if (jsonObj.containsKey("service")) return pretty.encodeToString(JsonObject.serializer(), jsonObj)
            return pretty.encodeToString(
                JsonObject.serializer(),
                JsonObject(jsonObj + ("service" to JsonPrimitive(SERVICE_NAME))),
            )
        }

        val metrics = jsonObj["metrics"] as? JsonObject
        val hr = firstPresent(metrics, "heartRate") ?: firstPresent(jsonObj, "heart_rate")
            ?: firstPresent(jsonObj, "heartRate")
        val bpMetrics = metrics?.get("bloodPressure") as? JsonObject
        val bpFlat = jsonObj["blood_pressure"] as? JsonObject
        val sys = intOrNull(bpMetrics?.get("systolic")) ?: intOrNull(bpFlat?.get("systolic"))
        val dia = intOrNull(bpMetrics?.get("diastolic")) ?: intOrNull(bpFlat?.get("diastolic"))
        val spo2 = metrics?.get("spO2")?.takeUnless { it is JsonNull }
            ?: jsonObj["spo2"]?.takeUnless { it is JsonNull }
        val temp = doubleOrNull(metrics?.get("temperatureCelsius")) ?: doubleOrNull(jsonObj["temperature"])
        val steps = intOrNull(metrics?.get("steps")) ?: intOrNull(jsonObj["steps"])
        val calories = doubleOrNull(metrics?.get("calories")) ?: doubleOrNull(jsonObj["calories"])
        val hrv = intOrNull(metrics?.get("hrvScore")) ?: intOrNull(jsonObj["hrv_score"])
        val isReal = when {
            jsonObj.containsKey("is_real_sensor_data") -> boolOrNull(jsonObj["is_real_sensor_data"])
            jsonObj.containsKey("isRealSensorData") -> boolOrNull(jsonObj["isRealSensorData"])
            else -> null
        }
        val deviceId = stringOrNull(jsonObj["device_id"]) ?: stringOrNull(jsonObj["deviceId"])
        val timestamp = stringOrNull(jsonObj["timestamp"]).orEmpty()

        val normalized = buildJsonObject {
            put("patient_id", resolvePatientId(stringOrNull(jsonObj["patient_id"])))
            if (!deviceId.isNullOrBlank()) put("device_id", resolveDeviceId(deviceId))
            if (timestamp.isNotBlank()) put("timestamp", timestamp)
            if (hr != null) put("heart_rate", hr)
            if (sys != null && dia != null && sys > 0 && dia > 0) {
                put(
                    "blood_pressure",
                    buildJsonObject {
                        put("systolic", sys)
                        put("diastolic", dia)
                    },
                )
            }
            if (spo2 != null && coreFloatNumber(spo2)?.let { it <= 0 } != true) put("spo2", spo2)
            if (temp != null && temp > 0.0) put("temperature", temp)
            if (steps != null) put("steps", steps)
            if (calories != null) put("calories", calories)
            if (hrv != null && hrv > 0) put("hrv_score", hrv)
            if (isReal != null) put("is_real_sensor_data", isReal)
            if (jsonObj.containsKey("ingest_source")) put("ingest_source", jsonObj.getValue("ingest_source"))
            if (jsonObj.containsKey("filter_type")) put("filter_type", jsonObj.getValue("filter_type"))
            if (jsonObj.containsKey("device_label")) put("device_label", jsonObj.getValue("device_label"))
            put("service", SERVICE_NAME)
        }
        return pretty.encodeToString(JsonObject.serializer(), normalized)
    }

    fun classifyHttp(code: Int): IngestHttpKind = when (code) {
        in 200..299 -> IngestHttpKind.SUCCESS
        401, 403 -> IngestHttpKind.AUTH
        in 400..499 -> IngestHttpKind.CLIENT
        else -> IngestHttpKind.SERVER
    }

    fun authErrorMessage(httpCode: Int, @Suppress("UNUSED_PARAMETER") errorBody: String?): String =
        "Falha de autenticação na API HealthTech (HTTP $httpCode). Verifique a chave em Ajustes."

    private fun firstPresent(obj: JsonObject?, key: String): JsonElement? {
        if (obj == null || !obj.containsKey(key) || obj[key] is JsonNull) return null
        val value = obj.getValue(key)
        return coreFloatNumber(value)?.let { JsonPrimitive(it) } ?: value
    }

    private fun intOrNull(value: JsonElement?): Int? {
        if (value == null || value is JsonNull) return null
        return (value as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    }

    private fun doubleOrNull(value: JsonElement?): Double? {
        if (value == null || value is JsonNull) return null
        return (value as? JsonPrimitive)?.content?.toDoubleOrNull()
    }

    private fun boolOrNull(value: JsonElement?): Boolean? {
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.content.toBooleanStrictOrNull()
    }

    private fun stringOrNull(value: JsonElement?): String? =
        (value as? JsonPrimitive)?.contentOrNull
}

class IngestDeduper(
    private val minIntervalMs: Long = 30_000L,
    private val elapsedMs: () -> Long = { defaultElapsedMs() },
) {
    private var lastSignature: String? = null
    private var lastAt: Long = 0L

    suspend fun saveIfNeeded(telemetry: HBandTelemetry, save: suspend (HBandTelemetry) -> Unit) {
        val previousSignature = lastSignature
        val previousAt = lastAt
        if (!shouldEnqueue(telemetry)) return
        try {
            save(telemetry)
        } catch (error: Throwable) {
            lastSignature = previousSignature
            lastAt = previousAt
            throw error
        }
    }

    fun shouldEnqueue(telemetry: HBandTelemetry, nowMs: Long = elapsedMs()): Boolean {
        if (!telemetry.isRealSensorData) return false
        if (!IngestPayload.isIngestible(telemetry)) return false
        val signature = listOf(
            telemetry.deviceId,
            telemetry.heartRate,
            telemetry.spO2,
            telemetry.bloodPressure.systolic,
            telemetry.bloodPressure.diastolic,
            telemetry.steps,
            telemetry.temperatureCelsius,
            telemetry.hrvScore,
            telemetry.calories,
            telemetry.distanceMeters,
            telemetry.sleepSummary.deepSleepMinutes,
            telemetry.sleepSummary.lightSleepMinutes,
            telemetry.sleepSummary.awakeMinutes,
        ).joinToString("|")
        val elapsed = nowMs - lastAt
        if (signature == lastSignature && elapsed >= 0L && elapsed < minIntervalMs) return false
        lastSignature = signature
        lastAt = nowMs
        return true
    }
}

internal expect fun defaultElapsedMs(): Long
