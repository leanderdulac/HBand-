package com.example.data.hband

/** Foreground-service / UI presentation of the VE30 BLE session. */
enum class BleSessionPhase {
    CONNECTED,
    CONNECTING,
    RECONNECTING,
    NOT_FOUND,
    DISCONNECTED,
}

/**
 * Recovery rules for a hung Veepoo connect.
 *
 * [HBandBleManager.isConnectingVeepoo] is also the PPG keep-alive latch, so a
 * connect/notify callback that never arrives used to skip every later
 * reconnect (`connect ignorado (connecting=true live=false …)`). These helpers
 * treat that combination as stale after a short watchdog.
 */
object VeepooReconnectPolicy {
    const val CONNECT_ATTEMPT_TIMEOUT_MS = 25_000L
    const val PROCESS_START_GRACE_MS = 3_000L
    const val BOND_ABORT_BACKOFF_MS = 20_000L
    const val MAX_TRACKED_ATTEMPTS = 8

    fun processStartDelayMs(
        createdAtElapsed: Long,
        nowElapsed: Long,
        graceMs: Long = PROCESS_START_GRACE_MS,
    ): Long {
        val age = (nowElapsed - createdAtElapsed).coerceAtLeast(0L)
        return (graceMs - age).coerceAtLeast(0L)
    }

    fun isConnectingStale(
        connecting: Boolean,
        startedAtElapsedMs: Long,
        nowElapsedMs: Long,
        notifyUp: Boolean,
        liveSession: Boolean,
        timeoutMs: Long = CONNECT_ATTEMPT_TIMEOUT_MS,
    ): Boolean {
        if (!connecting) return false
        if (notifyUp || liveSession) return false
        if (startedAtElapsedMs <= 0L) return true
        return nowElapsedMs - startedAtElapsedMs >= timeoutMs
    }

    fun delayMs(attempt: Int, notWorn: Boolean): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val shift = if (notWorn) 3 else 0
        val capMs = if (notWorn) 120_000L else 60_000L
        val exp = 2_000L * (1L shl (safeAttempt - 1 + shift).coerceAtMost(6))
        return exp.coerceAtMost(capMs)
    }

    /** Keep retrying; after several failures stay on the capped delay. */
    fun nextAttemptIndex(current: Int): Int =
        (current + 1).coerceAtMost(MAX_TRACKED_ATTEMPTS)

    fun phase(
        hardwareConnected: Boolean,
        deviceConnected: Boolean,
        autoReconnect: Boolean,
        hasPersistedSession: Boolean,
        userStopped: Boolean,
        connecting: Boolean,
        connectAttemptStale: Boolean,
        failedAttempts: Int,
    ): BleSessionPhase {
        if (hardwareConnected && deviceConnected) return BleSessionPhase.CONNECTED
        if (userStopped || !autoReconnect || !hasPersistedSession) return BleSessionPhase.DISCONNECTED
        val inFlight = connecting && !connectAttemptStale
        return when {
            inFlight && failedAttempts == 0 -> BleSessionPhase.CONNECTING
            inFlight -> BleSessionPhase.RECONNECTING
            failedAttempts > 0 -> BleSessionPhase.NOT_FOUND
            else -> BleSessionPhase.RECONNECTING
        }
    }
}
