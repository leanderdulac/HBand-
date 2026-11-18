package com.example.data.hband

/**
 * Stops Veepoo write storms. A non-success inuker/protocol code on an unproven
 * device is treated as "not a VE30" instead of being retried in a tight loop.
 */
class VeepooWriteBackoff(
    private val abortAfterFailures: Int = 2,
) {
    enum class Decision { CONTINUE, BACKOFF, ABORT_UNSUPPORTED }

    var failures: Int = 0
        private set

    fun onWriteResult(code: Int, successCode: Int = 0): Decision {
        if (code == successCode) {
            failures = 0
            return Decision.CONTINUE
        }
        failures++
        return if (failures >= abortAfterFailures) Decision.ABORT_UNSUPPORTED else Decision.BACKOFF
    }

    fun reset() {
        failures = 0
    }
}
