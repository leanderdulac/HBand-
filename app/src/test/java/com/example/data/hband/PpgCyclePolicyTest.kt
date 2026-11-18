package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PpgCyclePolicyTest {
    @Test fun successful_cycle_rests_five_minutes() {
        assertEquals(5 * 60_000L, PpgCyclePolicy.idleAfter(0))
        assertEquals(5 * 60_000L, PpgCyclePolicy.restAfterCycle(0, 0, failed = false))
    }

    @Test fun empty_cycles_double_up_to_thirty_minutes() {
        assertEquals(2 * 60_000L, PpgCyclePolicy.idleAfter(1))
        assertEquals(4 * 60_000L, PpgCyclePolicy.idleAfter(2))
        assertEquals(8 * 60_000L, PpgCyclePolicy.idleAfter(3))
        assertEquals(30 * 60_000L, PpgCyclePolicy.idleAfter(5))
        assertEquals(30 * 60_000L, PpgCyclePolicy.idleAfter(99))
    }

    @Test fun failure_backoff_starts_at_30s_and_caps_at_10_min() {
        assertEquals(0L, PpgCyclePolicy.failureBackoff(0))
        assertEquals(30_000L, PpgCyclePolicy.failureBackoff(1))
        assertEquals(60_000L, PpgCyclePolicy.failureBackoff(2))
        assertEquals(120_000L, PpgCyclePolicy.failureBackoff(3))
        assertEquals(10 * 60_000L, PpgCyclePolicy.failureBackoff(9))
        assertEquals(10 * 60_000L, PpgCyclePolicy.failureBackoff(99))
    }

    @Test fun failed_cycle_uses_the_longer_of_idle_and_backoff() {
        // 1º vazio = 2 min, 1ª falha = 30 s → 2 min
        assertEquals(2 * 60_000L, PpgCyclePolicy.restAfterCycle(1, 1, failed = true))
        // ciclo com leitura (5 min) e várias falhas (10 min) → 10 min
        assertEquals(10 * 60_000L, PpgCyclePolicy.restAfterCycle(0, 9, failed = true))
    }

    @Test fun blood_is_due_only_every_30_minutes_after_success() {
        val now = 1_000_000L
        assertFalse(PpgCyclePolicy.bloodIsDue(unsupported = true, lastSuccessAtElapsedMs = null, nowElapsedMs = now))
        assertTrue(PpgCyclePolicy.bloodIsDue(unsupported = false, lastSuccessAtElapsedMs = null, nowElapsedMs = now))
        assertFalse(
            PpgCyclePolicy.bloodIsDue(false, lastSuccessAtElapsedMs = now - 29 * 60_000L, nowElapsedMs = now),
        )
        assertTrue(
            PpgCyclePolicy.bloodIsDue(false, lastSuccessAtElapsedMs = now - 30 * 60_000L, nowElapsedMs = now),
        )
    }

    @Test fun single_flight_and_rest_window() {
        val now = 50_000L
        assertFalse(PpgCyclePolicy.shouldStartCycle(false, false, now, now, force = true))
        assertFalse(PpgCyclePolicy.shouldStartCycle(true, true, now, now, force = true))
        assertFalse(PpgCyclePolicy.shouldStartCycle(true, false, now, now + 1_000L, force = false))
        assertTrue(PpgCyclePolicy.shouldStartCycle(true, false, now, now + 1_000L, force = true))
        assertTrue(PpgCyclePolicy.shouldStartCycle(true, false, now, now, force = false))
    }

    @Test fun intra_cycle_gap_stays_short_but_must_not_restart_the_cycle() {
        assertEquals(400L, PpgCyclePolicy.STAGE_GAP_MS)
        assertTrue(PpgCyclePolicy.CYCLE_IDLE_MS > PpgCyclePolicy.STAGE_GAP_MS)
    }
}
