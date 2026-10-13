package com.example.ui.components

import com.example.data.hband.DeviceCapabilities
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.ExpectedRangePlaceholder
import com.example.ui.theme.ExpectedRangePlaceholders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalChartLogicTest {
    @Test
    fun expected_ranges_are_unconfigured_placeholders() {
        listOf(
            ExpectedRangePlaceholders.heartRate,
            ExpectedRangePlaceholders.spo2,
            ExpectedRangePlaceholders.systolic,
            ExpectedRangePlaceholders.diastolic,
            ExpectedRangePlaceholders.temperature,
        ).forEach { range ->
            assertFalse(range.isConfigured)
            assertTrue(range.caption.contains("não é limiar clínico"))
        }
    }

    @Test
    fun vital_cards_stay_hidden_until_capability_probe() {
        assertEquals(emptyList<VitalKind>(), visiblePatientVitals(DeviceCapabilities()))
        val probedNone = DeviceCapabilities(probed = true)
        assertEquals(emptyList<VitalKind>(), visiblePatientVitals(probedNone))
        val probedHeart = DeviceCapabilities(probed = true, isSupportHeart = true, isSupportSpo2 = true)
        assertEquals(listOf(VitalKind.HEART, VitalKind.SPO2), visiblePatientVitals(probedHeart))
    }

    @Test
    fun sparkline_and_trend_use_only_real_positive_values() {
        val now = 1_000_000L
        val metrics = listOf(
            metric(heartRate = 0, timestampMillis = now - 1_000),
            metric(heartRate = 72, timestampMillis = now - 2_000),
            metric(heartRate = 80, timestampMillis = now - 10L * 24 * 60 * 60 * 1000),
            metric(heartRate = 68, timestampMillis = now - 3_000),
        )
        assertEquals(
            listOf(68f, 72f),
            sparklineValues(metrics, now) { it.heartRate.takeIf { hr -> hr > 0 }?.toFloat() },
        )
        val points = trendPointsInPeriod(metrics, now, ChartPeriod.D7) {
            it.heartRate.takeIf { hr -> hr > 0 }?.toFloat()
        }
        assertEquals(listOf(68f, 72f), points.map { it.value })
    }

    @Test
    fun status_is_sem_faixa_until_a_placeholder_is_configured() {
        assertEquals(VitalStatusKind.NO_DATA, classifyVitalStatus(null, ExpectedRangePlaceholders.heartRate))
        assertEquals(VitalStatusKind.NO_RANGE, classifyVitalStatus(72f, ExpectedRangePlaceholders.heartRate))
        val configured = ExpectedRangePlaceholder("heart_rate", "Batimentos", "bpm", min = 60f, max = 80f)
        assertEquals(VitalStatusKind.NORMAL, classifyVitalStatus(72f, configured))
        assertEquals(VitalStatusKind.ATTENTION, classifyVitalStatus(90f, configured))
    }

    private fun metric(heartRate: Int, timestampMillis: Long) = HBandSensorMetricEntity(
        deviceId = "test",
        timestamp = "fixture",
        timestampMillis = timestampMillis,
        heartRate = heartRate,
        systolicBp = 0,
        diastolicBp = 0,
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = 0,
        calories = 0f,
        distanceMeters = 0f,
        hrvScore = 0,
        deepSleepMinutes = 0,
        lightSleepMinutes = 0,
        awakeMinutes = 0,
    )
}
