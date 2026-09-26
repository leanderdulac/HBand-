package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.HydrationLogEntity

@Composable
fun HydrationCard(
    currentMl: Int?,
    targetGoalMl: Int,
    logs: List<HydrationLogEntity>,
    onAddWater: (Int) -> Unit,
    onResetToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by remember { mutableStateOf(false) }
    val progress = if (currentMl != null && targetGoalMl > 0) (currentMl.toFloat() / targetGoalMl).coerceIn(0f, 1f) else null

    Card(
        modifier.fillMaxWidth().testTag("hydration_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Água que registrei hoje", style = MaterialTheme.typography.titleLarge)
            Text(currentMl?.let { "$it mL" } ?: "Carregando registros de água…", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("hydration_current_ml_text"))
            Text("Este total vem dos registros que você adiciona no aplicativo.", style = MaterialTheme.typography.bodyLarge)
            if (progress != null) {
                Text("Meta cadastrada: $targetGoalMl mL por dia", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(12.dp).testTag("hydration_progress_bar"),
                )
                Text("${(progress * 100).toInt()}% da meta cadastrada", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("hydration_percentage_badge"))
            } else if (targetGoalMl <= 0) {
                Text("Nenhuma meta de água cadastrada.", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Adicionar a quantidade que bebi", style = MaterialTheme.typography.titleMedium)
            for (amount in listOf(250, 500, 750)) {
                Button(
                    onClick = { onAddWater(amount) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("add_${amount}ml_water_button"),
                ) { Text("Adicionar $amount mL") }
            }
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = currentMl != null && (currentMl > 0 || logs.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("reset_hydration_button"),
            ) { Text("Apagar registros de água de hoje") }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Apagar os registros de hoje?") },
        text = { Text("O total de água de hoje será zerado. Os registros de outros dias serão mantidos.") },
        confirmButton = {
            TextButton(
                onClick = { if (currentMl != null) { confirmReset = false; onResetToday() } },
                enabled = currentMl != null,
                modifier = Modifier.heightIn(min = 56.dp).testTag("confirm_hydration_reset"),
            ) { Text("Apagar os registros") }
        },
        dismissButton = {
            TextButton(onClick = { confirmReset = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") }
        },
    )
}
