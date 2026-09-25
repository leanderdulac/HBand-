package com.example.ui.components

import com.example.data.local.HBandSensorMetricEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalysisHonestyTest {

    private val now = 1_725_000_000_000L
    private fun saved(at: Long = now) = HBandSensorMetricEntity(
        deviceId = "sleep-fixture", timestamp = "fixture", timestampMillis = at,
        heartRate = 0, systolicBp = 0, diastolicBp = 0, spO2 = 0, temperatureCelsius = 0f,
        steps = 0, calories = 0f, distanceMeters = 0f, hrvScore = 0,
        deepSleepMinutes = 80, lightSleepMinutes = 200, awakeMinutes = 15,
    )

    @Test fun `stale future and undated sleep do not populate recent sleep`() {
        assertNull(analyzeSleepMetrics(listOf(saved(now + 1), saved(0), saved(now - 8 * 86_400_000L)), now))
    }

    @Test fun `latest snapshot is not summed with duplicates or called a weekly average`() {
        val summary = analyzeSleepMetrics(listOf(saved(now - 1000).copy(deepSleepMinutes = 600), saved()), now)!!
        assertEquals(now, summary.timestampMillis)
        assertEquals(280L, summary.totalSleepMinutes)
    }

    @Test fun `unavailable phases remain absent and large durations do not overflow`() {
        val partial = analyzeSleepMetrics(listOf(saved().copy(deepSleepMinutes = 0, awakeMinutes = 0)), now)!!
        assertNull(partial.deepSleepMins)
        assertNull(partial.awakeMins)
        assertEquals(200L, partial.totalSleepMinutes)
        val large = analyzeSleepMetrics(listOf(saved().copy(deepSleepMinutes = Int.MAX_VALUE, lightSleepMinutes = Int.MAX_VALUE)), now)!!
        assertEquals(Int.MAX_VALUE.toLong() * 2, large.totalSleepMinutes)
    }

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
        assertNull(summary.remSleepMins)
        assertEquals(280L, summary.totalSleepMinutes)
    }
}
