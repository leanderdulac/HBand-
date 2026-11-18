package com.example.data.ingest

import com.example.data.hband.VeepooHistoryMapper
import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry

/**
 * Holds the last measured BP (both sides > 0) so later HR/SpO2 snapshots can
 * still carry it to Core. Never invents values: 0/0 stays 0/0 when nothing
 * valid was seen, or when the sample is older than [freshnessMs].
 */
class LastValidBloodPressure(
    private val freshnessMs: Long = DEFAULT_FRESHNESS_MS,
) {
    data class Sample(val systolic: Int, val diastolic: Int, val observedAtMs: Long)

    var last: Sample? = null
        private set

    fun remember(systolic: Int, diastolic: Int, observedAtMs: Long) {
        if (systolic <= 0 || diastolic <= 0 || observedAtMs <= 0L) return
        val previous = last
        if (previous == null || observedAtMs >= previous.observedAtMs) {
            last = Sample(systolic, diastolic, observedAtMs)
        }
    }

    fun rememberFromHistory(samples: List<HBandTelemetry>) {
        for (sample in samples) {
            remember(
                sample.bloodPressure.systolic,
                sample.bloodPressure.diastolic,
                VeepooHistoryMapper.parseIsoToMillis(sample.timestamp),
            )
        }
    }

    /**
     * Prefer a live reading when both sides are > 0. Otherwise reuse the last
     * valid sample if it is still inside the freshness window. Never fills
     * demo defaults such as 120/80.
     */
    fun resolve(systolic: Int, diastolic: Int, nowMs: Long): BloodPressure {
        if (systolic > 0 && diastolic > 0) {
            remember(systolic, diastolic, nowMs)
            return BloodPressure(systolic, diastolic)
        }
        val held = last ?: return BloodPressure(0, 0)
        val age = nowMs - held.observedAtMs
        if (age in 0L until freshnessMs) {
            return BloodPressure(held.systolic, held.diastolic)
        }
        return BloodPressure(0, 0)
    }

    fun clear() {
        last = null
    }

    companion object {
        const val DEFAULT_FRESHNESS_MS = 60 * 60 * 1000L
    }
}
