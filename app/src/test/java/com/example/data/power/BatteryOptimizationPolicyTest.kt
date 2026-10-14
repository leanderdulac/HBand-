package com.example.data.power

import com.example.ui.components.batteryGuidanceSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationPolicyTest {
    @Test fun promptsOnlyWhenPairedOptimizedAndNotPrompted() {
        assertTrue(BatteryOptimizationPolicy.shouldPromptOnce(false, false, true))
        assertFalse(BatteryOptimizationPolicy.shouldPromptOnce(true, false, true))
        assertFalse(BatteryOptimizationPolicy.shouldPromptOnce(false, true, true))
        assertFalse(BatteryOptimizationPolicy.shouldPromptOnce(false, false, false))
    }

    @Test fun samsungDetectedCaseInsensitive() {
        assertEquals(BatteryGuidance.SAMSUNG, BatteryOptimizationPolicy.guidanceFor("samsung"))
        assertEquals(BatteryGuidance.SAMSUNG, BatteryOptimizationPolicy.guidanceFor(" Samsung "))
        assertEquals(BatteryGuidance.GENERIC, BatteryOptimizationPolicy.guidanceFor("Multilaser"))
        assertEquals(BatteryGuidance.GENERIC, BatteryOptimizationPolicy.guidanceFor(null))
    }

    @Test fun samsungStepsMentionNeverSleepingAndUnrestricted() {
        val text = batteryGuidanceSteps(BatteryGuidance.SAMSUNG).joinToString()
        assertTrue(text.contains("Apps nunca suspensos"))
        assertTrue(text.contains("Sem restrições"))
    }

    @Test fun statusTextReflectsExemption() {
        assertTrue(BatteryOptimizationPolicy.statusText(true).startsWith("Liberado"))
        assertTrue(BatteryOptimizationPolicy.statusText(false).startsWith("Atenção"))
    }
}
