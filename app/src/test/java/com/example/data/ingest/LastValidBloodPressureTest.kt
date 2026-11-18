package com.example.data.ingest

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastValidBloodPressureTest {
    private val now = 1_800_000_000_000L

    @Test fun live_valid_reading_is_remembered_and_returned() {
        val hold = LastValidBloodPressure()
        val resolved = hold.resolve(124, 82, now)
        assertEquals(BloodPressure(124, 82), resolved)
        assertEquals(LastValidBloodPressure.Sample(124, 82, now), hold.last)
    }

    @Test fun later_zero_reuses_last_valid_inside_the_hour() {
        val hold = LastValidBloodPressure()
        hold.resolve(118, 76, now)
        val carried = hold.resolve(0, 0, now + 59 * 60 * 1000L)
        assertEquals(BloodPressure(118, 76), carried)
    }

    @Test fun stale_sample_is_not_reused_and_zeros_are_not_replaced_with_defaults() {
        val hold = LastValidBloodPressure()
        hold.resolve(118, 76, now)
        val stale = hold.resolve(0, 0, now + LastValidBloodPressure.DEFAULT_FRESHNESS_MS)
        assertEquals(BloodPressure(0, 0), stale)
        assertNull(LastValidBloodPressure().resolve(0, 0, now).let { it.takeIf { bp -> bp.systolic > 0 } })
        assertEquals(BloodPressure(0, 0), LastValidBloodPressure().resolve(0, 80, now))
        assertEquals(BloodPressure(0, 0), LastValidBloodPressure().resolve(120, 0, now))
    }

    @Test fun history_bp_seeds_later_live_hr_only_readings() {
        val hold = LastValidBloodPressure()
        hold.rememberFromHistory(
            listOf(
                sample(heartRate = 80, systolic = 0, diastolic = 0, timestamp = "2026-09-30T12:40:00Z"),
                sample(heartRate = 72, systolic = 130, diastolic = 85, timestamp = "2026-09-30T12:10:00Z"),
            ),
        )
        val historyMs = com.example.data.hband.VeepooHistoryMapper.parseIsoToMillis("2026-09-30T12:10:00Z")
        val carried = hold.resolve(0, 0, historyMs + 20 * 60 * 1000L)
        assertEquals(BloodPressure(130, 85), carried)
    }

    @Test fun older_history_does_not_overwrite_a_newer_live_sample() {
        val hold = LastValidBloodPressure()
        hold.remember(122, 80, now)
        hold.remember(140, 90, now - 1_000L)
        assertEquals(LastValidBloodPressure.Sample(122, 80, now), hold.last)
    }

    @Test fun clear_drops_the_held_sample() {
        val hold = LastValidBloodPressure()
        hold.resolve(110, 70, now)
        hold.clear()
        assertEquals(BloodPressure(0, 0), hold.resolve(0, 0, now + 1_000L))
    }

    private fun sample(
        heartRate: Int,
        systolic: Int,
        diastolic: Int,
        timestamp: String,
    ) = HBandTelemetry(
        deviceId = "TEST-WATCH",
        deviceModel = "VE30",
        timestamp = timestamp,
        heartRate = heartRate,
        bloodPressure = BloodPressure(systolic, diastolic),
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = 0,
        calories = 0f,
        distanceMeters = 0f,
        hrvScore = 0,
        sleepSummary = SleepSummary(),
        isRealSensorData = true,
    )
}
