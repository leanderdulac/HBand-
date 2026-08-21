package com.example.data.repository

import com.example.data.hband.HBandBleManager
import com.example.data.local.HBandSensorMetricDao
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.IngestQueueDao
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.data.model.HBandTelemetry
import com.example.data.remote.HealthTechApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ApiHealthState(
    val isOnline: Boolean = false,
    val statusCode: Int? = null,
    val latencyMs: Long = 0,
    val message: String = "Not checked",
    val lastCheckTime: Long = 0
)

class WearableRepository(
    private val queueDao: IngestQueueDao,
    private val sensorMetricDao: HBandSensorMetricDao,
    private val apiService: HealthTechApiService
) {
    val allQueueItems: Flow<List<IngestQueueEntity>> = queueDao.getAllItems()
    val allSensorMetrics: Flow<List<HBandSensorMetricEntity>> = sensorMetricDao.getAllMetrics()
    val latestSensorMetric: Flow<HBandSensorMetricEntity?> = sensorMetricDao.getLatestMetric()

    private val _apiHealth = MutableStateFlow(ApiHealthState())
    val apiHealth: StateFlow<ApiHealthState> = _apiHealth.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    suspend fun enqueueTelemetry(telemetry: HBandTelemetry, patientId: String = "PAT-HBAND-001"): Long = withContext(Dispatchers.IO) {
        // Save local sensor metric snapshot to Room
        val metricEntity = HBandSensorMetricEntity(
            deviceId = telemetry.deviceId,
            timestamp = telemetry.timestamp,
            heartRate = telemetry.heartRate,
            systolicBp = telemetry.bloodPressure.systolic,
            diastolicBp = telemetry.bloodPressure.diastolic,
            spO2 = telemetry.spO2,
            temperatureCelsius = telemetry.temperatureCelsius,
            steps = telemetry.steps,
            calories = telemetry.calories,
            distanceMeters = telemetry.distanceMeters,
            hrvScore = telemetry.hrvScore,
            deepSleepMinutes = telemetry.sleepSummary.deepSleepMinutes,
            lightSleepMinutes = telemetry.sleepSummary.lightSleepMinutes,
            awakeMinutes = telemetry.sleepSummary.awakeMinutes
        )
        sensorMetricDao.insertMetric(metricEntity)

        // Enqueue payload JSON into Room offline ingest queue with patientId
        val json = HBandBleManager.telemetryToJson(telemetry, patientId)
        val entity = IngestQueueEntity(
            payloadJson = json,
            status = QueueStatus.PENDING.name
        )
        val id = queueDao.insertItem(entity)
        // Automatically process queue so telemetry is synced immediately
        try { processQueue() } catch (_: Exception) {}
        id
    }

    suspend fun enqueueRawJson(json: String): Long = withContext(Dispatchers.IO) {
        val entity = IngestQueueEntity(
            payloadJson = json,
            status = QueueStatus.PENDING.name
        )
        val id = queueDao.insertItem(entity)
        try { processQueue() } catch (_: Exception) {}
        id
    }

    suspend fun enqueueBatchSimulatedReadings(
        bleManager: HBandBleManager,
        count: Int
    ) = withContext(Dispatchers.IO) {
        val device = bleManager.connectedDevice.value ?: com.example.data.model.HBandDevice()
        val now = System.currentTimeMillis()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (i in 0 until count) {
            val backdatedTime = now - (count - i) * 60000L
            val timestampStr = isoFormat.format(Date(backdatedTime))
            val baseTelemetry = bleManager.generateCurrentTelemetry(device)
            val telemetry = baseTelemetry.copy(
                timestamp = timestampStr,
                heartRate = baseTelemetry.heartRate + kotlin.random.Random.nextInt(-5, 6),
                steps = baseTelemetry.steps + i * 50
            )

            // Insert into local sensor metric table
            val metricEntity = HBandSensorMetricEntity(
                deviceId = device.deviceId,
                timestamp = timestampStr,
                timestampMillis = backdatedTime,
                heartRate = telemetry.heartRate,
                systolicBp = telemetry.bloodPressure.systolic,
                diastolicBp = telemetry.bloodPressure.diastolic,
                spO2 = telemetry.spO2,
                temperatureCelsius = telemetry.temperatureCelsius,
                steps = telemetry.steps,
                calories = telemetry.calories,
                distanceMeters = telemetry.distanceMeters,
                hrvScore = telemetry.hrvScore,
                deepSleepMinutes = telemetry.sleepSummary.deepSleepMinutes,
                lightSleepMinutes = telemetry.sleepSummary.lightSleepMinutes,
                awakeMinutes = telemetry.sleepSummary.awakeMinutes
            )
            sensorMetricDao.insertMetric(metricEntity)

            val json = HBandBleManager.telemetryToJson(telemetry)
            queueDao.insertItem(
                IngestQueueEntity(
                    payloadJson = json,
                    status = QueueStatus.PENDING.name,
                    createdAt = backdatedTime
                )
            )
        }
        try { processQueue() } catch (_: Exception) {}
    }

    suspend fun checkApiHealth(): ApiHealthState = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val response = apiService.checkHealth()
            val latency = System.currentTimeMillis() - startTime
            val healthState = if (response.isSuccessful) {
                ApiHealthState(
                    isOnline = true,
                    statusCode = response.code(),
                    latencyMs = latency,
                    message = "HealthTech Secure API Online (200 OK)",
                    lastCheckTime = System.currentTimeMillis()
                )
            } else {
                ApiHealthState(
                    isOnline = false,
                    statusCode = response.code(),
                    latencyMs = latency,
                    message = "API returned HTTP ${response.code()}",
                    lastCheckTime = System.currentTimeMillis()
                )
            }
            _apiHealth.value = healthState
            healthState
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val healthState = ApiHealthState(
                isOnline = false,
                statusCode = null,
                latencyMs = latency,
                message = "Connection Error: ${e.localizedMessage ?: "Network unreachable"}",
                lastCheckTime = System.currentTimeMillis()
            )
            _apiHealth.value = healthState
            healthState
        }
    }

    suspend fun processQueue(): Int = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext 0
        _isSyncing.value = true
        var syncedCount = 0

        try {
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
                _lastSyncResult.value = "Fila limpa. Nenhum item pendente para sincronizar."
                _isSyncing.value = false
                return@withContext 0
            }

            var consecutiveNetworkErrors = 0
            for (item in pendingList) {
                // If consecutive network failures occurred, halt batch processing to avoid inflating retry counters
                if (consecutiveNetworkErrors >= 2) {
                    _lastSyncResult.value = "Conexão com servidor indisponível. ${pendingList.size - syncedCount} dados preservados com segurança no banco local (Room DB)."
                    break
                }

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

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = jsonPayload.toRequestBody(mediaType)

                try {
                    val response = apiService.ingestWearableData(requestBody)
                    val now = System.currentTimeMillis()

                    if (response.isSuccessful) {
                        consecutiveNetworkErrors = 0
                        queueDao.updateItem(
                            item.copy(
                                payloadJson = jsonPayload,
                                status = QueueStatus.SYNCED.name,
                                lastAttemptAt = now,
                                errorMessage = null
                            )
                        )
                        syncedCount++
                    } else {
                        consecutiveNetworkErrors++
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        val newRetries = (item.retries + 1).coerceAtMost(3)
                        val newStatus = if (newRetries >= 3) {
                            QueueStatus.FAILED.name
                        } else {
                            QueueStatus.PENDING.name
                        }

                        queueDao.updateItem(
                            item.copy(
                                payloadJson = jsonPayload,
                                status = newStatus,
                                retries = newRetries,
                                lastAttemptAt = now,
                                errorMessage = "HTTP ${response.code()}: $errorBody"
                            )
                        )
                    }
                } catch (e: Exception) {
                    consecutiveNetworkErrors++
                    val now = System.currentTimeMillis()
                    val newRetries = (item.retries + 1).coerceAtMost(3)
                    val newStatus = if (newRetries >= 3) QueueStatus.FAILED.name else QueueStatus.PENDING.name

                    queueDao.updateItem(
                        item.copy(
                            payloadJson = jsonPayload,
                            status = newStatus,
                            retries = newRetries,
                            lastAttemptAt = now,
                            errorMessage = e.localizedMessage ?: "Aguardando conexão com servidor"
                        )
                    )
                }
            }

            if (syncedCount > 0) {
                _lastSyncResult.value = "Sincronizados $syncedCount itens com sucesso para o servidor."
            } else {
                _lastSyncResult.value = "Dados preservados com segurança no banco local (Room DB). Sincronização pendente aguardando conectividade."
            }
        } finally {
            _isSyncing.value = false
        }
        syncedCount
    }

    suspend fun markAllAsLocalSynced() = withContext(Dispatchers.IO) {
        val allItems = queueDao.getAllItemsSync()
        val now = System.currentTimeMillis()
        for (item in allItems) {
            if (item.status != QueueStatus.SYNCED.name) {
                queueDao.updateItem(
                    item.copy(
                        status = QueueStatus.SYNCED.name,
                        retries = 0,
                        lastAttemptAt = now,
                        errorMessage = "Salvo localmente no Room Database"
                    )
                )
            }
        }
        _lastSyncResult.value = "Todos os registros marcados como salvos e sincronizados localmente!"
    }

    suspend fun retryFailedItem(id: Long) = withContext(Dispatchers.IO) {
        val items = queueDao.getFailedItems()
        items.find { it.id == id }?.let { failedItem ->
            queueDao.updateItem(
                failedItem.copy(
                    status = QueueStatus.PENDING.name,
                    retries = 0,
                    errorMessage = null
                )
            )
        }
    }

    suspend fun retryAllFailed() = withContext(Dispatchers.IO) {
        val failedItems = queueDao.getFailedItems()
        for (item in failedItems) {
            queueDao.updateItem(
                item.copy(
                    status = QueueStatus.PENDING.name,
                    retries = 0,
                    errorMessage = null
                )
            )
        }
        _lastSyncResult.value = "${failedItems.size} itens re-enfileirados para sincronização."
    }

    suspend fun deleteQueueItem(id: Long) = withContext(Dispatchers.IO) {
        queueDao.deleteById(id)
    }

    suspend fun clearSyncedItems() = withContext(Dispatchers.IO) {
        queueDao.clearSyncedItems()
    }

    suspend fun clearAllQueue() = withContext(Dispatchers.IO) {
        queueDao.clearAll()
    }

    suspend fun clearAllSensorMetrics() = withContext(Dispatchers.IO) {
        sensorMetricDao.clearAllMetrics()
    }
}
