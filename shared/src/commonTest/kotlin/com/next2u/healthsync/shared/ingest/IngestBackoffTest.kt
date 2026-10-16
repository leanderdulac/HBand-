package com.next2u.healthsync.shared.ingest

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngestBackoffTest {
    @Test fun noFailureNoDelay() = assertEquals(0L, IngestBackoff.delayMs(0, null))

    @Test fun exponentialWithJitterAndCap() {
        val r = Random(7)
        for (n in 1..20) {
            val ceiling = (IngestBackoff.BASE_DELAY_MS shl (n - 1).coerceAtMost(16)).coerceAtMost(IngestBackoff.MAX_DELAY_MS)
            val d = IngestBackoff.delayMs(n, null, r)
            assertTrue(d in ceiling / 2..ceiling, "n=$n d=$d")
        }
    }

    @Test fun retryAfterFloor() {
        assertEquals(90_000L, IngestBackoff.delayMs(1, 90_000L, Random(1)))
        assertEquals(30_000L, IngestBackoff.parseRetryAfterSeconds("30"))
        assertNull(IngestBackoff.parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(IngestBackoff.parseRetryAfterSeconds("-1"))
    }

    @Test fun windowOpensAndResets() {
        val w = IngestBackoffWindow(Random(3))
        val d = w.recordTransientFailure(1_000L, 60_000L)
        assertTrue(d >= 60_000L)
        assertEquals(d, w.remainingMs(1_000L))
        w.recordSuccess()
        assertEquals(0L, w.remainingMs(1_000L))
    }
}
