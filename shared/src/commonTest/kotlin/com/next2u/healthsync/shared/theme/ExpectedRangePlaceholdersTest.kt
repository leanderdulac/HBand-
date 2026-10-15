package com.next2u.healthsync.shared.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ExpectedRangePlaceholdersTest {
    @Test
    fun placeholders_are_unconfigured_and_caption_is_frozen() {
        val all = listOf(
            ExpectedRangePlaceholders.heartRate,
            ExpectedRangePlaceholders.spo2,
            ExpectedRangePlaceholders.systolic,
            ExpectedRangePlaceholders.diastolic,
            ExpectedRangePlaceholders.temperature,
        )
        all.forEach { range ->
            assertNull(range.min)
            assertNull(range.max)
            assertFalse(range.isConfigured)
            assertEquals(
                "Faixa esperada: não configurada (placeholder R2/R3) — não é limiar clínico",
                range.caption,
            )
        }
        assertEquals("Batimentos", ExpectedRangePlaceholders.heartRate.label)
        assertEquals("Oxigênio no sangue", ExpectedRangePlaceholders.spo2.label)
    }
}
