package com.example.ui

import com.example.data.local.IngestQueueEntity
import com.example.data.repository.ApiHealthState
import com.example.ui.components.SyncDisplayStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueuePresentationStateTest {
    private val row = IngestQueueEntity(id = 57, payloadJson = "{\"synthetic\":true}", status = "PENDING")
    private val online = ApiHealthState(isOnline = true)

    @Test fun loading_empty_pending_and_failure_use_one_query_response() = runTest {
        val source = MutableSharedFlow<List<IngestQueueEntity>>()
        val state = source.queuePresentationState(backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertNull(state.value)
        assertEquals(SyncDisplayStatus.LOADING, state.value.displayStatus(false, online))
        source.emit(emptyList())
        runCurrent()
        assertEquals(0, state.value!!.pendingCount)
        assertEquals(SyncDisplayStatus.FULLY_SYNCED, state.value.displayStatus(false, online))
        val rows = listOf(row, row.copy(id = 58, status = "SYNCED"))
        source.emit(rows)
        runCurrent()
        assertSame(rows, state.value!!.items)
        assertEquals(1, state.value!!.pendingCount)
        assertEquals(1, state.value!!.syncedCount)
        assertEquals(0, state.value!!.failedCount)
        assertEquals(SyncDisplayStatus.PENDING_QUEUE, state.value.displayStatus(false, online))
        source.emit(listOf(row.copy(status = "FAILED")))
        runCurrent()
        assertEquals(57L, state.value!!.items.single().id)
        assertEquals(row.payloadJson, state.value!!.items.single().payloadJson)
        assertEquals(0, state.value!!.pendingCount)
        assertEquals(0, state.value!!.syncedCount)
        assertEquals(1, state.value!!.failedCount)
        assertEquals(SyncDisplayStatus.FAILED, state.value.displayStatus(false, online))
    }

    @Test fun leaving_last_screen_observer_stops_query_and_resume_requires_new_response() = runTest {
        val response = MutableSharedFlow<List<IngestQueueEntity>>()
        var starts = 0
        var stops = 0
        val source = flow {
            starts++
            try { emitAll(response) } finally { stops++ }
        }
        val state = source.queuePresentationState(backgroundScope)
        runCurrent()
        assertEquals(0, starts)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        val second = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals(1, starts)
        response.emit(listOf(row))
        runCurrent()
        first.cancel()
        runCurrent()
        assertEquals(0, stops)
        assertEquals(1, state.value!!.pendingCount)
        second.cancel()
        runCurrent()
        assertEquals(1, stops)
        assertNull(state.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        runCurrent()
        assertEquals(2, starts)
        assertNull(state.value)
        response.emit(emptyList())
        runCurrent()
        assertTrue(state.value!!.items.isEmpty())
    }

    @Test fun loaded_status_keeps_existing_sync_auth_and_service_precedence() {
        val blocked = QueuePresentationState(listOf(row.copy(status = "FAILED",
            errorMessage = "Falha de autenticação na API HealthTech (HTTP 401): denied")))
        assertEquals(SyncDisplayStatus.SYNCING, blocked.displayStatus(true, online))
        assertEquals(SyncDisplayStatus.AUTH_REQUIRED, blocked.displayStatus(false, ApiHealthState()))
        assertEquals(SyncDisplayStatus.OFFLINE, QueuePresentationState(listOf(row)).displayStatus(false, ApiHealthState()))
        assertEquals(SyncDisplayStatus.LOADING, (null as QueuePresentationState?).displayStatus(true, online))
    }
}
