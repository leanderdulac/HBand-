package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BpDetectRetryTest {
    @Test fun success_clears_failures_and_allows_the_next_cycle() {
        val retry = BpDetectRetry()
        retry.onAck(success = false, nowElapsedMs = 1_000L)
        assertFalse(retry.shouldAttempt(1_000L))
        retry.onAck(success = true, nowElapsedMs = 2_000L)
        assertEquals(0, retry.consecutiveFailures)
        assertTrue(retry.shouldAttempt(2_000L))
    }

    @Test fun failed_ack_skips_the_current_window_and_doubles_backoff() {
        val retry = BpDetectRetry(initialBackoffMs = 30_000L, maxBackoffMs = 120_000L)
        retry.onAck(success = false, nowElapsedMs = 0L)
        assertEquals(1, retry.consecutiveFailures)
        assertEquals(30_000L, retry.waitMs(0L))
        assertFalse(retry.shouldAttempt(29_999L))
        assertTrue(retry.shouldAttempt(30_000L))

        retry.onAck(success = false, nowElapsedMs = 30_000L)
        assertEquals(2, retry.consecutiveFailures)
        assertEquals(60_000L, retry.waitMs(30_000L))

        retry.onAck(success = false, nowElapsedMs = 90_000L)
        assertEquals(120_000L, retry.waitMs(90_000L))
    }

    @Test fun reset_allows_an_immediate_retry_on_a_new_session() {
        val retry = BpDetectRetry()
        retry.onAck(success = false, nowElapsedMs = 5_000L)
        retry.reset()
        assertEquals(0, retry.consecutiveFailures)
        assertTrue(retry.shouldAttempt(5_000L))
    }
}
