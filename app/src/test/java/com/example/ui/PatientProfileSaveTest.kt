package com.example.ui

import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientProfileSaveTest {
    private val profile = UserProfileEntity(patientId = "synthetic-patient", fullName = "Pessoa de teste")
    @Test fun pending_is_synchronous_and_repeated_clicks_cannot_write_twice() = runTest {
        val finish = CompletableDeferred<Unit>()
        val writes = mutableListOf<UserProfileEntity>()
        val after = mutableListOf<UserProfileEntity>()
        val save = PatientProfileSave(backgroundScope, { writes += it; finish.await() }, { after += it })
        save.save("a", profile)
        assertEquals(ProfileSaveState("a", profile.id, profile.patientId, ProfileSaveStatus.SAVING), save.state.value)
        save.save("a", profile); save.save("b", profile.copy(fullName = "Other")); runCurrent()
        assertEquals(listOf(profile), writes); assertTrue(after.isEmpty())
        finish.complete(Unit); runCurrent()
        assertEquals(ProfileSaveStatus.SAVED, save.state.value!!.status)
        assertEquals(listOf(profile), after)
    }
    @Test fun exception_preserves_uncertainty_and_never_retries_automatically() = runTest {
        var writes = 0
        var after = 0
        val save = PatientProfileSave(backgroundScope, { writes++; error("synthetic-private-detail") }, { after++ })
        save.save("a", profile); runCurrent(); advanceTimeBy(60_000); runCurrent()
        save.save("a", profile); runCurrent()
        assertEquals(1, writes); assertEquals(0, after)
        assertEquals(ProfileSaveStatus.UNCONFIRMED, save.state.value!!.status)
        save.save("b", profile.copy(fullName = "Corrected")); runCurrent()
        assertEquals(2, writes)
    }
    @Test fun completed_token_is_not_reused_even_after_another_attempt() = runTest {
        var writes = 0
        val save = PatientProfileSave(backgroundScope, { writes++ }, {})
        save.save("a", profile); runCurrent(); save.save("b", profile); runCurrent()
        save.save("a", profile); runCurrent()
        assertEquals(2, writes); assertEquals("b", save.state.value!!.token)
    }
    @Test fun cancellation_before_launch_marks_uncertain_without_a_write() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        var writes = 0
        val save = PatientProfileSave(scope, { writes++ }, {})
        job.cancel(); save.save("a", profile); runCurrent()
        assertEquals(0, writes)
        assertEquals(ProfileSaveStatus.UNCONFIRMED, save.state.value!!.status)
    }
    @Test fun cancellation_during_write_never_reports_success_or_replays() = runTest {
        val job = SupervisorJob()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        var writes = 0
        var after = 0
        val save = PatientProfileSave(scope, { writes++; awaitCancellation() }, { after++ })
        save.save("a", profile); runCurrent(); job.cancel(); runCurrent()
        assertEquals(1, writes); assertEquals(0, after)
        assertEquals(ProfileSaveStatus.UNCONFIRMED, save.state.value!!.status)
    }
    @Test fun post_write_notification_failure_cannot_reclassify_success() = runTest {
        lateinit var save: PatientProfileSave
        save = PatientProfileSave(backgroundScope, {}, {
            assertEquals(ProfileSaveStatus.SAVED, save.state.value!!.status)
            error("synthetic notice error")
        })
        save.save("a", profile); runCurrent()
        assertEquals(ProfileSaveStatus.SAVED, save.state.value!!.status)
    }
    @Test fun old_job_completion_cannot_replace_a_new_pending_attempt() = runTest {
        val finish = CompletableDeferred<Unit>()
        lateinit var save: PatientProfileSave
        save = PatientProfileSave(backgroundScope, { if (it.fullName == "New") finish.await() }, {
            if (it == profile) save.save("b", profile.copy(fullName = "New"))
        })
        save.save("a", profile); runCurrent()
        assertEquals("b", save.state.value!!.token)
        assertEquals(ProfileSaveStatus.SAVING, save.state.value!!.status)
        finish.complete(Unit); runCurrent()
        assertEquals(ProfileSaveStatus.SAVED, save.state.value!!.status)
    }
    @Test fun empty_token_cannot_create_a_receipt_or_write() = runTest {
        val save = PatientProfileSave(backgroundScope, { error("Unexpected write") }, {})
        save.save(" ", profile); runCurrent(); assertNull(save.state.value)
    }
}
