package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalBorder

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsTab(
    userProfile: com.example.data.local.UserProfileEntity? = null,
    onEditProfileClick: () -> Unit = {},
    autoReconnectBle: Boolean = true,
    onAutoReconnectChange: (Boolean) -> Unit = {},
    upperThreshold: Int,
    lowerThreshold: Int,
    alertsEnabled: Boolean,
    onUpperThresholdChange: (Int) -> Unit,
    onLowerThresholdChange: (Int) -> Unit,
    onAlertsEnabledChange: (Boolean) -> Unit,
    onTestHighAlert: () -> Unit,
    onTestLowAlert: () -> Unit,
    onTestApiSmoke: () -> Unit = {},
    onResetAllData: () -> Unit = {},
    capabilities: com.example.data.hband.DeviceCapabilities = com.example.data.hband.DeviceCapabilities(),
    autoMeasureState: com.example.data.hband.AutoMeasureUiState = com.example.data.hband.AutoMeasureUiState(),
    wearDetectState: com.example.data.hband.WearDetectUiState = com.example.data.hband.WearDetectUiState(),
    historySyncState: com.example.data.hband.HistorySyncUiState = com.example.data.hband.HistorySyncUiState(),
    hardwareConnected: Boolean = false,
    actionsEnabled: Boolean = hardwareConnected,
    onAutoMeasureChange: (Boolean) -> Unit = {},
    onSpo2AutoChange: (Boolean) -> Unit = {},
    onWearDetectChange: (Boolean) -> Unit = {},
    onSyncHistory: () -> Unit = {},
    alarmState: com.example.data.hband.AlarmUiState = com.example.data.hband.AlarmUiState(),
    heartWarningState: com.example.data.hband.HeartWarningUiState = com.example.data.hband.HeartWarningUiState(),
    longSeatState: com.example.data.hband.LongSeatUiState = com.example.data.hband.LongSeatUiState(),
    nightTurnState: com.example.data.hband.NightTurnUiState = com.example.data.hband.NightTurnUiState(),
    findDeviceState: com.example.data.hband.FindDeviceUiState = com.example.data.hband.FindDeviceUiState(),
    healthRemindState: com.example.data.hband.HealthRemindUiState = com.example.data.hband.HealthRemindUiState(),
    onAlarmChange: (Boolean) -> Unit = {},
    onHeartWarningChange: (Boolean) -> Unit = {},
    onLongSeatChange: (Boolean) -> Unit = {},
    onNightTurnChange: (Boolean) -> Unit = {},
    onFindDeviceChange: (Boolean) -> Unit = {},
    onStartFindByPhone: () -> Unit = {},
    onStopFindByPhone: () -> Unit = {},
    onHealthRemindChange: (Boolean) -> Unit = {},
    ingestDiagnostics: com.example.data.ingest.IngestDiagnostics = com.example.data.ingest.IngestDiagnostics(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showDevelopment by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        Card(
            modifier = Modifier.fillMaxWidth().testTag("user_profile_settings_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Meu perfil", style = MaterialTheme.typography.titleLarge)
                Text(userProfile?.fullName ?: "Perfil indisponível no momento", style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = onEditProfileClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_edit_profile_settings"),
                ) { Text(if (userProfile != null) "Ver e editar meu perfil" else "Sobre meu perfil") }
            }
        }

        // BLE Auto-Reconnect Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MinimalBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("auto_reconnect_switch")
                    .toggleable(
                        value = autoReconnectBle,
                        role = Role.Switch,
                        onValueChange = onAutoReconnectChange,
                    )
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reconectar relógio",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = "Tentar conectar novamente quando o relógio perder a conexão.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF44474E)
                    )
                }
                Switch(
                    checked = autoReconnectBle,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00639B)
                    ),
                )
            }
        }

        PatientSection(title = "Opções do relógio", forceExpanded = historySyncState.isRunning || findDeviceState.finding) {
            BandSdkSettingsCard(
                capabilities = capabilities,
                autoMeasure = autoMeasureState,
                wearDetect = wearDetectState,
                historySync = historySyncState,
                hardwareConnected = hardwareConnected,
                actionsEnabled = actionsEnabled,
                onAutoMeasureChange = onAutoMeasureChange,
                onSpo2AutoChange = onSpo2AutoChange,
                onWearDetectChange = onWearDetectChange,
                onSyncHistory = onSyncHistory,
                alarm = alarmState,
                heartWarning = heartWarningState,
                longSeat = longSeatState,
                nightTurn = nightTurnState,
                findDevice = findDeviceState,
                healthRemind = healthRemindState,
                onAlarmChange = onAlarmChange,
                onHeartWarningChange = onHeartWarningChange,
                onLongSeatChange = onLongSeatChange,
                onNightTurnChange = onNightTurnChange,
                onFindDeviceChange = onFindDeviceChange,
                onStartFindByPhone = onStartFindByPhone,
                onStopFindByPhone = onStopFindByPhone,
                onHealthRemindChange = onHealthRemindChange,
            )
        }

        PatientSection(title = "Avisos de batimentos", forceExpanded = false) {
            // Main Settings Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hr_threshold_settings_card"),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MinimalBorder),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Keep the named switch separate from the heading at large font scales.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Limites dos avisos",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF191C1E)
                                )
                                Text(
                                    text = "Avisos de batimentos neste aparelho",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF44474E)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("hr_alerts_toggle_switch")
                                .toggleable(
                                    value = alertsEnabled,
                                    role = Role.Switch,
                                    onValueChange = onAlertsEnabledChange,
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Ativar avisos neste aparelho",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = alertsEnabled,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFD32F2F)
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Local configuration does not establish delivery or a clinical safe range.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (alertsEnabled) Color(0xFFE8F5E9) else Color(0xFFF1F4F9),
                        border = BorderStroke(1.dp, if (alertsEnabled) Color(0xFFA5D6A7) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (alertsEnabled) Color(0xFF2E7D32) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (alertsEnabled) "Limites de aviso: $lowerThreshold – $upperThreshold bpm" else "Avisos desativados no aplicativo",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (alertsEnabled) Color(0xFF1B5E20) else Color(0xFF64748B)
                                )
                                Text(
                                    text = if (alertsEnabled)
                                        "Os avisos dependem das leituras recebidas e da permissão de notificação deste aparelho."
                                    else
                                        "Use a opção acima para ativar os avisos neste aparelho.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (alertsEnabled) Color(0xFF2E7D32) else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (alertsEnabled) {
                        PatientNotificationSettings()
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Upper Threshold Setting Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Avisar acima de",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF191C1E)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFEBEE)
                            ) {
                                Text(
                                    text = "$upperThreshold bpm",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFD32F2F),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("upper_threshold_display")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = upperThreshold.toFloat(),
                            onValueChange = { onUpperThresholdChange(it.toInt()) },
                            valueRange = 80f..180f,
                            steps = 99,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD32F2F),
                                activeTrackColor = Color(0xFFD32F2F),
                                inactiveTrackColor = Color(0xFFFFCDD2)
                            ),
                            enabled = alertsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upper_threshold_slider")
                                .semantics { contentDescription = "Limite superior de batimentos por minuto" }
                        )

                        // Preset buttons for upper threshold
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(90, 100, 120, 140, 160).forEach { preset ->
                                OutlinedButton(
                                    onClick = { onUpperThresholdChange(preset) },
                                    enabled = alertsEnabled,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (upperThreshold == preset) Color(0xFFD32F2F) else Color(0xFFE2E8F0)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (upperThreshold == preset) Color(0xFFFFEBEE) else Color.Transparent,
                                        contentColor = if (upperThreshold == preset) Color(0xFFD32F2F) else Color(0xFF44474E)
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 56.dp)
                                        .testTag("preset_upper_$preset")
                                        .semantics { selected = upperThreshold == preset }
                                ) {
                                    Text("$preset", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Lower Threshold Setting Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Avisar abaixo de",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF191C1E)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE3F2FD)
                            ) {
                                Text(
                                    text = "$lowerThreshold bpm",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF005EA6),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("lower_threshold_display")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = lowerThreshold.toFloat(),
                            onValueChange = { onLowerThresholdChange(it.toInt()) },
                            valueRange = 35f..75f,
                            steps = 39,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF0288D1),
                                activeTrackColor = Color(0xFF0288D1),
                                inactiveTrackColor = Color(0xFFBBDEFB)
                            ),
                            enabled = alertsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lower_threshold_slider")
                                .semantics { contentDescription = "Limite inferior de batimentos por minuto" }
                        )

                        // Preset buttons for lower threshold
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(40, 45, 50, 55, 60).forEach { preset ->
                                OutlinedButton(
                                    onClick = { onLowerThresholdChange(preset) },
                                    enabled = alertsEnabled,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (lowerThreshold == preset) Color(0xFF0288D1) else Color(0xFFE2E8F0)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (lowerThreshold == preset) Color(0xFFE3F2FD) else Color.Transparent,
                                        contentColor = if (lowerThreshold == preset) Color(0xFF005EA6) else Color(0xFF44474E)
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 56.dp)
                                        .testTag("preset_lower_$preset")
                                        .semantics { selected = lowerThreshold == preset }
                                ) {
                                    Text("$preset", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (com.example.BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = { showDevelopment = !showDevelopment },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if (showDevelopment) "Fechar ferramentas de desenvolvimento" else "Ferramentas de desenvolvimento") }
        }

        if (com.example.BuildConfig.DEBUG && showDevelopment) {
            PatientSection(title = "Cópia dos dados para suporte", forceExpanded = false) {
                CloudBackupUnavailableCard()
            }
        }
        if (com.example.BuildConfig.DEBUG && showDevelopment) {
            PatientSection(title = "Testar avisos", forceExpanded = false) {
                // Alert Simulation Testing Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hr_alert_testing_card"),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MinimalBorder),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFF3E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Testar Notificações de Alerta",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF191C1E)
                                )
                                Text(
                                    text = "Disparar alertas no canal de notificação do Android",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF44474E)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onTestHighAlert,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_high_hr_alert_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testar FC Alta", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = onTestLowAlert,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Color(0xFF0288D1)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0288D1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_low_hr_alert_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testar FC Baixa", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        if (com.example.BuildConfig.DEBUG && showDevelopment) {
            PatientSection(title = "Teste do serviço", forceExpanded = false) {
                // API Credentials & Smoke Test Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_credentials_smoke_test_card"),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MinimalBorder),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Conexão com o serviço",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF191C1E)
                                )
                                Text(
                                    text = "Verificação de disponibilidade, sem criar leituras.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        IngestDiagnosticsCard(ingestDiagnostics)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onTestApiSmoke,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_smoke_heart_test_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verificar conexão",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        if (com.example.BuildConfig.DEBUG && showDevelopment) {
            PatientSection(title = "Limpeza para testes", forceExpanded = false) {
                // Factory Reset / Clear Data Card for New Installation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("factory_reset_clean_data_card"),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Apagar registros locais de teste",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFC62828)
                                )
                                Text(
                                    text = "Limpa histórico local, métricas, logs de fila e biometria",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF74777F)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Esta limpeza é uma ferramenta de desenvolvimento. Ela não troca o paciente, não altera sua identificação e não confirma exclusão de dados enviados a outros serviços.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF44474E)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showResetConfirmDialog = true },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_all_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Revisar limpeza dos registros",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Zerar todos os dados locais?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFC62828)
                )
            },
            text = {
                Text(
                    text = "Esta ação apaga as medições, a fila de envio, os registros de água e os exercícios de respiração deste celular. Registros que ainda não foram enviados também serão apagados.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Confirmar e Zerar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
