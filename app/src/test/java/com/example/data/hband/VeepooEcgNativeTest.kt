package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooEcgNativeTest {

    @Test
    fun `UnsatisfiedLinkError becomes a non-running UI error instead of crashing`() {
        val state = VeepooEcgNative.uiError(
            UnsatisfiedLinkError("dlopen failed: library \"libnative-lib.so\" not found"),
        )
        assertFalse(state.running)
        assertTrue(state.supported)
        assertEquals(VeepooEcgNative.MISSING_LIB_HINT, state.lastError)
    }

    @Test
    fun `preload never throws when the .so is absent on the test host`() {
        val loaded = VeepooEcgNative.loadOnce()
        assertFalse(loaded)
        assertFalse(VeepooEcgNative.ensureLoaded())
    }
}
