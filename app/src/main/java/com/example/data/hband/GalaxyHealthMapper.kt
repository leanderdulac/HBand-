package com.example.data.hband

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Galaxy Watch stays on the Galaxy Wearable classic link and rejects a second
 * BLE central (GATT 147 / CONNECTION_ACCEPT_TIMEOUT). Heart rate already
 * synced into Samsung Health is the measurement we can upload.
 */
data class GalaxyHeartSample(val heartRate: Int, val measuredAtMs: Long)

data class SentCursor(val encoded: String, val lastMs: Long)

object GalaxyHealthMapper {
    const val FRESH_MS = 50L * 60L * 1000L
    const val HISTORY_MS = 12L * 60L * 60L * 1000L
    const val SAMPLE_BUCKET_MS = 5L * 60L * 1000L
    const val MAX_BACKFILL = 144
    private const val CLOCK_SKEW_MS = 2L * 60L * 1000L
    private const val VITAL_WINDOW_MS = 6L * 60L * 60L * 1000L
    const val PREF_LAST_SAMPLE_MS = "galaxy_health_last_sample_ms"
    const val PREF_SENT_MS = "galaxy_health_sent_ms"

    const val NOTE_PERMISSION =
        "O Galaxy Watch não mede por este Bluetooth. Autorize o Samsung Health para a frequência cardíaca entrar."
    const val NOTE_EMPTY =
        "O Samsung Health ainda não compartilhou a frequência cardíaca. Nele, abra Health Connect e permita frequência, oxigênio e pressão."
    const val NOTE_OTHER_ORIGIN =
        "Há frequência no Health Connect, mas não veio do Samsung Health. Ative o compartilhamento do relógio."
    const val NOTE_UNAVAILABLE =
        "Este Android não tem o Health Connect. Sem ele o Samsung Health não entrega a medição."

    fun isGalaxyWatch(name: String?): Boolean =
        name?.contains("Galaxy Watch", ignoreCase = true) == true

    fun isSamsungHealthOrigin(packageName: String?): Boolean {
        val value = packageName?.lowercase().orEmpty()
        return value.contains("shealth")
    }

    fun acceptHeartRate(bpm: Int): Boolean = bpm in 20..250

    fun normalizePercent(raw: Double): Int {
        val scaled = if (raw in 0.0..1.5) raw * 100.0 else raw
        return scaled.toInt()
    }

    fun isFresh(measuredAtMs: Long, nowMs: Long): Boolean =
        measuredAtMs in (nowMs - FRESH_MS)..(nowMs + CLOCK_SKEW_MS)

    /** Janela de 12 h. A de 50 min continua só para o relógio aparecer online. */
    fun inUploadWindow(measuredAtMs: Long, nowMs: Long): Boolean =
        measuredAtMs in (nowMs - HISTORY_MS)..(nowMs + CLOCK_SKEW_MS)

    fun shouldPublish(measuredAtMs: Long, lastPublishedMs: Long, nowMs: Long): Boolean =
        inUploadWindow(measuredAtMs, nowMs) && measuredAtMs > lastPublishedMs

    fun sentMillis(encoded: String?, lastPublishedMs: Long, nowMs: Long): Set<Long> {
        val windowStart = nowMs - HISTORY_MS
        val parsed = encoded.orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        val seeded = if (lastPublishedMs > 0L) parsed + lastPublishedMs else parsed
        return seeded.filter { it >= windowStart }.toSet()
    }

    fun rememberSent(encoded: String?, lastPublishedMs: Long, sampleMs: Long, nowMs: Long): SentCursor {
        val windowStart = nowMs - HISTORY_MS
        val kept = sentMillis(encoded, lastPublishedMs, nowMs).toMutableSet()
        if (sampleMs > 0L) kept.add(sampleMs)
        val fresh = kept.filter { it >= windowStart }.sorted().takeLast(400)
        return SentCursor(
            encoded = fresh.joinToString(","),
            lastMs = fresh.maxOrNull() ?: lastPublishedMs,
        )
    }

    /**
     * Amostras ainda não enviadas, no máximo uma a cada 5 min.
     * Amostra velha entra se ainda estiver nas 12 h e não tiver sido enviada.
     */
    fun pendingSamples(
        samples: List<GalaxyHeartSample>,
        alreadySentMs: Set<Long>,
        nowMs: Long,
    ): List<GalaxyHeartSample> {
        val byBucket = LinkedHashMap<Long, GalaxyHeartSample>()
        for (sample in samples.sortedBy { it.measuredAtMs }) {
            if (!acceptHeartRate(sample.heartRate)) continue
            if (sample.measuredAtMs in alreadySentMs) continue
            if (!inUploadWindow(sample.measuredAtMs, nowMs)) continue
            val bucket = sample.measuredAtMs / SAMPLE_BUCKET_MS
            val prev = byBucket[bucket]
            if (prev == null || sample.measuredAtMs >= prev.measuredAtMs) {
                byBucket[bucket] = sample
            }
        }
        val ordered = byBucket.values.toList()
        return if (ordered.size <= MAX_BACKFILL) ordered else ordered.takeLast(MAX_BACKFILL)
    }

    fun ageMinutes(measuredAtMs: Long, nowMs: Long): Long =
        ((nowMs - measuredAtMs) / 60_000L).coerceAtLeast(0L)

    fun spo2Near(heartAtMs: Long, spo2: Int, spo2AtMs: Long): Int {
        if (spo2 !in 50..100) return 0
        if (abs(spo2AtMs - heartAtMs) > VITAL_WINDOW_MS) return 0
        return spo2
    }

    fun bloodPressureNear(
        heartAtMs: Long,
        systolic: Int,
        diastolic: Int,
        atMs: Long,
    ): BloodPressure {
        val plausible = systolic in 70..250 &&
            diastolic in 40..150 &&
            systolic > diastolic &&
            abs(atMs - heartAtMs) <= VITAL_WINDOW_MS
        return if (plausible) BloodPressure(systolic, diastolic) else BloodPressure(0, 0)
    }

    fun isoUtc(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMs))
    }

    fun localClock(epochMs: Long): String {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(Date(epochMs))
    }

    fun noteForReading(measuredAtMs: Long, nowMs: Long): String =
        if (isFresh(measuredAtMs, nowMs)) {
            "Frequência do Samsung Health às ${localClock(measuredAtMs)}."
        } else {
            "Última frequência do Samsung Health há ${ageMinutes(measuredAtMs, nowMs)} min. Sem leitura nova."
        }

    fun toTelemetry(
        heartRate: Int,
        measuredAtMs: Long,
        deviceId: String,
        deviceModel: String,
        spo2: Int = 0,
        spo2AtMs: Long = 0L,
        systolic: Int = 0,
        diastolic: Int = 0,
        bloodPressureAtMs: Long = 0L,
        steps: Int = 0,
    ): HBandTelemetry? {
        if (!acceptHeartRate(heartRate)) return null
        val pressure = bloodPressureNear(measuredAtMs, systolic, diastolic, bloodPressureAtMs)
        return HBandTelemetry(
            deviceId = deviceId,
            deviceModel = deviceModel,
            timestamp = isoUtc(measuredAtMs),
            heartRate = heartRate,
            bloodPressure = pressure,
            spO2 = spo2Near(measuredAtMs, spo2, spo2AtMs),
            temperatureCelsius = 0f,
            steps = steps.coerceAtLeast(0),
            calories = 0f,
            distanceMeters = 0f,
            hrvScore = 0,
            sleepSummary = SleepSummary(0, 0, 0),
            isRealSensorData = true,
        )
    }
}
