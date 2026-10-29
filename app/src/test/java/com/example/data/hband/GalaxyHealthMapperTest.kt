package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyHealthMapperTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `galaxy watch name is not a veepoo band`() {
        assertTrue(GalaxyHealthMapper.isGalaxyWatch("Galaxy Watch8 (4WHN)"))
        assertFalse(GalaxyHealthMapper.isGalaxyWatch("VE30"))
        assertFalse(GalaxyHealthMapper.isGalaxyWatch("Gear S3"))
    }

    @Test
    fun `only samsung health origins count as the watch`() {
        assertTrue(GalaxyHealthMapper.isSamsungHealthOrigin("com.sec.android.app.shealth"))
        assertFalse(GalaxyHealthMapper.isSamsungHealthOrigin("com.google.android.apps.fitness"))
    }

    @Test
    fun `zero and out of range heart rates are not a reading`() {
        assertFalse(GalaxyHealthMapper.acceptHeartRate(0))
        assertFalse(GalaxyHealthMapper.acceptHeartRate(19))
        assertTrue(GalaxyHealthMapper.acceptHeartRate(86))
        assertFalse(GalaxyHealthMapper.acceptHeartRate(251))
        assertNull(
            GalaxyHealthMapper.toTelemetry(
                heartRate = 0,
                measuredAtMs = now,
                deviceId = "40:35:E6:40:C5:D8",
                deviceModel = "Galaxy Watch8 (4WHN)",
            )
        )
    }

    @Test
    fun `fresh samsung sample keeps the watch mac and the sample time`() {
        val measured = now - 5 * 60_000L
        val telemetry = GalaxyHealthMapper.toTelemetry(
            heartRate = 86,
            measuredAtMs = measured,
            deviceId = "40:35:E6:40:C5:D8",
            deviceModel = "Galaxy Watch8 (4WHN)",
            spo2 = 99,
            spo2AtMs = measured,
            systolic = 120,
            diastolic = 80,
            bloodPressureAtMs = measured,
            steps = 1400,
        )
        assertEquals("40:35:E6:40:C5:D8", telemetry!!.deviceId)
        assertEquals(86, telemetry.heartRate)
        assertEquals(99, telemetry.spO2)
        assertEquals(120, telemetry.bloodPressure.systolic)
        assertEquals(80, telemetry.bloodPressure.diastolic)
        assertEquals(1400, telemetry.steps)
        assertEquals(0f, telemetry.calories)
        assertEquals("2023-11-14T22:08:20Z", telemetry.timestamp)
        assertTrue(telemetry.isRealSensorData)
        assertTrue(GalaxyHealthMapper.isFresh(measured, now))
        assertTrue(GalaxyHealthMapper.shouldPublish(measured, lastPublishedMs = 0L, nowMs = now))
        assertFalse(GalaxyHealthMapper.shouldPublish(measured, lastPublishedMs = measured, nowMs = now))
    }

    @Test
    fun `a reading older than fifty minutes still uploads inside twelve hours`() {
        val measured = now - 51 * 60_000L
        assertFalse(GalaxyHealthMapper.isFresh(measured, now))
        assertTrue(GalaxyHealthMapper.shouldPublish(measured, lastPublishedMs = 0L, nowMs = now))
        assertFalse(GalaxyHealthMapper.shouldPublish(measured, lastPublishedMs = measured, nowMs = now))
        assertEquals(51L, GalaxyHealthMapper.ageMinutes(measured, now))
    }

    @Test
    fun `heart rate older than twelve hours stays out`() {
        val measured = now - 13 * 60 * 60 * 1000L
        assertFalse(GalaxyHealthMapper.shouldPublish(measured, lastPublishedMs = 0L, nowMs = now))
    }

    @Test
    fun `pending samples keep one unread heart rate per five minutes`() {
        val samples = (1..6).map { index ->
            GalaxyHeartSample(
                heartRate = 70 + index,
                measuredAtMs = now - index * GalaxyHealthMapper.SAMPLE_BUCKET_MS,
            )
        }
        val sameBucket = GalaxyHeartSample(99, samples.first().measuredAtMs - 1_000L)
        val pending = GalaxyHealthMapper.pendingSamples(samples + sameBucket, emptySet(), now)
        assertEquals(6, pending.size)
        assertEquals(samples.first().heartRate, pending.last().heartRate)
        val sent = setOf(samples.last().measuredAtMs)
        val rest = GalaxyHealthMapper.pendingSamples(samples, sent, now)
        assertEquals(5, rest.size)
        assertFalse(rest.any { it.measuredAtMs in sent })
    }

    @Test
    fun `old spo2 and incomplete blood pressure stay off the payload`() {
        val measured = now - 60_000L
        val telemetry = GalaxyHealthMapper.toTelemetry(
            heartRate = 72,
            measuredAtMs = measured,
            deviceId = "40:35:E6:40:C5:D8",
            deviceModel = "Galaxy Watch8 (4WHN)",
            spo2 = 97,
            spo2AtMs = measured - 7 * 60 * 60 * 1000L,
            systolic = 120,
            diastolic = 0,
            bloodPressureAtMs = measured,
        )
        assertEquals(0, telemetry!!.spO2)
        assertEquals(0, telemetry.bloodPressure.systolic)
        assertEquals(0, telemetry.bloodPressure.diastolic)
    }

    @Test
    fun `percent fraction from health connect becomes a spo2 integer`() {
        assertEquals(98, GalaxyHealthMapper.normalizePercent(0.98))
        assertEquals(99, GalaxyHealthMapper.normalizePercent(99.0))
    }
}
