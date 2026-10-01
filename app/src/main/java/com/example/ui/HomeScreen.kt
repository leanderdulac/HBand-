package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.components.SettingsTab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.components.DeviceControlCard
import com.example.ui.components.HomeWelcomeHeader
import com.example.ui.components.TelemetryGauges
import com.example.data.hband.VeepooSessionGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanFailure by viewModel.scanFailure.collectAsStateWithLifecycle()
    val latestTelemetry by viewModel.latestTelemetry.collectAsStateWithLifecycle()
    val metricsRead by viewModel.shareSensorMetrics.collectAsStateWithLifecycle()
    val shareSensorMetrics = metricsRead.valueOrNull()
    val autoIngestLive by viewModel.autoIngestLiveReadings.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()

    val profileRead by viewModel.userProfile.collectAsStateWithLifecycle()
    val userProfile = profileRead.valueOrNull()
    val profileForEditor by viewModel.profileForEditor.collectAsStateWithLifecycle()
    val profileSaveState by viewModel.profileSaveState.collectAsStateWithLifecycle()
    val autoReconnectBle by viewModel.autoReconnectBle.collectAsStateWithLifecycle()
    val deviceCapabilities by viewModel.deviceCapabilities.collectAsStateWithLifecycle()
    val autoMeasureState by viewModel.autoMeasureState.collectAsStateWithLifecycle()
    val wearDetectState by viewModel.wearDetectState.collectAsStateWithLifecycle()
    val historySyncState by viewModel.historySyncState.collectAsStateWithLifecycle()
    val isHardwareConnected by viewModel.isHardwareConnected.collectAsStateWithLifecycle()
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
    val geminiInsightMeta by viewModel.geminiInsightMeta.collectAsStateWithLifecycle()

    val hydrationRead by viewModel.todayHydrationMl.collectAsStateWithLifecycle()
    val todayHydrationMl = hydrationRead.valueOrNull()
    val breathingRead by viewModel.totalBreathingSeconds.collectAsStateWithLifecycle()
    val totalBreathingSeconds = breathingRead.valueOrNull()
    val breathingSaveState by viewModel.breathingSaveState.collectAsStateWithLifecycle()

    val sharePreview: PatientSharePreviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val activeShareData = sharePreview.data

    val snackbarHostState = remember { SnackbarHostState() }
    var savedTab by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = com.example.ui.components.patientMainTab(savedTab)
    BackHandler(enabled = selectedTab != 0 && !showProfileDialog && activeShareData == null) {
        savedTab = 0
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
        onSelect = { savedTab = it },
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
                    connectedDevice = connectedDevice,
                    latestTelemetry = latestTelemetry,
                    sensorMetrics = shareSensorMetrics,
                    shareSensorMetrics = shareSensorMetrics,
                    metricsReadFailed = metricsRead == LocalReadState.Failed,
                    hydrationReadFailed = hydrationRead == LocalReadState.Failed,
                    breathingReadFailed = breathingRead == LocalReadState.Failed,
                    onRetryMetricsRead = viewModel::retryMetricsRead,
                    onRetryBreathingRead = viewModel::retryBreathingRead,
                    onRetryShareReads = viewModel::retryShareReads,
                    autoIngestLive = autoIngestLive,
                    onToggleAutoIngest = { viewModel.setAutoIngestLiveReadings(it) },
                    onSpotCheck = { viewModel.triggerSpotCheck() },
                    onShowNotification = { viewModel.showNotification(it) },
                    onScanClick = { savedTab = 2 },
                    onDisconnect = { viewModel.disconnectDevice() },
                    geminiInsightText = geminiInsightText,
                    isGeneratingGeminiInsight = isGeneratingGeminiInsight,
                    onRefreshGeminiInsight = { viewModel.generateGeminiInsight() },
                    geminiInsightGeneratedAtMillis = geminiInsightMeta.generatedAtMillis,
                    geminiInsightFailed = geminiInsightMeta.failed,
                    todayHydrationMl = todayHydrationMl,
                    totalBreathingSeconds = totalBreathingSeconds,
                    onSaveBreathingSession = viewModel::saveBreathingSession,
                    breathingSaveState = breathingSaveState,
                    onGenerateShareData = sharePreview::open,
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

                4 -> SettingsTab(
                    userProfile = userProfile,
                    profileReadFailed = profileRead == LocalReadState.Failed,
                    onRetryProfileRead = viewModel::retryProfileRead,
                    onEditProfileClick = { showProfileDialog = true },
                    autoReconnectBle = autoReconnectBle,
                    onAutoReconnectChange = { viewModel.setAutoReconnectBle(it) },
                    upperThreshold = upperHrThreshold,
                    lowerThreshold = lowerHrThreshold,
                    alertsEnabled = hrAlertsEnabled,
                    onUpperThresholdChange = { viewModel.setUpperHrThreshold(it) },
                    onLowerThresholdChange = { viewModel.setLowerHrThreshold(it) },
                    onAlertsEnabledChange = { viewModel.setHrAlertsEnabled(it) },
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
            currentProfile = profileForEditor,
            readAvailable = profileRead is LocalReadState.Ready,
            readFailed = profileRead == LocalReadState.Failed,
            onRetryRead = viewModel::retryProfileRead,
            onDismissRequest = { showProfileDialog = false },
            onSaveProfile = viewModel::saveUserProfile,
            saveState = profileSaveState,
        )
    }

}

@Composable
internal fun DashboardTab(
    connectedDevice: com.example.data.model.HBandDevice?,
    latestTelemetry: com.example.data.model.HBandTelemetry?,
    sensorMetrics: List<com.example.data.local.HBandSensorMetricEntity>?,
    autoIngestLive: Boolean,
    onToggleAutoIngest: (Boolean) -> Unit,
    onSpotCheck: () -> Unit,
    onShowNotification: (String) -> Unit,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
    geminiInsightText: String = "",
    isGeneratingGeminiInsight: Boolean = false,
    onRefreshGeminiInsight: () -> Unit = {},
    geminiInsightGeneratedAtMillis: Long? = null,
    geminiInsightFailed: Boolean = false,
    // AI_INSIGHT_ENABLED=true highlights the summary right after "Meu relógio"; false keeps the
    // collapsed section near the end of the screen, exactly as before.
    aiInsightEnabled: Boolean = BuildConfig.AI_INSIGHT_ENABLED,
    todayHydrationMl: Int? = null,
    totalBreathingSeconds: Int? = null,
    onSaveBreathingSession: (String, Int) -> Unit = { _, _ -> },
    breathingSaveState: BreathingSaveState? = null,
    onGenerateShareData: (com.example.util.ShareProgressData) -> Unit = {},
    shareSensorMetrics: List<com.example.data.local.HBandSensorMetricEntity>? = null,
    metricsReadFailed: Boolean = false,
    hydrationReadFailed: Boolean = false,
    breathingReadFailed: Boolean = false,
    onRetryMetricsRead: () -> Unit = {},
    onRetryBreathingRead: () -> Unit = {},
    onRetryShareReads: () -> Unit = {},
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
                onScanClick = onScanClick,
                onDisconnect = onDisconnect,
            )

            com.example.ui.components.LowBatteryWarningCard(
                device = connectedDevice,
                modifier = Modifier.fillMaxWidth()
            )

            if (aiInsightEnabled) {
                com.example.ui.components.AiInsightHighlightCard(
                    insightText = geminiInsightText,
                    isLoading = isGeneratingGeminiInsight,
                    onRefreshInsight = onRefreshGeminiInsight,
                    generatedAtMillis = geminiInsightGeneratedAtMillis,
                    failed = geminiInsightFailed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }, second = {
            TelemetryGauges(telemetry = latestTelemetry)

            if (metricsReadFailed) com.example.ui.components.LocalReadNotice("o histórico", onRetryMetricsRead)
            else com.example.ui.components.DailyHealthSummaryCard(
                metrics = sensorMetrics,
                modifier = Modifier.fillMaxWidth()
            )


        })

        com.example.ui.components.BreathingExerciseCard(
            totalBreathingSeconds = totalBreathingSeconds,
            readFailed = breathingReadFailed,
            onRetryRead = onRetryBreathingRead,
            onSaveSession = onSaveBreathingSession,
            saveState = breathingSaveState,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.PatientSection(
            title = "Sono",
            forceExpanded = false,
        ) {
            if (metricsReadFailed) com.example.ui.components.LocalReadNotice("os registros de sono", onRetryMetricsRead)
            else com.example.ui.components.SleepAnalysisCard(
                metrics = sensorMetrics,
                modifier = Modifier.fillMaxWidth()
            )
        }

        com.example.ui.components.PatientSection(
            title = "Compartilhar registros",
            forceExpanded = false,
        ) {
            com.example.ui.components.PatientShareRecords(
                metrics = shareSensorMetrics,
                hydrationMl = todayHydrationMl,
                breathingSeconds = totalBreathingSeconds,
                metricsReadFailed = metricsReadFailed,
                diaryReadFailed = hydrationReadFailed || breathingReadFailed,
                onRetryRead = onRetryShareReads,
                onGenerate = onGenerateShareData,
                onNotify = onShowNotification,
            )
        }

        if (!aiInsightEnabled) com.example.ui.components.PatientSection(
            title = "Resumo com inteligência artificial",
            forceExpanded = false,
        ) {
            com.example.ui.components.GeminiHealthInsightCard(
                insightText = geminiInsightText,
                isLoading = isGeneratingGeminiInsight,
                onRefreshInsight = onRefreshGeminiInsight,
                modifier = Modifier.fillMaxWidth(),
                showInsight = false,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
