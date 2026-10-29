package com.example.ui

import android.database.sqlite.SQLiteException
import com.example.data.local.IngestQueueEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import com.example.data.repository.ApiHealthState
import com.example.ui.components.SyncDisplayStatus
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueReadFailureTest {
    @Test fun failed_read_retries_only_on_request_and_recovers_without_changing_rows() = runTest {
        val retries = MutableStateFlow(0)
        var reads = 0
        val row = IngestQueueEntity(id = 57, payloadJson = "{}", status = "FAILED",
            errorMessage = "Falha de autenticação na API HealthTech (HTTP 401): denied")
        val source = flow {
            reads++
            if (reads < 3) throw IllegalStateException("synthetic private storage detail")
            emit(listOf(row))
            awaitCancellation()
        }
        val state = source.queuePresentationState(backgroundScope, retries)
        repeat(2) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() } }
        runCurrent()
        assertEquals(1, reads)
        assertTrue(state.value!!.readFailed)
        assertEquals(SyncDisplayStatus.READ_ERROR, state.value.displayStatus(true, ApiHealthState()))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, reads)
        retries.value++
        runCurrent()
        assertEquals(2, reads)
        assertTrue(state.value!!.readFailed)
        retries.value++
        runCurrent()
        assertEquals(3, reads)
        assertFalse(state.value!!.readFailed)
        assertEquals(listOf(row), state.value!!.items)
        assertEquals(SyncDisplayStatus.AUTH_REQUIRED, state.value.displayStatus(false, ApiHealthState()))
    }

    @Test fun failure_after_rows_discards_stale_snapshot_and_reentry_starts_a_fresh_read() = runTest {
        val fail = CompletableDeferred<Unit>()
        var reads = 0
        val source = flow {
            reads++
            emit(listOf(IngestQueueEntity(id = 57, payloadJson = "{}", status = "PENDING")))
            fail.await()
            throw IllegalStateException("synthetic")
        }
        val state = source.queuePresentationState(backgroundScope)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals(1, state.value!!.pendingCount)
        fail.complete(Unit)
        runCurrent()
        assertTrue(state.value!!.readFailed)
        assertTrue(state.value!!.items.isEmpty())
        observer.cancel()
        runCurrent()
        assertNull(state.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals(2, reads)
    }

    @Test fun cancelling_query_is_not_presented_as_storage_failure() = runTest {
        val state = flow<List<IngestQueueEntity>> { throw CancellationException("cancelled") }
            .queuePresentationState(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertNull(state.value)
    }

    @Test fun a_queue_read_failure_does_not_escape_the_presentation_scope() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, error -> uncaught.add(error) })
        try {
            val state = flow<List<IngestQueueEntity>> { throw SQLiteException("synthetic private storage detail") }
                .queuePresentationState(scope)
            scope.launch { state.collect() }
            runCurrent()
            assertTrue("Read failure escaped to the Android UI scope", uncaught.isEmpty())
        } finally { scope.cancel() }
    }
}
