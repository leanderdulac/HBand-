package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WatchClockTest {
    private val t = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16, 22, 5, 9) }.timeInMillis

    @Test fun fieldsCopyPhoneTime() {
        assertEquals(WatchTimeFields(2026, 10, 16, 22, 5, 9, true), WatchClock.fields(t, true))
        assertFalse(WatchClock.fields(t, false).is24h)
    }

    @Test fun labels() {
        assertEquals("Hora do relógio atualizada às 22:05", WatchClock.successMessage(t))
        assertEquals("Última sincronização: 16/10 22:05", WatchClock.lastSyncLabel(t))
        assertEquals("Última sincronização: nunca", WatchClock.lastSyncLabel(null))
    }

    @Test fun photoDialGating() {
        assertFalse(WatchDialCapabilities().supportsPhotoDial)
        assertFalse(WatchDialCapabilities(known = true, watchUiCustomCount = 0).supportsPhotoDial)
        assertTrue(WatchDialCapabilities(known = true, watchUiCustomCount = 1).supportsPhotoDial)
    }
}
