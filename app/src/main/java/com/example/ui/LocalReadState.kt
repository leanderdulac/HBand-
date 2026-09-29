package com.example.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** A failed/unfinished read is never a confirmed empty value. */
sealed interface LocalReadState<out T> {
    data object Loading : LocalReadState<Nothing>
    data object Failed : LocalReadState<Nothing>
    data class Ready<T>(val value: T) : LocalReadState<T>
}

fun <T> LocalReadState<T>.valueOrNull(): T? = (this as? LocalReadState.Ready)?.value

/** Retries only restart observation. Cancellation and the original data remain intact. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.localReadState(
    scope: CoroutineScope,
    retries: Flow<Int> = flowOf(0),
    isPending: (T) -> Boolean = { false },
): StateFlow<LocalReadState<T>> = retries.flatMapLatest {
    this@localReadState.map<T, LocalReadState<T>> {
        if (isPending(it)) LocalReadState.Loading else LocalReadState.Ready(it)
    }.onStart { emit(LocalReadState.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(LocalReadState.Failed)
        }
}.stateIn(scope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), LocalReadState.Loading)
