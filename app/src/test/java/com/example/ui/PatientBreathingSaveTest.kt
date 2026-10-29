package com.example.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientBreathingSaveTest {
    @Test fun pending_is_synchronous_and_duplicate_clicks_do_not_write_again() = runTest {
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()
        val save = PatientBreathingSave(backgroundScope) { seconds, complete -> writes += seconds; release.await(); complete(true) }
        save.save("a", 75)
        assertEquals(BreathingSaveState("a", 75, BreathingSaveStatus.SAVING), save.state.value)
        save.save("a", 75); save.save("b", 90); runCurrent()
        assertEquals(listOf(75), writes)
        release.complete(Unit); runCurrent()
        assertEquals(BreathingSaveStatus.SAVED, save.state.value!!.status)
    }
    @Test fun completed_tokens_cannot_be_reused_after_another_attempt() = runTest {
        var writes = 0
        val save = PatientBreathingSave(backgroundScope) { _, complete -> writes++; complete(true) }
        save.save("a", 75); runCurrent()
        save.save("b", 90); runCurrent()
        save.save("a", 75); runCurrent()
        assertEquals(2, writes)
        assertEquals("b", save.state.value!!.token)
    }
    @Test fun prelaunch_cancellation_marks_uncertain_without_write() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        var writes = 0
        val save = PatientBreathingSave(scope) { _, _ -> writes++ }
        job.cancel(); save.save("a", 75); runCurrent()
        assertEquals(0, writes)
        assertEquals(BreathingSaveStatus.UNCONFIRMED, save.state.value!!.status)
    }
    @Test fun cancellation_during_write_marks_uncertain_without_retry() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        var writes = 0
        val save = PatientBreathingSave(scope) { _, _ -> writes++; awaitCancellation() }
        save.save("a", 75); runCurrent(); job.cancel(); runCurrent()
        assertEquals(1, writes)
        assertEquals(BreathingSaveStatus.UNCONFIRMED, save.state.value!!.status)
    }
    @Test fun stale_callback_and_old_completion_cannot_replace_new_attempt() = runTest {
        var oldComplete: ((Boolean) -> Unit)? = null
        val finishOld = CompletableDeferred<Unit>()
        val finishNew = CompletableDeferred<Unit>()
        val save = PatientBreathingSave(backgroundScope) { seconds, complete ->
            if (seconds == 75) { oldComplete = complete; complete(true); finishOld.await() }
            else { finishNew.await(); complete(true) }
        }
        save.save("a", 75); runCurrent(); save.save("b", 90); runCurrent()
        oldComplete!!(false); finishOld.complete(Unit); runCurrent()
        assertEquals(BreathingSaveState("b", 90, BreathingSaveStatus.SAVING), save.state.value)
        finishNew.complete(Unit); runCurrent()
        assertEquals(BreathingSaveState("b", 90, BreathingSaveStatus.SAVED), save.state.value)
    }
    @Test fun uncertain_result_is_retained_and_only_new_explicit_request_can_retry() = runTest {
        var writes = 0
        val save = PatientBreathingSave(backgroundScope) { _, complete -> writes++; complete(false) }
        save.save("a", 75); runCurrent(); advanceTimeBy(60_000); runCurrent()
        save.save("a", 75); runCurrent()
        assertEquals(1, writes)
        assertEquals(BreathingSaveStatus.UNCONFIRMED, save.state.value!!.status)
        save.save("b", 75); runCurrent()
        assertEquals(2, writes)
    }
    @Test fun notification_exception_after_success_cannot_reclassify_write() = runTest {
        val save = PatientBreathingSave(backgroundScope) { _, complete -> complete(true); throw IllegalStateException("notice") }
        save.save("a", 75); runCurrent()
        assertEquals(BreathingSaveStatus.SAVED, save.state.value!!.status)
    }
    @Test fun missing_result_or_exception_before_receipt_is_uncertain() = runTest {
        val save = PatientBreathingSave(backgroundScope) { seconds, _ -> if (seconds == 75) throw IllegalStateException("synthetic") }
        save.save("a", 75); runCurrent()
        assertEquals(BreathingSaveStatus.UNCONFIRMED, save.state.value!!.status)
        save.save("b", 90); runCurrent()
        assertEquals(BreathingSaveStatus.UNCONFIRMED, save.state.value!!.status)
    }
    @Test fun invalid_request_does_not_write_or_create_receipt() = runTest {
        var writes = 0
        val save = PatientBreathingSave(backgroundScope) { _, _ -> writes++ }
        save.save("", 75); save.save("a", 0); save.save("b", -1); runCurrent()
        assertEquals(0, writes)
        assertNull(save.state.value)
    }
}
