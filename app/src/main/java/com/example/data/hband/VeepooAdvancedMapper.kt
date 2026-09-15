package com.example.data.hband

import com.example.data.local.AdvancedMeasurementEntity
import com.example.data.local.AdvancedMeasurementKind
import com.veepoo.protocol.model.datas.BloodComponent
import com.veepoo.protocol.model.datas.BodyComponent
import com.veepoo.protocol.model.datas.BreathData
import com.veepoo.protocol.model.datas.EcgDetectResult
import com.veepoo.protocol.model.datas.FatigueData
import com.veepoo.protocol.model.enums.EDeviceStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts official Veepoo P1 packets into Room rows without inventing clinical
 * values. Empty / unfinished / out-of-range readings are discarded.
 */
object VeepooAdvancedMapper {
    const val MAX_STORED_WAVEFORM = 240

    fun fromEcgResult(
        result: EcgDetectResult?,
        deviceId: String,
        waveform: List<Int> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (result == null) return null
        val heart = result.aveHeart.takeIf { it in 30..240 } ?: 0
        val hrv = result.aveHrv.takeIf { it > 0 } ?: 0
        val signals = waveform.ifEmpty {
            downsample(result.filterSignals ?: result.originSign)
        }
        if (!result.isSuccess && heart == 0 && signals.isEmpty()) return null
        val summary = buildString {
            if (heart > 0) append("FC média $heart bpm")
            if (hrv > 0) {
                if (isNotEmpty()) append(" • ")
                append("HRV $hrv")
            }
            if (result.duration > 0) {
                if (isNotEmpty()) append(" • ")
                append("${result.duration}s")
            }
            if (signals.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("${signals.size} amostras ADC")
            }
            if (isEmpty()) append("ECG recebido")
        }
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.ECG,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = summary,
            numericValue = heart.toFloat(),
            secondaryValue = hrv.toFloat(),
            sampleCount = signals.size,
            payloadJson = JSONObject()
                .put("success", result.isSuccess)
                .put("ave_heart", heart)
                .put("ave_hrv", hrv)
                .put("duration", result.duration)
                .put("waveform", JSONArray(signals))
                .toString(),
            isReal = true,
        )
    }

    fun fromGlucose(
        value: Float,
        progress: Int,
        riskName: String?,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (progress in 1..99) return null
        if (!isPlausibleGlucose(value)) return null
        val risk = riskName?.takeIf { it.isNotBlank() && it != "NONE" }
        val summary = buildString {
            append("Glicose ${formatOneDecimal(value)}")
            if (risk != null) append(" • risco $risk")
        }
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.GLUCOSE,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = summary,
            numericValue = value,
            sampleCount = 1,
            payloadJson = JSONObject()
                .put("glucose", value.toDouble())
                .put("risk", risk ?: JSONObject.NULL)
                .toString(),
            isReal = true,
        )
    }

    fun fromBloodComponent(
        component: BloodComponent?,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (component == null) return null
        val uric = component.uricAcid.takeIf { it > 0f } ?: 0f
        val tcho = component.tCHO.takeIf { it > 0f } ?: 0f
        val tag = component.tAG.takeIf { it > 0f } ?: 0f
        val hdl = component.hDL.takeIf { it > 0f } ?: 0f
        val ldl = component.lDL.takeIf { it > 0f } ?: 0f
        if (uric == 0f && tcho == 0f && tag == 0f && hdl == 0f && ldl == 0f) return null
        val parts = buildList {
            if (uric > 0f) add("ácido úrico ${formatOneDecimal(uric)}")
            if (tcho > 0f) add("TCHO ${formatOneDecimal(tcho)}")
            if (tag > 0f) add("TAG ${formatOneDecimal(tag)}")
            if (hdl > 0f) add("HDL ${formatOneDecimal(hdl)}")
            if (ldl > 0f) add("LDL ${formatOneDecimal(ldl)}")
        }
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.BLOOD_COMPONENT,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = parts.joinToString(" • "),
            numericValue = uric,
            secondaryValue = tcho,
            sampleCount = parts.size,
            payloadJson = JSONObject()
                .put("uric_acid", uric.toDouble())
                .put("tcho", tcho.toDouble())
                .put("tag", tag.toDouble())
                .put("hdl", hdl.toDouble())
                .put("ldl", ldl.toDouble())
                .toString(),
            isReal = true,
        )
    }

    fun fromBodyComponent(
        component: BodyComponent?,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (component == null) return null
        val bmiRaw = component.BMI
        val fatRaw = component.bodyFatRate.takeIf { it > 0f } ?: component.fatRate
        val muscleRaw = component.muscleRate
        val waterRaw = component.bodyWater.takeIf { it > 0f } ?: component.waterContent
        val bmi = if (bmiRaw > 0f) bmiRaw else 0f
        val fat = if (fatRaw > 0f) fatRaw else 0f
        val muscle = if (muscleRaw > 0f) muscleRaw else 0f
        val water = if (waterRaw > 0f) waterRaw else 0f
        if (bmi == 0f && fat == 0f && muscle == 0f && water == 0f) return null
        val parts = buildList {
            if (bmi > 0f) add("BMI ${formatOneDecimal(bmi)}")
            if (fat > 0f) add("gordura ${formatOneDecimal(fat)}%")
            if (muscle > 0f) add("músculo ${formatOneDecimal(muscle)}%")
            if (water > 0f) add("água ${formatOneDecimal(water)}%")
        }
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.BODY_COMPONENT,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = parts.joinToString(" • "),
            numericValue = bmi,
            secondaryValue = fat,
            sampleCount = parts.size,
            payloadJson = JSONObject()
                .put("bmi", bmi.toDouble())
                .put("body_fat_rate", fat.toDouble())
                .put("muscle_rate", muscle.toDouble())
                .put("body_water", water.toDouble())
                .toString(),
            isReal = true,
        )
    }

    fun fromEmotion(
        progress: Int,
        value: Int,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (progress in 1..99) return null
        if (value <= 0) return null
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.EMOTION,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = "Emoção $value",
            numericValue = value.toFloat(),
            sampleCount = 1,
            payloadJson = JSONObject().put("emotion", value).toString(),
            isReal = true,
        )
    }

    fun fromFatigue(
        data: FatigueData?,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (data == null) return null
        if (data.progress in 1..99) return null
        val finished = data.deviceState == EDeviceStatus.FINISH || data.progress >= 100
        if (!finished && data.progress != 0) return null
        if (data.value <= 0) return null
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.FATIGUE,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = "Fadiga ${data.value}",
            numericValue = data.value.toFloat(),
            sampleCount = 1,
            payloadJson = JSONObject()
                .put("fatigue", data.value)
                .put("device_state", data.deviceState?.name ?: JSONObject.NULL)
                .toString(),
            isReal = true,
        )
    }

    fun fromBreath(
        data: BreathData?,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AdvancedMeasurementEntity? {
        if (data == null) return null
        if (data.progressValue in 1..99) return null
        if (data.value !in 4..60) return null
        return AdvancedMeasurementEntity(
            deviceId = deviceId,
            kind = AdvancedMeasurementKind.BREATH,
            timestamp = isoUtc(nowMs),
            timestampMillis = nowMs,
            summary = "Respiração ${data.value} rpm",
            numericValue = data.value.toFloat(),
            sampleCount = 1,
            payloadJson = JSONObject().put("breath_rate", data.value).toString(),
            isReal = true,
        )
    }

    fun downsample(raw: IntArray?): List<Int> {
        if (raw == null || raw.isEmpty()) return emptyList()
        if (raw.size <= MAX_STORED_WAVEFORM) return raw.toList()
        val step = raw.size / MAX_STORED_WAVEFORM.toFloat()
        return List(MAX_STORED_WAVEFORM) { index ->
            raw[(index * step).toInt().coerceIn(0, raw.lastIndex)]
        }
    }

    fun appendWaveform(current: List<Int>, incoming: IntArray?): List<Int> {
        if (incoming == null || incoming.isEmpty()) return current
        val merged = current + incoming.toList()
        return if (merged.size <= MAX_STORED_WAVEFORM) {
            merged
        } else {
            merged.takeLast(MAX_STORED_WAVEFORM)
        }
    }

    fun isPlausibleGlucose(value: Float): Boolean {
        // Firmware may report mmol/L (~2–33) or mg/dL (~40–400). Accept either;
        // never convert or invent a unit.
        return value in 1.5f..33.0f || value in 40f..400f
    }

    private fun formatOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

    private fun isoUtc(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(epochMs))
    }
}
