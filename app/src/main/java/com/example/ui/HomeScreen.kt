package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import com.example.ui.components.SettingsTab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.components.ApiHeader
import com.example.ui.components.DeviceControlCard
import com.example.ui.components.HomeWelcomeHeader
import com.example.ui.components.JsonPayloadModal
import com.example.ui.components.QueueInspector
import com.example.ui.components.TelemetryGauges
import com.example.ui.theme.MinimalBorder
import com.example.data.hband.VeepooSessionGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val apiHealth by viewModel.apiHealth.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanFailure by viewModel.scanFailure.collectAsStateWithLifecycle()
    val latestTelemetry by viewModel.latestTelemetry.collectAsStateWithLifecycle()
    val allSensorMetrics by viewModel.allSensorMetrics.collectAsStateWithLifecycle()
    val shareSensorMetrics by viewModel.shareSensorMetrics.collectAsStateWithLifecycle()
    val queuePresentation by viewModel.queuePresentation.collectAsStateWithLifecycle()
    val allQueueItems = queuePresentation?.items
    val pendingCount = queuePresentation?.pendingCount ?: 0
    val syncedCount = queuePresentation?.syncedCount ?: 0
    val failedCount = queuePresentation?.failedCount ?: 0
    val consecutiveFailures by viewModel.consecutiveFailures.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncDisplayStatus = queuePresentation.displayStatus(isSyncing, apiHealth)
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val autoIngestLive by viewModel.autoIngestLiveReadings.collectAsStateWithLifecycle()
    val selectedModalItem by viewModel.selectedQueueItemForPreview.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val autoReconnectBle by viewModel.autoReconnectBle.collectAsStateWithLifecycle()
    val deviceCapabilities by viewModel.deviceCapabilities.collectAsStateWithLifecycle()
    val autoMeasureState by viewModel.autoMeasureState.collectAsStateWithLifecycle()
    val wearDetectState by viewModel.wearDetectState.collectAsStateWithLifecycle()
    val historySyncState by viewModel.historySyncState.collectAsStateWithLifecycle()
    val isHardwareConnected by viewModel.isHardwareConnected.collectAsStateWithLifecycle()
    val ecgState by viewModel.ecgState.collectAsStateWithLifecycle()
    val glucoseState by viewModel.glucoseState.collectAsStateWithLifecycle()
    val bloodComponentState by viewModel.bloodComponentState.collectAsStateWithLifecycle()
    val bodyComponentState by viewModel.bodyComponentState.collectAsStateWithLifecycle()
    val emotionState by viewModel.emotionState.collectAsStateWithLifecycle()
    val fatigueState by viewModel.fatigueState.collectAsStateWithLifecycle()
    val breathDetectState by viewModel.breathDetectState.collectAsStateWithLifecycle()
    val alarmState by viewModel.alarmState.collectAsStateWithLifecycle()
    val heartWarningState by viewModel.heartWarningState.collectAsStateWithLifecycle()
    val longSeatState by viewModel.longSeatState.collectAsStateWithLifecycle()
    val nightTurnState by viewModel.nightTurnState.collectAsStateWithLifecycle()
    val findDeviceState by viewModel.findDeviceState.collectAsStateWithLifecycle()
    val healthRemindState by viewModel.healthRemindState.collectAsStateWithLifecycle()
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }

    val upperHrThreshold by viewModel.upperHrThreshold.collectAsStateWithLifecycle()
    val lowerHrThreshold by viewModel.lowerHrThreshold.collectAsStateWithLifecycle()
    val hrAlertsEnabled by viewModel.hrAlertsEnabled.collectAsStateWithLifecycle()
    val ingestDiagnostics by viewModel.ingestDiagnostics.collectAsStateWithLifecycle()


    val geminiInsightText by viewModel.geminiInsightText.collectAsStateWithLifecycle()
    val isGeneratingGeminiInsight by viewModel.isGeneratingGeminiInsight.collectAsStateWithLifecycle()

    val todayHydrationMl by viewModel.todayHydrationMl.collectAsStateWithLifecycle()
    val totalBreathingSeconds by viewModel.totalBreathingSeconds.collectAsStateWithLifecycle()

    val sharePreview: PatientSharePreviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val activeShareData = sharePreview.data

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = selectedTab != 0 && !showProfileDialog && activeShareData == null && selectedModalItem == null) {
        selectedTab = 0
    }
    val p1ActionsEnabled = VeepooSessionGate.actionsEnabled(
        hardwareConnected = isHardwareConnected,
        connectedMac = connectedDevice?.macAddress,
        telemetry = latestTelemetry,
    )

    activeShareData?.let { shareData ->
        com.example.ui.components.ShareProgressDialog(
            shareData = shareData,
            onDismiss = sharePreview::dismiss,
            onShowSnackbar = { viewModel.showNotification(it) }
        )
    }

    PatientNotificationEffect(notification, snackbarHostState, viewModel::dismissNotification)

    com.example.ui.components.PatientAdaptiveScaffold(
        modifier = modifier,
        selectedTab = selectedTab,
        pendingCount = queuePresentation?.pendingCount,
        onSelect = { selectedTab = it },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        header = {
            HomeWelcomeHeader(
                fullName = userProfile?.fullName.orEmpty(),
                patientId = userProfile?.patientId.orEmpty(),
                onEditProfile = { showProfileDialog = true },
                showGreeting = selectedTab == 0,
            )
        },
    ) {
        // Tab Content Body
        com.example.ui.components.PatientTabContent(
            selectedTab = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(
                    syncDisplayStatus = syncDisplayStatus,
                    pendingCount = pendingCount,
                    syncedCount = syncedCount,
                    failedCount = failedCount,
                    consecutiveFailures = consecutiveFailures,
                    syncLogs = syncLogs,
                    apiHealth = apiHealth,
                    connectedDevice = connectedDevice,
                    latestTelemetry = latestTelemetry,
                    sensorMetrics = allSensorMetrics,
                    shareSensorMetrics = shareSensorMetrics,
                    autoIngestLive = autoIngestLive,
                    onTriggerSync = { viewModel.triggerWorkManagerSync() },
                    onRefreshHealth = { viewModel.checkHealth() },
                    onRetryAll = { viewModel.retryAllFailed() },
                    onToggleAutoIngest = { viewModel.setAutoIngestLiveReadings(it) },
                    onSpotCheck = { viewModel.triggerSpotCheck() },
                    onSimulateBatch = { viewModel.enqueueBatchSimulated(it) },
                    onShowNotification = { viewModel.showNotification(it) },
                    onSimulateLowBattery = if (BuildConfig.DEBUG) ({ viewModel.simulateLowBattery() }) else null,
                    onRechargeBattery = if (BuildConfig.DEBUG) ({ viewModel.rechargeBattery() }) else null,
                    onScanClick = { selectedTab = 2 },
                    onDisconnect = { viewModel.disconnectDevice() },
                    geminiInsightText = geminiInsightText,
                    isGeneratingGeminiInsight = isGeneratingGeminiInsight,
                    onRefreshGeminiInsight = { viewModel.generateGeminiInsight() },
                    todayHydrationMl = todayHydrationMl,
                    hydrationTargetMl = userProfile?.targetWaterMl ?: 0,
                    onAddWater = { viewModel.addWaterIntake(it) },
                    onResetHydration = { viewModel.resetTodayHydration() },
                    totalBreathingSeconds = totalBreathingSeconds,
                    onSaveBreathingSession = { viewModel.saveBreathingSession(it) },
                    onGenerateShareData = sharePreview::open,
                    onShowHistory = { selectedTab = 1 },
                    capabilities = deviceCapabilities,
                    hardwareConnected = isHardwareConnected,
                    actionsEnabled = p1ActionsEnabled,
                    ecgState = ecgState,
                    glucoseState = glucoseState,
                    bloodComponentState = bloodComponentState,
                    bodyComponentState = bodyComponentState,
                    emotionState = emotionState,
                    fatigueState = fatigueState,
                    breathDetectState = breathDetectState,
                    onStartEcg = { viewModel.startEcgDetect() },
                    onStopEcg = { viewModel.stopEcgDetect() },
                    onReadEcg = { viewModel.readStoredEcg() },
                    onStartGlucose = { viewModel.startGlucoseDetect() },
                    onStopGlucose = { viewModel.stopGlucoseDetect() },
                    onStartBloodComponent = { viewModel.startBloodComponentDetect() },
                    onStopBloodComponent = { viewModel.stopBloodComponentDetect() },
                    onStartBodyComponent = { viewModel.startBodyComponentDetect() },
                    onStopBodyComponent = { viewModel.stopBodyComponentDetect() },
                    onStartEmotion = { viewModel.startEmotionDetect() },
                    onStopEmotion = { viewModel.stopEmotionDetect() },
                    onStartFatigue = { viewModel.startFatigueDetect() },
                    onStopFatigue = { viewModel.stopFatigueDetect() },
                    onStartBreath = { viewModel.startBreathDetect() },
                    onStopBreath = { viewModel.stopBreathDetect() },
                )

                1 -> com.example.ui.components.RechartsSensorDashboard(
                    sensorMetrics = allSensorMetrics,
                    onSimulateBatch = { viewModel.enqueueBatchSimulated(it) }
                )

                2 -> com.example.ui.components.PatientWatchScreen(
                    scannedDevices = scannedDevices,
                    connectedDevice = connectedDevice,
                    isScanning = isScanning,
                    scanFailure = scanFailure,
                    onStartScan = { viewModel.startBleScan() },
                    onStopScan = { viewModel.stopBleScan() },
                    onConnectDevice = { viewModel.connectDevice(it) },
                    onConnectByMac = { mac -> viewModel.connectByMacAddress(mac) },
                    onDisconnectDevice = { viewModel.disconnectDevice() }
                )

                3 -> QueueInspector(
                    pendingCount = pendingCount,
                    syncedCount = syncedCount,
                    failedCount = failedCount,
                    queueItems = allQueueItems,
                    syncLogs = syncLogs,
                    isSyncing = isSyncing,
                    onSyncNow = { viewModel.syncQueueNow() },
                    onRetryFailedItem = { viewModel.retryFailedItem(it) },
                    onRetryAllFailed = { viewModel.retryAllFailed() },
                    onDeleteItem = { viewModel.deleteQueueItem(it) },
                    onClearSynced = { viewModel.clearSynced() },
                    onClearAll = { viewModel.clearAll() },
                    onInspectItem = { viewModel.selectItemForPreview(it) },
                    onRefreshWorkManager = { viewModel.triggerWorkManagerSync() },
                    p1LocationHint = if (deviceCapabilities.hasAdvancedDetect) {
                        VeepooSessionGate.FILA_P1_LOCATION_HINT
                    } else {
                        null
                    },
                )

                4 -> SettingsTab(
                    userProfile = userProfile,
                    onEditProfileClick = { showProfileDialog = true },
                    autoReconnectBle = autoReconnectBle,
                    onAutoReconnectChange = { viewModel.setAutoReconnectBle(it) },
                    upperThreshold = upperHrThreshold,
                    lowerThreshold = lowerHrThreshold,
                    alertsEnabled = hrAlertsEnabled,
                    onUpperThresholdChange = { viewModel.setUpperHrThreshold(it) },
                    onLowerThresholdChange = { viewModel.setLowerHrThreshold(it) },
                    onAlertsEnabledChange = { viewModel.setHrAlertsEnabled(it) },
                    onTestHighAlert = { viewModel.testHighHrAlert() },
                    onTestLowAlert = { viewModel.testLowHrAlert() },
                    onTestApiSmoke = { viewModel.testServiceConnection() },
                    onResetAllData = { viewModel.resetAllDataToZero() },
                    capabilities = deviceCapabilities,
                    autoMeasureState = autoMeasureState,
                    wearDetectState = wearDetectState,
                    historySyncState = historySyncState,
                    hardwareConnected = isHardwareConnected,
                    actionsEnabled = p1ActionsEnabled,
                    onAutoMeasureChange = { viewModel.setBandAutoMeasure(it) },
                    onSpo2AutoChange = { viewModel.setBandSpo2AutoDetect(it) },
                    onWearDetectChange = { viewModel.setBandWearDetect(it) },
                    onSyncHistory = { viewModel.requestHistorySync() },
                    alarmState = alarmState,
                    heartWarningState = heartWarningState,
                    longSeatState = longSeatState,
                    nightTurnState = nightTurnState,
                    findDeviceState = findDeviceState,
                    healthRemindState = healthRemindState,
                    onAlarmChange = { viewModel.setBandAlarm(it) },
                    onHeartWarningChange = { viewModel.setBandHeartWarning(it) },
                    onLongSeatChange = { viewModel.setBandLongSeat(it) },
                    onNightTurnChange = { viewModel.setBandNightTurn(it) },
                    onFindDeviceChange = { viewModel.setBandFindDevice(it) },
                    onStartFindByPhone = { viewModel.startFindDeviceByPhone() },
                    onStopFindByPhone = { viewModel.stopFindDeviceByPhone() },
                    onHealthRemindChange = { viewModel.setBandHealthRemind(it) },
                    ingestDiagnostics = ingestDiagnostics,
                )
            }
        }
    }

    if (showProfileDialog) {
        com.example.ui.components.UserProfileDialog(
            currentProfile = userProfile,
            onDismissRequest = { showProfileDialog = false },
            onSaveProfile = { viewModel.saveUserProfile(it) }
        )
    }

    // Modal JSON Payload Inspector
    selectedModalItem?.let { item ->
        JsonPayloadModal(
            item = item,
            onDismiss = { viewModel.selectItemForPreview(null) }
        )
    }
}

@Composable
private fun DashboardTab(
    syncDisplayStatus: com.example.ui.components.SyncDisplayStatus,
    pendingCount: Int,
    syncedCount: Int,
    failedCount: Int,
    consecutiveFailures: Int = 0,
    syncLogs: List<com.example.ui.components.SyncLogEntry>,
    apiHealth: com.example.data.repository.ApiHealthState,
    connectedDevice: com.example.data.model.HBandDevice?,
    latestTelemetry: com.example.data.model.HBandTelemetry?,
    sensorMetrics: List<com.example.data.local.HBandSensorMetricEntity>,
    autoIngestLive: Boolean,
    onTriggerSync: () -> Unit,
    onRefreshHealth: () -> Unit,
    onRetryAll: () -> Unit = {},
    onToggleAutoIngest: (Boolean) -> Unit,
    onSpotCheck: () -> Unit,
    onSimulateBatch: (Int) -> Unit,
    onShowNotification: (String) -> Unit,
    onSimulateLowBattery: (() -> Unit)? = null,
    onRechargeBattery: (() -> Unit)? = null,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
    geminiInsightText: String = "",
    isGeneratingGeminiInsight: Boolean = false,
    onRefreshGeminiInsight: () -> Unit = {},
    todayHydrationMl: Int? = null,
    hydrationTargetMl: Int = 0,
    onAddWater: (Int) -> Unit = {},
    onResetHydration: () -> Unit = {},
    totalBreathingSeconds: Int? = null,
    onSaveBreathingSession: (Int) -> Unit = {},
    onGenerateShareData: (com.example.util.ShareProgressData) -> Unit = {},
    shareSensorMetrics: List<com.example.data.local.HBandSensorMetricEntity>? = null,
    onShowHistory: () -> Unit = {},
    capabilities: com.example.data.hband.DeviceCapabilities = com.example.data.hband.DeviceCapabilities(),
    hardwareConnected: Boolean = false,
    actionsEnabled: Boolean = hardwareConnected,
    ecgState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    glucoseState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    bloodComponentState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    bodyComponentState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    emotionState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    fatigueState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    breathDetectState: com.example.data.hband.DetectSessionUiState = com.example.data.hband.DetectSessionUiState(),
    onStartEcg: () -> Unit = {},
    onStopEcg: () -> Unit = {},
    onReadEcg: () -> Unit = {},
    onStartGlucose: () -> Unit = {},
    onStopGlucose: () -> Unit = {},
    onStartBloodComponent: () -> Unit = {},
    onStopBloodComponent: () -> Unit = {},
    onStartBodyComponent: () -> Unit = {},
    onStopBodyComponent: () -> Unit = {},
    onStartEmotion: () -> Unit = {},
    onStopEmotion: () -> Unit = {},
    onStartFatigue: () -> Unit = {},
    onStopFatigue: () -> Unit = {},
    onStartBreath: () -> Unit = {},
    onStopBreath: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        com.example.ui.components.PatientSummaryLayout(first = {
            DeviceControlCard(
                device = connectedDevice,
                autoIngestLive = autoIngestLive,
                onToggleAutoIngest = onToggleAutoIngest,
                onSpotCheck = onSpotCheck,
                onSimulateBatch = onSimulateBatch,
                onScanClick = onScanClick,
                onDisconnect = onDisconnect,
                onSimulateLowBattery = onSimulateLowBattery,
                onRechargeBattery = onRechargeBattery
            )

            com.example.ui.components.LowBatteryWarningCard(
                device = connectedDevice,
                onRechargeBattery = onRechargeBattery,
                modifier = Modifier.fillMaxWidth()
            )
        }, second = {
            TelemetryGauges(telemetry = latestTelemetry)

            com.example.ui.components.DailyHealthSummaryCard(
                metrics = sensorMetrics,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onShowHistory,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home_history_button"),
            ) { Text("Ver histórico") }

        })

        com.example.ui.components.SyncStatusIndicator(
            syncStatus = syncDisplayStatus,
            pendingCount = pendingCount,
            syncedCount = syncedCount,
            failedCount = failedCount,
            consecutiveFailures = consecutiveFailures,
            onTriggerSync = onTriggerSync,
            onRefreshHealth = onRefreshHealth,
            onRetryAll = onRetryAll,
            modifier = Modifier.fillMaxWidth()
        )

        if (capabilities.hasAdvancedDetect) {
            com.example.ui.components.PatientSection(
                title = "Medições do relógio",
                forceExpanded = ecgState.running || glucoseState.running || bloodComponentState.running || bodyComponentState.running || emotionState.running || fatigueState.running || breathDetectState.running,
            ) {
                com.example.ui.components.AdvancedDetectCard(
                    capabilities = capabilities,
                    hardwareConnected = hardwareConnected,
                    actionsEnabled = actionsEnabled,
                    ecg = ecgState,
                    glucose = glucoseState,
                    bloodComponent = bloodComponentState,
                    bodyComponent = bodyComponentState,
                    emotion = emotionState,
                    fatigue = fatigueState,
                    breath = breathDetectState,
                    onStartEcg = onStartEcg,
                    onStopEcg = onStopEcg,
                    onReadEcg = onReadEcg,
                    onStartGlucose = onStartGlucose,
                    onStopGlucose = onStopGlucose,
                    onStartBloodComponent = onStartBloodComponent,
                    onStopBloodComponent = onStopBloodComponent,
                    onStartBodyComponent = onStartBodyComponent,
                    onStopBodyComponent = onStopBodyComponent,
                    onStartEmotion = onStartEmotion,
                    onStopEmotion = onStopEmotion,
                    onStartFatigue = onStartFatigue,
                    onStopFatigue = onStopFatigue,
                    onStartBreath = onStartBreath,
                    onStopBreath = onStopBreath,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        com.example.ui.components.PatientSection(
            title = "Água no dia a dia",
            forceExpanded = false,
        ) {
            com.example.ui.components.HydrationCard(
                currentMl = todayHydrationMl,
                targetGoalMl = hydrationTargetMl,
                logs = emptyList(),
                onAddWater = onAddWater,
                onResetToday = onResetHydration,
                modifier = Modifier.fillMaxWidth()
            )
        }

        com.example.ui.components.BreathingExerciseCard(
            totalBreathingSeconds = totalBreathingSeconds,
            onSaveSession = onSaveBreathingSession,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.PatientSection(
            title = "Sono",
            forceExpanded = false,
        ) {
            com.example.ui.components.SleepAnalysisCard(
                metrics = sensorMetrics,
                modifier = Modifier.fillMaxWidth()
            )
        }

        com.example.ui.components.PatientSection(
            title = "Compartilhar registros",
            forceExpanded = false,
        ) {
            com.example.ui.components.ShareProgressCard(
                sensorMetrics = shareSensorMetrics,
                hydrationMl = todayHydrationMl,
                breathingSeconds = totalBreathingSeconds,
                onGenerateShareData = onGenerateShareData,
                modifier = Modifier.fillMaxWidth()
            )

            com.example.ui.components.CsvExportCard(
                metrics = shareSensorMetrics,
                onShowNotification = onShowNotification,
                modifier = Modifier.fillMaxWidth()
            )
        }

        com.example.ui.components.PatientSection(
            title = "Resumo com inteligência artificial",
            forceExpanded = false,
        ) {
            com.example.ui.components.GeminiHealthInsightCard(
                insightText = geminiInsightText,
                isLoading = isGeneratingGeminiInsight,
                onRefreshInsight = onRefreshGeminiInsight,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (BuildConfig.DEBUG) {
            com.example.ui.components.PatientSection(
                title = "Informações para suporte",
                forceExpanded = false,
            ) {
                com.example.ui.components.SyncHistoryLog(
                    syncLogs = syncLogs,
                    onRefreshWorkManager = onTriggerSync,
                    onTriggerSyncNow = onTriggerSync,
                    modifier = Modifier.fillMaxWidth()
                )

                ApiHeader(
                    apiHealth = apiHealth,
                    onRefreshHealth = onRefreshHealth
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
