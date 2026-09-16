package com.example.ui.components

import com.example.data.local.HBandSensorMetricEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalysisHonestyTest {

    @Test
    fun `no sleep metrics yields empty waiting state instead of demo 7h`() {
        val metrics = listOf(
            HBandSensorMetricEntity(
                deviceId = "AA:BB",
                timestamp = "2026-09-10T08:00:00Z",
                timestampMillis = 1_725_000_000_000L,
                heartRate = 70,
                systolicBp = 0,
                diastolicBp = 0,
                spO2 = 0,
                temperatureCelsius = 0f,
                steps = 100,
                calories = 0f,
                distanceMeters = 0f,
                hrvScore = 0,
                deepSleepMinutes = 0,
                lightSleepMinutes = 0,
                awakeMinutes = 0,
            )
        )
        assertNull(analyzeSleepMetrics(metrics, now = 1_725_000_000_000L))
    }

    @Test
    fun `real sleep is shown without rem invention or floor padding`() {
        val metrics = listOf(
            HBandSensorMetricEntity(
                deviceId = "AA:BB",
                timestamp = "2026-09-10T08:00:00Z",
                timestampMillis = 1_725_000_000_000L,
                heartRate = 0,
                systolicBp = 0,
                diastolicBp = 0,
                spO2 = 0,
                temperatureCelsius = 0f,
                steps = 0,
                calories = 0f,
                distanceMeters = 0f,
                hrvScore = 0,
                deepSleepMinutes = 80,
                lightSleepMinutes = 200,
                awakeMinutes = 15,
            )
        )
        val summary = analyzeSleepMetrics(metrics, now = 1_725_000_000_000L)
        assertEquals(80, summary!!.deepSleepMins)
        assertEquals(200, summary.lightSleepMins)
        assertEquals(15, summary.awakeMins)
        assertEquals(0, summary.remSleepMins)
        assertEquals(295, summary.totalSleepMinutes)
        assertTrue(summary.weeklyScore in 0..100)
    }
}
