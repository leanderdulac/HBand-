package com.example.ui

import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProfileSaveStatus { SAVING, SAVED, UNCONFIRMED }

/** Volatile UI receipt; token is not a patient ID or a durable transaction key. */
data class ProfileSaveState(val token: String, val profileId: String, val patientId: String, val status: ProfileSaveStatus)

internal class PatientProfileSave(
    private val scope: CoroutineScope,
    private val write: suspend (UserProfileEntity) -> Unit,
    private val afterSaved: (UserProfileEntity) -> Unit,
) {
    private val lock = Any()
    private val usedTokens = mutableSetOf<String>()
    private val current = MutableStateFlow<ProfileSaveState?>(null)
    val state = current.asStateFlow()

    fun save(token: String, profile: UserProfileEntity) {
        val pending = ProfileSaveState(token, profile.id, profile.patientId, ProfileSaveStatus.SAVING)
        synchronized(lock) {
            if (token.isBlank() || token in usedTokens || current.value?.status == ProfileSaveStatus.SAVING) return
            usedTokens.add(token)
            current.value = pending
        }
        val job = scope.launch {
            try {
                write(profile)
                current.compareAndSet(pending, pending.copy(status = ProfileSaveStatus.SAVED))
                // The write receipt is independent of later local identity/notification work.
                afterSaved(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // No rollback, replay or success is inferred from an exception.
            }
        }
        job.invokeOnCompletion {
            // Also covers cancellation before the coroutine body was entered.
            current.compareAndSet(pending, pending.copy(status = ProfileSaveStatus.UNCONFIRMED))
        }
    }
}
