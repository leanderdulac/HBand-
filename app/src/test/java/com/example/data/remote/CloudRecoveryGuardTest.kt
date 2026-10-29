package com.example.data.remote

import android.app.Application
import android.content.ContextWrapper
import com.example.data.local.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CloudRecoveryGuardTest {
    @Test fun both_entry_points_refuse_before_accessing_any_platform_dependency() = runBlocking {
        val unusable = ContextWrapper(null)
        for (result in listOf(FirestoreBackupManager.backupRoomMetricsToFirestore(unusable),
            FirestoreBackupManager.restoreRoomMetricsFromFirestore(unusable))) {
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is CloudBackupContractRequiredException)
            assertEquals(FirestoreBackupManager.UNAVAILABLE_MESSAGE, result.exceptionOrNull()?.message)
        }
    }

    @Test fun existing_metrics_and_queue_remain_unchanged_when_both_actions_are_requested() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = AppDatabase.schemaPreservingBuilder(context).build()
        try {
            val metric = HBandSensorMetricEntity(id = 42, deviceId = "SYNTHETIC-WATCH",
                timestamp = "2026-09-25T12:00:00Z", timestampMillis = 1234L,
                heartRate = 0, systolicBp = 0, diastolicBp = 0, spO2 = 0, temperatureCelsius = 0f,
                steps = 20, calories = 0f, distanceMeters = 0f, hrvScore = 0,
                deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0)
            val row = IngestQueueEntity(id = 42, payloadJson = "SYNTHETIC-UNCHANGED",
                clientReadingId = "synthetic-preserved-id", status = "FAILED", retries = 3,
                createdAt = 1234L, lastAttemptAt = 5678L,
                errorMessage = com.example.data.ingest.QueueAuthorization.UNAUTHORIZED_PREFIX)
            db.sensorMetricDao().insertMetric(metric)
            db.ingestQueueDao().insertItem(row)
            db.close()
            val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val before = file.readBytes()
            assertTrue(FirestoreBackupManager.backupRoomMetricsToFirestore(context).exceptionOrNull() is CloudBackupContractRequiredException)
            assertTrue(FirestoreBackupManager.restoreRoomMetricsFromFirestore(context).exceptionOrNull() is CloudBackupContractRequiredException)
            assertTrue(before.contentEquals(file.readBytes()))
            val reopened = AppDatabase.schemaPreservingBuilder(context).build()
            try {
                assertEquals(listOf(metric), reopened.sensorMetricDao().getAllMetricsList())
                assertEquals(listOf(row), reopened.ingestQueueDao().getAllItemsSync())
            } finally { reopened.close() }
        } finally { db.close() }
    }

    @Test fun cancellation_is_propagated_instead_of_returning_a_recovery_result() = runBlocking {
        for (restore in listOf(false, true)) {
            var returned = false
            val job = launch {
                currentCoroutineContext().cancel()
                if (restore) FirestoreBackupManager.restoreRoomMetricsFromFirestore(ContextWrapper(null))
                else FirestoreBackupManager.backupRoomMetricsToFirestore(ContextWrapper(null))
                returned = true
            }
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(returned)
        }
    }
}
