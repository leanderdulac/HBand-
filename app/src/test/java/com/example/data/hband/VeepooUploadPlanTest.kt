package com.example.data.hband

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooUploadPlanTest {

    private val base = 1_700_000_000_000L

    @Test
    fun `five minute slots stay separate and a closer pair keeps the richer one`() {
        val first = sample(base, heartRate = 70)
        val richer = sample(base + 60_000L, heartRate = 68, spo2 = 97, systolic = 120, diastolic = 80)
        val later = sample(base + VeepooUploadPlan.BUCKET_MS, heartRate = 75)
        val picked = VeepooUploadPlan.forIngest(listOf(first, richer, later))
        assertEquals(2, picked.size)
        assertEquals(97, picked[0].spO2)
        assertEquals(75, picked[1].heartRate)
    }

    @Test
    fun `sleep without heart rate is not uploaded`() {
        val sleep = sample(base, heartRate = 0).copy(
            sleepSummary = SleepSummary(deepSleepMinutes = 80, lightSleepMinutes = 200, awakeMinutes = 10),
        )
        assertTrue(VeepooUploadPlan.forIngest(listOf(sleep)).isEmpty())
    }

    @Test
    fun `cap keeps the newest day of five minute samples`() {
        val samples = (0 until 400).map { index ->
            sample(base + index * VeepooUploadPlan.BUCKET_MS, heartRate = 60 + (index % 20))
        }
        val picked = VeepooUploadPlan.forIngest(samples)
        assertEquals(VeepooUploadPlan.MAX_SAMPLES, picked.size)
        assertEquals(samples.last().timestamp, picked.last().timestamp)
        assertFalse(picked.any { it.timestamp == samples.first().timestamp })
    }
}

class VeepooBandPolicyTest {
    @Test
    fun `heart auto turns on only when the user wants it and the band is not already on`() {
        assertTrue(VeepooBandPolicy.shouldEnableHeartAuto(userWants = true, bandSwitchOpen = false))
        assertTrue(VeepooBandPolicy.shouldEnableHeartAuto(userWants = true, bandSwitchOpen = null))
        assertFalse(VeepooBandPolicy.shouldEnableHeartAuto(userWants = true, bandSwitchOpen = true))
        assertFalse(VeepooBandPolicy.shouldEnableHeartAuto(userWants = false, bandSwitchOpen = false))
    }

    @Test
    fun `night spo2 turns on only after a read that says it is closed`() {
        assertTrue(VeepooBandPolicy.shouldEnableNightSpo2(userWants = true, bandIsOpen = false))
        assertFalse(VeepooBandPolicy.shouldEnableNightSpo2(userWants = true, bandIsOpen = null))
        assertFalse(VeepooBandPolicy.shouldEnableNightSpo2(userWants = true, bandIsOpen = true))
        assertFalse(VeepooBandPolicy.shouldEnableNightSpo2(userWants = false, bandIsOpen = false))
    }
}

private fun sample(
    epochMs: Long,
    heartRate: Int,
    spo2: Int = 0,
    systolic: Int = 0,
    diastolic: Int = 0,
) = HBandTelemetry(
    deviceId = "84:03:A6:48:DD:ED",
    deviceModel = "VE30",
    timestamp = VeepooHistoryMapper.isoUtc(epochMs),
    heartRate = heartRate,
    bloodPressure = BloodPressure(systolic, diastolic),
    spO2 = spo2,
    temperatureCelsius = 0f,
    steps = 0,
    calories = 0f,
    distanceMeters = 0f,
    hrvScore = 0,
    sleepSummary = SleepSummary(0, 0, 0),
    isRealSensorData = true,
)
