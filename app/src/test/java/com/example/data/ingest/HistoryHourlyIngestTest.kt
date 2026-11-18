package com.example.data.ingest

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class HistoryHourlyIngestTest {
    @Test fun later_hr_only_sample_keeps_origin_bp_and_steps_from_the_same_hour() {
        val origin = sample("2026-09-30T12:05:00Z", hr = 72, systolic = 128, diastolic = 84, steps = 410)
        val hrvOnly = sample("2026-09-30T12:40:00Z", hr = 88)
        val merged = HistoryHourlyIngest.mergeHourly(hrvOnly, listOf(origin, hrvOnly))
        assertEquals(88, merged.heartRate)
        assertEquals("2026-09-30T12:40:00Z", merged.timestamp)
        assertEquals(128, merged.bloodPressure.systolic)
        assertEquals(84, merged.bloodPressure.diastolic)
        assertEquals(410, merged.steps)
        val json = JSONObject(IngestPayloadMapper.telemetryToJson(merged, "TEST-PATIENT"))
        assertEquals(128, json.getJSONObject("blood_pressure").getInt("systolic"))
        assertEquals(410, json.getInt("steps"))
    }

    @Test fun winner_keeps_its_own_bp_and_steps_instead_of_older_hour_values() {
        val older = sample("2026-09-30T12:05:00Z", hr = 70, systolic = 110, diastolic = 70, steps = 100)
        val winner = sample("2026-09-30T12:50:00Z", hr = 90, systolic = 132, diastolic = 86, steps = 500)
        val merged = HistoryHourlyIngest.mergeHourly(winner, listOf(older, winner))
        assertEquals(132, merged.bloodPressure.systolic)
        assertEquals(500, merged.steps)
    }

    @Test fun zeros_are_not_replaced_with_demo_bp_or_invented_steps() {
        val winner = sample("2026-09-30T12:00:00Z", hr = 80)
        val empty = sample("2026-09-30T12:10:00Z", hr = 0)
        val merged = HistoryHourlyIngest.mergeHourly(winner, listOf(winner, empty))
        assertEquals(0, merged.bloodPressure.systolic)
        assertEquals(0, merged.steps)
        assertFalse(JSONObject(IngestPayloadMapper.telemetryToJson(merged, "TEST-PATIENT")).has("blood_pressure"))
    }

    @Test fun origin_without_hr_still_supplies_bp_to_an_ingestible_winner() {
        val originNoHr = sample("2026-09-30T12:15:00Z", hr = 0, systolic = 121, diastolic = 79, steps = 220)
        val winner = sample("2026-09-30T12:20:00Z", hr = 74)
        val merged = HistoryHourlyIngest.mergeHourly(winner, listOf(originNoHr, winner))
        assertEquals(74, merged.heartRate)
        assertEquals(121, merged.bloodPressure.systolic)
        assertEquals(220, merged.steps)
        assertTrue(IngestPayloadMapper.isIngestible(merged))
    }

    @Test fun later_hr_only_winner_is_treated_as_missing_bp_for_a_follow_up_queue() {
        val stored = sample("2026-09-30T12:00:00Z", hr = 90)
        val origin = sample("2026-09-30T12:08:00Z", hr = 70, systolic = 119, diastolic = 77, steps = 80)
        val merged = HistoryHourlyIngest.mergeHourly(origin, listOf(stored, origin))
        assertTrue(HistoryHourlyIngest.bringsMissingBpOrSteps(stored, merged))
        assertFalse(HistoryHourlyIngest.bringsMissingBpOrSteps(merged, merged))
    }

    private fun sample(
        timestamp: String,
        hr: Int,
        systolic: Int = 0,
        diastolic: Int = 0,
        steps: Int = 0,
    ) = HBandTelemetry(
        deviceId = "TEST-WATCH",
        deviceModel = "VE30",
        timestamp = timestamp,
        heartRate = hr,
        bloodPressure = BloodPressure(systolic, diastolic),
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = steps,
        calories = if (steps > 0) 2.5f else 0f,
        distanceMeters = if (steps > 0) 25f else 0f,
        hrvScore = 0,
        sleepSummary = SleepSummary(),
        isRealSensorData = true,
    )
}
