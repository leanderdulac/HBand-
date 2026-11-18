package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Test

class VeepooWriteBackoffTest {
    @Test
    fun success_resets_failures() {
        val gate = VeepooWriteBackoff(abortAfterFailures = 2)
        assertEquals(VeepooWriteBackoff.Decision.BACKOFF, gate.onWriteResult(-1))
        assertEquals(VeepooWriteBackoff.Decision.CONTINUE, gate.onWriteResult(0))
        assertEquals(0, gate.failures)
    }

    @Test
    fun second_failure_aborts_instead_of_looping() {
        val gate = VeepooWriteBackoff(abortAfterFailures = 2)
        assertEquals(VeepooWriteBackoff.Decision.BACKOFF, gate.onWriteResult(-1))
        assertEquals(VeepooWriteBackoff.Decision.ABORT_UNSUPPORTED, gate.onWriteResult(-1))
        assertEquals(2, gate.failures)
    }
}
