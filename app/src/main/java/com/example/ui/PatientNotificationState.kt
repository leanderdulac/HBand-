package com.example.ui

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiNotification(
    /** Volatile presentation identity only; not a timestamp or persisted/domain ID. */
    val id: Long,
    val message: String,
    val isError: Boolean = false,
)

/** Keeps the existing latest-message policy, with acknowledgement of the displayed event only. */
internal class PatientNotificationState {
    private val sequence = AtomicLong()
    private val current = MutableStateFlow<UiNotification?>(null)
    val notification: StateFlow<UiNotification?> = current.asStateFlow()

    fun post(message: String, isError: Boolean = false) {
        current.value = UiNotification(sequence.incrementAndGet(), message, isError)
    }

    fun dismiss(displayed: UiNotification) {
        current.compareAndSet(displayed, null)
    }
}
