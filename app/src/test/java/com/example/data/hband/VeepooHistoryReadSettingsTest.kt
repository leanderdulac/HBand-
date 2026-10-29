package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooHistoryReadSettingsTest {

    @Test
    fun `origin args match official SDK demo ReadOriginSetting 0 1 false watchDay`() {
        val args = VeepooHistoryReadSettings.originArgs(7)
        assertEquals(0, args.day)
        assertEquals(1, args.position)
        assertFalse(args.onlyReadOneDay)
        assertEquals(7, args.watchday)
        assertTrue(args.position >= 1)
    }

    @Test
    fun `sleep args start at today with firmware capacity as watchday`() {
        val args = VeepooHistoryReadSettings.sleepArgs(7)
        assertEquals(0, args.day)
        assertFalse(args.onlyReadOneDay)
        assertEquals(7, args.watchday)
    }

    @Test
    fun `watchday is clamped to a sane firmware range`() {
        assertEquals(1, VeepooHistoryReadSettings.originArgs(0).watchday)
        assertEquals(14, VeepooHistoryReadSettings.originArgs(99).watchday)
    }
}
