package com.example.data.local

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StorageStartupGateTest {
    @Test fun consumers_wait_for_open_and_runtime() = runBlocking {
        val gate = StorageStartupGate()
        val release = CompletableDeferred<Unit>()
        val opened = CompletableDeferred<Unit>()
        var activated = 0
        val waiting = async { gate.awaitReady() }
        val init = launch { gate.initialize({ opened.complete(Unit); release.await() }, { activated++ }) }
        opened.await()
        assertFalse(waiting.isCompleted); assertFalse(gate.isReady); assertEquals(0, activated)
        release.complete(Unit); init.join()
        assertTrue(waiting.await()); assertEquals(1, activated)
        gate.initialize({ error("Second opening must not happen") }, { error("Second runtime must not start") })
    }

    @Test fun failures_never_activate_or_retry() = runBlocking {
        for (error in listOf(IllegalStateException("private-database-path"),
            DatabaseKeyUnavailableException(), UnsatisfiedLinkError("private-native-details"))) {
            val gate = StorageStartupGate()
            var opened = 0
            gate.initialize({ opened++; throw error }, { error("Runtime must not start") })
            assertFalse(gate.awaitReady())
            assertEquals(StorageStartupState.UNAVAILABLE, gate.state.value)
            gate.initialize({ opened++ }, { error("No automatic retry") })
            assertEquals(1, opened)
        }
    }

    @Test fun cancellation_is_propagated_without_activation() = runBlocking {
        val gate = StorageStartupGate()
        val entered = CompletableDeferred<Unit>()
        val init = launch { gate.initialize({ entered.complete(Unit); awaitCancellation() }, { error("Must not start") }) }
        entered.await(); init.cancelAndJoin()
        assertTrue(init.isCancelled)
        assertFalse(gate.awaitReady())
    }

    @Test fun concurrent_initializers_open_once() = runBlocking {
        val gate = StorageStartupGate()
        var opened = 0; var activated = 0
        val release = CompletableDeferred<Unit>()
        val jobs = (1..8).map { launch { gate.initialize({ opened++; release.await() }, { activated++ }) } }
        yield(); assertEquals(1, opened)
        release.complete(Unit); jobs.joinAll()
        assertEquals(1, activated); assertTrue(gate.isReady)
    }
}
