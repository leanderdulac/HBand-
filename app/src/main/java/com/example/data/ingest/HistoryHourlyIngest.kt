package com.example.data.ingest

import com.example.data.hband.VeepooHistoryMapper
import com.example.data.model.HBandTelemetry

/**
 * Hourly Core ingest still picks the highest ingestible HR, but origin BP and
 * steps from the same hour must ride along instead of being dropped by a later
 * HR-only sample. Missing fields stay 0 — nothing is fabricated.
 */
object HistoryHourlyIngest {
    fun hourKey(telemetry: HBandTelemetry): Pair<String, Long> {
        val bucket = VeepooHistoryMapper.parseIsoToMillis(telemetry.timestamp) / 3_600_000L
        return telemetry.deviceId to bucket
    }

    fun hasUploadableBp(telemetry: HBandTelemetry): Boolean =
        telemetry.bloodPressure.systolic > 0 && telemetry.bloodPressure.diastolic > 0

    fun hasUploadableSteps(telemetry: HBandTelemetry): Boolean = telemetry.steps > 0

    fun mergeHourly(winner: HBandTelemetry, hourSamples: List<HBandTelemetry>): HBandTelemetry {
        val bpSource = hourSamples
            .filter { hasUploadableBp(it) }
            .maxByOrNull { VeepooHistoryMapper.parseIsoToMillis(it.timestamp) }
        val stepSource = hourSamples
            .filter { hasUploadableSteps(it) }
            .maxByOrNull { VeepooHistoryMapper.parseIsoToMillis(it.timestamp) }

        val bloodPressure = if (hasUploadableBp(winner)) winner.bloodPressure
            else bpSource?.bloodPressure ?: winner.bloodPressure

        if (hasUploadableSteps(winner) || stepSource == null) {
            return winner.copy(bloodPressure = bloodPressure)
        }
        return winner.copy(
            bloodPressure = bloodPressure,
            steps = stepSource.steps,
            calories = if (winner.calories > 0f) winner.calories else stepSource.calories,
            distanceMeters = if (winner.distanceMeters > 0f) winner.distanceMeters else stepSource.distanceMeters,
        )
    }

    fun bringsMissingBpOrSteps(storedWinner: HBandTelemetry, mergedNew: HBandTelemetry): Boolean {
        val newBp = hasUploadableBp(mergedNew) && !hasUploadableBp(storedWinner)
        val newSteps = hasUploadableSteps(mergedNew) && !hasUploadableSteps(storedWinner)
        return newBp || newSteps
    }
}
