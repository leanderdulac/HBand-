package com.example.data.hband

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchFaceTest {
    @Test fun centerSquareLandscape() = assertArrayEquals(intArrayOf(100, 0, 600), WatchFace.centerSquare(800, 600))
    @Test fun centerSquarePortrait() = assertArrayEquals(intArrayOf(0, 50, 300), WatchFace.centerSquare(300, 400))
    @Test fun centerSquareSquare() = assertArrayEquals(intArrayOf(0, 0, 5), WatchFace.centerSquare(5, 5))

    @Test fun statusMapping() {
        assertEquals(true, WatchFace.statusToBool("SUPPORT_OPEN"))
        assertEquals(false, WatchFace.statusToBool("SUPPORT_CLOSE"))
        assertNull(WatchFace.statusToBool("UNSUPPORT"))
        assertNull(WatchFace.statusToBool(null))
    }

    @Test fun progressClamped() {
        assertEquals("Enviando a foto para o relógio… 100%", WatchFace.progressMessage(140))
        assertEquals("Enviando a foto para o relógio… 0%", WatchFace.progressMessage(-3))
    }

    @Test fun messagesHaveNoTechnicalTerms() {
        val banned = listOf("JL", "JieLi", "SDK", "MTU", "RCSP", "GATT", "erro ", "code")
        WatchFaceFailure.values().forEach { f ->
            val m = WatchFace.failureMessage(f)
            banned.forEach { b -> assertFalse("$f: $m", m.contains(b)) }
        }
    }

    @Test fun autoMeasureLabel() {
        assertNull(WatchFace.autoMeasureLabel(WatchAutoMeasureState()))
        assertTrue(WatchFace.autoMeasureLabel(WatchAutoMeasureState(true, true))!!.contains("automaticamente"))
        assertTrue(WatchFace.autoMeasureLabel(WatchAutoMeasureState(true, false))!!.contains("desligada"))
        assertNull(WatchFace.autoMeasureLabel(WatchAutoMeasureState(true, null)))
    }
}
