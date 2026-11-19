package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchBatteryPolicyTest {
    private fun slot(type: String, open: Boolean, interval: Int = 10, step: Int = 5, modify: Boolean = true) =
        WatchBatteryPolicy.AutoSlot(type, open, interval, step, modify)

    @Test fun heartRateAutoOnEvery30Min() {
        val d = WatchBatteryPolicy.desired(slot("PULSE_RATE", open = false, interval = 5), heartRateUserOff = false)!!
        assertTrue(d.open)
        assertEquals(30, d.intervalMin)
    }

    @Test fun heartRateRespectsUserOff() {
        val d = WatchBatteryPolicy.desired(slot("PULSE_RATE", open = true, interval = 30), heartRateUserOff = true)!!
        assertFalse(d.open)
    }

    @Test fun compliantSlotIsNotRewritten() {
        assertNull(WatchBatteryPolicy.desired(slot("PULSE_RATE", open = true, interval = 30), false))
        assertNull(WatchBatteryPolicy.desired(slot("BLOOD_OXYGEN", open = false), false))
        assertNull(WatchBatteryPolicy.desired(slot("PULSE_RATE", open = true, interval = 60), false))
    }

    @Test fun opticalExtrasAreTurnedOff() {
        for (t in listOf("BLOOD_OXYGEN", "HRV", "STRESS", "BODY_TEMPERATURE", "BLOOD_GLUCOSE", "BLOOD_COMPOSITION", "LORENZ")) {
            assertFalse(t, WatchBatteryPolicy.desired(slot(t, open = true), false)!!.open)
        }
    }

    @Test fun bloodPressureHourlyOrOff() {
        val hourly = WatchBatteryPolicy.desired(slot("BLOOD_PRESSURE", open = true, interval = 30), false)!!
        assertTrue(hourly.open)
        assertEquals(60, hourly.intervalMin)
        val fixed = WatchBatteryPolicy.desired(slot("BLOOD_PRESSURE", open = true, interval = 30, modify = false), false)!!
        assertFalse(fixed.open)
    }

    @Test fun intervalRoundsUpToFirmwareStep() {
        assertEquals(35, WatchBatteryPolicy.intervalFor(slot("PULSE_RATE", true, interval = 10, step = 7), 30))
        assertEquals(10, WatchBatteryPolicy.intervalFor(slot("PULSE_RATE", true, interval = 10, modify = false), 30))
    }

    @Test fun manualMeasureOnlyForegroundAndRateLimited() {
        assertFalse(WatchBatteryPolicy.manualAllowed(null, 0L, foreground = false))
        assertTrue(WatchBatteryPolicy.manualAllowed(null, 0L, foreground = true))
        assertFalse(WatchBatteryPolicy.manualAllowed(1_000L, 30_000L, foreground = true))
        assertTrue(WatchBatteryPolicy.manualAllowed(1_000L, 61_000L, foreground = true))
    }

    @Test fun noAutomaticLiveLoopOrContinuousTemperature() {
        assertFalse(WatchBatteryPolicy.AUTOMATIC_LIVE_LOOP)
        assertFalse(WatchBatteryPolicy.CONTINUOUS_TEMPERATURE)
        assertTrue(WatchBatteryPolicy.HISTORY_POLL_MS >= 30 * 60_000L)
        assertTrue(WatchBatteryPolicy.RSSI_POLL_MS >= 60_000L)
    }

    @Test fun reconnectBacksOffToFiveMinutesAfterManyFailures() {
        assertEquals(60_000L, WatchBatteryPolicy.reconnectDelayMs(60_000L, 3))
        assertEquals(300_000L, WatchBatteryPolicy.reconnectDelayMs(60_000L, 8))
    }

    @Test fun lockedShortHeartRateIntervalIsTurnedOff() {
        val locked = WatchBatteryPolicy.desired(
            slot("PULSE_RATE", open = true, interval = 5, modify = false),
            heartRateUserOff = false,
        )!!
        assertFalse(locked.open)
        assertNull(
            WatchBatteryPolicy.desired(
                slot("PULSE_RATE", open = false, interval = 5, modify = false),
                false,
            ),
        )
        assertNull(
            WatchBatteryPolicy.desired(
                slot("PULSE_RATE", open = true, interval = 60, modify = false),
                false,
            ),
        )
    }

    @Test fun connectCommandsStopRealtimeSensors() {
        val commands = WatchBatteryPolicy.connectStopCommands()
        assertEquals(4, commands.size)
        assertTrue(commands.all { it.size >= 2 && it[1] == 0.toByte() })
        assertFalse(commands.any { it[1] == 1.toByte() })
        assertTrue(WatchBatteryPolicy.GATT_KEEPALIVE_MS >= 60_000L)
        assertTrue(WatchBatteryPolicy.GATT_KEEPALIVE_MS != 5_000L)
    }

    @Test fun liveSampleOnlyDuringManualCycle() {
        assertFalse(WatchBatteryPolicy.acceptLiveSample(manualCycleActive = false))
        assertTrue(WatchBatteryPolicy.acceptLiveSample(manualCycleActive = true))
    }

    @Test fun continuousCustomSwitchesCloseOnlyWhenOpen() {
        assertTrue(WatchBatteryPolicy.customSwitchShouldClose("PPG", "SUPPORT_OPEN"))
        assertTrue(WatchBatteryPolicy.customSwitchShouldClose("BODY_TEMPERATURE", "SUPPORT_OPEN"))
        assertTrue(WatchBatteryPolicy.customSwitchShouldClose("ECG_ALWAYS", "SUPPORT_OPEN"))
        assertFalse(WatchBatteryPolicy.customSwitchShouldClose("PPG", "SUPPORT_CLOSE"))
        assertFalse(WatchBatteryPolicy.customSwitchShouldClose("PPG", "UNSUPPORT"))
        assertFalse(WatchBatteryPolicy.customSwitchShouldClose("AUTO_HEART", "SUPPORT_OPEN"))
        assertTrue(WatchBatteryPolicy.allDaySpo2ShouldClose("SUPPORT_OPEN"))
        assertFalse(WatchBatteryPolicy.allDaySpo2ShouldClose("SUPPORT_CLOSE"))
    }
}
