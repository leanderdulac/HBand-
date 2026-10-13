package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.HBandDevice
import com.example.ui.theme.LocalStatusColors

@Composable
fun LowBatteryWarningCard(
    device: HBandDevice?,
    modifier: Modifier = Modifier,
) {
    val current = device ?: return
    val batteryLevel = visibleWatchBattery(current) ?: return
    if (batteryLevel > 20) return

    Card(
        modifier.fillMaxWidth().testTag("low_battery_warning_card"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, LocalStatusColors.current.attentionForeground),
        colors = CardDefaults.cardColors(containerColor = LocalStatusColors.current.attentionContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Bateria baixa na última leitura",
                style = MaterialTheme.typography.titleLarge, color = LocalStatusColors.current.attentionForeground,
            )
            Text("$batteryLevel%", style = MaterialTheme.typography.headlineLarge, color = LocalStatusColors.current.attentionForeground)
            if (current.name.isNotBlank()) Text(current.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (!current.isConnected) "O relógio está desconectado. Esta carga pode estar desatualizada."
                else "Esta é a última carga informada pelo relógio.",
                style = MaterialTheme.typography.bodyLarge, color = LocalStatusColors.current.attentionForeground,
            )
            Text(
                "Se a carga ainda estiver baixa, coloque o relógio no carregador.",
                style = MaterialTheme.typography.bodyLarge, color = LocalStatusColors.current.attentionForeground,
            )

        }
    }
}
