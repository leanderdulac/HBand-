package com.example.ui

import com.example.data.local.LocalWellnessRecords
import kotlinx.coroutines.CancellationException

/** Reports local diary writes without retrying or inferring rollback on failure. */
internal class PatientWellnessActions(
    private val records: LocalWellnessRecords,
    private val notify: (String, Boolean) -> Unit,
) {
    suspend fun addWaterIntake(amountMl: Int) = perform(
        success = "Mais $amountMl mL de água registrados neste celular.",
        failure = "Não foi possível confirmar o registro de água neste celular. Confira o total antes de registrar novamente.",
    ) { records.addWaterIntake(amountMl) }

    suspend fun resetTodayHydration() = perform(
        success = "Registros de água de hoje apagados neste celular.",
        failure = "Não foi possível confirmar a exclusão dos registros de água de hoje. Confira o total antes de tentar novamente.",
    ) { records.resetTodayHydration() }

    suspend fun saveBreathingSession(durationSeconds: Int) {
        if (durationSeconds <= 0) return
        perform(
            success = "Tempo de respiração salvo neste celular: ${durationSeconds / 60} min ${durationSeconds % 60} s.",
            failure = "Não foi possível confirmar o registro de respiração neste celular. Confira o tempo total salvo.",
        ) { records.saveBreathingSession(durationSeconds) }
    }

    private suspend fun perform(success: String, failure: String, write: suspend () -> Unit) {
        try {
            write()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            notify(failure, true)
            return
        }
        // A notification failure must not be misreported as a storage failure.
        notify(success, false)
    }
}
