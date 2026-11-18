package com.example.data.ingest

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Pure backoff math for automatic Core uploads (503/429/network). Exponential with
 * full jitter, never shorter than a server Retry-After, capped so a long outage
 * still retries at least every [MAX_DELAY_MS].
 */
object IngestBackoff {
    const val BASE_DELAY_MS = 15_000L
    const val MAX_DELAY_MS = 15 * 60_000L
    const val MAX_RETRY_AFTER_MS = 60 * 60_000L

    fun delayMs(consecutiveFailures: Int, retryAfterMs: Long?, random: Random = Random.Default): Long {
        if (consecutiveFailures <= 0 && retryAfterMs == null) return 0L
        val exp = (consecutiveFailures - 1).coerceIn(0, 16)
        val ceiling = (BASE_DELAY_MS shl exp).coerceAtMost(MAX_DELAY_MS)
        // Full jitter keeps many phones from hammering a recovering Core at the same instant.
        val jittered = ceiling / 2 + (if (ceiling / 2 > 0) random.nextLong(ceiling / 2 + 1) else 0L)
        val serverFloor = retryAfterMs?.coerceIn(0L, MAX_RETRY_AFTER_MS) ?: 0L
        return maxOf(jittered, serverFloor)
    }

    /** RFC 9110 Retry-After: delta-seconds or an HTTP-date. Returns null when absent/invalid. */
    fun parseRetryAfter(header: String?, nowMs: Long): Long? {
        val value = header?.trim().orEmpty()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { return if (it >= 0) it * 1000 else null }
        return runCatching {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            (format.parse(value)!!.time - nowMs).coerceAtLeast(0L)
        }.getOrNull()
    }
}

/** Shared admission gate so UI, BLE runtime and WorkManager respect one backoff window. */
interface IngestBackoffGate {
    fun waitRemainingMs(nowMs: Long): Long
    fun recordSuccess()
    fun recordTransientFailure(nowMs: Long, retryAfterMs: Long?)

    object Disabled : IngestBackoffGate {
        override fun waitRemainingMs(nowMs: Long) = 0L
        override fun recordSuccess() {}
        override fun recordTransientFailure(nowMs: Long, retryAfterMs: Long?) {}
    }

    companion object {
        fun shared(context: Context): IngestBackoffGate = PrefsIngestBackoffGate(context.applicationContext)
    }
}

/** Persisted in SharedPreferences so a process restart does not reset the backoff window. */
class PrefsIngestBackoffGate(context: Context, private val random: Random = Random.Default) : IngestBackoffGate {
    private val prefs = context.getSharedPreferences("ingest_backoff", Context.MODE_PRIVATE)

    override fun waitRemainingMs(nowMs: Long): Long =
        (prefs.getLong(KEY_NEXT, 0L) - nowMs).coerceAtLeast(0L)

    @Synchronized
    override fun recordSuccess() {
        prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_NEXT, 0L).apply()
    }

    @Synchronized
    override fun recordTransientFailure(nowMs: Long, retryAfterMs: Long?) {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val delay = IngestBackoff.delayMs(failures, retryAfterMs, random)
        prefs.edit().putInt(KEY_FAILURES, failures).putLong(KEY_NEXT, nowMs + delay).apply()
    }

    private companion object {
        const val KEY_FAILURES = "consecutive_failures"
        const val KEY_NEXT = "next_attempt_at"
    }
}
