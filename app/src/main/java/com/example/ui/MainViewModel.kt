package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HBandHealthSyncApp
import com.example.data.hband.AlarmUiState
import com.example.data.hband.AutoMeasureUiState
import com.example.data.hband.DetectSessionUiState
import com.example.data.hband.DeviceCapabilities
import com.example.data.hband.FindDeviceUiState
import com.example.data.hband.HBandBleManager
import com.example.data.hband.HBandBleService
import com.example.data.hband.HealthRemindUiState
import com.example.data.hband.HeartWarningUiState
import com.example.data.hband.HistorySyncUiState
import com.example.data.hband.LongSeatUiState
import com.example.data.hband.NightTurnUiState
import com.example.data.hband.WearDetectUiState
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.local.AppDatabase
import com.example.data.local.localWriteTransaction
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.data.model.HBandDevice
import com.example.data.model.HBandTelemetry
import com.example.data.remote.RetrofitClient
import com.example.data.repository.ApiHealthState
import com.example.data.repository.QueueProcessResult
import com.example.data.repository.QueuePersistenceException
import com.example.data.repository.WearableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.asFlow
import androidx.work.WorkManager

import com.example.ui.components.SyncDisplayStatus
import com.example.ui.components.SyncLogEntry
import com.example.util.HrNotificationHelper
import com.example.worker.HBandWorkScheduler
import android.content.Context
import android.content.SharedPreferences

data class UiNotification(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = WearableRepository(
        queueDao = db.ingestQueueDao(),
        sensorMetricDao = db.sensorMetricDao(),
        apiService = RetrofitClient.apiService,
        ingestTransportProvider = { RetrofitClient.captureIngestTransport() },
        localWriteTransaction = db.localWriteTransaction(),
        advancedMeasurementDao = db.advancedMeasurementDao(),
    )

    val bleManager: HBandBleManager =
        (application as? HBandHealthSyncApp)?.bleManager
            ?: HBandBleManager(application.applicationContext, viewModelScope)

    val apiHealth: StateFlow<ApiHealthState> = repository.apiHealth
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val lastSyncResult: StateFlow<String?> = repository.lastSyncResult

    val ingestDiagnostics = combine(repository.allQueueItems, RetrofitClient.configurationState) { items, configuration ->
        com.example.data.ingest.IngestDiagnostics.from(items, configuration)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.ingest.IngestDiagnostics())

    val scannedDevices: StateFlow<List<HBandDevice>> = bleManager.scannedDevices
    val connectedDevice: StateFlow<HBandDevice?> = bleManager.connectedDevice
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val scanFailure = bleManager.scanFailure
    val latestTelemetry: StateFlow<HBandTelemetry?> = bleManager.latestTelemetry
    val deviceCapabilities: StateFlow<DeviceCapabilities> = bleManager.capabilities
    val autoMeasureState: StateFlow<AutoMeasureUiState> = bleManager.autoMeasureState
    val wearDetectState: StateFlow<WearDetectUiState> = bleManager.wearDetectState
    val historySyncState: StateFlow<HistorySyncUiState> = bleManager.historySyncState
    val isHardwareConnected: StateFlow<Boolean> = bleManager.isHardwareConnected
    val ecgState: StateFlow<DetectSessionUiState> = bleManager.ecgState
    val glucoseState: StateFlow<DetectSessionUiState> = bleManager.glucoseState
    val bloodComponentState: StateFlow<DetectSessionUiState> = bleManager.bloodComponentState
    val bodyComponentState: StateFlow<DetectSessionUiState> = bleManager.bodyComponentState
    val emotionState: StateFlow<DetectSessionUiState> = bleManager.emotionState
    val fatigueState: StateFlow<DetectSessionUiState> = bleManager.fatigueState
    val breathDetectState: StateFlow<DetectSessionUiState> = bleManager.breathDetectState
    val alarmState: StateFlow<AlarmUiState> = bleManager.alarmState
    val heartWarningState: StateFlow<HeartWarningUiState> = bleManager.heartWarningState
    val longSeatState: StateFlow<LongSeatUiState> = bleManager.longSeatState
    val nightTurnState: StateFlow<NightTurnUiState> = bleManager.nightTurnState
    val findDeviceState: StateFlow<FindDeviceUiState> = bleManager.findDeviceState
    val healthRemindState: StateFlow<HealthRemindUiState> = bleManager.healthRemindState

    val allSensorMetrics: StateFlow<List<HBandSensorMetricEntity>> = repository.allSensorMetrics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allQueueItems: StateFlow<List<IngestQueueEntity>> = repository.allQueueItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingCount: StateFlow<Int> = allQueueItems.combine(MutableStateFlow(0)) { items, _ ->
        items.count { it.status == QueueStatus.PENDING.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncedCount: StateFlow<Int> = allQueueItems.combine(MutableStateFlow(0)) { items, _ ->
        items.count { it.status == QueueStatus.SYNCED.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = allQueueItems.combine(MutableStateFlow(0)) { items, _ ->
        items.count { it.status == QueueStatus.FAILED.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncDisplayStatus: StateFlow<SyncDisplayStatus> = combine(
        isSyncing,
        apiHealth,
        pendingCount,
        allQueueItems
    ) { syncing, health, pending, items ->
        when {
            syncing -> SyncDisplayStatus.SYNCING
            items.any(com.example.data.ingest.QueueAuthorization::isBlocked) -> SyncDisplayStatus.AUTH_REQUIRED
            !health.isOnline -> SyncDisplayStatus.OFFLINE
            items.any { it.status == QueueStatus.FAILED.name } -> SyncDisplayStatus.FAILED
            pending > 0 -> SyncDisplayStatus.PENDING_QUEUE
            else -> SyncDisplayStatus.FULLY_SYNCED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncDisplayStatus.FULLY_SYNCED)

    val syncLogs: StateFlow<List<SyncLogEntry>> = combine(
        WorkManager.getInstance(application).getWorkInfosByTagLiveData("hband_sync_worker").asFlow(),
        allQueueItems
    ) { workInfoList, queueItems ->
        val logs = mutableListOf<SyncLogEntry>()

        // 1. Add WorkManager DB Job Entries
        workInfoList.forEachIndexed { idx, workInfo ->
            val statusStr = workInfo.state.name
            val runCount = workInfo.runAttemptCount
            val desc = "WorkManager Ingest Task #${idx + 1}"
            val detail = "State: ${workInfo.state} | Run Attempt: $runCount | Work ID: ${workInfo.id.toString().take(8)}"

            logs.add(
                SyncLogEntry(
                    id = "work_${workInfo.id}",
                    source = "WorkManager DB",
                    timestampMillis = System.currentTimeMillis() - (idx * 30000L),
                    status = statusStr,
                    summary = desc,
                    details = detail,
                    attemptCount = runCount
                )
            )
        }

        // 2. Add Room Database Queue Sync Attempt Logs
        queueItems.forEach { item ->
            val attemptTime = item.lastAttemptAt ?: item.createdAt
            val statusStr = item.status
            val summary = "Room Queue Record #${item.id}"
            val detail = if (!item.errorMessage.isNullOrBlank()) {
                "Retries: ${item.retries} | Error: ${item.errorMessage}"
            } else if (item.status == QueueStatus.SYNCED.name) {
                "Synced to HealthTech API (Payload size: ${item.payloadJson.length} bytes)"
            } else {
                "Queued in Room DB for next upload cycle"
            }

            logs.add(
                SyncLogEntry(
                    id = "queue_${item.id}_${attemptTime}",
                    source = "Room Queue Engine",
                    timestampMillis = attemptTime,
                    status = statusStr,
                    summary = summary,
                    details = detail,
                    attemptCount = item.retries
                )
            )
        }

        logs.sortedByDescending { it.timestampMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consecutiveFailures: StateFlow<Int> = combine(syncLogs, failedCount) { logs, failed ->
        var count = 0
        for (log in logs) {
            if (log.status == "FAILED") {
                count++
            } else if (log.status == "SUCCEEDED" || log.status == "SYNCED") {
                break
            }
        }
        if (count == 0 && failed > 0) failed else count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _notification = MutableStateFlow<UiNotification?>(null)
    val notification: StateFlow<UiNotification?> = _notification.asStateFlow()

    private val _selectedQueueItemForPreview = MutableStateFlow<IngestQueueEntity?>(null)
    val selectedQueueItemForPreview: StateFlow<IngestQueueEntity?> = _selectedQueueItemForPreview.asStateFlow()

    private val prefs: SharedPreferences = application.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)

    private val _autoIngestLiveReadings = MutableStateFlow(prefs.getBoolean("auto_ingest_live", true))
    val autoIngestLiveReadings: StateFlow<Boolean> = _autoIngestLiveReadings.asStateFlow()

    private val _upperHrThreshold = MutableStateFlow(prefs.getInt("upper_hr_threshold", 100))
    val upperHrThreshold: StateFlow<Int> = _upperHrThreshold.asStateFlow()

    private val _lowerHrThreshold = MutableStateFlow(prefs.getInt("lower_hr_threshold", 50))
    val lowerHrThreshold: StateFlow<Int> = _lowerHrThreshold.asStateFlow()

    private val _hrAlertsEnabled = MutableStateFlow(prefs.getBoolean("hr_alerts_enabled", true))
    val hrAlertsEnabled: StateFlow<Boolean> = _hrAlertsEnabled.asStateFlow()

    private var lastAlertTimeMs = 0L

    private val _geminiInsightText = MutableStateFlow("")
    val geminiInsightText: StateFlow<String> = _geminiInsightText.asStateFlow()

    private val _isGeneratingGeminiInsight = MutableStateFlow(false)
    val isGeneratingGeminiInsight: StateFlow<Boolean> = _isGeneratingGeminiInsight.asStateFlow()

    private val hydrationDao = db.hydrationDao()
    private val breathingDao = db.breathingDao()
    private val userProfileDao = db.userProfileDao()
    private val todayDateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

    val userProfile: StateFlow<com.example.data.local.UserProfileEntity?> = userProfileDao.getUserProfileFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _autoReconnectBle = MutableStateFlow(prefs.getBoolean("auto_reconnect_ble", true))
    val autoReconnectBle: StateFlow<Boolean> = _autoReconnectBle.asStateFlow()

    val todayHydrationMl: StateFlow<Int> = hydrationDao.getTodayTotalMlFlow(todayDateString)
        .combine(MutableStateFlow(0)) { total, _ -> total ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val totalBreathingSeconds: StateFlow<Int> = breathingDao.getTotalBreathingSecondsFlow()
        .combine(MutableStateFlow(0)) { total, _ -> total ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val hydrationTargetGoalMl: Int = 2500

    fun addWaterIntake(amountMl: Int) {
        viewModelScope.launch {
            hydrationDao.insertLog(
                com.example.data.local.HydrationLogEntity(
                    amountMl = amountMl,
                    dateString = todayDateString
                )
            )
            showNotification("Mais $amountMl mL de água registrados neste celular.")
        }
    }

    fun resetTodayHydration() {
        viewModelScope.launch {
            hydrationDao.resetTodayLogs(todayDateString)
            showNotification("Registros de água de hoje apagados neste celular.")
        }
    }

    fun saveBreathingSession(durationSeconds: Int) {
        if (durationSeconds <= 0) return
        viewModelScope.launch {
            breathingDao.insertSession(
                com.example.data.local.BreathingSessionEntity(
                    durationSeconds = durationSeconds,
                    dateString = todayDateString
                )
            )
            showNotification("Tempo de respiração salvo neste celular: ${durationSeconds / 60} min ${durationSeconds % 60} s.")
        }
    }

    fun generateGeminiInsight() {
        viewModelScope.launch {
            _isGeneratingGeminiInsight.value = true
            val metricsList = allSensorMetrics.value
            val insight = com.example.data.remote.GeminiHealthAnalyzer.generateSevenDayInsight(metricsList)
            _geminiInsightText.value = insight
            _isGeneratingGeminiInsight.value = false
        }
    }

    init {
        bleManager.isAutoReconnectEnabled = _autoReconnectBle.value
        checkHealth()

        viewModelScope.launch {
            userProfileDao.getUserProfile()?.let { bleManager.setPatientId(it.patientId) }
        }

        viewModelScope.launch {
            bleManager.sessionMessage.collect { msg ->
                if (!msg.isNullOrBlank()) {
                    showNotification(msg, isError = true)
                    bleManager.consumeSessionMessage()
                }
            }
        }

        viewModelScope.launch {
            latestTelemetry.collect { telemetry ->
                if (telemetry == null) return@collect
                if (telemetry.heartRate > 0) {
                    evaluateHeartRateThresholds(telemetry.heartRate)
                }
            }
        }

        viewModelScope.launch {
            allSensorMetrics.map { com.example.data.remote.clinicalInsightRecords(it) }.distinctUntilChanged().collect { metrics ->
                if (metrics.isNotEmpty() && _geminiInsightText.value.isEmpty() && !_isGeneratingGeminiInsight.value) {
                    _isGeneratingGeminiInsight.value = true
                    val insight = com.example.data.remote.GeminiHealthAnalyzer.generateSevenDayInsight(metrics)
                    _geminiInsightText.value = insight
                    _isGeneratingGeminiInsight.value = false
                }
            }
        }
    }

    fun setUpperHrThreshold(value: Int) {
        val clamped = value.coerceIn(80, 180)
        _upperHrThreshold.value = clamped
        prefs.edit().putInt("upper_hr_threshold", clamped).apply()
        showNotification("Limite superior de batimentos: $clamped bpm.")
    }

    fun setLowerHrThreshold(value: Int) {
        val clamped = value.coerceIn(35, 75)
        _lowerHrThreshold.value = clamped
        prefs.edit().putInt("lower_hr_threshold", clamped).apply()
        showNotification("Limite inferior de batimentos: $clamped bpm.")
    }

    fun setHrAlertsEnabled(enabled: Boolean) {
        _hrAlertsEnabled.value = enabled
        prefs.edit().putBoolean("hr_alerts_enabled", enabled).apply()
        showNotification(if (enabled) "Avisos de batimentos ativados no aplicativo. Confira também a permissão nas opções do celular." else "Avisos de batimentos desativados no aplicativo.")
    }

    private fun evaluateHeartRateThresholds(hr: Int) {
        if (!_hrAlertsEnabled.value) return

        val now = System.currentTimeMillis()
        // 8 second cooldown between repeated automatic notifications
        if (now - lastAlertTimeMs < 8000) return

        val upper = _upperHrThreshold.value
        val lower = _lowerHrThreshold.value

        if (hr > upper) {
            lastAlertTimeMs = now
            HrNotificationHelper.sendHighHrNotification(getApplication(), hr, upper)
            showNotification("Batimentos: $hr bpm. Acima do limite cadastrado de $upper bpm.", isError = true)
        } else if (hr < lower) {
            lastAlertTimeMs = now
            HrNotificationHelper.sendLowHrNotification(getApplication(), hr, lower)
            showNotification("Batimentos: $hr bpm. Abaixo do limite cadastrado de $lower bpm.", isError = true)
        }
    }

    fun testHighHrAlert() {
        val upper = _upperHrThreshold.value
        val testHr = (upper + 18).coerceAtLeast(125)
        HrNotificationHelper.sendHighHrNotification(getApplication(), testHr, upper, isTest = true)
        showNotification("Teste de aviso: $testHr bpm, acima do limite de $upper bpm. Não é uma leitura do relógio.", isError = true)
    }

    fun testLowHrAlert() {
        val lower = _lowerHrThreshold.value
        val testHr = (lower - 8).coerceAtMost(42)
        HrNotificationHelper.sendLowHrNotification(getApplication(), testHr, lower, isTest = true)
        showNotification("Teste de aviso: $testHr bpm, abaixo do limite de $lower bpm. Não é uma leitura do relógio.", isError = true)
    }

    fun setAutoReconnectBle(enabled: Boolean) {
        _autoReconnectBle.value = enabled
        bleManager.isAutoReconnectEnabled = enabled
        prefs.edit().putBoolean("auto_reconnect_ble", enabled).apply()
        showNotification(if (enabled) "Reconexão automática do relógio ativada." else "Reconexão automática do relógio desativada.")
    }

    fun setBandAutoMeasure(enabled: Boolean) {
        bleManager.setAutoMeasureEnabled(enabled)
        showNotification(
            if (enabled) "Pedido para ativar as medições automáticas. Confira o estado nas opções do relógio."
            else "Pedido para desativar as medições automáticas. Confira o estado nas opções do relógio."
        )
    }

    fun setBandSpo2AutoDetect(enabled: Boolean) {
        bleManager.setSpo2AutoDetectEnabled(enabled)
        showNotification(
            if (enabled) "Pedido para ativar a medição de oxigênio à noite. Confira o estado nas opções do relógio."
            else "Pedido para desativar a medição de oxigênio à noite. Confira o estado nas opções do relógio."
        )
    }

    fun setBandWearDetect(enabled: Boolean) {
        bleManager.setWearDetectEnabled(enabled)
        showNotification(
            if (enabled) "Pedido para ativar a detecção do relógio no pulso. Confira o estado nas opções do relógio."
            else "Pedido para desativar a detecção do relógio no pulso. Confira o estado nas opções do relógio."
        )
    }

    fun requestHistorySync() {
        bleManager.requestHistorySync()
        showNotification("Busca de dados do relógio solicitada. Confira o andamento na tela Relógio.")
    }

    fun startEcgDetect() {
        bleManager.startEcgDetect()
        showNotification("Início do ECG solicitado. Confira o estado da medição na tela.")
    }

    fun stopEcgDetect() = bleManager.stopEcgDetect()
    fun readStoredEcg() = bleManager.readStoredEcg()
    fun startGlucoseDetect() = bleManager.startGlucoseDetect()
    fun stopGlucoseDetect() = bleManager.stopGlucoseDetect()
    fun startBloodComponentDetect() = bleManager.startBloodComponentDetect()
    fun stopBloodComponentDetect() = bleManager.stopBloodComponentDetect()
    fun startBodyComponentDetect() = bleManager.startBodyComponentDetect()
    fun stopBodyComponentDetect() = bleManager.stopBodyComponentDetect()
    fun startEmotionDetect() = bleManager.startEmotionDetect()
    fun stopEmotionDetect() = bleManager.stopEmotionDetect()
    fun startFatigueDetect() = bleManager.startFatigueDetect()
    fun stopFatigueDetect() = bleManager.stopFatigueDetect()
    fun startBreathDetect() = bleManager.startBreathDetect()
    fun stopBreathDetect() = bleManager.stopBreathDetect()
    fun setBandAlarm(enabled: Boolean) = bleManager.setBandAlarmEnabled(enabled)
    fun setBandHeartWarning(enabled: Boolean) = bleManager.setHeartWarningEnabled(enabled)
    fun setBandLongSeat(enabled: Boolean) = bleManager.setLongSeatEnabled(enabled)
    fun setBandNightTurn(enabled: Boolean) = bleManager.setNightTurnEnabled(enabled)
    fun setBandFindDevice(enabled: Boolean) = bleManager.setFindDeviceEnabled(enabled)
    fun startFindDeviceByPhone() = bleManager.startFindDeviceByPhone()
    fun stopFindDeviceByPhone() = bleManager.stopFindDeviceByPhone()
    fun setBandHealthRemind(enabled: Boolean) = bleManager.setHealthRemindEnabled(enabled)

    fun saveUserProfile(profile: com.example.data.local.UserProfileEntity) {
        viewModelScope.launch {
            userProfileDao.saveUserProfile(profile)
            bleManager.setPatientId(profile.patientId)
            showNotification("Perfil salvo neste celular.")
        }
    }

    fun setAutoIngestLiveReadings(enabled: Boolean) {
        _autoIngestLiveReadings.value = enabled
        prefs.edit().putBoolean("auto_ingest_live", enabled).apply()
    }

    fun triggerWorkManagerSync() {
        viewModelScope.launch {
            HBandWorkScheduler.triggerImmediateIngest(getApplication())
            syncQueueNow()
            showNotification("Tentativa de envio solicitada. Confira a tela Envios.")
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            val result = repository.checkApiHealth()
            showNotification(
                when {
                    result.isOnline -> "O serviço de envio está acessível. Confira os registros na tela Envios."
                    result.statusCode == 401 || result.statusCode == 403 ->
                        "Há um problema de acesso ao serviço de envio. Peça ajuda à equipe responsável pelo aplicativo."
                    else -> "Não foi possível acessar o serviço de envio. Confira a internet e tente novamente. O serviço também pode estar indisponível."
                },
                isError = !result.isOnline
            )
        }
    }

    fun testServiceConnection() {
        viewModelScope.launch {
            val result = repository.checkApiHealth()
            showNotification(if (result.isOnline)
                "O serviço respondeu à verificação. Isso não confirma autorização nem envio de leituras."
                else result.message, isError = !result.isOnline)
        }
    }

    fun startBleScan() {
        bleManager.checkBondedOrAutoConnect()
        bleManager.startScanning()
    }

    fun stopBleScan() = bleManager.stopScanning()

    /**
     * Envia o perfil biométrico real do usuário para o BLE manager antes de conectar, para
     * que o VE30 calibre PA/HRV com altura/peso/idade/sexo reais em vez do fallback genérico
     * (175cm/72kg/32 anos) — essa era a causa da PA estimada não bater com o visor do relógio.
     */
    private fun syncBiometricProfileToBleManager() {
        val profile = userProfile.value ?: return
        val isMale = !profile.gender.trim().lowercase().startsWith("f")
        bleManager.updateBiometricProfile(
            heightCm = profile.heightCm.toInt(),
            weightKg = profile.weightKg.toInt(),
            age = profile.age,
            isMale = isMale,
            stepGoal = profile.dailyStepGoal,
        )
    }

    fun connectDevice(device: HBandDevice) {
        syncBiometricProfileToBleManager()
        if (bleManager.connectDevice(device)) {
            showNotification("Tentando conectar a ${device.name}...")
        }
    }

    fun connectByMacAddress(macAddress: String, customName: String = "VE30 Smart Band") {
        syncBiometricProfileToBleManager()
        val trimmed = macAddress.trim().uppercase()
        val dev = HBandDevice(
            deviceId = trimmed,
            name = customName,
            macAddress = trimmed,
            batteryLevel = null,
            rssi = -50,
            isConnected = false,
            firmwareVersion = "VE30 Direct MAC"
        )
        if (bleManager.connectDevice(dev)) {
            showNotification("Tentando conectar ao relógio pelo endereço informado...")
        }
    }

    fun disconnectDevice() {
        bleManager.disconnectDevice()
        HBandBleService.stop(getApplication())
        showNotification("Desconexão solicitada. Confira o estado do relógio na tela.")
    }

    fun triggerSpotCheck() {
        val telemetry = bleManager.triggerSpotCheck()
        if (!IngestPayloadMapper.isIngestible(telemetry)) {
            showNotification(
                "Ainda não há leitura de batimentos disponível para envio. Confira o relógio no pulso e aguarde a leitura.",
                isError = true
            )
            return
        }
        val patientId = userProfile.value?.patientId ?: bleManager.currentPatientId
        runQueueAction {
            repository.enqueueAndProcessTelemetry(telemetry, patientId)
        }
    }

    fun enqueueBatchSimulated(count: Int) {
        viewModelScope.launch {
            repository.enqueueBatchSimulatedReadings(bleManager, count)
            showNotification("Enqueued $count simulated HBand sensor batch readings to Room DB offline queue!")
        }
    }

    fun quickConnectVE30() {
        connectByMacAddress("C4:E3:42:VE:30:A4", "VE30 Smart Band")
    }

    fun updateApiConfig(newBaseUrl: String, newApiKey: String) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RetrofitClient.updateConfig(getApplication(), newBaseUrl, newApiKey)
                }
                showNotification("Configuração salva. A pausa de autorização permanece até uma tentativa confirmada pelo serviço.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showNotification("Não foi possível salvar a configuração. Confira o endereço HTTPS e tente novamente.", isError = true)
            }
        }
    }

    private fun runQueueAction(action: suspend () -> QueueProcessResult) {
        viewModelScope.launch {
            try {
                val result = action()
                showNotification(result.patientMessage(), isError = result.hasIncompleteItems)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: QueuePersistenceException) {
                showNotification(
                    "Não foi possível atualizar a fila no aparelho. O resultado do envio pode não estar registrado. Peça ajuda à equipe antes de repetir a tentativa.",
                    isError = true,
                )
            } catch (_: Exception) {
                showNotification("Não foi possível concluir a tentativa. Confira a fila e tente novamente.", isError = true)
            }
        }
    }

    fun syncQueueNow() = runQueueAction {
        repository.checkApiHealth()
        repository.processQueueDetailed()
    }

    fun retryFailedItem(id: Long) = runQueueAction { repository.retryFailedItem(id) }

    fun retryAllFailed() = runQueueAction { repository.retryAllFailed() }

    fun deleteQueueItem(id: Long) = runQueueRemoval("Exclusão do registro #$id concluída.") {
        repository.deleteQueueItem(id)
    }

    fun clearSynced() = runQueueRemoval("Exclusão dos registros concluídos da fila finalizada.") {
        repository.clearSyncedItems()
    }

    fun clearAll() = runQueueRemoval("Exclusão dos registros da fila concluída.") {
        repository.clearAllQueue()
    }

    fun resetAllDataToZero() = runQueueRemoval("Limpeza dos dados de teste concluída.") {
        if (!repository.clearAllQueue()) false else {
            repository.clearAllSensorMetrics()
            hydrationDao.clearAll()
            breathingDao.clearAll()
            bleManager.resetBiometricsToZero()
            _geminiInsightText.value = ""
            true
        }
    }

    private fun runQueueRemoval(successMessage: String, action: suspend () -> Boolean) {
        viewModelScope.launch {
            try {
                if (action()) showNotification(successMessage)
                else showNotification("A fila está ocupada. A exclusão não foi iniciada. Aguarde e tente novamente.", isError = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showNotification("Não foi possível concluir a exclusão. Confira os registros antes de tentar novamente.", isError = true)
            }
        }
    }

    fun selectItemForPreview(item: IngestQueueEntity?) {
        _selectedQueueItemForPreview.value = item
    }

    // Keep legacy actions blocked even if invoked outside Settings.
    fun triggerFirestoreBackup() = showNotification(
        com.example.data.remote.FirestoreBackupManager.UNAVAILABLE_MESSAGE, isError = true,
    )

    fun restoreFromFirestoreBackup() = showNotification(
        com.example.data.remote.FirestoreBackupManager.UNAVAILABLE_MESSAGE, isError = true,
    )
    fun simulateLowBattery() {
        bleManager.simulateLowBattery()
        showNotification("Simulação de teste: aviso de bateria fraca (14%) — não é leitura da pulseira", isError = true)
    }

    fun rechargeBattery() {
        bleManager.rechargeBattery()
        showNotification("Simulação de teste: 98% — não é leitura da pulseira")
    }

    fun dismissNotification() {
        _notification.value = null
    }

    fun showNotification(msg: String, isError: Boolean = false) {
        _notification.value = UiNotification(message = msg, isError = isError)
    }
}
