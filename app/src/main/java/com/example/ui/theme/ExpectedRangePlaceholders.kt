package com.example.ui.theme

/**
 * Configurable expected-range placeholders for charts and status chips.
 *
 * These are **not** clinical thresholds. min/max stay null until R2/R3 supplies
 * per-patient ranges. Do not invent values here.
 */
data class ExpectedRangePlaceholder(
    val metricKey: String,
    val label: String,
    val unit: String,
    val min: Float? = null,
    val max: Float? = null,
) {
    val isConfigured: Boolean get() = min != null || max != null

    val caption: String
        get() = if (isConfigured) {
            "Faixa esperada (placeholder configurável): ${formatBound()} — não é limiar clínico"
        } else {
            "Faixa esperada: não configurada (placeholder R2/R3) — não é limiar clínico"
        }

    private fun formatBound(): String = buildString {
        if (min != null) append("≥ ${trim(min)} $unit")
        if (min != null && max != null) append(" · ")
        if (max != null) append("≤ ${trim(max)} $unit")
    }

    private fun trim(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}

object ExpectedRangePlaceholders {
    val heartRate = ExpectedRangePlaceholder("heart_rate", "Batimentos", "bpm")
    val spo2 = ExpectedRangePlaceholder("spo2", "Oxigênio no sangue", "%")
    val systolic = ExpectedRangePlaceholder("bp_systolic", "Pressão sistólica", "mmHg")
    val diastolic = ExpectedRangePlaceholder("bp_diastolic", "Pressão diastólica", "mmHg")
    val temperature = ExpectedRangePlaceholder("temperature", "Temperatura", "°C")
}
