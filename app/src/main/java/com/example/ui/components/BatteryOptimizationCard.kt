package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.power.BatteryGuidance
import com.example.data.power.BatteryOptimization
import com.example.data.power.BatteryOptimizationPolicy
import com.example.ui.theme.LocalStatusColors

/** Re-reads the exemption state every time the screen resumes (e.g. returning from system settings). */
@Composable
fun rememberIgnoringBatteryOptimizations(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var ignoring by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) ignoring = BatteryOptimization.isIgnoring(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return ignoring
}

/** One-time prompt shown after a watch is paired, while the app is still battery-optimized. */
@Composable
fun BatteryOptimizationPrompt(hasPairedWatch: Boolean) {
    val context = LocalContext.current
    val ignoring = rememberIgnoringBatteryOptimizations()
    var prompted by rememberSaveable { mutableStateOf(BatteryOptimization.wasPrompted(context)) }
    if (!BatteryOptimizationPolicy.shouldPromptOnce(ignoring, prompted, hasPairedWatch)) return
    val dismiss = { BatteryOptimization.markPrompted(context); prompted = true }
    AlertDialog(
        modifier = Modifier.testTag("battery_optimization_prompt"),
        onDismissRequest = dismiss,
        title = { Text("Manter o relógio enviando dados", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "Para suas medições chegarem à equipe de saúde a tempo, o celular não pode " +
                    "pausar este app. Na próxima tela, toque em \"Permitir\".",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(
                onClick = { dismiss(); BatteryOptimization.openExemptionRequest(context) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("btn_battery_prompt_allow"),
            ) { Text("Continuar") }
        },
        dismissButton = {
            TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Agora não") }
        },
    )
}

/** Settings status item plus manufacturer help. */
@Composable
fun BatteryOptimizationSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ignoring = rememberIgnoringBatteryOptimizations()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val status = LocalStatusColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("battery_optimization_card"),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (ignoring) status.normalContainer else status.attentionContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Envio contínuo das leituras", style = MaterialTheme.typography.titleLarge)
            Text(
                BatteryOptimizationPolicy.statusText(ignoring),
                style = MaterialTheme.typography.bodyLarge,
                color = if (ignoring) status.normalForeground else status.attentionForeground,
                modifier = Modifier.testTag("battery_optimization_status"),
            )
            if (!ignoring) {
                Button(
                    onClick = { BatteryOptimization.openExemptionRequest(context) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_battery_exemption"),
                ) { Text("Liberar o app") }
            }
            OutlinedButton(
                onClick = { showHelp = !showHelp },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_battery_help"),
            ) { Text(if (showHelp) "Fechar ajuda" else "Como deixar o app sempre ativo") }
            if (showHelp) BatteryGuidanceSteps(BatteryOptimization.guidance())
        }
    }
}

@Composable
fun BatteryGuidanceSteps(guidance: BatteryGuidance) {
    val steps = batteryGuidanceSteps(guidance)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("battery_help_steps")) {
        Text(
            if (guidance == BatteryGuidance.SAMSUNG) "Celulares Samsung" else "Outros celulares (Multilaser e demais)",
            style = MaterialTheme.typography.titleMedium,
        )
        steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
    }
}

fun batteryGuidanceSteps(guidance: BatteryGuidance): List<String> = when (guidance) {
    BatteryGuidance.SAMSUNG -> listOf(
        "Abra Configurações > Aplicativos > App Paciente > Bateria e escolha \"Sem restrições\".",
        "Abra Configurações > Bateria > Limites de uso em segundo plano > \"Apps nunca suspensos\" e adicione o App Paciente.",
        "Deixe o Bluetooth ligado e não feche o app pela tela de apps recentes.",
    )
    BatteryGuidance.GENERIC -> listOf(
        "Abra Configurações > Aplicativos > App Paciente > Bateria e escolha \"Sem restrições\" ou \"Não otimizar\".",
        "Se houver \"Iniciar automaticamente\" ou \"Execução em segundo plano\", deixe ativado para o App Paciente.",
        "Desligue o modo de economia de bateria quando estiver usando o relógio.",
        "Deixe o Bluetooth ligado e não feche o app pela tela de apps recentes.",
    )
}
