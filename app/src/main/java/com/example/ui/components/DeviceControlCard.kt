package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.hband.VeepooBatteryMapper
import com.example.data.model.HBandDevice

@Composable
fun DeviceControlCard(
    device: HBandDevice?,
    autoIngestLive: Boolean,
    onToggleAutoIngest: (Boolean) -> Unit,
    onSpotCheck: () -> Unit,
    onSimulateBatch: (Int) -> Unit,
    onScanClick: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onSimulateLowBattery: (() -> Unit)? = null,
    onRechargeBattery: (() -> Unit)? = null
) {
    val connected = device?.isConnected == true
    val batteryLevel = visibleWatchBattery(device)
    var showDetails by remember { mutableStateOf(false) }
    var showTests by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("device_control_card"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Watch, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Meu relógio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                if (connected) "Relógio conectado" else "Relógio desconectado",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("device_connection_status")
            )
            if (!device?.name.isNullOrBlank()) {
                Text(device!!.name, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = if (connected) onSpotCheck else onScanClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .testTag(if (connected) "spot_check_button" else "connect_watch_button")
            ) {
                Text(if (connected) "Salvar e enviar leitura" else "Conectar meu relógio")
            }
            Text(
                if (connected) "Salva a leitura disponível e tenta enviar os registros pendentes. Se o acesso estiver pausado, os dados ficam salvos."
                else "Deixe seu relógio perto deste aparelho. Toque em Conectar meu relógio para procurar e conectar.",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (connected) "Bateria: " else "Última carga: ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    VeepooBatteryMapper.displayLabel(batteryLevel, device?.batteryIsSimulated == true),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("device_battery_level")
                )
            }
            if (batteryLevel == null) {
                Text("A carga da bateria ainda não foi informada.", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(
                onClick = { showDetails = !showDetails },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("watch_details_button")
            ) {
                Text(if (showDetails) "Fechar opções do relógio" else "Opções do relógio")
            }
            if (showDetails) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Guardar novas leituras", style = MaterialTheme.typography.titleSmall)
                        Text("Salvar neste aparelho as leituras recebidas do relógio.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = autoIngestLive,
                        onCheckedChange = onToggleAutoIngest,
                        modifier = Modifier.testTag("auto_ingest_switch").semantics { contentDescription = "Guardar novas leituras" }
                    )
                }
                if (connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("disconnect_watch_button")
                    ) { Text("Desconectar relógio") }
                }
                if (BuildConfig.DEBUG) {
                    TextButton(
                        onClick = { showTests = !showTests },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("watch_test_tools_button")
                    ) { Text(if (showTests) "Fechar ferramentas de teste" else "Ferramentas de teste") }
                    if (showTests) {
                        Text("Somente para desenvolvimento. Os valores gerados são simulações.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { onSimulateBatch(5) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("simulate_batch_button")
                        ) { Text("Gerar 5 registros de teste") }
                        onSimulateLowBattery?.let { action ->
                            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text("Simular bateria fraca")
                            }
                        }
                        onRechargeBattery?.let { action ->
                            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text("Simular bateria em 98%")
                            }
                        }
                    }
                }
            }
        }
    }
}
