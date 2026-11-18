package com.example.data.hband

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Estado do acerto de hora do relógio (seção "Relógio" em Ajustes). */
data class WatchClockUiState(
    val syncing: Boolean = false,
    val lastSyncEpochMs: Long = 0L,
    val lastResultOk: Boolean? = null,
    val message: String? = null,
)

/** Campos enviados ao relógio, derivados da hora local do telefone. */
data class WatchTimeFields(
    val year: Int, val month: Int, val day: Int,
    val hour: Int, val minute: Int, val second: Int,
    val is24h: Boolean,
)

object WatchClock {
    // minSdk 24: java.util em vez de java.time.
    private fun fmt(pattern: String, epochMs: Long) =
        SimpleDateFormat(pattern, Locale("pt", "BR")).format(java.util.Date(epochMs))

    fun fields(epochMs: Long, is24h: Boolean): WatchTimeFields {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        return WatchTimeFields(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), is24h,
        )
    }

    fun successMessage(epochMs: Long): String = "Hora do relógio atualizada às ${fmt("HH:mm", epochMs)}"

    fun lastSyncLabel(epochMs: Long?): String =
        if (epochMs == null || epochMs <= 0) "Última sincronização: nunca"
        else "Última sincronização: ${fmt("dd/MM HH:mm", epochMs)}"
}
