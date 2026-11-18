package com.next2u.healthsync.shared.ingest

import kotlin.random.Random

/**
 * Backoff for automatic Core uploads (503/429/network), shared with Android's policy:
 * exponential 15 s → 15 min with jitter, never shorter than Retry-After (capped at 1 h).
 */
object IngestBackoff {
    const val BASE_DELAY_MS = 15_000L
    const val MAX_DELAY_MS = 15 * 60_000L
    const val MAX_RETRY_AFTER_MS = 60 * 60_000L

    fun delayMs(consecutiveFailures: Int, retryAfterMs: Long?, random: Random = Random.Default): Long {
        if (consecutiveFailures <= 0 && retryAfterMs == null) return 0L
        val exp = (consecutiveFailures - 1).coerceIn(0, 16)
        val ceiling = (BASE_DELAY_MS shl exp).coerceAtMost(MAX_DELAY_MS)
        val half = ceiling / 2
        val jittered = half + if (half > 0) random.nextLong(half + 1) else 0L
        return maxOf(jittered, retryAfterMs?.coerceIn(0L, MAX_RETRY_AFTER_MS) ?: 0L)
    }

    /** Retry-After as delta-seconds (what Cloud Run / the Core send). HTTP-dates are ignored. */
    fun parseRetryAfterSeconds(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1000 }
}

/** In-process backoff window guarding automatic flushes. */
class IngestBackoffWindow(private val random: Random = Random.Default) {
    var consecutiveFailures: Int = 0
        private set
    var nextAttemptAtMs: Long = 0L
        private set

    fun remainingMs(nowMs: Long): Long = (nextAttemptAtMs - nowMs).coerceAtLeast(0L)

    fun recordSuccess() { consecutiveFailures = 0; nextAttemptAtMs = 0L }

    fun recordTransientFailure(nowMs: Long, retryAfterMs: Long?): Long {
        consecutiveFailures++
        val delay = IngestBackoff.delayMs(consecutiveFailures, retryAfterMs, random)
        nextAttemptAtMs = nowMs + delay
        return delay
    }
}
