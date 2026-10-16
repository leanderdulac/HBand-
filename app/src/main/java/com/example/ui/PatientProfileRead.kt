package com.example.ui

import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** Retain an editor baseline during read failure, but never authorize a save from it. */
internal class PatientProfileRead(scope: CoroutineScope, source: Flow<UserProfileEntity?>) {
    private val retries = MutableStateFlow(0)
    private val editor = MutableStateFlow<UserProfileEntity?>(null)
    val editorProfile = editor.asStateFlow()
    val state = source.onEach { editor.value = it }.localReadState(scope, retries)

    fun retry() { retries.value += 1 }

    fun canSave(profile: UserProfileEntity): Boolean {
        val read = state.value as? LocalReadState.Ready ?: return false
        // Confirmed empty store: allow creating the single CURRENT_USER row.
        val confirmed = read.value ?: return profile.id == UserProfileEntity().id &&
            profile.patientId == com.example.data.ingest.IngestPayloadMapper.DEFAULT_PATIENT_ID
        return confirmed.id == profile.id && confirmed.patientId == profile.patientId
    }

    suspend fun firstConfirmed(): UserProfileEntity? =
        state.filterIsInstance<LocalReadState.Ready<UserProfileEntity?>>().first().value
}
