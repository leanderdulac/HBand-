package com.example.ui

import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientProfileReadTest {
    private val original = UserProfileEntity(patientId = "synthetic", fullName = "Pessoa de teste")

    @Test fun failure_retains_editor_baseline_but_disallows_save_until_identity_is_confirmed() = runTest {
        val responses = Channel<Result<UserProfileEntity?>>(Channel.UNLIMITED)
        val reader = PatientProfileRead(backgroundScope, flow {
            while (true) emit(responses.receive().getOrThrow())
        })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.state.collect() }
        runCurrent()
        assertFalse(reader.canSave(original))
        responses.send(Result.success(original)); runCurrent()
        assertEquals(original, reader.editorProfile.value)
        assertTrue(reader.canSave(original.copy(fullName = "Rascunho")))
        responses.send(Result.failure(IllegalStateException("synthetic"))); runCurrent()
        assertEquals(LocalReadState.Failed, reader.state.value)
        assertEquals(original, reader.editorProfile.value)
        assertFalse(reader.canSave(original))
        reader.retry(); runCurrent()
        assertEquals(LocalReadState.Loading, reader.state.value)
        assertEquals(original, reader.editorProfile.value)
        assertFalse(reader.canSave(original))
        val other = original.copy(patientId = "other-synthetic")
        responses.send(Result.success(other)); runCurrent()
        assertEquals(other, reader.editorProfile.value)
        assertFalse(reader.canSave(original))
        assertTrue(reader.canSave(other))
        responses.send(Result.success(null)); runCurrent()
        assertNull(reader.editorProfile.value)
        assertFalse(reader.canSave(other))
    }

    @Test fun startup_identity_waits_through_read_failure_and_returns_only_confirmed_data() = runTest {
        var reads = 0
        val reader = PatientProfileRead(backgroundScope, flow {
            if (++reads == 1) error("synthetic")
            emit(original)
        })
        val first = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { reader.firstConfirmed() }
        runCurrent()
        assertEquals(LocalReadState.Failed, reader.state.value)
        assertFalse(first.isCompleted)
        reader.retry(); runCurrent()
        assertEquals(original, first.await())
        assertEquals(2, reads)
    }

    @Test fun only_the_default_to_generated_id_change_is_allowed() = runTest {
        val stored = UserProfileEntity(patientId = com.example.data.ingest.IngestPayloadMapper.DEFAULT_PATIENT_ID, fullName = "")
        val reader = PatientProfileRead(backgroundScope, flowOf(stored))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.state.collect() }
        runCurrent()
        assertTrue(reader.canSave(stored.copy(fullName = "Ana", patientId = "PAT-ANA-1B2C")))
        assertFalse(reader.canSave(stored.copy(fullName = "Ana", patientId = "OUTRO")))
        val generated = PatientProfileRead(backgroundScope, flowOf(stored.copy(patientId = "PAT-ANA-1B2C")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { generated.state.collect() }
        runCurrent()
        assertFalse(generated.canSave(stored.copy(patientId = "PAT-BIA-0000")))
        assertTrue(generated.canSave(stored.copy(patientId = "PAT-ANA-1B2C", fullName = "Bia")))
    }

    @Test fun empty_store_allows_creating_first_profile_from_blank_form() = runTest {
        val reader = PatientProfileRead(backgroundScope, flowOf(null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reader.state.collect() }
        runCurrent()
        val blank = com.example.ui.components.blankPatientProfile()
        assertEquals("", blank.fullName)
        assertTrue(reader.canSave(blank.copy(fullName = "Paciente novo")))
        assertTrue(reader.canSave(blank.copy(fullName = "Paciente novo", patientId = "PAT-PACIENTE-00AF")))
        assertFalse(reader.canSave(original))
    }
}
