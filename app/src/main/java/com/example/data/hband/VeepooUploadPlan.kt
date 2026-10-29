package com.example.data.hband

import com.example.data.ingest.IngestPayloadMapper
import com.example.data.model.HBandTelemetry

/**
 * O VE30 grava Origin em intervalos curtos. O envio fica em uma amostra
 * a cada 5 minutos, com batimento real, e no máximo um dia dessa grade.
 * Sono sem FC continua só no banco local.
 */
object VeepooUploadPlan {
    const val BUCKET_MS = 5L * 60L * 1000L
    const val MAX_SAMPLES = 288

    fun forIngest(samples: List<HBandTelemetry>): List<HBandTelemetry> {
        val chosen = linkedMapOf<Long, HBandTelemetry>()
        for (sample in samples) {
            if (!IngestPayloadMapper.isIngestible(sample)) continue
            val epoch = VeepooHistoryMapper.parseIsoToMillis(sample.timestamp)
            if (epoch <= 0L) continue
            val bucket = epoch / BUCKET_MS
            val current = chosen[bucket]
            if (current == null || prefer(sample, current)) {
                chosen[bucket] = sample
            }
        }
        return chosen.values
            .sortedBy { VeepooHistoryMapper.parseIsoToMillis(it.timestamp) }
            .takeLast(MAX_SAMPLES)
    }

    private fun prefer(candidate: HBandTelemetry, current: HBandTelemetry): Boolean {
        val score = richness(candidate) - richness(current)
        if (score != 0) return score > 0
        return candidate.heartRate > current.heartRate
    }

    private fun richness(sample: HBandTelemetry): Int {
        var score = 0
        if (sample.spO2 in 50..100) score += 4
        if (sample.bloodPressure.systolic in 60..240 && sample.bloodPressure.diastolic in 30..160) score += 4
        if (sample.hrvScore > 0) score += 2
        if (sample.temperatureCelsius in 30f..43f) score += 1
        if (sample.steps > 0) score += 1
        return score
    }
}
