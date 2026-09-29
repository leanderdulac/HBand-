package com.example.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only diary; no clinical/backend clock or synchronization authority. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LocalWellnessRecords(
    private val hydration: HydrationDao,
    private val breathing: BreathingDao,
    private val dayChanges: Flow<Unit>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    // Resolve the existing local calendar format afresh; never relabel historical rows.
    private fun localDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

    val todayHydrationMl: Flow<Int?> = dayChanges
        .onStart { emit(Unit) }
        .map { localDate(nowMillis()) }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            hydration.getTodayTotalMlFlow(day).map<Int?, Int?> { it ?: 0 }
                // Unknown until Room answers; an empty completed SUM is a real zero.
                .onStart { emit(null) }
        }

    val totalBreathingSeconds: Flow<Int?> = breathing.getTotalBreathingSecondsFlow()
        .map<Int?, Int?> { it ?: 0 }
        .onStart { emit(null) }

    suspend fun addWaterIntake(amountMl: Int) {
        val timestamp = nowMillis()
        hydration.insertLog(HydrationLogEntity(amountMl = amountMl, timestampMillis = timestamp, dateString = localDate(timestamp)))
    }

    suspend fun resetTodayHydration() { hydration.resetTodayLogs(localDate(nowMillis())) }

    suspend fun saveBreathingSession(durationSeconds: Int) {
        if (durationSeconds <= 0) return
        val timestamp = nowMillis()
        breathing.insertSession(BreathingSessionEntity(durationSeconds = durationSeconds, timestampMillis = timestamp, dateString = localDate(timestamp)))
    }
}
