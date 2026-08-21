package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MinimalBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
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
    firestoreStatus: String = "Idle",
    lastBackupTime: Long? = null,
    lastBackupCount: Int = 0,
    onTriggerBackup: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onTestApiSmoke: () -> Unit = {},
    onResetAllData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val profile = userProfile ?: com.example.data.local.UserProfileEntity()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User & Patient Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_profile_settings_card"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF00639B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = profile.fullName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                            Text(
                                text = "ID Paciente: ${profile.patientId} • ${profile.age} anos",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00639B)
                            )
                        }
                    }

                    Button(
                        onClick = onEditProfileClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier.testTag("btn_edit_profile_settings")
                    ) {
                        Text("Editar Perfil", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Altura / Peso:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text("${profile.heightCm.toInt()} cm / ${profile.weightKg.toInt()} kg", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F172A))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Meta Diária:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text("${profile.dailyStepGoal} passos • ${profile.targetWaterMl} mL", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F172A))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Emergência:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text(profile.emergencyContact, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF0F172A))
                        }
                    }
                }
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
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reconexão Automática BLE",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = "Reconectar automaticamente com pulseiras VE30 / HBand se o sinal cair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF44474E)
                    )
                }
                Switch(
                    checked = autoReconnectBle,
                    onCheckedChange = onAutoReconnectChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00639B)
                    ),
                    modifier = Modifier.testTag("auto_reconnect_switch")
                )
            }
        }
        // Firebase Firestore Cloud Backup Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("firestore_cloud_backup_card"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color(0xFF0288D1),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Backup Firebase Firestore",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                            Text(
                                text = "Sincronização de Saúde na Nuvem",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF44474E)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            firestoreStatus.contains("Synced") -> Color(0xFFE8F5E9)
                            firestoreStatus.contains("Backing") -> Color(0xFFFFF3E0)
                            else -> Color(0xFFF1F5F9)
                        }
                    ) {
                        Text(
                            text = when {
                                firestoreStatus.contains("Synced") -> "Sincronizado"
                                firestoreStatus.contains("Backing") -> "Salvando..."
                                else -> firestoreStatus
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                firestoreStatus.contains("Synced") -> Color(0xFF2E7D32)
                                firestoreStatus.contains("Backing") -> Color(0xFFE65100)
                                else -> Color(0xFF475569)
                            },
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("firestore_sync_status_badge")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (lastBackupTime != null) {
                    val formattedTime = remember(lastBackupTime) {
                        SimpleDateFormat("dd/MM, HH:mm:ss", Locale.forLanguageTag("pt-BR")).format(Date(lastBackupTime))
                    }
                    Text(
                        text = "Último backup: $formattedTime ($lastBackupCount registros)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF00639B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTriggerBackup,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_to_firestore_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fazer Backup", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    OutlinedButton(
                        onClick = onRestoreBackup,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFF00639B)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00639B)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_from_firestore_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restaurar Dados", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

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
                // Row 1: Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                                text = "Limites de Frequência Cardíaca",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                            Text(
                                text = "Alertas do Sistema no Dispositivo Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF44474E)
                            )
                        }
                    }

                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = onAlertsEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.testTag("hr_alerts_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Safe Zone Banner
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
                                text = if (alertsEnabled) "Zona Segura: $lowerThreshold – $upperThreshold BPM" else "Alertas Desativados",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (alertsEnabled) Color(0xFF1B5E20) else Color(0xFF64748B)
                            )
                            Text(
                                text = if (alertsEnabled)
                                    "Notificação local gerada imediatamente se a frequência ultrapassar estes limites."
                                else
                                    "Ative o botão acima para monitorar os limites de frequência cardíaca.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alertsEnabled) Color(0xFF2E7D32) else Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Upper Threshold Setting Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Limite Superior (BPM Máx):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF191C1E)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = "$upperThreshold BPM",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                    )

                    // Preset buttons for upper threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    .weight(1f)
                                    .testTag("preset_upper_$preset")
                            ) {
                                Text("$preset", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Lower Threshold Setting Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Limite Inferior (BPM Mín):",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF191C1E)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE3F2FD)
                        ) {
                            Text(
                                text = "$lowerThreshold BPM",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0288D1),
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
                    )

                    // Preset buttons for lower threshold
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    contentColor = if (lowerThreshold == preset) Color(0xFF0288D1) else Color(0xFF44474E)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preset_lower_$preset")
                            ) {
                                Text("$preset", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }

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
                            text = "Credenciais & Teste de Conexão API",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Text(
                            text = "Base: https://healthtech-secure-api-5794833455.us-central1.run.app",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0284C7)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "• Módulo: com.healthtech.companion.net.HealthtechRepository",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "• Paciente Padrão: PAT-HBAND-001",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "• Header X-API-Key: Configurado via BuildConfig / .env",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF475569)
                        )
                    }
                }

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
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Executar Teste de Ingestão (smokeHeart)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

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
                            text = "Zerar Dados para Nova Instalação",
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
                    text = "Use esta opção para preparar o app para um novo dispositivo VE30 ou paciente, garantindo que toda a telemetria inicie limpa do zero.",
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
                        text = "Zerar Todos os Dados Agora",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
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
                    text = "Esta ação apagará todas as métricas biométricas salvas no banco de dados Room, esvaziará a fila de ingestão e zerará os contadores para iniciar a coleta limpa dos VE30.",
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
