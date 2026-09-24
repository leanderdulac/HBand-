package com.example.data.repository

import android.util.Log
import com.example.data.hband.HBandBleManager
import com.example.data.hband.VeepooHistoryMapper
import com.example.data.ingest.AuthBackoffState
import com.example.data.ingest.IngestApiKey
import com.example.data.ingest.IngestDiagnostics
import com.example.data.ingest.IngestHttpKind
import com.example.data.ingest.IngestItemDecision
import com.example.data.ingest.IngestItemOutcome
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.ingest.IngestReconciler
import com.example.data.local.AdvancedMeasurementDao
import com.example.data.local.AdvancedMeasurementEntity
import com.example.data.local.HBandSensorMetricDao
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.IngestQueueDao
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.data.model.HBandTelemetry
import com.example.data.remote.HealthTechApiService
import com.example.data.remote.RetrofitClient
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
import java.util.UUID

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
    val configurationError: String? = null,
    val hadTransientFailure: Boolean = false,
    val lastHttpStatus: Int? = null,
    val message: String = ""
)

class WearableRepository(
    private val queueDao: IngestQueueDao,
    private val sensorMetricDao: HBandSensorMetricDao,
    private val apiService: HealthTechApiService,
    private val advancedMeasurementDao: AdvancedMeasurementDao? = null,
) {
    val allQueueItems: Flow<List<IngestQueueEntity>> = queueDao.getAllItems()
    val allSensorMetrics: Flow<List<HBandSensorMetricEntity>> = sensorMetricDao.getAllMetrics()
    val latestSensorMetric: Flow<HBandSensorMetricEntity?> = sensorMetricDao.getLatestMetric()
    val allAdvancedMeasurements: Flow<List<AdvancedMeasurementEntity>> =
        advancedMeasurementDao?.getAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    private val _apiHealth = MutableStateFlow(ApiHealthState())
    val apiHealth: StateFlow<ApiHealthState> = _apiHealth.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    private val _lastHttpStatus = MutableStateFlow<Int?>(null)
    val lastHttpStatus: StateFlow<Int?> = _lastHttpStatus.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var authBackoff: AuthBackoffState? = null

    fun currentDiagnostics(queuedCount: Int = 0): IngestDiagnostics {
        val configured = RetrofitClient.isKeyConfigured
        return IngestDiagnostics(
            baseUrl = RetrofitClient.currentBaseUrl.trimEnd('/'),
            keyConfigured = configured,
            usingSettingsOverride = RetrofitClient.hasSettingsKeyOverride,
            lastHttpStatus = _lastHttpStatus.value,
            queuedCount = queuedCount,
            lastError = _lastError.value ?: _lastSyncResult.value,
            configurationError = if (configured) null else IngestApiKey.configurationError(),
        )
    }

    suspend fun enqueueTelemetry(telemetry: HBandTelemetry, patientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID): Long =
        withContext(Dispatchers.IO) {
            val metricEntity = HBandSensorMetricEntity(
                deviceId = IngestPayloadMapper.resolveDeviceId(telemetry.deviceId),
                timestamp = telemetry.timestamp,
                timestampMillis = VeepooHistoryMapper.parseIsoToMillis(telemetry.timestamp)
                    .takeIf { it > 0L } ?: System.currentTimeMillis(),
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

            val entity = newQueueEntity(IngestPayloadMapper.telemetryToJson(telemetry, patientId))
            val id = queueDao.insertItem(entity)
            try {
                processQueue()
            } catch (_: Exception) {
            }
            id
        }

    /**
     * Persists multi-day Origin/sleep/HRV/SpO2 samples to Room. HealthTech ingest
     * only receives hourly samples that already have a real heart rate — never
     * fabricated vitals and never a 5-minute flood.
     */
    suspend fun persistHistorySamples(
        samples: List<HBandTelemetry>,
        patientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID,
    ): Int = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext 0
        val entities = samples.map { telemetry ->
            val epoch = VeepooHistoryMapper.parseIsoToMillis(telemetry.timestamp)
                .takeIf { it > 0L } ?: System.currentTimeMillis()
            HBandSensorMetricEntity(
                deviceId = IngestPayloadMapper.resolveDeviceId(telemetry.deviceId),
                timestamp = telemetry.timestamp,
                timestampMillis = epoch,
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
                awakeMinutes = telemetry.sleepSummary.awakeMinutes,
            )
        }
        sensorMetricDao.insertMetrics(entities)

        val hourly = samples
            .filter { IngestPayloadMapper.isIngestible(it) }
            .groupBy { telemetry ->
                val bucket = VeepooHistoryMapper.parseIsoToMillis(telemetry.timestamp) / 3_600_000L
                telemetry.deviceId to bucket
            }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.heartRate } }

        for (telemetry in hourly) {
            queueDao.insertItem(newQueueEntity(IngestPayloadMapper.telemetryToJson(telemetry, patientId)))
        }
        try {
            processQueue()
        } catch (_: Exception) {
        }
        entities.size
    }

    /**
     * Persists a real P1 detect sample locally. HealthTech ingest only happens
     * when the sample includes a real heart rate (ECG average) — glucose and
     * composition never invent an HR just to pass the API contract.
     */
    suspend fun persistAdvancedSample(
        entity: AdvancedMeasurementEntity,
        patientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID,
    ) = withContext(Dispatchers.IO) {
        if (!entity.isReal) return@withContext
        advancedMeasurementDao?.insert(entity)
        val heart = entity.numericValue.toInt()
        if (entity.kind == com.example.data.local.AdvancedMeasurementKind.ECG &&
            IngestPayloadMapper.isIngestibleHeartRate(heart)
        ) {
            val json = IngestPayloadMapper.telemetryToJson(
                HBandTelemetry(
                    deviceId = entity.deviceId,
                    deviceModel = "VE30",
                    timestamp = entity.timestamp,
                    heartRate = heart,
                    bloodPressure = com.example.data.model.BloodPressure(0, 0),
                    spO2 = 0,
                    temperatureCelsius = 0f,
                    steps = 0,
                    calories = 0f,
                    distanceMeters = 0f,
                    hrvScore = entity.secondaryValue.toInt().coerceAtLeast(0),
                    sleepSummary = com.example.data.model.SleepSummary(0, 0, 0),
                    isRealSensorData = true,
                ),
                patientId,
            )
            queueDao.insertItem(newQueueEntity(json))
            try {
                processQueue()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Writes a real sport / live snapshot to Room without inventing HR or
     * enqueueing HealthTech ingest. Used for daily steps/kcal from
     * `readSportStep` when the live HR path has not fired yet.
     */
    suspend fun persistLocalTelemetry(telemetry: HBandTelemetry) = withContext(Dispatchers.IO) {
        val metricEntity = HBandSensorMetricEntity(
            deviceId = IngestPayloadMapper.resolveDeviceId(telemetry.deviceId),
            timestamp = telemetry.timestamp,
            timestampMillis = VeepooHistoryMapper.parseIsoToMillis(telemetry.timestamp)
                .takeIf { it > 0L } ?: System.currentTimeMillis(),
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
    }

    suspend fun enqueueRawJson(json: String): Long = withContext(Dispatchers.IO) {
        val entity = newQueueEntity(json)
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
            queueDao.insertItem(newQueueEntity(json, createdAt = backdatedTime))
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
        var configurationError: String? = null
        var hadTransientFailure = false
        var lastHttpStatus: Int? = _lastHttpStatus.value

        try {
            val pendingList = queueDao.getPendingItems()

            if (pendingList.isEmpty()) {
                val message = "Fila limpa. Nenhum item pendente para sincronizar."
                _lastSyncResult.value = message
                return@withContext QueueProcessResult(message = message, lastHttpStatus = lastHttpStatus)
            }

            val key = RetrofitClient.apiKey
            if (!IngestApiKey.isUsable(key)) {
                configurationError = IngestApiKey.configurationError()
                Log.w(TAG, "Ingest skipped: $configurationError")
                val now = System.currentTimeMillis()
                for (item in pendingList) {
                    queueDao.updateItem(
                        item.copy(
                            lastAttemptAt = now,
                            errorMessage = configurationError,
                        )
                    )
                }
                _lastError.value = configurationError
                _lastSyncResult.value = configurationError
                return@withContext QueueProcessResult(
                    configurationError = configurationError,
                    lastHttpStatus = lastHttpStatus,
                    message = configurationError!!,
                )
            }

            val fingerprint = IngestReconciler.keyFingerprint(key)
            if (IngestReconciler.shouldSkipServerCall(System.currentTimeMillis(), fingerprint, authBackoff)) {
                authError = authBackoff?.lastAuthHttp?.let { IngestReconciler.authMessage(it) }
                    ?: IngestReconciler.AUTH_INVALID_MESSAGE
                Log.w(TAG, "Ingest backoff active after HTTP ${authBackoff?.lastAuthHttp}")
                _lastError.value = authError
                _lastSyncResult.value = authError
                return@withContext QueueProcessResult(
                    authError = authError,
                    lastHttpStatus = authBackoff?.lastAuthHttp,
                    message = authError!!,
                )
            }

            val prepared = mutableListOf<Pair<IngestQueueEntity, String>>()
            for (item in pendingList) {
                val jsonPayload = try {
                    IngestPayloadMapper.normalizeQueuePayload(item.payloadJson)
                } catch (_: Exception) {
                    item.payloadJson
                }
                val ensured = IngestReconciler.ensureReadingPayload(jsonPayload, item.clientReadingId)
                if (!IngestPayloadMapper.isIngestibleJson(ensured)) {
                    skippedCount++
                    failedCount++
                    queueDao.updateItem(
                        item.copy(
                            payloadJson = ensured,
                            status = QueueStatus.FAILED.name,
                            lastAttemptAt = System.currentTimeMillis(),
                            errorMessage = IngestPayloadMapper.MISSING_HR_ERROR
                        )
                    )
                    continue
                }
                prepared += item.copy(payloadJson = ensured) to ensured
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val chunks = prepared.chunked(IngestReconciler.BATCH_MAX_ITEMS)

            chunkLoop@ for (chunk in chunks) {
                if (authError != null) break
                val items = chunk.map { it.first }
                val payloads = chunk.map { it.second }
                val ids = items.map { it.clientReadingId }
                val patientId = IngestReconciler.patientIdFrom(payloads.first())
                val idempotencyKey = IngestReconciler.flushIdempotencyKey(ids)

                val decisions: List<IngestItemDecision>
                try {
                    val batchJson = IngestReconciler.buildBatchBody(patientId, payloads)
                    val batchResponse = apiService.batchIngestWearableData(
                        body = batchJson.toRequestBody(mediaType),
                        idempotencyKey = idempotencyKey,
                    )
                    lastHttpStatus = batchResponse.code()
                    _lastHttpStatus.value = lastHttpStatus

                    if (batchResponse.code() == 404) {
                        Log.w(TAG, "batch-ingest returned 404; falling back to single ingest")
                        decisions = flushSingles(items, payloads, mediaType)
                        lastHttpStatus = _lastHttpStatus.value
                    } else {
                        val body = if (batchResponse.isSuccessful) {
                            batchResponse.body()?.string()
                        } else {
                            batchResponse.errorBody()?.string() ?: batchResponse.message()
                        }
                        decisions = IngestReconciler.decisionsForHttp(
                            clientReadingIds = ids,
                            httpCode = batchResponse.code(),
                            responseBody = body,
                        )
                    }
                } catch (e: Exception) {
                    hadTransientFailure = true
                    lastHttpStatus = null
                    decisions = IngestReconciler.decisionsForHttp(
                        clientReadingIds = ids,
                        httpCode = 0,
                        responseBody = null,
                        networkError = true,
                    ).map { it.copy(errorMessage = e.localizedMessage ?: it.errorMessage) }
                }

                val now = System.currentTimeMillis()
                for ((item, decision) in items.zip(decisions)) {
                    applyDecision(item, decision, now)
                    when {
                        decision.markSynced -> syncedCount++
                        decision.outcome == IngestItemOutcome.REJECTED ||
                            decision.outcome == IngestItemOutcome.CLIENT_ERROR -> failedCount++
                        decision.outcome == IngestItemOutcome.TRANSIENT -> hadTransientFailure = true
                        decision.outcome == IngestItemOutcome.AUTH_INVALID ||
                            decision.outcome == IngestItemOutcome.AUTH_FORBIDDEN -> {
                            authError = decision.errorMessage
                            authBackoff = AuthBackoffState(
                                lastAuthHttp = decision.httpStatus,
                                lastAuthAtMs = now,
                                keyFingerprint = fingerprint,
                            )
                        }
                    }
                }
                if (authError != null) break@chunkLoop
            }

            val message = when {
                configurationError != null -> configurationError!!
                authError != null -> authError!!
                hadTransientFailure && syncedCount == 0 ->
                    "Conexão com servidor indisponível. Dados preservados com segurança no banco local (Room DB)."
                syncedCount > 0 && failedCount == 0 ->
                    "Sincronizados $syncedCount itens com sucesso para o servidor."
                syncedCount > 0 ->
                    "Sincronizados $syncedCount itens. $failedCount rejeitados."
                skippedCount > 0 && syncedCount == 0 ->
                    IngestPayloadMapper.MISSING_HR_ERROR
                else ->
                    "Dados preservados com segurança no banco local (Room DB). Sincronização pendente aguardando conectividade."
            }
            _lastSyncResult.value = message
            if (authError != null || configurationError != null || hadTransientFailure || failedCount > 0) {
                _lastError.value = message
            } else if (syncedCount > 0) {
                _lastError.value = null
            }
            QueueProcessResult(
                syncedCount = syncedCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                authError = authError,
                configurationError = configurationError,
                hadTransientFailure = hadTransientFailure,
                lastHttpStatus = lastHttpStatus,
                message = message
            )
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun flushSingles(
        items: List<IngestQueueEntity>,
        payloads: List<String>,
        mediaType: okhttp3.MediaType?,
    ): List<IngestItemDecision> {
        val decisions = mutableListOf<IngestItemDecision>()
        for ((item, payload) in items.zip(payloads)) {
            try {
                val response = apiService.ingestWearableData(
                    body = payload.toRequestBody(mediaType),
                    idempotencyKey = item.clientReadingId,
                )
                _lastHttpStatus.value = response.code()
                val body = if (response.isSuccessful) {
                    val ingested = response.body()
                    val status = when {
                        ingested?.ingest_status.equals("duplicate", ignoreCase = true) -> "duplicate"
                        ingested?.duplicate == true -> "duplicate"
                        else -> ingested?.ingest_status ?: "accepted"
                    }
                    """{"ingest_status":"$status"}"""
                } else {
                    response.errorBody()?.string() ?: response.message()
                }
                val parsed = IngestReconciler.decisionsForHttp(
                    listOf(item.clientReadingId),
                    response.code(),
                    body,
                )
                decisions += parsed
                if (parsed.firstOrNull()?.outcome == IngestItemOutcome.AUTH_INVALID ||
                    parsed.firstOrNull()?.outcome == IngestItemOutcome.AUTH_FORBIDDEN
                ) {
                    break
                }
            } catch (e: Exception) {
                decisions += IngestReconciler.decisionsForHttp(
                    listOf(item.clientReadingId),
                    httpCode = 0,
                    responseBody = null,
                    networkError = true,
                ).map { it.copy(errorMessage = e.localizedMessage ?: it.errorMessage) }
                break
            }
        }
        val remaining = items.drop(decisions.size)
        if (remaining.isNotEmpty()) {
            decisions += remaining.map { item ->
                IngestItemDecision(
                    clientReadingId = item.clientReadingId,
                    outcome = IngestItemOutcome.TRANSIENT,
                    markSynced = false,
                    keepQueued = true,
                    errorMessage = "Flush interrompido após falha anterior.",
                )
            }
        }
        return decisions
    }

    private suspend fun applyDecision(item: IngestQueueEntity, decision: IngestItemDecision, now: Long) {
        val status = when {
            decision.markSynced -> QueueStatus.SYNCED.name
            decision.keepQueued -> QueueStatus.PENDING.name
            else -> QueueStatus.FAILED.name
        }
        val retries = if (decision.outcome == IngestItemOutcome.TRANSIENT) item.retries + 1 else item.retries
        val finalStatus = if (
            decision.outcome == IngestItemOutcome.TRANSIENT && retries >= 5
        ) {
            QueueStatus.FAILED.name
        } else {
            status
        }
        queueDao.updateItem(
            item.copy(
                status = finalStatus,
                retries = retries,
                lastAttemptAt = now,
                errorMessage = if (decision.markSynced) null else decision.errorMessage,
            )
        )
    }

    private fun newQueueEntity(payloadJson: String, createdAt: Long = System.currentTimeMillis()): IngestQueueEntity {
        val clientReadingId = UUID.randomUUID().toString()
        val json = IngestReconciler.ensureReadingPayload(payloadJson, clientReadingId)
        return IngestQueueEntity(
            payloadJson = json,
            status = QueueStatus.PENDING.name,
            createdAt = createdAt,
            clientReadingId = clientReadingId,
        )
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

    fun clearAuthBackoff() {
        authBackoff = null
    }

    suspend fun retryAllFailed() = withContext(Dispatchers.IO) {
        clearAuthBackoff()
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
        advancedMeasurementDao?.clearAll()
    }

    companion object {
        private const val TAG = "WearableRepository"
    }
}
