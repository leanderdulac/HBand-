package com.example.data.hband

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import com.veepoo.protocol.model.datas.HRVOriginData
import com.veepoo.protocol.model.datas.OriginData
import com.veepoo.protocol.model.datas.OriginData3
import com.veepoo.protocol.model.datas.SleepData
import com.veepoo.protocol.model.datas.Spo2hOriginData
import com.veepoo.protocol.model.datas.TimeData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts official Veepoo Origin / sleep / HRV / SpO2 packets into
 * [HBandTelemetry] without inventing placeholders. Missing readings stay 0
 * and callers must skip empty samples.
 */
object VeepooHistoryMapper {
    data class MappedSample(
        val telemetry: HBandTelemetry,
        val epochMs: Long,
        val worn: Boolean? = null,
        val kind: Kind,
    ) {
        enum class Kind { ORIGIN, SLEEP, HRV, SPO2 }
    }

    fun fromOrigin(
        origin: OriginData,
        deviceId: String,
        deviceModel: String,
    ): MappedSample? {
        val epochMs = epochMsOf(origin.getmTime(), origin.date)
        if (epochMs <= 0L) return null

        val heart = origin.rateValue.takeIf { it in 30..240 } ?: 0
        val sys = origin.highValue.takeIf { it in 60..240 } ?: 0
        val dia = origin.lowValue.takeIf { it in 30..160 } ?: 0
        val steps = origin.stepValue.coerceAtLeast(0)
        val calories = origin.calValue.toFloat().takeIf { it > 0f } ?: 0f
        val distance = origin.disValue.toFloat().takeIf { it > 0f } ?: 0f
        val temp = origin.temperature.toFloat().takeIf { it in 30f..43f } ?: 0f
        val spo2 = extractOriginSpo2(origin)
        val worn = when (origin.wear) {
            0 -> false
            1, 2 -> true
            else -> null
        }

        if (heart == 0 && sys == 0 && dia == 0 && steps == 0 && spo2 == 0 && temp == 0f) {
            return null
        }

        return MappedSample(
            telemetry = HBandTelemetry(
                deviceId = deviceId,
                deviceModel = deviceModel,
                timestamp = isoUtc(epochMs),
                heartRate = heart,
                bloodPressure = BloodPressure(sys, dia),
                spO2 = spo2,
                temperatureCelsius = temp,
                steps = steps,
                calories = calories,
                distanceMeters = distance,
                hrvScore = 0,
                sleepSummary = SleepSummary(0, 0, 0),
                isRealSensorData = true,
            ),
            epochMs = epochMs,
            worn = worn,
            kind = MappedSample.Kind.ORIGIN,
        )
    }

    fun fromSleep(
        sleep: SleepData,
        deviceId: String,
        deviceModel: String,
    ): MappedSample? {
        val deep = sleep.deepSleepTime.coerceAtLeast(0)
        val light = sleep.lowSleepTime.coerceAtLeast(0)
        val all = sleep.allSleepTime.coerceAtLeast(0)
        if (deep == 0 && light == 0 && all == 0) return null

        val awake = (all - deep - light).coerceAtLeast(0)
        val epochMs = epochMsOf(sleep.sleepDown, sleep.date).takeIf { it > 0L }
            ?: epochMsOf(sleep.sleepUp, sleep.date)
        if (epochMs <= 0L) return null

        return MappedSample(
            telemetry = HBandTelemetry(
                deviceId = deviceId,
                deviceModel = deviceModel,
                timestamp = isoUtc(epochMs),
                heartRate = 0,
                bloodPressure = BloodPressure(0, 0),
                spO2 = 0,
                temperatureCelsius = 0f,
                steps = 0,
                calories = 0f,
                distanceMeters = 0f,
                hrvScore = 0,
                sleepSummary = SleepSummary(
                    deepSleepMinutes = deep,
                    lightSleepMinutes = light,
                    awakeMinutes = awake,
                ),
                isRealSensorData = true,
            ),
            epochMs = epochMs,
            kind = MappedSample.Kind.SLEEP,
        )
    }

    fun fromHrv(
        hrv: HRVOriginData,
        deviceId: String,
        deviceModel: String,
    ): MappedSample? {
        val score = hrv.hrvValue.takeIf { it > 0 } ?: return null
        val epochMs = epochMsOf(hrv.getmTime(), hrv.date)
        if (epochMs <= 0L) return null
        val heart = hrv.rate.toIntOrNull()?.takeIf { it in 30..240 } ?: 0

        return MappedSample(
            telemetry = HBandTelemetry(
                deviceId = deviceId,
                deviceModel = deviceModel,
                timestamp = isoUtc(epochMs),
                heartRate = heart,
                bloodPressure = BloodPressure(0, 0),
                spO2 = 0,
                temperatureCelsius = 0f,
                steps = 0,
                calories = 0f,
                distanceMeters = 0f,
                hrvScore = score,
                sleepSummary = SleepSummary(0, 0, 0),
                isRealSensorData = true,
            ),
            epochMs = epochMs,
            kind = MappedSample.Kind.HRV,
        )
    }

    fun fromSpo2(
        spo2: Spo2hOriginData,
        deviceId: String,
        deviceModel: String,
    ): MappedSample? {
        val oxygen = spo2.oxygenValue.takeIf { it in 50..100 } ?: 0
        val heart = spo2.heartValue.takeIf { it in 30..240 } ?: 0
        if (oxygen == 0 && heart == 0) return null
        val epochMs = epochMsOf(spo2.getmTime(), spo2.date)
        if (epochMs <= 0L) return null

        return MappedSample(
            telemetry = HBandTelemetry(
                deviceId = deviceId,
                deviceModel = deviceModel,
                timestamp = isoUtc(epochMs),
                heartRate = heart,
                bloodPressure = BloodPressure(0, 0),
                spO2 = oxygen,
                temperatureCelsius = 0f,
                steps = spo2.stepValue.coerceAtLeast(0),
                calories = 0f,
                distanceMeters = 0f,
                hrvScore = spo2.gethRVariation().takeIf { it > 0 } ?: 0,
                sleepSummary = SleepSummary(0, 0, 0),
                isRealSensorData = true,
            ),
            epochMs = epochMs,
            kind = MappedSample.Kind.SPO2,
        )
    }

    fun epochMsOf(time: TimeData?, dateFallback: String?): Long {
        if (time != null) {
            try {
                val calendar = time.toCalendar()
                val ms = calendar.timeInMillis
                if (ms > 0L) return ms
            } catch (_: Exception) {
            }
            val year = normalizeYear(time.year)
            if (year >= 2000 && time.month in 1..12 && time.day in 1..31) {
                val cal = java.util.Calendar.getInstance()
                cal.clear()
                cal.set(year, time.month - 1, time.day, time.hour.coerceIn(0, 23), time.minute.coerceIn(0, 59), time.second.coerceIn(0, 59))
                return cal.timeInMillis
            }
        }
        return parseVeepooDate(dateFallback)
    }

    fun isoUtc(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(epochMs))
    }

    fun parseIsoToMillis(iso: String): Long {
        if (iso.isBlank()) return 0L
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            format.parse(iso)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractOriginSpo2(origin: OriginData): Int {
        if (origin is OriginData3) {
            val fromArray = origin.oxygens?.lastOrNull { it in 50..100 }
            if (fromArray != null) return fromArray
        }
        return 0
    }

    private fun normalizeYear(year: Int): Int = when {
        year in 0..99 -> 2000 + year
        year in 100..199 -> 1900 + year
        else -> year
    }

    private fun parseVeepooDate(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return 0L
        val patterns = listOf("yyyy-MM-dd", "yyyy.MM.dd", "yyyy/MM/dd", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                val parsed = format.parse(value)
                if (parsed != null && parsed.time > 0L) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}
