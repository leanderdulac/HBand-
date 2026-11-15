package com.example.data.hband

/**
 * Intervalos do revezamento PPG no VE30. Espelha a política iOS do build 18:
 * o sensor óptico precisa descansar entre ciclos, senão a bateria acaba.
 */
object PpgCyclePolicy {
    const val CYCLE_IDLE_MS: Long = 5 * 60_000L
    const val MAX_IDLE_MS: Long = 30 * 60_000L
    const val EMPTY_RETRY_MS: Long = 2 * 60_000L
    const val BLOOD_CADENCE_MS: Long = 30 * 60_000L
    const val FAILURE_INITIAL_BACKOFF_MS: Long = 30_000L
    const val FAILURE_MAX_BACKOFF_MS: Long = 10 * 60_000L
    /** Folga curta só entre estágios do mesmo ciclo (FC → SpO2 → PA → HRV). */
    const val STAGE_GAP_MS: Long = 400L

    fun idleAfter(emptyCycles: Int): Long {
        if (emptyCycles <= 0) return CYCLE_IDLE_MS
        val shift = (emptyCycles - 1).coerceAtMost(4)
        var idle = EMPTY_RETRY_MS
        repeat(shift) { idle = (idle * 2).coerceAtMost(MAX_IDLE_MS) }
        return idle.coerceAtMost(MAX_IDLE_MS)
    }

    fun failureBackoff(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        val shift = (consecutiveFailures - 1).coerceAtMost(8)
        var delay = FAILURE_INITIAL_BACKOFF_MS
        repeat(shift) { delay = (delay * 2).coerceAtMost(FAILURE_MAX_BACKOFF_MS) }
        return delay.coerceAtMost(FAILURE_MAX_BACKOFF_MS)
    }

    fun restAfterCycle(
        emptyCycles: Int,
        consecutiveFailures: Int,
        failed: Boolean,
    ): Long {
        var idle = idleAfter(emptyCycles)
        if (failed) {
            idle = maxOf(idle, failureBackoff(consecutiveFailures))
        }
        return idle
    }

    fun bloodIsDue(unsupported: Boolean, lastSuccessAtElapsedMs: Long?, nowElapsedMs: Long): Boolean {
        if (unsupported) return false
        val last = lastSuccessAtElapsedMs ?: return true
        return nowElapsedMs - last >= BLOOD_CADENCE_MS
    }

    fun shouldStartCycle(
        hardwareReady: Boolean,
        cycleRunning: Boolean,
        nowElapsedMs: Long,
        nextCycleAtElapsedMs: Long,
        force: Boolean,
    ): Boolean {
        if (!hardwareReady || cycleRunning) return false
        if (force) return true
        return nowElapsedMs >= nextCycleAtElapsedMs
    }
}
