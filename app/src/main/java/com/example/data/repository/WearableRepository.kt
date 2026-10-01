package com.example.data.repository

import com.example.data.hband.VeepooHistoryMapper
import com.example.data.ingest.IngestReconciler
import com.example.data.ingest.IngestItemOutcome
import com.example.data.ingest.PreparedReading
import com.example.data.ingest.IngestHttpKind
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.ingest.QueueAuthorization
import com.example.data.local.AdvancedMeasurementDao
import com.example.data.local.AdvancedMeasurementEntity
import com.example.data.local.HBandSensorMetricDao
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.IngestQueueDao
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.data.local.LocalWriteTransaction
import com.example.data.model.HBandTelemetry
import com.example.data.remote.HealthTechApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

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
    val message: String = "",
    /** Still pending among the records selected for this execution, not the whole queue. */
    val pendingCount: Int = 0,
    val configurationError: String? = null,
) {
    val hasIncompleteItems: Boolean
        get() = configurationError != null || authError != null || hadTransientFailure || failedCount > 0 || pendingCount > 0

    fun patientMessage(): String {
        val counts = if (syncedCount + failedCount + pendingCount > 0) {
            "Nesta tentativa: Concluídos no aplicativo: $syncedCount. Com falha: $failedCount. Aguardando envio: $pendingCount. "
        } else ""
        return when {
            configurationError != null -> configurationError
            authError != null -> counts + QueueAuthorization.PAUSED_MESSAGE
            counts.isNotEmpty() -> counts + "Confira a tela Envios."
            else -> message.ifBlank { "Tentativa de envio concluída. Confira os registros na tela Envios." }
        }
    }
}

/** The HTTP result may already exist remotely; do not treat a local write failure as a network failure. */
class QueuePersistenceException(cause: Exception) : Exception("Unable to persist queue processing result", cause)

class WearableRepository(
    private val queueDao: IngestQueueDao,
    private val sensorMetricDao: HBandSensorMetricDao,
    private val apiService: HealthTechApiService,
    private val localWriteTransaction: LocalWriteTransaction,
    private val advancedMeasurementDao: AdvancedMeasurementDao? = null,
    private val maxBatchItems: Int = IngestReconciler.BATCH_MAX_ITEMS,
    private val ingestTransportProvider: () -> com.example.data.remote.IngestTransport = {
        com.example.data.remote.IngestTransport(service = apiService)
    },
) {
    val allQueueItems: Flow<List<IngestQueueEntity>> = queueDao.getAllItems()
    val allSensorMetrics: Flow<List<HBandSensorMetricEntity>> = sensorMetricDao.getAllMetrics()
    val latestSensorMetric: Flow<HBandSensorMetricEntity?> = sensorMetricDao.getLatestMetric()
    val allAdvancedMeasurements: Flow<List<AdvancedMeasurementEntity>> =
        advancedMeasurementDao?.getAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    private val _apiHealth = MutableStateFlow(ApiHealthState())
    val apiHealth: StateFlow<ApiHealthState> = _apiHealth.asStateFlow()

    // UI, BLE history and WorkManager construct separate repositories for the same
    // app database. Admission and its observable state must be shared in this process.
    private companion object {
        val queueOperation = Mutex()
        val _isSyncing = MutableStateFlow(false)
    }
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    /** Counters observed from readSportStep stay local; no HTTP, queue flush or copied vitals. */
    suspend fun persistSportReading(deviceId: String, reading: com.example.data.hband.VeepooSportReading): Long =
        withContext(Dispatchers.IO) {
            require(deviceId.isNotBlank()) { "Sport reading requires its observed device" }
            sensorMetricDao.insertMetric(HBandSensorMetricEntity(
                deviceId = deviceId,
                timestamp = VeepooHistoryMapper.isoUtc(reading.observedAtMillis),
                timestampMillis = reading.observedAtMillis,
                steps = reading.steps ?: 0,
                calories = reading.calories ?: 0f,
                distanceMeters = reading.distanceMeters ?: 0f,
                heartRate = 0, systolicBp = 0, diastolicBp = 0, spO2 = 0,
                temperatureCelsius = 0f, hrvScore = 0,
                deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
            ))
        }

    suspend fun enqueueTelemetry(telemetry: HBandTelemetry, patientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID): Long =
        withContext(Dispatchers.IO) {
            val id = persistTelemetry(telemetry, patientId)
            if (id < 0) return@withContext id
            try {
                processQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            id
        }

    /** Manual capture needs the actual attempt result, without a second implicit retry. */
    suspend fun enqueueAndProcessTelemetry(telemetry: HBandTelemetry, patientId: String): QueueProcessResult =
        withContext(Dispatchers.IO) {
            require(IngestPayloadMapper.isIngestible(telemetry)) { "Telemetry is not eligible for ingestion" }
            persistTelemetry(telemetry, patientId)
            checkApiHealth()
            processQueueDetailed()
        }

    /** Local transaction only; callers may request queue processing after the commit. */
    internal suspend fun persistTelemetry(telemetry: HBandTelemetry, patientId: String): Long =
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
            var queueId = -1L
            localWriteTransaction.run {
                sensorMetricDao.insertMetric(metricEntity)
                if (IngestPayloadMapper.isIngestible(telemetry)) {
                    queueId = queueDao.insertItem(IngestQueueEntity(
                        payloadJson = IngestPayloadMapper.telemetryToJson(telemetry, patientId),
                        status = QueueStatus.PENDING.name,
                    ))
                }
            }
            queueId
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
        val observedTimes = samples.map { VeepooHistoryMapper.parseIsoToMillis(it.timestamp) }
        val entities = samples.mapIndexed { index, telemetry ->
            val epoch = observedTimes[index].takeIf { it > 0L } ?: System.currentTimeMillis()
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
        val hourly = samples.indices
            .filter { IngestPayloadMapper.isIngestible(samples[it]) }
            .groupBy { index ->
                val telemetry = samples[index]
                val bucket = VeepooHistoryMapper.parseIsoToMillis(telemetry.timestamp) / 3_600_000L
                telemetry.deviceId to bucket
            }
            .values
            .mapNotNull { group -> group.maxByOrNull { samples[it].heartRate } }

        var insertedCount = 0
        localWriteTransaction.run {
            // Only exact stored snapshots are coalesced. A different timestamp,
            // device or value remains a separate row, including corrections.
            // Ignore the generated local row ID, never delete/rewrite old rows.
            val known = HashSet<HBandSensorMetricEntity>()
            for ((deviceId, window) in entities.groupBy { it.deviceId }) {
                sensorMetricDao.getHistoryWindow(deviceId,
                    window.minOf { it.timestampMillis }, window.maxOf { it.timestampMillis })
                    .mapTo(known) { it.copy(id = 0) }
            }
            // An invalid source timestamp has no stable observation time. Do not
            // treat the insertion-time fallback as evidence of a replay.
            val fresh = entities.filterIndexed { index, entity -> observedTimes[index] <= 0L || known.add(entity) }
            sensorMetricDao.insertMetrics(fresh)
            insertedCount = fresh.size
            val admitted = fresh.toHashSet()
            // Keep the existing per-hour selection over the complete pull. An
            // already stored winner must not acquire another queue ID/receipt.
            for (index in hourly) {
                if (entities[index] !in admitted) continue
                val telemetry = samples[index]
                queueDao.insertItem(
                    IngestQueueEntity(
                        payloadJson = IngestPayloadMapper.telemetryToJson(telemetry, patientId),
                        status = QueueStatus.PENDING.name,
                    )
                )
            }
        }
        try {
            processQueue()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
        insertedCount
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
        val heart = entity.numericValue.toInt()
        val shouldEnqueue = entity.kind == com.example.data.local.AdvancedMeasurementKind.ECG &&
            IngestPayloadMapper.isIngestibleHeartRate(heart)
        val json = if (shouldEnqueue) IngestPayloadMapper.telemetryToJson(
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
            ) else null
        localWriteTransaction.run {
            advancedMeasurementDao?.insert(entity)
            if (json != null) {
                queueDao.insertItem(IngestQueueEntity(payloadJson = json, status = QueueStatus.PENDING.name))
            }
        }
        if (shouldEnqueue) {
            try {
                processQueue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    suspend fun enqueueRawJson(json: String): Long = withContext(Dispatchers.IO) {
        val entity = IngestQueueEntity(
            payloadJson = json,
            status = QueueStatus.PENDING.name
        )
        val id = queueDao.insertItem(entity)
        try {
            processQueue()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
        id
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
                    IngestPayloadMapper.authErrorMessage(response.code(), null)
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
            response.errorBody()?.close()
            _apiHealth.value = healthState
            healthState
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val healthState = ApiHealthState(
                isOnline = false,
                statusCode = null,
                latencyMs = latency,
                message = "Não foi possível verificar a conexão com o serviço. Confira a configuração e a rede.",
                lastCheckTime = System.currentTimeMillis()
            )
            _apiHealth.value = healthState
            healthState
        }
    }

    suspend fun processQueue(): Int = processQueueDetailed().syncedCount

    suspend fun processQueueDetailed(): QueueProcessResult =
        processQueueDetailed(retryFailed = false, retryId = null)

    private suspend fun processQueueDetailed(retryFailed: Boolean, retryId: Long?): QueueProcessResult = withContext(Dispatchers.IO) {
        if (!queueOperation.tryLock()) {
            return@withContext QueueProcessResult(message = "Outra operação da fila está em andamento. Aguarde e tente novamente.")
        }
        var syncedCount = 0
        var failedCount = 0
        var skippedCount = 0
        var authError: String? = null
        var hadTransientFailure = false

        try {
            _isSyncing.value = true
            // Persisted queue evidence is shared by UI, BLE and workers, and survives restart.
            // Explicit retry may try again; automatic processing must not repeatedly probe auth.
            val authorizationBlocked = queueDao.hasAuthorizationBlock(
                QueueAuthorization.UNAUTHORIZED_PREFIX,
                QueueAuthorization.FORBIDDEN_PREFIX,
            )
            if (authorizationBlocked && !retryFailed) {
                val result = QueueProcessResult(authError = QueueAuthorization.PAUSED_MESSAGE)
                _lastSyncResult.value = result.patientMessage()
                return@withContext result.copy(message = result.patientMessage())
            }
            // Capture before changing any stored record; use this service for every chunk.
            val transport = ingestTransportProvider()
            if (transport.configurationError != null) {
                val result = QueueProcessResult(configurationError = transport.configurationError)
                _lastSyncResult.value = result.patientMessage()
                return@withContext result.copy(message = result.patientMessage())
            }
            val ingestService = checkNotNull(transport.service)
            if (retryFailed) {
                // Requeue and send under the same admission gate used by automatic processing.
                // A late click must not reset another processor's attempt or a completed item.
                val failedItems = queueDao.getAllItemsSync().filter {
                    (it.status == QueueStatus.FAILED.name || QueueAuthorization.isBlocked(it)) &&
                        (retryId == null || it.id == retryId)
                }
                if (failedItems.isEmpty()) {
                    val message = "Nenhum registro com falha disponível para esta tentativa. Confira a fila atualizada."
                    _lastSyncResult.value = message
                    return@withContext QueueProcessResult(message = message)
                }
                for (item in failedItems) {
                    // Keep the auth evidence until a response is recorded. Cancellation during
                    // a manual retry must not silently reopen automatic sends.
                    queueDao.updateItem(item.copy(status = QueueStatus.PENDING.name, retries = 0,
                        errorMessage = item.errorMessage.takeIf { QueueAuthorization.isBlocked(item) }))
                }
            }
            val pendingList = queueDao.getPendingItems()

            if (pendingList.isEmpty()) {
                val message = "Não há registros aguardando envio nesta tentativa. Confira a tela Envios."
                _lastSyncResult.value = message
                return@withContext QueueProcessResult(message = message)
            }

            val prepared = mutableListOf<PreparedReading>()
            for (item in pendingList) {
                val reading = runCatching { IngestReconciler.prepare(item) }.getOrNull()
                val localError = when {
                    reading == null -> IngestReconciler.INVALID_LOCAL
                    !IngestPayloadMapper.isIngestibleJson(reading.json) -> IngestPayloadMapper.MISSING_HR_ERROR
                    !IngestPayloadMapper.isCompatibleSpo2Json(reading.json) -> IngestPayloadMapper.INVALID_SPO2_ERROR
                    !IngestPayloadMapper.isCompatibleIngestSourceJson(reading.json) -> IngestPayloadMapper.INVALID_SOURCE_ERROR
                    !IngestPayloadMapper.isCompatibleFilterTypeJson(reading.json) -> IngestPayloadMapper.INVALID_FILTER_ERROR
                    else -> null
                }
                if (localError != null) {
                    skippedCount++
                    failedCount++
                    persistQueueResult(item.copy(
                        status = QueueStatus.FAILED.name,
                        lastAttemptAt = System.currentTimeMillis(),
                        errorMessage = item.errorMessage.takeIf { QueueAuthorization.isBlocked(item) }
                            ?: localError,
                    ))
                } else {
                    prepared += reading!!
                }
            }
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            var consecutiveNetworkErrors = 0
            for (chunk in IngestReconciler.chunks(prepared, maxBatchItems)) {
                if (consecutiveNetworkErrors >= 2 || authError != null) break
                var responseCode: Int? = null
                // Derivation is deterministic from persisted payload + persisted ID. No new
                // timestamp/identity is generated at flush, even if a receipt write is lost.
                // Only transport/body reads belong in this catch; persistence below must abort.
                val decisions = try {
                    // Batch also for one item: its envelope echoes the request ID even when
                    // a natural-key duplicate returns an older frame with a different/no ID.
                    val response = ingestService.batchIngestWearableData(
                        IngestReconciler.batchBody(chunk).toRequestBody(mediaType),
                        IngestReconciler.flushIdempotencyKey(chunk),
                    )
                    responseCode = response.code()
                    response.errorBody()?.close()
                    IngestReconciler.batch(chunk, response.code(), response.body()?.use { it.string() })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    chunk.map { IngestReconciler.unconfirmed() }
                }
                if (decisions.any { it.outcome == IngestItemOutcome.TRANSIENT }) {
                    consecutiveNetworkErrors++
                } else {
                    consecutiveNetworkErrors = 0
                }
                for ((reading, decision) in chunk.zip(decisions)) {
                    val item = reading.item
                    val transient = decision.outcome == IngestItemOutcome.TRANSIENT
                    val denied = decision.outcome == IngestItemOutcome.AUTH
                    val retries = if (transient || decision.outcome == IngestItemOutcome.CLIENT_ERROR)
                        item.retries + 1 else item.retries
                    val status = when {
                        decision.confirmed -> QueueStatus.SYNCED.name
                        transient && retries < 5 -> QueueStatus.PENDING.name
                        else -> QueueStatus.FAILED.name
                    }
                    val keepAuthEvidence = QueueAuthorization.isBlocked(item) &&
                        (transient || responseCode in listOf(408, 429))
                    persistQueueResult(item.copy(
                        status = status,
                        retries = retries,
                        lastAttemptAt = System.currentTimeMillis(),
                        errorMessage = if (decision.confirmed) null
                            else if (keepAuthEvidence) item.errorMessage else decision.message,
                    ))
                    if (decision.confirmed) syncedCount++
                    if (status == QueueStatus.FAILED.name) failedCount++
                    if (transient) hadTransientFailure = true
                    if (denied) authError = decision.message
                }
            }
            val result = QueueProcessResult(
                syncedCount = syncedCount,
                failedCount = failedCount,
                skippedCount = skippedCount,
                authError = authError,
                hadTransientFailure = hadTransientFailure,
                pendingCount = pendingList.size - syncedCount - failedCount,
            )
            val message = result.patientMessage()
            _lastSyncResult.value = message
            result.copy(message = message)
        } finally {
            _isSyncing.value = false
            queueOperation.unlock()
        }
    }

    private suspend fun persistQueueResult(item: IngestQueueEntity) {
        try {
            queueDao.updateItem(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw QueuePersistenceException(error)
        }
    }

    suspend fun retryFailedItem(id: Long): QueueProcessResult =
        processQueueDetailed(retryFailed = true, retryId = id)

    suspend fun retryAllFailed(): QueueProcessResult =
        processQueueDetailed(retryFailed = true, retryId = null)

    suspend fun deleteQueueItem(id: Long): Boolean = removeFromQueue {
        queueDao.deleteById(id)
    }

    suspend fun clearSyncedItems(): Boolean = removeFromQueue {
        queueDao.clearSyncedItems()
    }

    suspend fun clearAllQueue(): Boolean = removeFromQueue {
        queueDao.clearAll()
    }

    /** Never wait behind an in-flight send and then silently delete its evidence. */
    private suspend fun removeFromQueue(action: suspend () -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!queueOperation.tryLock()) return@withContext false
        try {
            action()
            true
        } finally {
            queueOperation.unlock()
        }
    }

    suspend fun clearAllSensorMetrics() = withContext(Dispatchers.IO) {
        sensorMetricDao.clearAllMetrics()
        advancedMeasurementDao?.clearAll()
    }
}
