package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompetingWatchAppTest {
    @Test
    fun running_copy_is_stronger_than_installed() {
        assertEquals(CompetingWatchApp.MSG_RUNNING, CompetingWatchApp.warningText(installed = true, running = true))
        assertEquals(CompetingWatchApp.MSG_INSTALLED, CompetingWatchApp.warningText(installed = true, running = false))
        assertNull(CompetingWatchApp.warningText(installed = false, running = false))
    }

    @Test
    fun copy_has_no_technical_jargon() {
        val combined = CompetingWatchApp.MSG_RUNNING + CompetingWatchApp.MSG_INSTALLED
        for (token in listOf("GATT", "SDK", "BLE", "MAC", "Veepoo", "com.veepoo")) {
            assertEquals("jargon=$token", false, combined.contains(token))
        }
    }
}
