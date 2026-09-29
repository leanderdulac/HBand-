package com.example.ui.components

import com.example.data.local.HBandSensorMetricEntity
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class PatientHistorySummaryTest {
    private val zone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val now = time(2026, 9, 15, 12)

    @Test fun empty_week_never_creates_values() {
        val week = buildSavedWeek(emptyList(), now, zone)
        assertEquals(7, week.size)
        week.forEach {
            assertEquals(0, it.recordCount)
            assertNull(it.averageHeartRate)
            assertNull(it.highestSteps)
            assertNull(it.highestCalories)
        }
    }

    @Test fun today_does_not_fall_back_to_yesterday_or_future_records() {
        val week = buildSavedWeek(listOf(
            metric(time(2026, 9, 14, 12)), metric(now + 60_000), metric(0),
            metric(time(2026, 9, 1, 12)),
        ), now, zone)
        assertEquals(0, week.last().recordCount)
        assertEquals(1, week[5].recordCount)
        assertEquals(1, week.sumOf { it.recordCount })
    }

    @Test fun counters_are_not_added_and_missing_heart_rate_is_not_averaged_as_zero() {
        val day = buildSavedWeek(listOf(
            metric(now).copy(heartRate = 60, steps = 200, calories = 12f),
            metric(now - 1000).copy(heartRate = 80, steps = 500, calories = 25f),
            metric(now - 2000).copy(heartRate = 0, steps = 0, calories = Float.NaN),
        ), now, zone).last()
        assertEquals(70, day.averageHeartRate)
        assertEquals(500, day.highestSteps)
        assertEquals(25, day.highestCalories)
    }

    @Test fun zero_sentinels_are_unavailable_not_patient_measurements() {
        val empty = metric(now).copy(heartRate = 0)
        val day = buildSavedWeek(listOf(empty), now, zone).last()
        assertEquals(1, day.recordCount)
        assertNull(day.averageHeartRate)
        assertNull(day.highestSteps)
        assertNull(day.highestCalories)
        ChartMetricType.entries.forEach { assertNull(savedMetricValue(empty, it)) }
    }

    @Test fun local_day_boundary_uses_the_requested_timezone() {
        val midnight = time(2026, 9, 15, 0)
        val week = buildSavedWeek(listOf(metric(midnight - 1), metric(midnight)), now, zone)
        assertEquals(1, week[5].recordCount)
        assertEquals(1, week[6].recordCount)
    }

    @Test fun daylight_saving_transition_keeps_seven_distinct_calendar_days() {
        val dstZone = TimeZone.getTimeZone("America/New_York")
        val dstNow = time(2026, 3, 10, 0, dstZone)
        val days = buildSavedWeek(emptyList(), dstNow, dstZone)
        assertEquals(7, days.map { it.dateLabel }.distinct().size)
        assertTrue(days.first().dateLabel.endsWith("04/03/2026"))
        assertTrue(days.last().dateLabel.endsWith("10/03/2026"))
    }

    @Test fun pressure_requires_both_values_and_temperature_rejects_nonfinite_values() {
        assertNull(savedMetricValue(metric(now).copy(systolicBp = 120), ChartMetricType.BLOOD_PRESSURE))
        assertEquals("120 / 80 mmHg", savedMetricValue(
            metric(now).copy(systolicBp = 120, diastolicBp = 80), ChartMetricType.BLOOD_PRESSURE))
        assertNull(savedMetricValue(metric(now).copy(temperatureCelsius = Float.POSITIVE_INFINITY), ChartMetricType.TEMPERATURE))
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, tz: TimeZone = zone): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun metric(at: Long) = HBandSensorMetricEntity(
        deviceId = "test-device", timestamp = "test", timestampMillis = at,
        heartRate = 60, systolicBp = 0, diastolicBp = 0, spO2 = 0,
        temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
        hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
    )
}
