package com.example.data.ingest

import com.example.data.model.HBandTelemetry
import org.json.JSONObject

enum class IngestHttpKind {
    SUCCESS,
    AUTH,
    CLIENT,
    SERVER,
    NETWORK
}

/**
 * Builds and normalizes HealthTech wearable ingest payloads.
 *
 * Contract (from the HealthTech smoke test and observed 422s):
 * - `patient_id`, `device_id`, `timestamp`, `heart_rate` are required
 * - queued `heart_rate` must be in [MIN_HEART_RATE]..[MAX_HEART_RATE] or the
 *   confirmed Core schema rejects the entire batch with HTTP 422
 * - optional vitals (BP, SpO2, temperature, HRV) are sent only when a real
 *   reading exists — never filled with demo defaults
 */
object IngestPayloadMapper {
    const val DEFAULT_PATIENT_ID = "PAT-HBAND-001"
    const val SERVICE_NAME = "healthtech-secure-api"
    const val MIN_HEART_RATE = 20
    const val MAX_HEART_RATE = 250

    /** Placeholder MAC from the old [com.example.data.model.HBandDevice] defaults. */
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

    // Core schemas.py at 75e5e02c839f381069212bb7c7d3a2befa491b83; not a full ingest validator.
    private val ingestSources = setOf("companion_manual", "ble_sim", "ble_hband", "http")
    private val filterTypes = setOf("BMO", "Wavelet", "Butterworth", "Raw", "Adaptive")
    private val spo2Decimal = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")

    private fun spo2Number(value: Any?): Double? {
        if (value is Number) return value.toDouble()
        if (value !is String) return null
        // Core's float parser accepts decimal strings, not Java suffixes or hexadecimal floats.
        // Its underscore fallback does not trim whitespace and rejects edge/repeated underscores.
        val decimal = if ('_' in value) {
            if (value.startsWith('_') || value.endsWith('_') || "__" in value) return null
            value.replace("_", "")
        } else value.trim { it !in '\u001c'..'\u001f' && (it.isWhitespace() || it == '\u0085') }
        return decimal.takeIf { spo2Decimal.matches(it) }?.toDoubleOrNull()
    }

    fun isCompatibleSpo2Json(json: String): Boolean = try {
        val payload = JSONObject(json)
        // Optional in Core: retain absence/null; inspect the transport value without rewriting it.
        if (!payload.has("spo2") || payload.isNull("spo2")) true else {
            val value = spo2Number(payload.opt("spo2"))
            value != null && value.isFinite() && value in 50.0..100.0
        }
    } catch (_: Exception) {
        false
    }

    fun isCompatibleFilterTypeJson(json: String): Boolean = try {
        val payload = JSONObject(json)
        val filter = payload.opt("filter_type")
        // Unlike ingest_source, Core retains null/empty. Absence alone defaults to BMO there.
        !payload.has("filter_type") || filter == JSONObject.NULL ||
            (filter is String && (filter.isEmpty() || filter in filterTypes))
    } catch (_: Exception) {
        false
    }

    fun isCompatibleIngestSourceJson(json: String): Boolean = try {
        val payload = JSONObject(json)
        val source = payload.opt("ingest_source")
        // Core accepts absent/null/empty; leave each representation untouched on the wire.
        !payload.has("ingest_source") || source == JSONObject.NULL ||
            (source is String && (source.isEmpty() || source in ingestSources))
    } catch (_: Exception) {
        false
    }

    fun resolvePatientId(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.ifEmpty { DEFAULT_PATIENT_ID }
    }

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

    // Existing local capture eligibility: do not discard an out-of-contract reading before saving it.
    fun isIngestibleHeartRate(heartRate: Int): Boolean = heartRate >= MIN_HEART_RATE

    fun isIngestible(telemetry: HBandTelemetry): Boolean = isIngestibleHeartRate(telemetry.heartRate)

    fun isIngestibleJson(json: String): Boolean {
        return try {
            // Validate the transport value without truncating fractions or rewriting the durable payload.
            val heartRate = JSONObject(json).optDouble("heart_rate", Double.NaN)
            heartRate.isFinite() && heartRate in MIN_HEART_RATE.toDouble()..MAX_HEART_RATE.toDouble()
        } catch (_: Exception) {
            false
        }
    }

    fun telemetryToJson(telemetry: HBandTelemetry, patientId: String): String {
        val json = JSONObject()
        json.put("patient_id", resolvePatientId(patientId))
        json.put("device_id", resolveDeviceId(telemetry.deviceId))
        json.put("device_model", telemetry.deviceModel)
        json.put("timestamp", telemetry.timestamp)
        json.put("heart_rate", telemetry.heartRate)

        val sys = telemetry.bloodPressure.systolic
        val dia = telemetry.bloodPressure.diastolic
        if (sys > 0 && dia > 0) {
            json.put(
                "blood_pressure",
                JSONObject().apply {
                    put("systolic", sys)
                    put("diastolic", dia)
                }
            )
        }

        if (telemetry.spO2 > 0) json.put("spo2", telemetry.spO2)
        if (telemetry.temperatureCelsius > 0f) json.put("temperature", telemetry.temperatureCelsius.toDouble())
        json.put("steps", telemetry.steps)
        json.put("calories", telemetry.calories.toDouble())
        if (telemetry.distanceMeters > 0f) json.put("distance", telemetry.distanceMeters.toDouble())
        if (telemetry.hrvScore > 0) json.put("hrv_score", telemetry.hrvScore)
        json.put("is_real_sensor_data", telemetry.isRealSensorData)
        json.put("service", SERVICE_NAME)
        return json.toString(2)
    }

    /**
     * Flattens legacy `{ metrics: { heartRate, ... } }` payloads into the HealthTech
     * snake_case shape. Existing numeric values are preserved; missing vitals are
     * omitted instead of being replaced with demo defaults (72 / 118/78 / 98 / 36.6).
     */
    fun normalizeQueuePayload(raw: String): String {
        val jsonObj = JSONObject(raw)
        val alreadyFlat = jsonObj.has("patient_id") && !jsonObj.has("metrics")
        if (alreadyFlat) {
            if (!jsonObj.has("service")) jsonObj.put("service", SERVICE_NAME)
            return jsonObj.toString(2)
        }

        val patientId = resolvePatientId(jsonObj.optString("patient_id", ""))
        val deviceId = when {
            jsonObj.has("device_id") -> jsonObj.optString("device_id")
            jsonObj.has("deviceId") -> jsonObj.optString("deviceId")
            else -> ""
        }
        val timestamp = jsonObj.optString("timestamp", "")
        val metrics = jsonObj.optJSONObject("metrics")

        val hr = firstPresentDouble(metrics, "heartRate")
            ?: firstPresentDouble(jsonObj, "heart_rate")
            ?: firstPresentDouble(jsonObj, "heartRate")

        val sys = metrics?.optJSONObject("bloodPressure")?.takeIf { it.has("systolic") }?.optInt("systolic")
            ?: jsonObj.optJSONObject("blood_pressure")?.takeIf { it.has("systolic") }?.optInt("systolic")
        val dia = metrics?.optJSONObject("bloodPressure")?.takeIf { it.has("diastolic") }?.optInt("diastolic")
            ?: jsonObj.optJSONObject("blood_pressure")?.takeIf { it.has("diastolic") }?.optInt("diastolic")

        val spo2 = metrics?.opt("spO2")?.takeUnless { it == JSONObject.NULL }
            ?: jsonObj.opt("spo2").takeUnless { it == JSONObject.NULL }
        val temp = firstPresentDouble(metrics, "temperatureCelsius")
            ?: firstPresentDouble(jsonObj, "temperature")
        val steps = firstPresentInt(metrics, "steps")
            ?: firstPresentInt(jsonObj, "steps")
        val calories = firstPresentDouble(metrics, "calories")
            ?: firstPresentDouble(jsonObj, "calories")
        val hrv = firstPresentInt(metrics, "hrvScore")
            ?: firstPresentInt(jsonObj, "hrv_score")
        val isReal = when {
            jsonObj.has("is_real_sensor_data") -> jsonObj.optBoolean("is_real_sensor_data")
            jsonObj.has("isRealSensorData") -> jsonObj.optBoolean("isRealSensorData")
            else -> null
        }

        val normalized = JSONObject()
        normalized.put("patient_id", patientId)
        if (deviceId.isNotBlank()) normalized.put("device_id", resolveDeviceId(deviceId))
        if (timestamp.isNotBlank()) normalized.put("timestamp", timestamp)
        if (hr != null) normalized.put("heart_rate", hr)
        if (sys != null && dia != null && sys > 0 && dia > 0) {
            normalized.put(
                "blood_pressure",
                JSONObject().apply {
                    put("systolic", sys)
                    put("diastolic", dia)
                }
            )
        }
        // Preserve explicit malformed values for local review; retain the legacy <=0 sentinel rule.
        if (spo2 != null && spo2Number(spo2)?.let { it <= 0 } != true) normalized.put("spo2", spo2)
        if (temp != null && temp > 0.0) normalized.put("temperature", temp)
        if (steps != null) normalized.put("steps", steps)
        if (calories != null) normalized.put("calories", calories)
        if (hrv != null && hrv > 0) normalized.put("hrv_score", hrv)
        if (isReal != null) normalized.put("is_real_sensor_data", isReal)
        // Preserve declared provenance exactly; absence is not evidence of any source.
        if (jsonObj.has("ingest_source")) normalized.put("ingest_source", jsonObj.get("ingest_source"))
        if (jsonObj.has("filter_type")) normalized.put("filter_type", jsonObj.get("filter_type"))
        normalized.put("service", SERVICE_NAME)
        return normalized.toString(2)
    }

    fun classifyHttp(code: Int): IngestHttpKind {
        return when (code) {
            in 200..299 -> IngestHttpKind.SUCCESS
            401, 403 -> IngestHttpKind.AUTH
            in 400..499 -> IngestHttpKind.CLIENT
            else -> IngestHttpKind.SERVER
        }
    }

    fun authErrorMessage(httpCode: Int, @Suppress("UNUSED_PARAMETER") errorBody: String?): String {
        // Keep the persisted pause prefix; never store a server body that may echo a credential.
        return "Falha de autenticação na API HealthTech (HTTP $httpCode). Verifique a chave em Ajustes."
    }

    private fun firstPresentInt(obj: JSONObject?, key: String): Int? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.optInt(key)
    }

    private fun firstPresentDouble(obj: JSONObject?, key: String): Double? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.optDouble(key)
    }
}

class IngestDeduper(
    private val minIntervalMs: Long = 30_000L,
    // Process-local interval clock; never a measurement timestamp or persisted value.
    private val elapsedMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastSignature: String? = null
    private var lastAt: Long = 0L

    /** Serial collector only: a failed local save must not consume its deduplication slot. */
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
        if (!IngestPayloadMapper.isIngestible(telemetry)) return false
        // Compare every measurement persisted by the live recorder, not just the core vitals.
        // Timestamp/model metadata alone still do not bypass the existing elapsed interval.
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
            telemetry.sleepSummary.awakeMinutes
        ).joinToString("|")
        val elapsed = nowMs - lastAt
        // A reset of an injected clock must not leave the recorder stuck behind lastAt.
        if (signature == lastSignature && elapsed >= 0L && elapsed < minIntervalMs) return false
        lastSignature = signature
        lastAt = nowMs
        return true
    }
}
