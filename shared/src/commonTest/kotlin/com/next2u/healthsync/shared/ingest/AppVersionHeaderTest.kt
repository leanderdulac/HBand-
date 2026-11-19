package com.next2u.healthsync.shared.ingest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionHeaderTest {
    @Test
    fun ios_header_is_marketing_plus_build() {
        assertEquals("ios-0.1.0+21", AppVersionHeader.ios("0.1.0", "21"))
        assertEquals("ios-0.1.0+21", AppVersionHeader.ios(" 0.1.0 ", " 21 "))
        assertNull(AppVersionHeader.ios("$(MARKETING_VERSION)", "21"))
        assertNull(AppVersionHeader.ios("0.1.0", "$(CURRENT_PROJECT_VERSION)"))
        assertNull(AppVersionHeader.ios("", "21"))
        assertNull(AppVersionHeader.ios("0.1.0", ""))
    }

    @Test
    fun ktor_path_keeps_the_build_when_the_explicit_value_drops_it() {
        assertEquals("ios-0.1.0+21", AppVersionHeader.choose("ios-0.1.0", "ios-0.1.0+21"))
        assertEquals("ios-0.1.0+21", AppVersionHeader.choose("ios-0.1.0+", "ios-0.1.0+21"))
        assertEquals("ios-0.1.0+21", AppVersionHeader.choose(null, "ios-0.1.0+21"))
        assertEquals("ios-0.1.0+21", AppVersionHeader.choose("ios-0.1.0+21", "ios-0.1.0+20"))
        assertEquals("ios-0.1.0", AppVersionHeader.choose("ios-0.1.0", null))
        assertFalse(AppVersionHeader.hasBuild("ios-0.1.0"))
        assertTrue(AppVersionHeader.hasBuild("ios-0.1.0+21"))
    }
}
