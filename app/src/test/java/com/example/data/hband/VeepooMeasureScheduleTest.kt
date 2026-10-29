package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooMeasureScheduleTest {

    @Test
    fun `interval is half an hour`() {
        assertEquals(30L * 60L * 1000L, VeepooMeasureSchedule.INTERVAL_MS)
    }

    @Test
    fun `finished round waits thirty minutes before the next one`() {
        val now = 1_700_000_000_000L
        val resting = VeepooMeasureSchedule.resting(now)
        assertEquals(VeepooMeasureSchedule.Phase.RESTING, resting.phase)
        assertEquals(VeepooMeasureSchedule.INTERVAL_MS, VeepooMeasureSchedule.remainingMs(resting, now))
        assertEquals(0L, VeepooMeasureSchedule.remainingMs(resting, now + VeepooMeasureSchedule.INTERVAL_MS))
        assertEquals(0L, VeepooMeasureSchedule.remainingMs(resting, now + VeepooMeasureSchedule.INTERVAL_MS + 5_000L))
    }

    @Test
    fun `connect and manual start do not wait`() {
        val measuring = VeepooMeasureSchedule.measuring()
        assertEquals(VeepooMeasureSchedule.Phase.MEASURING, measuring.phase)
        assertEquals(0L, measuring.nextRoundAtMs)
        assertEquals(0L, VeepooMeasureSchedule.remainingMs(measuring, 50L))
        assertEquals(0L, VeepooMeasureSchedule.remainingMs(VeepooMeasureSchedule.idle(), 50L))
    }

    @Test
    fun `exhausted reconnect wakes again in fifteen minutes instead of giving up`() {
        val now = 5_000L
        val overdue = now - 60_000L
        val soon = VeepooMeasureSchedule.nextWakeAtMs(now, overdue, fastReconnectExhausted = true)
        assertEquals(now + VeepooMeasureSchedule.SLOW_RECONNECT_MS, soon)
        assertEquals(15L * 60L * 1000L, VeepooMeasureSchedule.SLOW_RECONNECT_MS)
        val laterRound = now + 10L * 60L * 1000L
        assertEquals(
            laterRound,
            VeepooMeasureSchedule.nextWakeAtMs(now, laterRound, fastReconnectExhausted = true),
        )
    }

    @Test
    fun `a round is due only while the wake is armed and the time has passed`() {
        val now = 90_000L
        assertFalse(VeepooMeasureSchedule.roundIsDue(now, now - 1, armed = false))
        assertTrue(VeepooMeasureSchedule.roundIsDue(now, now - 1, armed = true))
        assertTrue(VeepooMeasureSchedule.roundIsDue(now, 0L, armed = true))
        assertFalse(VeepooMeasureSchedule.roundIsDue(now, now + 1, armed = true))
    }

    @Test
    fun `gatt silence during rest keeps the session and the timer`() {
        val now = 10_000L
        val resting = VeepooMeasureSchedule.resting(now)
        assertTrue(VeepooMeasureSchedule.keepSessionOnGattDisconnect(resting))
        assertFalse(VeepooMeasureSchedule.keepSessionOnGattDisconnect(VeepooMeasureSchedule.measuring()))
        assertFalse(VeepooMeasureSchedule.keepSessionOnGattDisconnect(VeepooMeasureSchedule.idle()))
        assertEquals(45_000L, VeepooMeasureSchedule.REST_LINK_CONFIRM_MS)
    }
}
