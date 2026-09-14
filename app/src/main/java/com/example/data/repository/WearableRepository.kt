package com.example.data.repository

import com.example.data.hband.HBandBleManager
import com.example.data.ingest.IngestHttpKind
import com.example.data.ingest.IngestPayloadMapper
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

data class QueueProcessResult(
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val authError: String? = null,
    val hadTransientFailure: Boolean = false,
    val message: String = ""
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

    suspend fun enqueueTelemetry(telemetry: HBandTelemetry, patientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID): Long =
        withContext(Dispatchers.IO) {
            val metricEntity = HBandSensorMetricEntity(
                deviceId = IngestPayloadMapper.resolveDeviceId(telemetry.deviceId),
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

            if (!IngestPayloadMapper.isIngestible(telemetry)) {
                return@withContext -1L
            }

            val json = IngestPayloadMapper.telemetryToJson(telemetry, patientId)
            val entity = IngestQueueEntity(
                payloadJson = json,
                status = QueueStatus.PENDING.name
            )
            val id = queueDao.insertItem(entity)
            try {
                processQueue()
            } catch (_: Exception) {
            }
            id
        }

    suspend fun enqueueRawJson(json: String): Long = withContext(Dispatchers.IO) {
        val entity = IngestQueueEntity(
            payloadJson = json,
            status = QueueStatus.PENDING.name
        )
        val id = queueDao.insertItem(entity)
        try {
            processQueue()
        } catch (_: Exception) {
        }
        id
    }

    suspend fun enqueueBatchSimulatedReadings(
        bleManager: HBandBleManager,
        count: Int
    ) = withContext(Dispatchers.IO) {
        val device = bleManager.connectedDevice.value
        val now = System.currentTimeMillis()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (i in 0 until count) {
            val backdatedTime = now - (count - i) * 60000L
            val timestampStr = isoFormat.format(Date(backdatedTime))
            val baseTelemetry = bleManager.generateCurrentTelemetry()
            val baseHeartRate = if (baseTelemetry.heartRate > 0) baseTelemetry.heartRate else 72
            val baseSteps = if (baseTelemetry.steps > 0) baseTelemetry.steps else 1000
            val deviceId = IngestPayloadMapper.resolveDeviceId(
                device?.macAddress ?: device?.deviceId,
                baseTelemetry.deviceId
            )
            val telemetry = baseTelemetry.copy(
                deviceId = if (IngestPayloadMapper.isPlaceholderDeviceId(deviceId)) "SIM-${backdatedTime}" else deviceId,
                timestamp = timestampStr,
                heartRate = (baseHeartRate + kotlin.random.Random.nextInt(-5, 6)).coerceIn(50, 160),
                steps = baseSteps + i * 50,
                isRealSensorData = false
            )

            val metricEntity = HBandSensorMetricEntity(
                deviceId = telemetry.deviceId,
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

            val json = IngestPayloadMapper.telemetryToJson(telemetry, IngestPayloadMapper.DEFAULT_PATIENT_ID)
            queueDao.insertItem(
                IngestQueueEntity(
                    payloadJson = json,
                    status = QueueStatus.PENDING.name,
                    createdAt = backdatedTime
                )
            )
        }
        try {
            processQueue()
        } catch (_: Exception) {
        }
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
                val kind = IngestPayloadMapper.classifyHttp(response.code())
                val message = if (kind == IngestHttpKind.AUTH) {
                    IngestPayloadMapper.authErrorMessage(response.code(), response.errorBody()?.string())
                } else {
                    "API returned HTTP ${response.code()}"
                }
                ApiHealthState(
                    isOnline = false,
                    statusCode = response.code(),
                    latencyMs = latency,
                    message = message,
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

    suspend fun processQueue(): Int = processQueueDetailed().syncedCount

    suspend fun processQueueDetailed(): QueueProcessResult = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext QueueProcessResult(message = "Sincronização já em andamento.")
        }
        _isSyncing.value = true
        var syncedCount = 0
        var failedCount = 0
        var skippedCount = 0
        var authError: String? = null
        var hadTransientFailure = false

        try {
            val pendingList = queueDao.getPendingItems()

            if (pendingList.isEmpty()) {
                val message = "Fila limpa. Nenhum item pendente para sincronizar."
                _lastSyncResult.value = message
                return@withContext QueueProcessResult(message = message)
            }

            var consecutiveNetworkErrors = 0
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            for (item in pendingList) {
                if (consecutiveNetworkErrors >= 2) {
                    hadTransientFailure = true
                    break
                }
                if (authError != null) {
                    break
                }

                val jsonPayload = try {
                    IngestPayloadMapper.normalizeQueuePayload(item.payloadJson)
                } catch (_: Exception) {
                    item.payloadJson
                }

                if (!IngestPayloadMapper.isIngestibleJson(jsonPayload)) {
                    skippedCount++
                    failedCount++
                    queueDao.updateItem(
                        item.copy(
                            payloadJson = jsonPayload,
                            status = QueueStatus.FAILED.name,
                            lastAttemptAt = System.currentTimeMillis(),
                            errorMessage = IngestPayloadMapper.MISSING_HR_ERROR
                        )
                    )
                    continue
                }

                val requestBody = jsonPayload.toRequestBody(mediaType)

                try {
                    val response = apiService.ingestWearableData(requestBody)
                    val now = System.currentTimeMillis()
                    val kind = IngestPayloadMapper.classifyHttp(response.code())

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
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        when (kind) {
                            IngestHttpKind.AUTH -> {
                                authError = IngestPayloadMapper.authErrorMessage(response.code(), errorBody)
                                queueDao.updateItem(
                                    item.copy(
                                        payloadJson = jsonPayload,
                                        status = QueueStatus.FAILED.name,
                                        lastAttemptAt = now,
                                        errorMessage = authError
                                    )
                                )
                                failedCount++
                            }
                            IngestHttpKind.CLIENT -> {
                                queueDao.updateItem(
                                    item.copy(
                                        payloadJson = jsonPayload,
                                        status = QueueStatus.FAILED.name,
                                        retries = item.retries + 1,
                                        lastAttemptAt = now,
                                        errorMessage = "HTTP ${response.code()}: $errorBody"
                                    )
                                )
                                failedCount++
                            }
                            else -> {
                                consecutiveNetworkErrors++
                                hadTransientFailure = true
                                val newRetries = item.retries + 1
                                val newStatus = if (newRetries >= 5) {
                                    failedCount++
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
                        }
                    }
                } catch (e: Exception) {
                    consecutiveNetworkErrors++
                    hadTransientFailure = true
                    val now = System.currentTimeMillis()
                    val newRetries = item.retries + 1
                    val newStatus = if (newRetries >= 5) {
                        failedCount++
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
                            errorMessage = e.localizedMessage ?: "Aguardando conexão com servidor"
                        )
                    )
                }
            }

            val message = when {
                authError != null -> authError!!
                consecutiveNetworkErrors >= 2 ->
                    "Conexão com servidor indisponível. Dados preservados com segurança no banco local (Room DB)."
                syncedCount > 0 && failedCount == 0 ->
                    "Sincronizados $syncedCount itens com sucesso para o servidor."
                syncedCount > 0 ->
                    "Sincronizados $syncedCount itens. $failedCount falharam."
                skippedCount > 0 && syncedCount == 0 ->
                    IngestPayloadMapper.MISSING_HR_ERROR
                else ->
                    "Dados preservados com segurança no banco local (Room DB). Sincronização pendente aguardando conectividade."
            }
            _lastSyncResult.value = message
            QueueProcessResult(
                syncedCount = syncedCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                authError = authError,
                hadTransientFailure = hadTransientFailure,
                message = message
            )
        } finally {
            _isSyncing.value = false
        }
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
