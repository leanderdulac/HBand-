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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.asFlow
import androidx.work.WorkManager

import com.example.ui.components.SyncLogEntry
import com.example.util.HrNotificationHelper
import com.example.worker.HBandWorkScheduler
import android.content.Context
import android.content.SharedPreferences

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

    private val queueReadRetries = MutableStateFlow(0)
    val queuePresentation = repository.allQueueItems.queuePresentationState(viewModelScope, queueReadRetries)

    fun retryQueueRead() { queueReadRetries.value += 1 }

    val ingestDiagnostics = combine(queuePresentation, RetrofitClient.configurationState) { state, configuration ->
        when {
            state == null -> com.example.data.ingest.IngestDiagnostics(configuration = configuration)
            state.readFailed -> com.example.data.ingest.IngestDiagnostics(configuration = configuration, readFailed = true)
            else -> com.example.data.ingest.IngestDiagnostics.from(state.items, configuration)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), com.example.data.ingest.IngestDiagnostics())

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

    private val metricsReadRetries = MutableStateFlow(0)
    val shareSensorMetrics = repository.allSensorMetrics.shareMetricsState(viewModelScope, metricsReadRetries)
    fun retryMetricsRead() { metricsReadRetries.value += 1 }

    private val allQueueItems: StateFlow<List<IngestQueueEntity>> = queuePresentation
        .map { it?.takeUnless { state -> state.readFailed }?.items.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0),
            initialValue = emptyList()
        )

    val failedCount: StateFlow<Int> = allQueueItems.combine(MutableStateFlow(0)) { items, _ ->
        items.count { it.status == QueueStatus.FAILED.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), 0)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), 0)

    private val notificationState = PatientNotificationState()
    val notification: StateFlow<UiNotification?> = notificationState.notification

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

    private var lastAlertTimeMs: Long? = null

    private val insightReview = PatientInsightReview(viewModelScope, com.example.BuildConfig.DEBUG, repository.allSensorMetrics) { metrics ->
        com.example.data.remote.GeminiHealthAnalyzer.generateSevenDayInsight(metrics)
    }
    val geminiInsightText = insightReview.state.map { it.text }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val isGeneratingGeminiInsight = insightReview.state.map { it.isLoading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val hydrationDao = db.hydrationDao()
    private val breathingDao = db.breathingDao()
    private val userProfileDao = db.userProfileDao()
    private val localWellness = com.example.data.local.LocalWellnessRecords(
        hydrationDao, breathingDao, com.example.util.localCalendarChanges(application)
    )
    private val wellnessActions = PatientWellnessActions(localWellness, ::showNotification)
    private val breathingSave = PatientBreathingSave(viewModelScope, wellnessActions::saveBreathingSession)
    val breathingSaveState = breathingSave.state
    private val profileRead = PatientProfileRead(viewModelScope, userProfileDao.getUserProfileFlow())
    val userProfile = profileRead.state
    val profileForEditor = profileRead.editorProfile
    fun retryProfileRead() = profileRead.retry()
    private val profileSave = PatientProfileSave(viewModelScope, { profile ->
        check(profileRead.canSave(profile)) { "Profile read unavailable or identity changed" }
        userProfileDao.saveUserProfile(profile)
    }) { profile ->
        bleManager.setPatientId(profile.patientId)
        showNotification("Perfil salvo neste celular.")
    }
    val profileSaveState = profileSave.state

    private val _autoReconnectBle = MutableStateFlow(prefs.getBoolean("auto_reconnect_ble", true))
    val autoReconnectBle: StateFlow<Boolean> = _autoReconnectBle.asStateFlow()

    private val hydrationReadRetries = MutableStateFlow(0)
    private val breathingReadRetries = MutableStateFlow(0)
    val todayHydrationMl = localWellness.todayHydrationMl.localReadState(viewModelScope, hydrationReadRetries) { it == null }
    val totalBreathingSeconds = localWellness.totalBreathingSeconds.localReadState(viewModelScope, breathingReadRetries) { it == null }
    fun retryHydrationRead() { hydrationReadRetries.value += 1 }
    fun retryBreathingRead() { breathingReadRetries.value += 1 }
    fun retryShareReads() {
        if (shareSensorMetrics.value == LocalReadState.Failed) retryMetricsRead()
        if (todayHydrationMl.value == LocalReadState.Failed) retryHydrationRead()
        if (totalBreathingSeconds.value == LocalReadState.Failed) retryBreathingRead()
    }

    val hydrationTargetGoalMl: Int = 2500

    fun addWaterIntake(amountMl: Int) {
        viewModelScope.launch {
            wellnessActions.addWaterIntake(amountMl)
        }
    }

    fun resetTodayHydration() {
        viewModelScope.launch {
            wellnessActions.resetTodayHydration()
        }
    }

    fun saveBreathingSession(token: String, durationSeconds: Int) = breathingSave.save(token, durationSeconds)

    fun generateGeminiInsight() = insightReview.request()

    init {
        bleManager.isAutoReconnectEnabled = _autoReconnectBle.value
        checkHealth()

        viewModelScope.launch {
            profileRead.firstConfirmed()?.let { bleManager.setPatientId(it.patientId) }
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

        val now = android.os.SystemClock.elapsedRealtime()
        // Local elapsed duration, independent of civil-time corrections; includes device sleep.
        lastAlertTimeMs?.let { if (now - it < 8000) return }

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
        showNotification("Busca de dados do relógio solicitada. Confira o andamento em Ajustes → Opções do relógio.")
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

    fun saveUserProfile(token: String, profile: com.example.data.local.UserProfileEntity) = profileSave.save(token, profile)

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
        val profile = userProfile.value.valueOrNull() ?: return
        val isMale = !profile.gender.trim().lowercase().startsWith("f")
        bleManager.updateBiometricProfile(
            heightCm = profile.heightCm.toInt(),
            weightKg = profile.weightKg.toInt(),
            age = profile.age,
            isMale = isMale,
            stepGoal = profile.dailyStepGoal,
        )
    }

    private fun profileReadReady(): Boolean {
        if (userProfile.value is LocalReadState.Ready) return true
        showNotification("Aguarde a leitura do perfil. Se houver falha, abra Meu perfil e tente a leitura novamente.", isError = true)
        return false
    }

    fun connectDevice(device: HBandDevice) {
        if (!profileReadReady()) return
        syncBiometricProfileToBleManager()
        if (bleManager.connectDevice(device)) {
            showNotification("Tentando conectar a ${device.name}...")
        }
    }

    fun connectByMacAddress(macAddress: String, customName: String = "VE30 Smart Band") {
        if (!profileReadReady()) return
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
        if (!profileReadReady()) return
        val telemetry = bleManager.triggerSpotCheck()
        if (telemetry == null || !IngestPayloadMapper.isIngestible(telemetry)) {
            showNotification(
                "Ainda não há leitura de batimentos disponível para envio. Confira o relógio no pulso e aguarde a leitura.",
                isError = true
            )
            return
        }
        val patientId = userProfile.value.valueOrNull()?.patientId ?: bleManager.currentPatientId
        runQueueAction {
            repository.enqueueAndProcessTelemetry(telemetry, patientId)
        }
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
            insightReview.clear()
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
    fun dismissNotification(displayed: UiNotification) {
        notificationState.dismiss(displayed)
    }

    fun showNotification(msg: String, isError: Boolean = false) {
        notificationState.post(msg, isError)
    }
}
