package com.example.worker

import android.app.Application
import android.app.Service
import android.content.Intent
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.example.HBandHealthSyncApp
import com.example.data.hband.HBandBleService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class StartupConsumersTest {
    private fun params(): WorkerParameters {
        val direct = Executor { it.run() }
        return WorkerParameters(UUID.randomUUID(), Data.EMPTY, emptyList(), WorkerParameters.RuntimeExtras(), 0, 0,
            direct, Dispatchers.Unconfined, WorkManagerTaskExecutor(direct), object : WorkerFactory() {
                override fun createWorker(appContext: android.content.Context, workerClassName: String,
                    workerParameters: WorkerParameters): ListenableWorker? = null
            },
            ProgressUpdater { _, _, _ -> error("No progress on blocked storage") },
            ForegroundUpdater { _, _, _ -> error("No foreground work on blocked storage") })
    }

    @Test fun ingest_waits_for_storage_while_disabled_cloud_worker_fails_immediately() = runBlocking {
        // Unattached app deliberately cannot supply prefs/Room/Firebase/network.
        // Reaching those dependencies would fail this test instead of returning Result.failure.
        val app = HBandHealthSyncApp()
        try {
            val ingest = async { HBandIngestWorker(app, params()).doWork() }
            val backup = async { FirestoreSyncWorker(app, params()).doWork() }
            yield()
            assertFalse(ingest.isCompleted); assertTrue(backup.isCompleted)
            app.storageStartup.initialize({ error("Synthetic storage failure") }, { error("No runtime") })
            assertEquals(ListenableWorker.Result.failure(), ingest.await())
            assertEquals(ListenableWorker.Result.failure(), backup.await())
            assertNull(app.readyBleManagerOrNull())
        } finally { app.bleScope.cancel() }
    }

    @Test fun worker_cancellation_does_not_change_startup_state() = runBlocking {
        val app = HBandHealthSyncApp()
        try {
            val worker = launch { HBandIngestWorker(app, params()).doWork() }
            yield(); worker.cancelAndJoin()
            assertTrue(worker.isCancelled)
            assertEquals(com.example.data.local.StorageStartupState.OPENING, app.storageStartup.state.value)
        } finally { app.bleScope.cancel() }
    }

    @Test fun cloud_contract_unavailable_is_failure_not_retry_even_after_storage_ready() = runBlocking {
        val app = HBandHealthSyncApp()
        try {
            app.storageStartup.initialize({}, {})
            // Unattached context: reaching Firebase/Room/preferences would fail the test.
            assertEquals(ListenableWorker.Result.failure(), FirestoreSyncWorker(app, params()).doWork())
        } finally { app.bleScope.cancel() }
    }

    @Test fun recreated_service_without_ready_manager_stops_without_foreground() {
        val controller = Robolectric.buildService(HBandBleService::class.java).create()
        try {
            val service = controller.get()
            assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(), 0, 1))
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertNull(shadowOf(service).lastForegroundNotification)
        } finally { controller.destroy() }
    }
}
