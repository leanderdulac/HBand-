package com.example.data.hband

/**
 * Password confirm policy for VE30 reconnect.
 *
 * The SDK arms a connection-confirm timer (default 12s) in
 * [com.veepoo.protocol.VPOperateManager.confirmDevicePwd]. That timer is
 * **not** cancelled when [onPwdDataChange] / custom-settings succeed, so a
 * late [onConnectionConfirmTimeout] can fire after HeartData is already
 * streaming. Tearing down `_isHardwareConnected` then schedules a new GATT
 * connect, which collides with the live session.
 */
object VeepooPasswordHandshake {
    const val MAX_CONFIRM_ATTEMPTS = 2
    const val SDK_CONFIRM_TIMEOUT_SEC = 30
    const val LATE_CALLBACK_GRACE_MS = 800L
    const val PERSON_INFO_FALLBACK_MS = 1_500L

    const val MSG_TIMEOUT_RETRY =
        "Tempo esgotado ao confirmar a senha do VE30. Nova tentativa sem derrubar a sessão."
    const val MSG_TIMEOUT_LIVE =
        "Confirmação de senha atrasada; a sessão ao vivo foi mantida. Toque em reconectar só se os dados pararem."
    const val MSG_TIMEOUT_KEEP =
        "Tempo esgotado ao confirmar a senha do VE30. Sessão GATT mantida — toque em reconectar se os dados pararem."
    const val MSG_TIMEOUT_RECONNECT =
        "Tempo esgotado ao confirmar a senha do VE30. Tentando reconectar."
    const val MSG_WRITE_FAIL =
        "Falha ao escrever a senha do VE30 (GATT). Nova tentativa sem reconectar."

    enum class Action {
        IGNORE,
        RETRY_CONFIRM,
        KEEP_SESSION,
        RECONNECT,
    }

    data class TimeoutDecision(
        val action: Action,
        val keepLiveSession: Boolean,
        val sessionMessage: String?,
        val logReason: String,
    )

    /**
     * @param attemptIndex 1-based attempt that just timed out
     * @param handshakeSucceeded pwd/custom-settings already accepted
     * @param liveSession [VeepooSessionGate] or recent HeartData
     * @param notifyUp GATT notify already succeeded
     */
    fun onConfirmTimeout(
        attemptIndex: Int,
        handshakeSucceeded: Boolean,
        liveSession: Boolean,
        notifyUp: Boolean,
    ): TimeoutDecision {
        if (handshakeSucceeded) {
            return TimeoutDecision(
                action = Action.IGNORE,
                keepLiveSession = true,
                sessionMessage = null,
                logReason = "late-timeout-after-success",
            )
        }
        if (attemptIndex < MAX_CONFIRM_ATTEMPTS) {
            return TimeoutDecision(
                action = Action.RETRY_CONFIRM,
                keepLiveSession = true,
                sessionMessage = MSG_TIMEOUT_RETRY,
                logReason = "timeout-retry-attempt-$attemptIndex",
            )
        }
        if (liveSession || notifyUp) {
            return TimeoutDecision(
                action = Action.KEEP_SESSION,
                keepLiveSession = true,
                sessionMessage = if (liveSession) MSG_TIMEOUT_LIVE else MSG_TIMEOUT_KEEP,
                logReason = "timeout-exhausted-keep-session live=$liveSession notify=$notifyUp",
            )
        }
        return TimeoutDecision(
            action = Action.RECONNECT,
            keepLiveSession = false,
            sessionMessage = MSG_TIMEOUT_RECONNECT,
            logReason = "timeout-exhausted-reconnect",
        )
    }

    fun isPwdAccepted(statusName: String?): Boolean {
        return when (statusName) {
            "CHECK_SUCCESS",
            "CHECK_AND_TIME_SUCCESS",
            "SETTING_SUCCESS",
            "READ_SUCCESS",
            -> true
            else -> false
        }
    }

    fun shouldSkipDuplicateConnect(
        connecting: Boolean,
        liveSession: Boolean,
        notifyUp: Boolean,
        confirmInFlight: Boolean,
    ): Boolean {
        if (connecting) return true
        // Stale HeartData after Desconectar is not a live GATT session.
        if (notifyUp && liveSession) return true
        if (notifyUp && confirmInFlight) return true
        return false
    }
}
