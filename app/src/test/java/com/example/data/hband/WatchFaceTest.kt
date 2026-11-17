package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceTest {
    @Test fun statusMapping() {
        assertEquals(true, WatchFace.statusToBool("SUPPORT_OPEN"))
        assertEquals(false, WatchFace.statusToBool("SUPPORT_CLOSE"))
        assertNull(WatchFace.statusToBool("UNSUPPORT"))
        assertNull(WatchFace.statusToBool(null))
    }

    @Test fun autoMeasureLabel() {
        assertNull(WatchFace.autoMeasureLabel(WatchAutoMeasureState()))
        assertTrue(WatchFace.autoMeasureLabel(WatchAutoMeasureState(true, true))!!.contains("automaticamente"))
        assertTrue(WatchFace.autoMeasureLabel(WatchAutoMeasureState(true, false))!!.contains("desligada"))
        assertNull(WatchFace.autoMeasureLabel(WatchAutoMeasureState(true, null)))
    }
}
