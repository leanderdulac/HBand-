package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HBandDevice
import com.example.ui.theme.MinimalBorder

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
    val batteryLevel = device?.batteryLevel ?: 88
    val (batteryColor, batteryBg) = when {
        batteryLevel <= 20 -> Pair(Color(0xFFD32F2F), Color(0xFFFFEBEE))
        batteryLevel <= 50 -> Pair(Color(0xFFE65100), Color(0xFFFFF3E0))
        else -> Pair(Color(0xFF2E7D32), Color(0xFFE8F5E9))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_control_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MinimalBorder),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
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
                            .background(
                                if (device?.isConnected == true) Color(0xFFC2E7FF)
                                else Color(0xFFF1F4F9)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Watch,
                            contentDescription = null,
                            tint = if (device?.isConnected == true) Color(0xFF004A77) else Color(0xFF44474E),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = device?.name ?: "VE30 Smart Band",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C1E)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MAC: ${device?.macAddress ?: device?.deviceId ?: "Desconectado"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (device?.isConnected == true) Color(0xFF00639B) else Color(0xFF74777F)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Battery Pill Indicator
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(batteryBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = batteryColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$batteryLevel%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = batteryColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Vital Data / Measure Button in Clean Minimalism style
            Button(
                onClick = onSpotCheck,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("spot_check_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B))
            ) {
                Icon(
                    imageVector = Icons.Default.AddAlert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sincronizar Dados Vitais", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSimulateBatch(5) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate_batch_button"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MinimalBorder)
                ) {
                    Text("Teste em Lote (+5)", style = MaterialTheme.typography.labelMedium, color = Color(0xFF00639B))
                }

                if (onSimulateLowBattery != null && onRechargeBattery != null) {
                    if (batteryLevel > 20) {
                        OutlinedButton(
                            onClick = onSimulateLowBattery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_low_battery_button"),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFCC80)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100))
                        ) {
                            Text("Simular Bat. Fraca", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRechargeBattery,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("recharge_battery_button"),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFF81C784)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                        ) {
                            Text("Recarregar (98%)", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-Ingest Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Enfileiramento Automático de Sensores",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = "Salvar automaticamente dados BLE ao vivo no Room",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF44474E)
                    )
                }

                Switch(
                    checked = autoIngestLive,
                    onCheckedChange = onToggleAutoIngest,
                    modifier = Modifier.testTag("auto_ingest_switch")
                )
            }
        }
    }
}
