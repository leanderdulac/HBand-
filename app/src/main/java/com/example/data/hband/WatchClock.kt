package com.example.data.hband

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Estado do acerto de hora do relógio (seção "Relógio" em Ajustes). */
data class WatchClockUiState(
    val syncing: Boolean = false,
    val lastSyncEpochMs: Long = 0L,
    val lastResultOk: Boolean? = null,
    val message: String? = null,
)

/**
 * Capacidades de mostrador reportadas pelo firmware (onFunctionSupportDataChange).
 * Guardadas para liberar a futura opção "Trocar foto do mostrador" só quando suportada.
 */
data class WatchDialCapabilities(
    val known: Boolean = false,
    val watchUiCustomCount: Int = 0,
    val watchUiServerCount: Int = 0,
    val screenType: Int = 0,
    val screenTypeTag: Int = 0,
    val screenTypeDes: String? = null,
    val screenStyle: Int = 0,
    val photoAlbum: String? = null,
) {
    val supportsPhotoDial: Boolean get() = known && watchUiCustomCount > 0

    fun logLine(): String =
        "Mostrador VE30: watchUiCoustomCount=$watchUiCustomCount watchUiServerCount=$watchUiServerCount " +
            "screenType=$screenType screenTypeTag=$screenTypeTag screenTypeDes=$screenTypeDes " +
            "screenStyle=$screenStyle photoAlbum=$photoAlbum"
}

/** Campos enviados ao relógio, derivados da hora local do telefone. */
data class WatchTimeFields(
    val year: Int, val month: Int, val day: Int,
    val hour: Int, val minute: Int, val second: Int,
    val is24h: Boolean,
)

object WatchClock {
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun fields(now: LocalDateTime, is24h: Boolean) = WatchTimeFields(
        now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second, is24h,
    )

    fun successMessage(at: LocalDateTime): String = "Hora do relógio atualizada às ${at.format(HHMM)}"

    fun lastSyncLabel(at: LocalDateTime?): String =
        if (at == null) "Última sincronização: nunca" else "Última sincronização: ${at.format(FULL)}"
}
