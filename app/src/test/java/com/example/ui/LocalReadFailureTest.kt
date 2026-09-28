package com.example.ui

import com.example.data.local.HBandSensorMetricEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalReadFailureTest {
    @Test fun failed_read_discards_data_and_retries_without_a_loop_or_writes() = runTest {
        val trigger = MutableStateFlow(0)
        val fail = CompletableDeferred<Unit>()
        var reads = 0
        val state = flow {
            reads++
            if (reads == 1) { emit(42); fail.await(); error("synthetic") }
            if (reads == 2) error("still unavailable")
            emit(0)
            awaitCancellation()
        }.localReadState(backgroundScope, trigger)
        repeat(2) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() } }
        runCurrent()
        assertEquals(42, state.value.valueOrNull())
        fail.complete(Unit); runCurrent()
        assertEquals(LocalReadState.Failed, state.value)
        assertNull(state.value.valueOrNull())
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, reads)
        trigger.value++; runCurrent()
        assertEquals(LocalReadState.Failed, state.value)
        assertEquals(2, reads)
        trigger.value++; runCurrent()
        assertEquals(LocalReadState.Ready(0), state.value)
        assertEquals(3, reads)
    }

    @Test fun diary_day_transition_is_loading_but_a_confirmed_missing_profile_is_ready() = runTest {
        val source = MutableSharedFlow<Int?>()
        val state = source.localReadState(backgroundScope, isPending = { it == null })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        source.emit(750); runCurrent()
        assertEquals(LocalReadState.Ready(750), state.value)
        source.emit(null); runCurrent()
        assertEquals(LocalReadState.Loading, state.value)
        source.emit(0); runCurrent()
        assertEquals(LocalReadState.Ready(0), state.value)
        val profile = flowOf<String?>(null).localReadState(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { profile.collect() }
        runCurrent()
        assertEquals(LocalReadState.Ready<String?>(null), profile.value)
    }

    @Test fun cancelling_a_read_does_not_become_an_error() = runTest {
        val state = flow<Int> { throw CancellationException("cancelled") }.localReadState(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals(LocalReadState.Loading, state.value)
    }

    @Test fun a_history_read_failure_does_not_escape_the_presentation_scope() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, error -> uncaught.add(error) })
        try {
            val state = flow<List<HBandSensorMetricEntity>> { throw IllegalStateException("synthetic private storage detail") }
                .shareMetricsState(scope)
            scope.launch { state.collect() }
            runCurrent()
            assertTrue("Read failure escaped to the Android UI scope", uncaught.isEmpty())
        } finally { scope.cancel() }
    }
}
