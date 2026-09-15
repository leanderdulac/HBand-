package com.example.data.hband

import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooSessionGateTest {

    private fun telemetry(
        mac: String = "C4:E3:42:AA:30:A4",
        heartRate: Int = 80,
        isReal: Boolean = true,
    ) = HBandTelemetry(
        deviceId = mac,
        deviceModel = "VE30",
        timestamp = "2026-09-15T01:00:00Z",
        heartRate = heartRate,
        bloodPressure = BloodPressure(0, 0),
        spO2 = 0,
        temperatureCelsius = 0f,
        steps = 0,
        calories = 0f,
        distanceMeters = 0f,
        hrvScore = 0,
        sleepSummary = SleepSummary(0, 0, 0),
        isRealSensorData = isReal,
    )

    @Test
    fun `hardwareConnected is enough even without telemetry`() {
        assertTrue(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = true,
                connectedMac = "C4:E3:42:AA:30:A4",
                telemetry = null,
            ),
        )
    }

    @Test
    fun `live HeartData with matching MAC enables actions when GATT latch is false`() {
        assertTrue(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = false,
                connectedMac = "C4:E3:42:AA:30:A4",
                telemetry = telemetry(heartRate = 80),
            ),
        )
    }

    @Test
    fun `placeholder or unmatched MAC does not enable P1 actions`() {
        assertFalse(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = false,
                connectedMac = "C4:E3:42:AA:30:A4",
                telemetry = telemetry(mac = "00:11:22:33:44:55", heartRate = 80),
            ),
        )
        assertFalse(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = false,
                connectedMac = "",
                telemetry = telemetry(heartRate = 80),
            ),
        )
        assertFalse(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = false,
                connectedMac = "C4:E3:42:AA:30:A4",
                telemetry = telemetry(heartRate = 80, isReal = false),
            ),
        )
        assertFalse(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = false,
                connectedMac = "C4:E3:42:AA:30:A4",
                telemetry = telemetry(heartRate = 0),
            ),
        )
    }

    @Test
    fun `live SpO2 with matching MAC enables actions without heart rate`() {
        assertTrue(
            VeepooSessionGate.actionsEnabled(
                hardwareConnected = false,
                connectedMac = "C4:E3:42:AA:30:A4",
                telemetry = telemetry(heartRate = 0).copy(spO2 = 97),
            ),
        )
    }

    @Test
    fun `disconnected hint is reconnect until live telemetry enables actions`() {
        assertEquals(
            VeepooSessionGate.RECONNECT_HINT,
            VeepooSessionGate.hintWhenDisconnected(actionsEnabled = false),
        )
        assertEquals(
            VeepooSessionGate.LIVE_TELEMETRY_HINT,
            VeepooSessionGate.hintWhenDisconnected(actionsEnabled = true),
        )
    }
}
