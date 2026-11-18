package com.example.data.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IngestBackoffTest {
    @Test fun noFailuresMeansNoDelay() = assertEquals(0L, IngestBackoff.delayMs(0, null))

    @Test fun growsExponentiallyWithJitterAndCap() {
        val r = Random(42)
        for (n in 1..20) {
            val ceiling = (IngestBackoff.BASE_DELAY_MS shl (n - 1).coerceAtMost(16)).coerceAtMost(IngestBackoff.MAX_DELAY_MS)
            val d = IngestBackoff.delayMs(n, null, r)
            assertTrue("n=$n d=$d", d in ceiling / 2..ceiling)
        }
    }

    @Test fun retryAfterIsAFloor() {
        assertEquals(120_000L, IngestBackoff.delayMs(1, 120_000L, Random(1)))
        assertEquals(IngestBackoff.MAX_RETRY_AFTER_MS, IngestBackoff.delayMs(1, Long.MAX_VALUE / 2, Random(1)))
    }

    @Test fun parsesSecondsAndHttpDate() {
        assertEquals(30_000L, IngestBackoff.parseRetryAfter(" 30 ", 0))
        assertEquals(10_000L, IngestBackoff.parseRetryAfter("Thu, 01 Jan 1970 00:00:10 GMT", 0))
        assertNull(IngestBackoff.parseRetryAfter("soon", 0))
        assertNull(IngestBackoff.parseRetryAfter(null, 0))
        assertNull(IngestBackoff.parseRetryAfter("-5", 0))
    }
}
