package com.example.data.hband

/**
 * Backs off [startDetectBP] after a VE30 write ACK failure so the PPG cycle
 * does not hammer the watch. Success clears the window; failure doubles the
 * delay up to [maxBackoffMs]. Time is elapsed realtime, not wall clock.
 */
class BpDetectRetry(
    private val initialBackoffMs: Long = INITIAL_BACKOFF_MS,
    private val maxBackoffMs: Long = MAX_BACKOFF_MS,
) {
    var consecutiveFailures: Int = 0
        private set
    var nextAttemptAtElapsedMs: Long = 0L
        private set

    fun shouldAttempt(nowElapsedMs: Long): Boolean = nowElapsedMs >= nextAttemptAtElapsedMs

    fun waitMs(nowElapsedMs: Long): Long = (nextAttemptAtElapsedMs - nowElapsedMs).coerceAtLeast(0L)

    fun onAck(success: Boolean, nowElapsedMs: Long) {
        if (success) {
            reset()
            return
        }
        consecutiveFailures++
        val shift = (consecutiveFailures - 1).coerceAtMost(8)
        val delay = (initialBackoffMs shl shift).coerceAtMost(maxBackoffMs)
        nextAttemptAtElapsedMs = nowElapsedMs + delay
    }

    fun reset() {
        consecutiveFailures = 0
        nextAttemptAtElapsedMs = 0L
    }

    companion object {
        const val INITIAL_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 10 * 60_000L
    }
}
