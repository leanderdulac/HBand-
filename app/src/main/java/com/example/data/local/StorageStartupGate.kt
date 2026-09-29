package com.example.data.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StorageStartupState { OPENING, READY, UNAVAILABLE }

/** One storage attempt per process. No automatic repair, reset or in-process retry. */
class StorageStartupGate {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(StorageStartupState.OPENING)
    val state = mutableState.asStateFlow()
    val isReady: Boolean get() = state.value == StorageStartupState.READY

    suspend fun initialize(openStorage: suspend () -> Unit, startRuntime: () -> Unit) = mutex.withLock {
        if (state.value != StorageStartupState.OPENING) return@withLock
        try {
            openStorage()
        } catch (cancelled: CancellationException) {
            mutableState.value = StorageStartupState.UNAVAILABLE
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = StorageStartupState.UNAVAILABLE
            return@withLock
        } catch (_: LinkageError) {
            // Missing SQLCipher native library must not crash before the recovery guidance.
            mutableState.value = StorageStartupState.UNAVAILABLE
            return@withLock
        }
        // Runtime construction is outside the storage error boundary: SDK/network
        // programming failures must not be misreported as a corrupted database.
        startRuntime()
        mutableState.value = StorageStartupState.READY
    }

    suspend fun awaitReady(): Boolean = state.first { it != StorageStartupState.OPENING } == StorageStartupState.READY
}
