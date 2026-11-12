package com.example.data.hband

/** Estado da troca de foto do mostrador (Ajustes > Relógio). Textos voltados ao paciente. */
data class WatchFaceUiState(
    val busy: Boolean = false,
    val progress: Int? = null,
    val lastResultOk: Boolean? = null,
    val message: String? = null,
)

/** Medição automática configurada no próprio relógio (lida a cada conexão). */
data class WatchAutoMeasureState(
    val known: Boolean = false,
    val heartRateAuto: Boolean? = null,
    val hrvAuto: Boolean? = null,
)

enum class WatchFaceFailure { NOT_CONNECTED, UNSUPPORTED, LOW_BATTERY, IMAGE, AUTH, TRANSFER, TIMEOUT }

object WatchFace {
    /** Lado máximo da imagem enviada; o SDK JieLi redimensiona para a tela do relógio. */
    const val MAX_SIDE_PX = 480
    const val SUCCESS_MESSAGE = "Foto do mostrador atualizada."
    const val PREPARING_MESSAGE = "Preparando a foto…"

    fun progressMessage(progress: Int): String =
        "Enviando a foto para o relógio… ${progress.coerceIn(0, 100)}%"

    fun failureMessage(f: WatchFaceFailure): String = when (f) {
        WatchFaceFailure.NOT_CONNECTED -> "Conecte o relógio para trocar a foto."
        WatchFaceFailure.UNSUPPORTED -> "Este relógio não permite trocar a foto do mostrador."
        WatchFaceFailure.LOW_BATTERY -> "Bateria do relógio baixa. Carregue e tente de novo."
        WatchFaceFailure.IMAGE -> "Não foi possível usar esta foto. Escolha outra."
        WatchFaceFailure.AUTH, WatchFaceFailure.TRANSFER, WatchFaceFailure.TIMEOUT ->
            "Não foi possível enviar a foto. Deixe o relógio perto do celular e tente de novo."
    }

    /** Recorte quadrado central: [left, top, side]. */
    fun centerSquare(width: Int, height: Int): IntArray {
        require(width > 0 && height > 0)
        val side = minOf(width, height)
        return intArrayOf((width - side) / 2, (height - side) / 2, side)
    }

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
