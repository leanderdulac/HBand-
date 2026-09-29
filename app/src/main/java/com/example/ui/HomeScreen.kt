package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import com.example.ui.components.SettingsTab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DeviceControlCard
import com.example.ui.components.HomeWelcomeHeader
import com.example.ui.components.TelemetryGauges
import com.example.ui.theme.MinimalBorder
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
    val latestTelemetry by viewModel.latestTelemetry.collectAsStateWithLifecycle()
    val allSensorMetrics by viewModel.allSensorMetrics.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val autoIngestLive by viewModel.autoIngestLiveReadings.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
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
    var showProfileDialog by remember { mutableStateOf(false) }

    val upperHrThreshold by viewModel.upperHrThreshold.collectAsStateWithLifecycle()
    val lowerHrThreshold by viewModel.lowerHrThreshold.collectAsStateWithLifecycle()
    val hrAlertsEnabled by viewModel.hrAlertsEnabled.collectAsStateWithLifecycle()

    val firestoreSyncStatus by viewModel.firestoreSyncStatus.collectAsStateWithLifecycle()
    val lastFirestoreBackupTime by viewModel.lastFirestoreBackupTime.collectAsStateWithLifecycle()
    val lastFirestoreBackupCount by viewModel.lastFirestoreBackupCount.collectAsStateWithLifecycle()

    val geminiInsightText by viewModel.geminiInsightText.collectAsStateWithLifecycle()
    val isGeneratingGeminiInsight by viewModel.isGeneratingGeminiInsight.collectAsStateWithLifecycle()

    val todayHydrationMl by viewModel.todayHydrationMl.collectAsStateWithLifecycle()
    val totalBreathingSeconds by viewModel.totalBreathingSeconds.collectAsStateWithLifecycle()

    var activeShareData by remember { mutableStateOf<com.example.util.ShareProgressData?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val p1ActionsEnabled = VeepooSessionGate.actionsEnabled(
        hardwareConnected = isHardwareConnected,
        connectedMac = connectedDevice?.macAddress,
        telemetry = latestTelemetry,
    )

    activeShareData?.let { shareData ->
        com.example.ui.components.ShareProgressDialog(
            shareData = shareData,
            onDismiss = { activeShareData = null },
            onShowSnackbar = { viewModel.showNotification(it) }
        )
    }

    LaunchedEffect(notification) {
        notification?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.dismissNotification()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF8F9FF),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.triggerSpotCheck() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Sincronizar Agora"
                    )
                },
                text = {
                    Text(
                        text = if (isSyncing) "Sincronizando..." else "Sincronizar Agora",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                containerColor = Color(0xFF00639B),
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("sync_now_fab")
            )
        },
        bottomBar = {
            PatientMainNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HomeWelcomeHeader(
                fullName = userProfile?.fullName ?: "Alex Rivera",
                patientId = userProfile?.patientId ?: "PAT-HBAND-001",
                onEditProfile = { showProfileDialog = true },
            )

            // Tab Content Body
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        connectedDevice = connectedDevice,
                        latestTelemetry = latestTelemetry,
                        sensorMetrics = allSensorMetrics,
                        autoIngestLive = autoIngestLive,
                        onToggleAutoIngest = { viewModel.setAutoIngestLiveReadings(it) },
                        onSpotCheck = { viewModel.triggerSpotCheck() },
                        onScanClick = { selectedTab = 2 },
                        onDisconnect = { viewModel.disconnectDevice() },
                        geminiInsightText = geminiInsightText,
                        isGeneratingGeminiInsight = isGeneratingGeminiInsight,
                        onRefreshGeminiInsight = { viewModel.generateGeminiInsight() },
                        todayHydrationMl = todayHydrationMl,
                        totalBreathingSeconds = totalBreathingSeconds,
                        onSaveBreathingSession = { viewModel.saveBreathingSession(it) },
                        onGenerateShareData = { activeShareData = it },
                    )

                    2 -> BleDevicesTab(
                        scannedDevices = scannedDevices,
                        connectedDevice = connectedDevice,
                        isScanning = isScanning,
                        onStartScan = { viewModel.startBleScan() },
                        onConnectDevice = { viewModel.connectDevice(it) },
                        onDisconnectDevice = { viewModel.disconnectDevice() }
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
                        firestoreStatus = firestoreSyncStatus,
                        lastBackupTime = lastFirestoreBackupTime,
                        lastBackupCount = lastFirestoreBackupCount,
                        onTriggerBackup = { viewModel.triggerFirestoreBackup() },
                        onRestoreBackup = { viewModel.restoreFromFirestoreBackup() },
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
                    )
                }
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

}

@Composable
internal fun DashboardTab(
    connectedDevice: com.example.data.model.HBandDevice?,
    latestTelemetry: com.example.data.model.HBandTelemetry?,
    sensorMetrics: List<com.example.data.local.HBandSensorMetricEntity>,
    autoIngestLive: Boolean,
    onToggleAutoIngest: (Boolean) -> Unit,
    onSpotCheck: () -> Unit,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
    geminiInsightText: String = "",
    isGeneratingGeminiInsight: Boolean = false,
    onRefreshGeminiInsight: () -> Unit = {},
    todayHydrationMl: Int = 0,
    totalBreathingSeconds: Int = 0,
    onSaveBreathingSession: (Int) -> Unit = {},
    onGenerateShareData: (com.example.util.ShareProgressData) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DeviceControlCard(
            device = connectedDevice,
            autoIngestLive = autoIngestLive,
            onToggleAutoIngest = onToggleAutoIngest,
            onSpotCheck = onSpotCheck,
            onScanClick = onScanClick,
            onDisconnect = onDisconnect,
        )

        TelemetryGauges(telemetry = latestTelemetry)

        com.example.ui.components.GeminiHealthInsightCard(
            insightText = geminiInsightText,
            isLoading = isGeneratingGeminiInsight,
            onRefreshInsight = onRefreshGeminiInsight,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.LowBatteryWarningCard(
            device = connectedDevice,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.DailyHealthSummaryCard(
            metrics = sensorMetrics,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.ShareProgressCard(
            sensorMetrics = sensorMetrics,
            hydrationMl = todayHydrationMl,
            breathingSeconds = totalBreathingSeconds,
            onGenerateShareData = onGenerateShareData,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.BreathingExerciseCard(
            totalBreathingSeconds = totalBreathingSeconds,
            onSaveSession = onSaveBreathingSession,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.SleepAnalysisCard(
            metrics = sensorMetrics,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun BleDevicesTab(
    scannedDevices: List<com.example.data.model.HBandDevice>,
    connectedDevice: com.example.data.model.HBandDevice?,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onConnectDevice: (com.example.data.model.HBandDevice) -> Unit,
    onDisconnectDevice: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onStartScan()
    }

    fun handleScanClick() {
        val needsPermissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                needsPermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                needsPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needsPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needsPermissions.isNotEmpty()) {
            permissionLauncher.launch(needsPermissions.toTypedArray())
        } else {
            onStartScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Scanner BLE HBand & VE30",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF191C1E)
                )
                Text(
                    text = "Descubra e conecte pulseiras Bluetooth VE30 / HBand físicas",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF44474E)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { handleScanClick() },
                    enabled = !isScanning,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("scan_ble_button")
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Buscando..." else "Buscar dispositivos")
                }
            }
        }

        Text(
            text = "DISPOSITIVOS DETECTADOS / PAREADOS",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Color(0xFF44474E)
        )

        if (scannedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Toque em 'Buscar dispositivos' para encontrar seu VE30.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF44474E)
                )
            }
        } else {
            scannedDevices.forEach { device ->
                val isCurrent = connectedDevice?.deviceId == device.deviceId && connectedDevice?.isConnected == true

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MinimalBorder),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                            Text(
                                text = "MAC: ${device.macAddress} | RSSI: ${device.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF44474E)
                            )
                            if (device.firmwareVersion.isNotEmpty()) {
                                Text(
                                    text = device.firmwareVersion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00639B)
                                )
                            }
                        }

                        if (isCurrent) {
                            OutlinedButton(
                                onClick = onDisconnectDevice,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Desconectar", color = Color(0xFFBA1A1A))
                            }
                        } else {
                            Button(
                                onClick = { onConnectDevice(device) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B))
                            ) {
                                Text("Conectar")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun PatientMainNavigation(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFFF1F4F9),
        modifier = Modifier
            .border(BorderStroke(1.dp, MinimalBorder))
            .testTag("main_tab_row")
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
            label = { Text("Visão Geral", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF004A77),
                selectedTextColor = Color(0xFF004A77),
                unselectedIconColor = Color(0xFF44474E),
                unselectedTextColor = Color(0xFF44474E),
                indicatorColor = Color(0xFFD1E4FF)
            ),
            modifier = Modifier.testTag("tab_dashboard")
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Default.Watch, contentDescription = null) },
            label = { Text("Dispositivos", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF004A77),
                selectedTextColor = Color(0xFF004A77),
                unselectedIconColor = Color(0xFF44474E),
                unselectedTextColor = Color(0xFF44474E),
                indicatorColor = Color(0xFFD1E4FF)
            ),
            modifier = Modifier.testTag("tab_ble")
        )
        NavigationBarItem(
            selected = selectedTab == 4,
            onClick = { onSelect(4) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Ajustes", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF004A77),
                selectedTextColor = Color(0xFF004A77),
                unselectedIconColor = Color(0xFF44474E),
                unselectedTextColor = Color(0xFF44474E),
                indicatorColor = Color(0xFFD1E4FF)
            ),
            modifier = Modifier.testTag("tab_settings")
        )
    }
}
