package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.localWriteTransaction
import com.example.data.remote.RetrofitClient
import com.example.data.repository.WearableRepository

class HBandIngestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? com.example.HBandHealthSyncApp ?: return Result.failure()
        if (!app.storageStartup.awaitReady()) return Result.failure()
        RetrofitClient.initialize(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = WearableRepository(
            queueDao = database.ingestQueueDao(),
            sensorMetricDao = database.sensorMetricDao(),
            apiService = RetrofitClient.apiService,
            ingestTransportProvider = { RetrofitClient.captureIngestTransport() },
            localWriteTransaction = database.localWriteTransaction(),
        )

        val result = repository.processQueueDetailed()
        // Auth / client errors are permanent until the user fixes the key or payload.
        // Retry only when the network or the server was transiently unavailable.
        return if (result.hadTransientFailure && result.authError == null && result.configurationError == null) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
