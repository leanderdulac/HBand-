package com.example.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HrAlertGateTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("gate", 0).also { it.edit().clear().commit() }

    @Test fun one_reminder_after_thirty_minutes_then_silent() {
        val gate = HrAlertGate(prefs())
        val t0 = 1_000_000L
        assertEquals(HrAlertGate.Kind.HIGH, gate.evaluate(110, 100, 50, t0))
        assertNull(gate.evaluate(110, 100, 50, t0 + 29 * 60_000))
        assertEquals(HrAlertGate.Kind.HIGH, gate.evaluate(110, 100, 50, t0 + 30 * 60_000))
        assertNull(gate.evaluate(110, 100, 50, t0 + 120 * 60_000))
    }

    @Test fun state_survives_a_new_gate_instance() {
        val p = prefs()
        assertEquals(HrAlertGate.Kind.HIGH, HrAlertGate(p).evaluate(110, 100, 50, 1L))
        assertNull(HrAlertGate(p).evaluate(111, 100, 50, 2L))
    }
}
