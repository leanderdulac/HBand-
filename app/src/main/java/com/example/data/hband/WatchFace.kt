package com.example.data.hband

/** Medição automática configurada no próprio relógio (lida a cada conexão). */
data class WatchAutoMeasureState(
    val known: Boolean = false,
    val heartRateAuto: Boolean? = null,
    val hrvAuto: Boolean? = null,
)

object WatchFace {
    /** Mapeia o nome de EFunctionStatus do SDK Veepoo para ligado/desligado/desconhecido. */
    fun statusToBool(statusName: String?): Boolean? = when (statusName) {
        "SUPPORT_OPEN" -> true
        "SUPPORT_CLOSE" -> false
        else -> null
    }

    fun autoMeasureLabel(state: WatchAutoMeasureState): String? {
        if (!state.known) return null
        return when (state.heartRateAuto) {
            true -> "O relógio mede seus batimentos automaticamente ao longo do dia."
            false -> "A medição automática de batimentos está desligada no relógio."
            null -> null
        }
    }
}
