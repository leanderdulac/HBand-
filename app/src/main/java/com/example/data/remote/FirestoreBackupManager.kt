package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** No remote read/write or local mutation until recovery scope and authorization are confirmed. */
class CloudBackupContractRequiredException : IllegalStateException(FirestoreBackupManager.UNAVAILABLE_MESSAGE)

object FirestoreBackupManager {
    const val UNAVAILABLE_MESSAGE = "Cópia e recuperação em nuvem ainda não estão disponíveis. " +
        "Não limpe os dados nem reinstale o aplicativo para tentar recuperar registros. " +
        "Peça orientação à equipe responsável."

    // Keep these entry points for existing callers/work requests, but fail before
    // touching Context, Room, Firebase or a transport. Configuration alone cannot unlock them.
    @Suppress("UNUSED_PARAMETER")
    suspend fun backupRoomMetricsToFirestore(context: Context): Result<Int> = unavailable()

    @Suppress("UNUSED_PARAMETER")
    suspend fun restoreRoomMetricsFromFirestore(context: Context): Result<Int> = unavailable()

    private suspend fun unavailable(): Result<Int> {
        currentCoroutineContext().ensureActive()
        return Result.failure(CloudBackupContractRequiredException())
    }
}
