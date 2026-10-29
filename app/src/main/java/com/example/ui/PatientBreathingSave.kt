package com.example.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Volatile UI receipt, never a domain ID or a durable deduplication guarantee. */
enum class BreathingSaveStatus { SAVING, SAVED, UNCONFIRMED }

data class BreathingSaveState(val token: String, val seconds: Int, val status: BreathingSaveStatus)

internal class PatientBreathingSave(
    private val scope: CoroutineScope,
    private val write: suspend (Int, (Boolean) -> Unit) -> Unit,
) {
    private val lock = Any()
    private val usedTokens = mutableSetOf<String>()
    private val current = MutableStateFlow<BreathingSaveState?>(null)
    val state = current.asStateFlow()

    fun save(token: String, seconds: Int) {
        val pending = BreathingSaveState(token, seconds, BreathingSaveStatus.SAVING)
        synchronized(lock) {
            if (token.isBlank() || seconds <= 0 || token in usedTokens || current.value?.status == BreathingSaveStatus.SAVING) return
            usedTokens.add(token)
            // Publish before scheduling so another click cannot start another write.
            current.value = pending
        }
        val job = scope.launch {
            try {
                write(seconds) { saved ->
                    current.compareAndSet(pending, pending.copy(status =
                        if (saved) BreathingSaveStatus.SAVED else BreathingSaveStatus.UNCONFIRMED))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Includes an unavailable result or a failed notice after the receipt.
                // Completion below cannot overwrite an already confirmed write.
            }
        }
        job.invokeOnCompletion {
            // Also runs if the scope was cancelled before the launch body could start.
            current.compareAndSet(pending, pending.copy(status = BreathingSaveStatus.UNCONFIRMED))
        }
    }
}
