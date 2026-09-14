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
 * - `heart_rate` must be >= [MIN_HEART_RATE] or the API returns HTTP 422
 * - optional vitals (BP, SpO2, temperature, HRV) are sent only when a real
 *   reading exists — never filled with demo defaults
 */
object IngestPayloadMapper {
    const val DEFAULT_PATIENT_ID = "PAT-HBAND-001"
    const val SERVICE_NAME = "healthtech-secure-api"
    const val MIN_HEART_RATE = 20

    /** Placeholder MAC from the old [com.example.data.model.HBandDevice] defaults. */
    const val PLACEHOLDER_DEVICE_ID = "HBAND-B57-89A4"
    const val PLACEHOLDER_MAC = "E4:A8:B6:12:89:A4"

    const val MISSING_HR_ERROR =
        "FC ausente ou inválida (heart_rate < $MIN_HEART_RATE). Amostra não enviada — nenhum valor foi inventado."

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

    fun isIngestibleHeartRate(heartRate: Int): Boolean = heartRate >= MIN_HEART_RATE

    fun isIngestible(telemetry: HBandTelemetry): Boolean = isIngestibleHeartRate(telemetry.heartRate)

    fun isIngestibleJson(json: String): Boolean {
        return try {
            isIngestibleHeartRate(JSONObject(json).optInt("heart_rate", 0))
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

        val hr = firstPresentInt(metrics, "heartRate")
            ?: firstPresentInt(jsonObj, "heart_rate")
            ?: firstPresentInt(jsonObj, "heartRate")

        val sys = metrics?.optJSONObject("bloodPressure")?.takeIf { it.has("systolic") }?.optInt("systolic")
            ?: jsonObj.optJSONObject("blood_pressure")?.takeIf { it.has("systolic") }?.optInt("systolic")
        val dia = metrics?.optJSONObject("bloodPressure")?.takeIf { it.has("diastolic") }?.optInt("diastolic")
            ?: jsonObj.optJSONObject("blood_pressure")?.takeIf { it.has("diastolic") }?.optInt("diastolic")

        val spo2 = firstPresentInt(metrics, "spO2")
            ?: firstPresentInt(jsonObj, "spo2")
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
        if (spo2 != null && spo2 > 0) normalized.put("spo2", spo2)
        if (temp != null && temp > 0.0) normalized.put("temperature", temp)
        if (steps != null) normalized.put("steps", steps)
        if (calories != null) normalized.put("calories", calories)
        if (hrv != null && hrv > 0) normalized.put("hrv_score", hrv)
        if (isReal != null) normalized.put("is_real_sensor_data", isReal)
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

    fun authErrorMessage(httpCode: Int, errorBody: String?): String {
        val detail = errorBody?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        return "Falha de autenticação na API HealthTech (HTTP $httpCode)$detail. Verifique a chave em Ajustes."
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
    private val minIntervalMs: Long = 30_000L
) {
    private var lastSignature: String? = null
    private var lastAt: Long = 0L

    fun shouldEnqueue(telemetry: HBandTelemetry, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!telemetry.isRealSensorData) return false
        if (!IngestPayloadMapper.isIngestible(telemetry)) return false
        val signature = listOf(
            telemetry.deviceId,
            telemetry.heartRate,
            telemetry.spO2,
            telemetry.bloodPressure.systolic,
            telemetry.bloodPressure.diastolic,
            telemetry.steps
        ).joinToString("|")
        if (signature == lastSignature && nowMs - lastAt < minIntervalMs) return false
        lastSignature = signature
        lastAt = nowMs
        return true
    }
}
