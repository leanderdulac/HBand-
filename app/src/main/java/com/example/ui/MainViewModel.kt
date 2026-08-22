package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.hband.HBandBleManager
import com.example.data.local.AppDatabase
import com.example.data.local.HBandSensorMetricEntity
import com.example.data.local.IngestQueueEntity
import com.example.data.local.QueueStatus
import com.example.data.model.HBandDevice
import com.example.data.model.HBandTelemetry
import com.example.data.remote.RetrofitClient
import com.example.data.repository.ApiHealthState
import com.example.data.repository.WearableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        apiService = RetrofitClient.apiService
    )

    val bleManager = HBandBleManager(application.applicationContext, viewModelScope)

    val apiHealth: StateFlow<ApiHealthState> = repository.apiHealth
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val lastSyncResult: StateFlow<String?> = repository.lastSyncResult

    val scannedDevices: StateFlow<List<HBandDevice>> = bleManager.scannedDevices
    val connectedDevice: StateFlow<HBandDevice?> = bleManager.connectedDevice
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val latestTelemetry: StateFlow<HBandTelemetry?> = bleManager.latestTelemetry

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
        failedCount
    ) { syncing, health, pending, failed ->
        when {
            syncing -> SyncDisplayStatus.SYNCING
            !health.isOnline -> SyncDisplayStatus.OFFLINE
            failed > 0 -> SyncDisplayStatus.FAILED
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

    private val _autoIngestLiveReadings = MutableStateFlow(true)
    val autoIngestLiveReadings: StateFlow<Boolean> = _autoIngestLiveReadings.asStateFlow()

    private val prefs: SharedPreferences = application.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)

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

    private val _autoReconnectBle = MutableStateFlow(bleManager.isAutoReconnectEnabled)
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
            showNotification("Logged +$amountMl mL water intake")
        }
    }

    fun resetTodayHydration() {
        viewModelScope.launch {
            hydrationDao.resetTodayLogs(todayDateString)
            showNotification("Reset today's hydration logs")
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
            showNotification("Saved ${durationSeconds / 60}m ${durationSeconds % 60}s relaxation breathing session!")
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
        // Initial health check with HealthtechRepository
        checkHealth()

        // Schedule background worker tasks on app startup
        com.example.worker.HBandWorkScheduler.schedulePeriodicIngest(application)

        // Auto-enqueue live telemetry and check HR thresholds if enabled
        viewModelScope.launch {
            latestTelemetry.collect { telemetry ->
                if (telemetry != null && (telemetry.heartRate > 0 || telemetry.steps > 0)) {
                    if (_autoIngestLiveReadings.value) {
                        val patientId = userProfile.value?.patientId ?: bleManager.currentPatientId
                        repository.enqueueTelemetry(telemetry, patientId)
                    }
                    if (telemetry.heartRate > 0) {
                        evaluateHeartRateThresholds(telemetry.heartRate)
                    }
                }
            }
        }

        // Auto-trigger Gemini AI Health Insight when 7-day sensor metrics are available
        viewModelScope.launch {
            allSensorMetrics.collect { metrics ->
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
        showNotification("Upper heart rate threshold set to $clamped BPM")
    }

    fun setLowerHrThreshold(value: Int) {
        val clamped = value.coerceIn(35, 75)
        _lowerHrThreshold.value = clamped
        prefs.edit().putInt("lower_hr_threshold", clamped).apply()
        showNotification("Lower heart rate threshold set to $clamped BPM")
    }

    fun setHrAlertsEnabled(enabled: Boolean) {
        _hrAlertsEnabled.value = enabled
        prefs.edit().putBoolean("hr_alerts_enabled", enabled).apply()
        showNotification(if (enabled) "Heart rate threshold local alerts enabled" else "Heart rate threshold alerts disabled")
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
            showNotification("⚠️ High Heart Rate Alert: $hr BPM exceeds $upper BPM threshold limit!", isError = true)
        } else if (hr < lower) {
            lastAlertTimeMs = now
            HrNotificationHelper.sendLowHrNotification(getApplication(), hr, lower)
            showNotification("⚠️ Low Heart Rate Alert: $hr BPM is below $lower BPM threshold limit!", isError = true)
        }
    }

    fun testHighHrAlert() {
        val upper = _upperHrThreshold.value
        val testHr = (upper + 18).coerceAtLeast(125)
        HrNotificationHelper.sendHighHrNotification(getApplication(), testHr, upper)
        showNotification("⚠️ High Heart Rate Alert: $testHr BPM exceeds $upper BPM threshold limit!", isError = true)
    }

    fun testLowHrAlert() {
        val lower = _lowerHrThreshold.value
        val testHr = (lower - 8).coerceAtMost(42)
        HrNotificationHelper.sendLowHrNotification(getApplication(), testHr, lower)
        showNotification("⚠️ Low Heart Rate Alert: $testHr BPM is below $lower BPM threshold limit!", isError = true)
    }

    fun setAutoReconnectBle(enabled: Boolean) {
        _autoReconnectBle.value = enabled
        bleManager.isAutoReconnectEnabled = enabled
        prefs.edit().putBoolean("auto_reconnect_ble", enabled).apply()
        showNotification(if (enabled) "Reconexão Automática BLE ativada" else "Reconexão Automática BLE desativada")
    }

    fun saveUserProfile(profile: com.example.data.local.UserProfileEntity) {
        viewModelScope.launch {
            userProfileDao.saveUserProfile(profile)
            bleManager.setPatientId(profile.patientId)
            showNotification("Perfil de ${profile.fullName} salvo com sucesso!")
        }
    }

    fun setAutoIngestLiveReadings(enabled: Boolean) {
        _autoIngestLiveReadings.value = enabled
    }

    fun triggerWorkManagerSync() {
        viewModelScope.launch {
            HBandWorkScheduler.triggerImmediateIngest(getApplication())
            syncQueueNow()
            showNotification("WorkManager upload task triggered")
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            val result = repository.checkApiHealth()
            showNotification(
                if (result.isOnline) "HealthTech API Online: Latency ${result.latencyMs}ms"
                else "API Check: ${result.message}",
                isError = !result.isOnline
            )
        }
    }

    fun testSmokeHeartConnection(patientId: String = "PAT-HBAND-001") {
        viewModelScope.launch {
            try {
                val apiKey = RetrofitClient.apiKey
                val repo = com.healthtech.companion.net.HealthtechRepository.create(
                    baseUrl = "https://healthtech-secure-api-5794833455.us-central1.run.app",
                    apiKey = apiKey
                )
                val response = repo.smokeHeart(patientId)
                if (response.isSuccessful) {
                    showNotification("SmokeHeart Test Success (200 OK) for patient $patientId!")
                } else {
                    val err = response.errorBody()?.string() ?: response.message()
                    showNotification("SmokeHeart Test (HTTP ${response.code()}): $err", isError = true)
                }
            } catch (e: Exception) {
                showNotification("SmokeHeart Error: ${e.localizedMessage ?: "Connection failed"}", isError = true)
            }
        }
    }

    fun startBleScan() {
        bleManager.checkBondedOrAutoConnect()
        bleManager.startScanning()
    }

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
        bleManager.connectDevice(device)
        showNotification("Conectando a ${device.name} [${device.macAddress}]...")
    }

    fun connectByMacAddress(macAddress: String, customName: String = "VE30 Smart Band") {
        syncBiometricProfileToBleManager()
        val trimmed = macAddress.trim().uppercase()
        val dev = HBandDevice(
            deviceId = trimmed,
            name = customName,
            macAddress = trimmed,
            batteryLevel = 90,
            rssi = -50,
            isConnected = false,
            firmwareVersion = "VE30 Direct MAC"
        )
        bleManager.connectDevice(dev)
        showNotification("Conectando diretamente ao MAC: $trimmed")
    }

    fun disconnectDevice() {
        bleManager.disconnectDevice()
        showNotification("Disconnected HBand device")
    }

    fun triggerSpotCheck() {
        viewModelScope.launch {
            val telemetry = bleManager.triggerSpotCheck()
            val patientId = userProfile.value?.patientId ?: bleManager.currentPatientId
            repository.enqueueTelemetry(telemetry, patientId)
            repository.checkApiHealth()
            val synced = repository.processQueue()
            if (synced > 0) {
                showNotification("Sincronizado com sucesso ($synced item): FC ${telemetry.heartRate} BPM, Passos ${telemetry.steps}")
            } else {
                showNotification("Dados vitais salvos no Room DB e enfileirados: FC ${telemetry.heartRate} BPM [${telemetry.deviceId}]")
            }
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

    fun markAllAsLocalSynced() {
        viewModelScope.launch {
            repository.markAllAsLocalSynced()
            showNotification("Todos os registros marcados como salvos no banco local (Room DB)!")
        }
    }

    fun updateApiConfig(newBaseUrl: String, newApiKey: String) {
        RetrofitClient.updateConfig(getApplication(), newBaseUrl, newApiKey)
        checkHealth()
        showNotification("Configurações do endpoint da API atualizadas!")
    }

    fun syncQueueNow() {
        viewModelScope.launch {
            // First re-verify API status
            repository.checkApiHealth()
            val synced = repository.processQueue()
            if (synced > 0) {
                showNotification("Successfully synced $synced items to HealthTech Ingest API!")
            } else {
                val res = lastSyncResult.value ?: "Sync complete"
                showNotification(res)
            }
        }
    }

    fun retryFailedItem(id: Long) {
        viewModelScope.launch {
            repository.retryFailedItem(id)
            showNotification("Re-queued failed item #$id for sync")
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            repository.retryAllFailed()
            showNotification("Re-queued all failed items")
        }
    }

    fun deleteQueueItem(id: Long) {
        viewModelScope.launch {
            repository.deleteQueueItem(id)
            showNotification("Deleted queue item #$id")
        }
    }

    fun clearSynced() {
        viewModelScope.launch {
            repository.clearSyncedItems()
            showNotification("Cleared all synced items from local database")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllQueue()
            showNotification("Cleared entire offline ingestion queue")
        }
    }

    fun resetAllDataToZero() {
        viewModelScope.launch {
            repository.clearAllQueue()
            repository.clearAllSensorMetrics()
            hydrationDao.clearAll()
            breathingDao.clearAll()
            bleManager.resetBiometricsToZero()
            _geminiInsightText.value = ""
            showNotification("Todos os dados foram zerados com sucesso para nova instalação!")
        }
    }

    fun selectItemForPreview(item: IngestQueueEntity?) {
        _selectedQueueItemForPreview.value = item
    }

    private val _firestoreSyncStatus = MutableStateFlow("Idle")
    val firestoreSyncStatus: StateFlow<String> = _firestoreSyncStatus.asStateFlow()

    private val _lastFirestoreBackupTime = MutableStateFlow<Long?>(null)
    val lastFirestoreBackupTime: StateFlow<Long?> = _lastFirestoreBackupTime.asStateFlow()

    private val _lastFirestoreBackupCount = MutableStateFlow(0)
    val lastFirestoreBackupCount: StateFlow<Int> = _lastFirestoreBackupCount.asStateFlow()

    fun triggerFirestoreBackup() {
        viewModelScope.launch {
            _firestoreSyncStatus.value = "Backing up..."
            val result = com.example.data.remote.FirestoreBackupManager.backupRoomMetricsToFirestore(getApplication())
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _firestoreSyncStatus.value = "Synced ($count records)"
                _lastFirestoreBackupTime.value = System.currentTimeMillis()
                _lastFirestoreBackupCount.value = count
                showNotification("Firebase Firestore Cloud Backup successful: $count Room records synced")
            } else {
                _firestoreSyncStatus.value = "Failed"
                val err = result.exceptionOrNull()?.localizedMessage ?: "Firestore upload error"
                showNotification("Firestore Backup Error: $err", isError = true)
            }
        }
    }

    fun restoreFromFirestoreBackup() {
        viewModelScope.launch {
            _firestoreSyncStatus.value = "Restoring..."
            val result = com.example.data.remote.FirestoreBackupManager.restoreRoomMetricsFromFirestore(getApplication())
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _firestoreSyncStatus.value = "Restored ($count records)"
                showNotification("Restored $count health records from Firebase Firestore cloud backup")
            } else {
                _firestoreSyncStatus.value = "Restore Failed"
                val err = result.exceptionOrNull()?.localizedMessage ?: "Firestore download error"
                showNotification("Firestore Restore Error: $err", isError = true)
            }
        }
    }

    fun simulateLowBattery() {
        bleManager.simulateLowBattery()
        showNotification("HBand BLE listener reported low battery warning (14%)", isError = true)
    }

    fun rechargeBattery() {
        bleManager.rechargeBattery()
        showNotification("HBand Wearable connected to magnetic charger (98%)")
    }

    fun dismissNotification() {
        _notification.value = null
    }

    fun showNotification(msg: String, isError: Boolean = false) {
        _notification.value = UiNotification(message = msg, isError = isError)
    }
}
