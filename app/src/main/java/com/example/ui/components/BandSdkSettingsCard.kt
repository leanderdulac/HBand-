package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.hband.AlarmUiState
import com.example.data.hband.AutoMeasureUiState
import com.example.data.hband.DeviceCapabilities
import com.example.data.hband.FindDeviceUiState
import com.example.data.hband.HealthRemindUiState
import com.example.data.hband.HeartWarningUiState
import com.example.data.hband.HistorySyncUiState
import com.example.data.hband.historyReadStatusText
import com.example.data.hband.LongSeatUiState
import com.example.data.hband.NightTurnUiState
import com.example.data.hband.WearDetectUiState
import com.example.data.hband.VeepooSessionGate
import com.example.ui.theme.MinimalBorder

@Composable
fun BandSdkSettingsCard(
    capabilities: DeviceCapabilities,
    autoMeasure: AutoMeasureUiState,
    wearDetect: WearDetectUiState,
    historySync: HistorySyncUiState,
    hardwareConnected: Boolean,
    actionsEnabled: Boolean = hardwareConnected,
    onAutoMeasureChange: (Boolean) -> Unit,
    onSpo2AutoChange: (Boolean) -> Unit,
    onWearDetectChange: (Boolean) -> Unit,
    onSyncHistory: () -> Unit,
    alarm: AlarmUiState = AlarmUiState(),
    heartWarning: HeartWarningUiState = HeartWarningUiState(),
    longSeat: LongSeatUiState = LongSeatUiState(),
    nightTurn: NightTurnUiState = NightTurnUiState(),
    findDevice: FindDeviceUiState = FindDeviceUiState(),
    healthRemind: HealthRemindUiState = HealthRemindUiState(),
    onAlarmChange: (Boolean) -> Unit = {},
    onHeartWarningChange: (Boolean) -> Unit = {},
    onLongSeatChange: (Boolean) -> Unit = {},
    onNightTurnChange: (Boolean) -> Unit = {},
    onFindDeviceChange: (Boolean) -> Unit = {},
    onStartFindByPhone: () -> Unit = {},
    onStopFindByPhone: () -> Unit = {},
    onHealthRemindChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("band_sdk_settings_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        tint = Color(0xFF00639B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Opções do meu relógio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = if (capabilities.probed) {
                            "Opções identificadas na última conexão."
                        } else {
                            "Conecte o relógio para verificar as opções disponíveis."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF00639B)
                    )
                }
            }

            if (com.example.BuildConfig.DEBUG) PatientSection("Informações para suporte") { Text(
                text = capabilityLine(capabilities),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF44474E),
                modifier = Modifier.testTag("band_capability_summary")
            ) }

            if (capabilities.probed && !hardwareConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF4E5))
                        .padding(12.dp)
                        .testTag("band_sdk_reconnect_hint")
                ) {
                    Text(
                        text = patientWatchConnectionHint(actionsEnabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF9A3412)
                    )
                }
            }

            SettingToggleRow(
                title = "Batimentos automáticos",
                subtitle = if (autoMeasure.supported) {
                    "Permite ao relógio fazer leituras automaticamente."
                } else {
                    settingAvailability(capabilities.probed)
                },
                checked = autoMeasure.heartRateEnabled,
                enabled = actionsEnabled && autoMeasure.supported,
                testTag = "auto_measure_switch",
                onCheckedChange = onAutoMeasureChange,
            )

            SettingToggleRow(
                title = "Oxigênio durante a noite",
                subtitle = if (autoMeasure.spo2AutoSupported) {
                    "Leituras automáticas de oxigênio no sangue durante a noite."
                } else {
                    settingAvailability(capabilities.probed)
                },
                checked = autoMeasure.spo2NightAutoEnabled,
                enabled = actionsEnabled && autoMeasure.spo2AutoSupported,
                testTag = "spo2_auto_switch",
                onCheckedChange = onSpo2AutoChange,
            )

            val wearHint = when (wearDetect.lastWorn) {
                true -> "Último estado: em uso"
                false -> "Último estado: fora do pulso"
                null -> "Estado de uso ainda não lido"
            }
            SettingToggleRow(
                title = "Identificar uso no pulso",
                subtitle = if (wearDetect.supported) {
                    wearHint
                } else {
                    settingAvailability(capabilities.probed)
                },
                checked = wearDetect.enabled,
                enabled = actionsEnabled && wearDetect.supported,
                testTag = "wear_detect_switch",
                onCheckedChange = onWearDetectChange,
            )

            if (historySync.isRunning) {
                Column {
                    Text(
                        text = "Recebendo o histórico do relógio…",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00639B),
                        modifier = Modifier.testTag("history_sync_phase")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { historySync.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_sync_progress"),
                        color = Color(0xFF00639B),
                        trackColor = Color(0xFFD1E4FF),
                    )
                }
            } else {
                Text(
                    text = historyReadStatusText(historySync),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF334155),
                    modifier = Modifier.testTag("history_sync_status")
                )
            }

            Button(
                onClick = onSyncHistory,
                enabled = actionsEnabled && !historySync.isRunning,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("sync_history_button")
            ) {
                Text(
                    text = if (historySync.isRunning) "Recebendo histórico…" else "Receber histórico do relógio",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (alarm.supported) {
                SettingToggleRow(
                    title = "Alarme do relógio",
                    subtitle = alarm.summary.ifBlank { "Horário ainda não informado." },
                    checked = alarm.enabled,
                    enabled = actionsEnabled,
                    testTag = "alarm_switch",
                    onCheckedChange = onAlarmChange,
                )
            }
            if (heartWarning.supported) {
                SettingToggleRow(
                    title = "Aviso de batimentos no relógio",
                    subtitle = heartWarning.summary.ifBlank { "Limites ainda não informados." },
                    checked = heartWarning.enabled,
                    enabled = actionsEnabled,
                    testTag = "heart_warning_switch",
                    onCheckedChange = onHeartWarningChange,
                )
            }
            if (healthRemind.supported) {
                SettingToggleRow(
                    title = "Lembrete de saúde",
                    subtitle = healthRemind.summary.ifBlank { "Configuração ainda não informada." },
                    checked = healthRemind.enabled,
                    enabled = actionsEnabled,
                    testTag = "health_remind_switch",
                    onCheckedChange = onHealthRemindChange,
                )
            }
            if (longSeat.supported) {
                SettingToggleRow(
                    title = "Lembrete para se movimentar",
                    subtitle = longSeat.summary.ifBlank { "Horário ainda não informado." },
                    checked = longSeat.enabled,
                    enabled = actionsEnabled,
                    testTag = "long_seat_switch",
                    onCheckedChange = onLongSeatChange,
                )
            }
            if (nightTurn.supported) {
                SettingToggleRow(
                    title = "Virar pulso à noite",
                    subtitle = nightTurn.summary.ifBlank { "Configuração ainda não informada." },
                    checked = nightTurn.enabled,
                    enabled = actionsEnabled,
                    testTag = "night_turn_switch",
                    onCheckedChange = onNightTurnChange,
                )
            }
            if (findDevice.supported) {
                SettingToggleRow(
                    title = "Ajuda para encontrar o relógio",
                    subtitle = findDevice.summary.ifBlank { "Configuração ainda não informada." },
                    checked = findDevice.enabled,
                    enabled = actionsEnabled,
                    testTag = "find_device_switch",
                    onCheckedChange = onFindDeviceChange,
                )
            }
            if (findDevice.findByPhoneSupported) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onStartFindByPhone,
                        enabled = actionsEnabled && !findDevice.finding,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                        modifier = Modifier
                            .fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("find_by_phone_start")
                    ) {
                        Text("Localizar meu relógio", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onStopFindByPhone,
                        enabled = actionsEnabled && findDevice.finding,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth().heightIn(min = 56.dp)
                            .testTag("find_by_phone_stop")
                    ) {
                        Text("Parar busca", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF191C1E)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF44474E)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00639B)
            ),
            modifier = Modifier.testTag(testTag).semantics { contentDescription = title }
        )
    }
}

private fun capabilityLine(caps: DeviceCapabilities): String {
    if (!caps.probed) return "Nenhuma flag lida ainda."
    val flags = buildList {
        add("Origin v${caps.originProtocolVersion}")
        if (caps.isSupportAutoMeasure) add("auto-measure")
        if (caps.isSupportPreciseSleep) add("sono preciso")
        if (caps.isSupportWearDetect) add("wear")
        if (caps.isSupportSpo2) add("SpO2")
        if (caps.isSupportHrv) add("HRV")
        if (caps.isSupportBp) add("PA")
        if (caps.isSupportTemperature) add("temp")
        if (caps.isSupportEcg) add(if (caps.isSupportMultiLeadEcg) "ECG multi-lead" else "ECG")
        if (caps.isSupportBloodGlucose) add("glicose")
        if (caps.isSupportBloodComponent) add("sangue")
        if (caps.isSupportBodyComponent) add("corpo")
        if (caps.isSupportEmotion) add("emoção")
        if (caps.isSupportFatigue) add("fadiga")
        if (caps.isSupportBreath) add("respiração")
        if (caps.isSupportAlarm2) add("alarme")
        if (caps.isSupportHeartWarning) add("alerta FC")
        if (caps.isSupportLongSeat) add("sedentarismo")
        if (caps.isSupportFindDevice) add("encontrar")
    }
    return flags.joinToString(" • ")
}

private fun settingAvailability(probed: Boolean): String =
    if (probed) "Não disponível neste relógio." else "Ainda não foi verificado."
