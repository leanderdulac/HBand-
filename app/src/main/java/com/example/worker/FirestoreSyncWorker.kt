package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.remote.FirestoreBackupManager

class FirestoreSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = FirestoreBackupManager.backupRoomMetricsToFirestore(applicationContext)
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
