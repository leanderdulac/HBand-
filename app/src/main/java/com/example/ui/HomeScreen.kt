package com.example.ui

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
import com.example.ui.components.ApiHeader
import com.example.ui.components.DeviceControlCard
import com.example.ui.components.JsonPayloadModal
import com.example.ui.components.QueueInspector
import com.example.ui.components.TelemetryGauges
import com.example.ui.theme.MinimalBorder

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
    val latestTelemetry by viewModel.latestTelemetry.collectAsStateWithLifecycle()
    val allSensorMetrics by viewModel.allSensorMetrics.collectAsStateWithLifecycle()
    val allQueueItems by viewModel.allQueueItems.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()
    val consecutiveFailures by viewModel.consecutiveFailures.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncDisplayStatus by viewModel.syncDisplayStatus.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val autoIngestLive by viewModel.autoIngestLiveReadings.collectAsStateWithLifecycle()
    val selectedModalItem by viewModel.selectedQueueItemForPreview.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val autoReconnectBle by viewModel.autoReconnectBle.collectAsStateWithLifecycle()
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
            // Clean Minimalism Bottom Navigation Bar matching design HTML
            NavigationBar(
                containerColor = Color(0xFFF1F4F9),
                modifier = Modifier
                    .border(BorderStroke(1.dp, MinimalBorder))
                    .testTag("main_tab_row")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
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
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QueryStats, contentDescription = null) },
                    label = { Text("Gráficos", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF004A77),
                        selectedTextColor = Color(0xFF004A77),
                        unselectedIconColor = Color(0xFF44474E),
                        unselectedTextColor = Color(0xFF44474E),
                        indicatorColor = Color(0xFFD1E4FF)
                    ),
                    modifier = Modifier.testTag("tab_recharts")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
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
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(containerColor = Color(0xFF00639B)) { Text("$pendingCount", color = Color.White) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                        }
                    },
                    label = { Text("Fila", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF004A77),
                        selectedTextColor = Color(0xFF004A77),
                        unselectedIconColor = Color(0xFF44474E),
                        unselectedTextColor = Color(0xFF44474E),
                        indicatorColor = Color(0xFFD1E4FF)
                    ),
                    modifier = Modifier.testTag("tab_queue")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Clean Minimalism Header (from design HTML) with Profile Editing Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Bem-vindo de volta,",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF44474E)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userProfile?.fullName ?: "Alex Rivera",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color(0xFF001D31)
                        )
                    }
                    Text(
                        text = "ID: ${userProfile?.patientId ?: "PAT-HBAND-001"} • Toque para editar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00639B)
                    )
                }

                Button(
                    onClick = { showProfileDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0F2FE), contentColor = Color(0xFF004A77)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("header_edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Editar Perfil",
                        tint = Color(0xFF004A77),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Perfil", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            // Tab Content Body
            Box(
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
                        autoIngestLive = autoIngestLive,
                        onTriggerSync = { viewModel.triggerWorkManagerSync() },
                        onRefreshHealth = { viewModel.checkHealth() },
                        onMarkLocalSynced = { viewModel.markAllAsLocalSynced() },
                        onRetryAll = { viewModel.retryAllFailed() },
                        onToggleAutoIngest = { viewModel.setAutoIngestLiveReadings(it) },
                        onSpotCheck = { viewModel.triggerSpotCheck() },
                        onSimulateBatch = { viewModel.enqueueBatchSimulated(it) },
                        onShowNotification = { viewModel.showNotification(it) },
                        onSimulateLowBattery = { viewModel.simulateLowBattery() },
                        onRechargeBattery = { viewModel.rechargeBattery() },
                        onScanClick = { selectedTab = 2 },
                        onDisconnect = { viewModel.disconnectDevice() },
                        geminiInsightText = geminiInsightText,
                        isGeneratingGeminiInsight = isGeneratingGeminiInsight,
                        onRefreshGeminiInsight = { viewModel.generateGeminiInsight() },
                        todayHydrationMl = todayHydrationMl,
                        hydrationTargetMl = userProfile?.targetWaterMl ?: viewModel.hydrationTargetGoalMl,
                        onAddWater = { viewModel.addWaterIntake(it) },
                        onResetHydration = { viewModel.resetTodayHydration() },
                        totalBreathingSeconds = totalBreathingSeconds,
                        onSaveBreathingSession = { viewModel.saveBreathingSession(it) },
                        onGenerateShareData = { activeShareData = it }
                    )

                    1 -> com.example.ui.components.RechartsSensorDashboard(
                        sensorMetrics = allSensorMetrics,
                        onSimulateBatch = { viewModel.enqueueBatchSimulated(it) }
                    )

                    2 -> BleDevicesTab(
                        scannedDevices = scannedDevices,
                        connectedDevice = connectedDevice,
                        isScanning = isScanning,
                        onStartScan = { viewModel.startBleScan() },
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
                        onRefreshWorkManager = { viewModel.triggerWorkManagerSync() }
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
                        firestoreStatus = firestoreSyncStatus,
                        lastBackupTime = lastFirestoreBackupTime,
                        lastBackupCount = lastFirestoreBackupCount,
                        onTriggerBackup = { viewModel.triggerFirestoreBackup() },
                        onRestoreBackup = { viewModel.restoreFromFirestoreBackup() },
                        onTestApiSmoke = { viewModel.testSmokeHeartConnection(userProfile?.patientId ?: "PAT-HBAND-001") },
                        onResetAllData = { viewModel.resetAllDataToZero() }
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
    onMarkLocalSynced: () -> Unit = {},
    onRetryAll: () -> Unit = {},
    onToggleAutoIngest: (Boolean) -> Unit,
    onSpotCheck: () -> Unit,
    onSimulateBatch: (Int) -> Unit,
    onShowNotification: (String) -> Unit,
    onSimulateLowBattery: () -> Unit,
    onRechargeBattery: () -> Unit,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
    geminiInsightText: String = "",
    isGeneratingGeminiInsight: Boolean = false,
    onRefreshGeminiInsight: () -> Unit = {},
    todayHydrationMl: Int = 0,
    hydrationTargetMl: Int = 2500,
    onAddWater: (Int) -> Unit = {},
    onResetHydration: () -> Unit = {},
    totalBreathingSeconds: Int = 0,
    onSaveBreathingSession: (Int) -> Unit = {},
    onGenerateShareData: (com.example.util.ShareProgressData) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        com.example.ui.components.SyncStatusIndicator(
            syncStatus = syncDisplayStatus,
            pendingCount = pendingCount,
            syncedCount = syncedCount,
            failedCount = failedCount,
            consecutiveFailures = consecutiveFailures,
            onTriggerSync = onTriggerSync,
            onRefreshHealth = onRefreshHealth,
            onMarkLocalSynced = onMarkLocalSynced,
            onRetryAll = onRetryAll,
            modifier = Modifier.fillMaxWidth()
        )

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

        com.example.ui.components.GeminiHealthInsightCard(
            insightText = geminiInsightText,
            isLoading = isGeneratingGeminiInsight,
            onRefreshInsight = onRefreshGeminiInsight,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.LowBatteryWarningCard(
            device = connectedDevice,
            onRechargeBattery = onRechargeBattery,
            modifier = Modifier.fillMaxWidth()
        )

        TelemetryGauges(telemetry = latestTelemetry)

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

        com.example.ui.components.HydrationCard(
            currentMl = todayHydrationMl,
            targetGoalMl = hydrationTargetMl,
            logs = emptyList(),
            onAddWater = onAddWater,
            onResetToday = onResetHydration,
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

        com.example.ui.components.RechartsSensorDashboard(
            sensorMetrics = sensorMetrics,
            onSimulateBatch = onSimulateBatch,
            isScrollable = false,
            modifier = Modifier.fillMaxWidth()
        )

        com.example.ui.components.CsvExportCard(
            metrics = sensorMetrics,
            onShowNotification = onShowNotification,
            modifier = Modifier.fillMaxWidth()
        )

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

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BleDevicesTab(
    scannedDevices: List<com.example.data.model.HBandDevice>,
    connectedDevice: com.example.data.model.HBandDevice?,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onConnectDevice: (com.example.data.model.HBandDevice) -> Unit,
    onConnectByMac: (String) -> Unit,
    onDisconnectDevice: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var customMacInput by remember { mutableStateOf("") }
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
        // Quick Connect Gear S3 Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_connect_gears3_card"),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFFBBE9FF)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00639B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Samsung Gear S3 LE",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF001D31)
                            )
                            Text(
                                text = "Conexão GATT direta (GATT Heart Rate & RSC)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00639B)
                            )
                        }
                    }

                    Button(
                        onClick = { onConnectByMac("DC:C1:C6:6B:D4:FC") },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier.testTag("quick_connect_gears3_button")
                    ) {
                        Text(
                            text = if (connectedDevice?.name?.contains("Gear", ignoreCase = true) == true && connectedDevice.isConnected) "Conectado" else "Conectar Gear S3",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Dica Gear S3: Mantenha o relógio firme no pulso e inicie a medição de FC ou Treino no relógio para transmitir os batimentos continuamente via Bluetooth LE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF44474E)
                )
            }
        }

        // Quick Connect VE30 Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_connect_ve30_card"),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Smartwatch VE30 (HBand)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF001D31)
                            )
                            Text(
                                text = "Ativar conexão direta e telemetria de sensores",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Button(
                        onClick = { onConnectByMac("C4:E3:42:VE:30:A4") },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.testTag("quick_connect_ve30_button")
                    ) {
                        Text(
                            text = if (connectedDevice?.deviceId == "C4:E3:42:VE:30:A4" && connectedDevice.isConnected) "Conectado" else "Conectar VE30",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                    }

                    Button(
                        onClick = { handleScanClick() },
                        enabled = !isScanning,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier.testTag("scan_ble_button")
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isScanning) "Buscando..." else "Escanear BLE")
                    }
                }
            }
        }

        // Direct MAC address connection card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Conexão Direta por Endereço MAC",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF001D31)
                )
                Text(
                    text = "Digite o endereço MAC exato do seu VE30 para conexão direta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF44474E)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMacInput,
                        onValueChange = { customMacInput = it },
                        placeholder = { Text("Ex: DC:23:4E:91:A2:3B", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_mac_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (customMacInput.isNotBlank()) {
                                onConnectByMac(customMacInput.trim())
                            }
                        },
                        enabled = customMacInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier.testTag("connect_mac_button")
                    ) {
                        Text("Conectar")
                    }
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
                    text = "Toque em 'Escanear BLE' ou insira o MAC acima para conectar seu VE30.",
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

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = Color(0xFF00639B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Arquitetura do Pipeline HBand SDK",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF001D31)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "1. Características BLE GATT (FC, PA, SpO2, Temp, Passos) decodificadas pelo HBand BleManager.\n" +
                            "2. Formatadas em estrutura de payload JSON padronizada.\n" +
                            "3. Enfileiradas localmente no banco Room para resiliência offline.\n" +
                            "4. Sincronizadas via HTTP POST para /api/v1/wearables/ingest com repetição automática.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = Color(0xFF44474E)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
