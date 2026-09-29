package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.HBandDevice

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
        border = BorderStroke(1.dp, Color(0xFF8C3D00)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F0)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Bateria baixa na última leitura",
                style = MaterialTheme.typography.titleLarge, color = Color(0xFF5D2800),
            )
            Text("$batteryLevel%", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF5D2800))
            if (current.name.isNotBlank()) Text(current.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (!current.isConnected) "O relógio está desconectado. Esta carga pode estar desatualizada."
                else "Esta é a última carga informada pelo relógio.",
                style = MaterialTheme.typography.bodyLarge, color = Color(0xFF5D2800),
            )
            Text(
                "Se a carga ainda estiver baixa, coloque o relógio no carregador.",
                style = MaterialTheme.typography.bodyLarge, color = Color(0xFF5D2800),
            )

        }
    }
}
