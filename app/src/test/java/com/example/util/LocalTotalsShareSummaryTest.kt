package com.example.util

import org.junit.Assert.*
import org.junit.Test

class LocalTotalsShareSummaryTest {
    @Test fun absent_totals_are_unavailable_while_known_zero_and_positive_values_are_preserved() {
        fun totals(water: Int?, breathing: Int?) = weeklyShareSummary(emptyList(), water, breathing, 1790467200000L).lines.takeLast(2).map { it.second }
        assertEquals(listOf("Indisponível", "Indisponível"), totals(null, null))
        assertEquals(listOf("0 mL", "0 min 0 s"), totals(0, 0))
        assertEquals(listOf("500 mL", "1 min 30 s"), totals(500, 90))
        assertEquals(listOf("Indisponível", "Indisponível"), totals(-1, -1))
    }
}
