package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.HBandSensorMetricEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirestoreBackupManager {

    private const val TAG = "FirestoreBackupManager"
    private const val COLLECTION_HEALTH_METRICS = "health_metrics_backup"

    suspend fun backupRoomMetricsToFirestore(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val metricDao = database.sensorMetricDao()
            val metrics = metricDao.getAllMetricsList()

            if (metrics.isEmpty()) {
                return@withContext Result.success(0)
            }

            var firestore: FirebaseFirestore? = null
            try {
                firestore = FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore default instance unavailable: ${e.localizedMessage}")
            }

            if (firestore != null) {
                var backedUpCount = 0
                val collectionRef = firestore.collection(COLLECTION_HEALTH_METRICS)

                // Batch or individual document set
                for (item in metrics) {
                    val docId = "${item.deviceId}_${item.timestampMillis}"
                    val docData = hashMapOf(
                        "id" to item.id,
                        "deviceId" to item.deviceId,
                        "timestamp" to item.timestamp,
                        "timestampMillis" to item.timestampMillis,
                        "heartRate" to item.heartRate,
                        "systolicBp" to item.systolicBp,
                        "diastolicBp" to item.diastolicBp,
                        "spO2" to item.spO2,
                        "temperatureCelsius" to item.temperatureCelsius,
                        "steps" to item.steps,
                        "calories" to item.calories,
                        "distanceMeters" to item.distanceMeters,
                        "hrvScore" to item.hrvScore,
                        "deepSleepMinutes" to item.deepSleepMinutes,
                        "lightSleepMinutes" to item.lightSleepMinutes,
                        "awakeMinutes" to item.awakeMinutes,
                        "syncedAtMillis" to System.currentTimeMillis()
                    )

                    collectionRef.document(docId).set(docData, SetOptions.merge()).await()
                    backedUpCount++
                }
                Result.success(backedUpCount)
            } else {
                // Firebase isn't configured in this build (no google-services.json), so there is
                // nowhere to actually back up to. Fail honestly instead of reporting a fake success.
                Result.failure(IllegalStateException("Backup na nuvem indisponível: Firebase não configurado neste build (google-services.json ausente)."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up to Firestore: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun restoreRoomMetricsFromFirestore(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var firestore: FirebaseFirestore? = null
            try {
                firestore = FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore instance unavailable: ${e.localizedMessage}")
            }

            if (firestore != null) {
                val snapshot = firestore.collection(COLLECTION_HEALTH_METRICS).get().await()
                val database = AppDatabase.getDatabase(context)
                val metricDao = database.sensorMetricDao()

                var restoredCount = 0
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val entity = HBandSensorMetricEntity(
                        deviceId = data["deviceId"] as? String ?: "HBAND_FIREBASE_RESTORED",
                        timestamp = data["timestamp"] as? String ?: "",
                        timestampMillis = (data["timestampMillis"] as? Long) ?: System.currentTimeMillis(),
                        heartRate = (data["heartRate"] as? Long)?.toInt() ?: 72,
                        systolicBp = (data["systolicBp"] as? Long)?.toInt() ?: 120,
                        diastolicBp = (data["diastolicBp"] as? Long)?.toInt() ?: 80,
                        spO2 = (data["spO2"] as? Long)?.toInt() ?: 98,
                        temperatureCelsius = (data["temperatureCelsius"] as? Double)?.toFloat() ?: 36.6f,
                        steps = (data["steps"] as? Long)?.toInt() ?: 0,
                        calories = (data["calories"] as? Double)?.toFloat() ?: 0f,
                        distanceMeters = (data["distanceMeters"] as? Double)?.toFloat() ?: 0f,
                        hrvScore = (data["hrvScore"] as? Long)?.toInt() ?: 65,
                        deepSleepMinutes = (data["deepSleepMinutes"] as? Long)?.toInt() ?: 0,
                        lightSleepMinutes = (data["lightSleepMinutes"] as? Long)?.toInt() ?: 0,
                        awakeMinutes = (data["awakeMinutes"] as? Long)?.toInt() ?: 0
                    )
                    metricDao.insertMetric(entity)
                    restoredCount++
                }
                Result.success(restoredCount)
            } else {
                Result.failure(IllegalStateException("Restauração da nuvem indisponível: Firebase não configurado neste build (google-services.json ausente)."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from Firestore: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
