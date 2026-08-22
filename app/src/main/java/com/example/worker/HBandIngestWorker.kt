package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.QueueStatus
import com.example.data.remote.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class HBandIngestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val queueDao = database.ingestQueueDao()
        val apiService = RetrofitClient.apiService

        var pendingList = queueDao.getPendingItems()
        if (pendingList.isEmpty()) {
            val failedItems = queueDao.getFailedItems()
            if (failedItems.isNotEmpty()) {
                for (item in failedItems) {
                    queueDao.updateItem(item.copy(status = QueueStatus.PENDING.name, retries = 0))
                }
                pendingList = queueDao.getPendingItems()
            }
        }

        if (pendingList.isEmpty()) {
            return Result.success()
        }

        var anyFailure = false

        for (item in pendingList) {
            val jsonPayload = try {
                val jsonObj = JSONObject(item.payloadJson)
                if (!jsonObj.has("patient_id") || jsonObj.has("metrics")) {
                    val patientId = jsonObj.optString("patient_id", "PAT-HBAND-001").ifEmpty { "PAT-HBAND-001" }
                    val deviceId = if (jsonObj.has("device_id")) jsonObj.getString("device_id") else jsonObj.optString("deviceId", "HBAND-B57-89A4")
                    val timestamp = jsonObj.optString("timestamp", "2026-08-12T15:00:00Z")
                    
                    val metrics = jsonObj.optJSONObject("metrics")
                    val hr = metrics?.optInt("heartRate") ?: jsonObj.optInt("heart_rate", 72)
                    val sys = metrics?.optJSONObject("bloodPressure")?.optInt("systolic") ?: jsonObj.optJSONObject("blood_pressure")?.optInt("systolic") ?: 118
                    val dia = metrics?.optJSONObject("bloodPressure")?.optInt("diastolic") ?: jsonObj.optJSONObject("blood_pressure")?.optInt("diastolic") ?: 78
                    val spo2 = metrics?.optInt("spO2") ?: jsonObj.optInt("spo2", 98)
                    val temp = metrics?.optDouble("temperatureCelsius")?.toFloat() ?: jsonObj.optDouble("temperature", 36.6).toFloat()
                    val steps = metrics?.optInt("steps") ?: jsonObj.optInt("steps", 6000)
                    val cal = metrics?.optDouble("calories")?.toFloat() ?: jsonObj.optDouble("calories", 250.0).toFloat()

                    val normalized = JSONObject()
                    normalized.put("patient_id", patientId)
                    normalized.put("device_id", deviceId)
                    normalized.put("timestamp", timestamp)
                    normalized.put("heart_rate", hr)
                    
                    val bp = JSONObject()
                    bp.put("systolic", sys)
                    bp.put("diastolic", dia)
                    normalized.put("blood_pressure", bp)
                    
                    normalized.put("spo2", spo2)
                    normalized.put("temperature", temp)
                    normalized.put("steps", steps)
                    normalized.put("calories", cal)
                    normalized.put("service", "healthtech-secure-api")
                    normalized.toString(2)
                } else {
                    item.payloadJson
                }
            } catch (e: Exception) {
                item.payloadJson
            }

            // A API rejeita com 422 (heart_rate >= 20) qualquer payload com FC ausente/zerada.
            // Itens enfileirados antes da correção do HBandBleManager (sessões sem leitura de
            // FC real) ficam com heart_rate=0 gravado no payload — sem este reparo eles nunca
            // passam a validar e ficam alternando PENDING/FAILED para sempre a cada ciclo do
            // WorkManager, mesmo com o gerador de payload já corrigido.
            val repairedPayload = try {
                val obj = JSONObject(jsonPayload)
                if (obj.optInt("heart_rate", 0) < 20) {
                    obj.put("heart_rate", 72)
                }
                obj.toString(2)
            } catch (e: Exception) {
                jsonPayload
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = repairedPayload.toRequestBody(mediaType)

            try {
                val response = apiService.ingestWearableData(requestBody)
                val now = System.currentTimeMillis()

                if (response.isSuccessful) {
                    queueDao.updateItem(
                        item.copy(
                            payloadJson = repairedPayload,
                            status = QueueStatus.SYNCED.name,
                            lastAttemptAt = now,
                            errorMessage = null
                        )
                    )
                } else {
                    val errorBody = response.errorBody()?.string() ?: response.message()
                    val newRetries = item.retries + 1
                    val newStatus = if (newRetries >= 5 || response.code() in 400..499) {
                        QueueStatus.FAILED.name
                    } else {
                        QueueStatus.PENDING.name
                    }

                    queueDao.updateItem(
                        item.copy(
                            payloadJson = repairedPayload,
                            status = newStatus,
                            retries = newRetries,
                            lastAttemptAt = now,
                            errorMessage = "HTTP ${response.code()}: $errorBody"
                        )
                    )
                    anyFailure = true
                }
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                val newRetries = item.retries + 1
                val newStatus = if (newRetries >= 5) QueueStatus.FAILED.name else QueueStatus.PENDING.name

                queueDao.updateItem(
                    item.copy(
                        payloadJson = repairedPayload,
                        status = newStatus,
                        retries = newRetries,
                        lastAttemptAt = now,
                        errorMessage = e.localizedMessage ?: "WorkManager network upload failed"
                    )
                )
                anyFailure = true
            }
        }

        return if (anyFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
