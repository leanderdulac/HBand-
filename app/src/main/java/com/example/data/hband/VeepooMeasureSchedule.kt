package com.example.data.hband

/**
 * Uma rodada PPG (FC, SpO2, PA, HRV) e depois descanso.
 * A primeira rodada da sessão e a medição manual começam na hora.
 * O fim da rodada marca a próxima para daqui a [INTERVAL_MS].
 *
 * No descanso o sensor óptico fica desligado de propósito. Silêncio de
 * pacotes nesse intervalo não é link morto: [keepSessionOnGattDisconnect]
 * segura a sessão GATT e o timer. Uma queda que continua sem resposta
 * por [REST_LINK_CONFIRM_MS] é que vira reconexão.
 */
object VeepooMeasureSchedule {
    const val INTERVAL_MS = 30L * 60L * 1000L
    const val REST_LINK_CONFIRM_MS = 45_000L

    /** Depois das tentativas rápidas, continua procurando a pulseira. */
    const val SLOW_RECONNECT_MS = 15L * 60L * 1000L

    enum class Phase { IDLE, MEASURING, RESTING }

    data class State(
        val phase: Phase = Phase.IDLE,
        val nextRoundAtMs: Long = 0L,
    )

    fun idle(): State = State()

    fun measuring(): State = State(phase = Phase.MEASURING)

    fun resting(nowMs: Long, intervalMs: Long = INTERVAL_MS): State =
        State(phase = Phase.RESTING, nextRoundAtMs = nowMs + intervalMs)

    fun remainingMs(state: State, nowMs: Long): Long {
        if (state.phase != Phase.RESTING) return 0L
        return (state.nextRoundAtMs - nowMs).coerceAtLeast(0L)
    }

    fun keepSessionOnGattDisconnect(state: State): Boolean = state.phase == Phase.RESTING

    /**
     * Próximo instante para acordar o app. Com a reconexão rápida esgotada,
     * espera [SLOW_RECONNECT_MS] em vez de desistir. Uma rodada já vencida
     * não dispara de novo na hora: as tentativas rápidas acabaram de falhar.
     */
    fun nextWakeAtMs(nowMs: Long, nextRoundAtMs: Long, fastReconnectExhausted: Boolean): Long {
        if (fastReconnectExhausted) {
            val slow = nowMs + SLOW_RECONNECT_MS
            return if (nextRoundAtMs > nowMs) minOf(nextRoundAtMs, slow) else slow
        }
        return if (nextRoundAtMs > nowMs) nextRoundAtMs else nowMs
    }

    fun roundIsDue(nowMs: Long, nextRoundAtMs: Long, armed: Boolean): Boolean {
        if (!armed) return false
        return nextRoundAtMs <= nowMs
    }
}
