package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.example.data.local.HBandSensorMetricEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

/** Presentation of stored snapshots only; no inferred duration, missing days or daily totals. */
internal data class SavedDaySummary(
    val dateLabel: String,
    val recordCount: Int,
    val averageHeartRate: Int?,
    val highestSteps: Int?,
    val highestCalories: Int?,
)

internal fun buildSavedWeek(
    metrics: List<HBandSensorMetricEntity>,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): List<SavedDaySummary> {
    val locale = Locale.forLanguageTag("pt-BR")
    val keyFormat = SimpleDateFormat("yyyy-MM-dd", locale).apply { this.timeZone = timeZone }
    val labelFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", locale).apply { this.timeZone = timeZone }
    val grouped = metrics.filter { it.timestampMillis > 0 && it.timestampMillis <= nowMillis }
        .groupBy { keyFormat.format(Date(it.timestampMillis)) }
    val today = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
    return (6 downTo 0).map { daysAgo ->
        // Calendar days, not fixed 24-hour offsets: daylight-saving days may be shorter/longer.
        val date = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time
        val records = grouped[keyFormat.format(date)].orEmpty()
        val heartRates = records.map { it.heartRate }.filter { it > 0 }
        SavedDaySummary(
            dateLabel = labelFormat.format(date),
            recordCount = records.size,
            averageHeartRate = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            highestSteps = records.map { it.steps }.filter { it > 0 }.maxOrNull(),
            highestCalories = records.map { it.calories }
                .filter { it.isFinite() && it > 0f } .maxOrNull()?.toInt(),
        )
    }
}

/** Refresh the displayed day while the screen stays open; cancels when it leaves composition. */
@Composable
internal fun rememberHistoryTime(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(60_000)
    }
}
