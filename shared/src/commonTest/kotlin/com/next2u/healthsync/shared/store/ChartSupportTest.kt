package com.next2u.healthsync.shared.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartSupportTest {
    private val now = 1_000_000_000_000L

    @Test
    fun sparkline_keeps_only_real_values_in_window() {
        val metrics = listOf(
            metric(id = 1, ts = now - 8 * DAY, hr = 70),
            metric(id = 2, ts = now - 2 * DAY, hr = 0),
            metric(id = 3, ts = now - DAY, hr = 80),
            metric(id = 4, ts = now - 1_000, hr = 76),
        )
        val values = sparklineValues(metrics, now) { it.heartRateValue() }
        assertEquals(listOf(80f, 76f), values)
    }

    @Test
    fun trend_points_follow_period_and_ignore_zeros() {
        val metrics = listOf(
            metric(id = 1, ts = now - 40 * DAY, hr = 90),
            metric(id = 2, ts = now - 3 * DAY, hr = 72),
            metric(id = 3, ts = now - 1_000, hr = 0),
        )
        val week = trendPointsInPeriod(metrics, now, ChartPeriod.D7) { it.heartRateValue() }
        assertEquals(1, week.size)
        assertEquals(72f, week.single().value)
        val month = trendPointsInPeriod(metrics, now, ChartPeriod.D30) { it.heartRateValue() }
        assertEquals(1, month.size)
        assertEquals("7 dias", ChartPeriod.D7.label)
        assertEquals("30 dias", ChartPeriod.D30.label)
        assertEquals("3 meses", ChartPeriod.M3.label)
        assertTrue(ChartPeriod.M3.days == 90)
    }

    private fun metric(id: Long, ts: Long, hr: Int) = SensorMetric(
        id = id,
        deviceId = "AA",
        timestamp = "t",
        timestampMillis = ts,
        heartRate = hr,
    )

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
    }
}
