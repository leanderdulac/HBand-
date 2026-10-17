package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.power.BatteryGuidance
import com.example.data.power.BatteryOptimization
import com.example.ui.theme.LocalStatusColors

/** Pure visibility rules for the Home telemetry banners. */
object TelemetryBannerPolicy {
    fun showReconnecting(pairedMac: String?, hardwareConnected: Boolean, autoReconnect: Boolean): Boolean =
        !pairedMac.isNullOrBlank() && !hardwareConnected && autoReconnect

    fun showBatteryWarning(hasPairedWatch: Boolean, ignoringOptimizations: Boolean): Boolean =
        hasPairedWatch && !ignoringOptimizations

    fun batteryWarningText(guidance: BatteryGuidance): String = when (guidance) {
        BatteryGuidance.SAMSUNG ->
            "O celular pode pausar o app e atrasar o envio. Em Bateria, escolha \"Sem restrições\" e tire o App Paciente dos apps em suspensão."
        BatteryGuidance.GENERIC ->
            "O celular pode pausar o app e atrasar o envio. Em Bateria, escolha \"Sem restrições\" ou \"Não otimizar\"."
    }
}

@Composable
fun TelemetryStatusBanners(
    pairedMac: String?,
    hardwareConnected: Boolean,
    autoReconnect: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val ignoring = rememberIgnoringBatteryOptimizations()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (TelemetryBannerPolicy.showReconnecting(pairedMac, hardwareConnected, autoReconnect)) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("banner_reconnecting")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = status.attentionContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = status.attentionForeground)
                    Text("Reconectando…  As leituras ficam guardadas e seguem quando o relógio voltar.",
                        style = MaterialTheme.typography.bodyLarge, color = status.attentionForeground)
                }
            }
        }
        if (TelemetryBannerPolicy.showBatteryWarning(!pairedMac.isNullOrBlank(), ignoring)) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("banner_battery_warning"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = status.attentionContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(TelemetryBannerPolicy.batteryWarningText(BatteryOptimization.guidance()),
                        style = MaterialTheme.typography.bodyLarge, color = status.attentionForeground)
                    Button(
                        onClick = { BatteryOptimization.openExemptionRequest(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_banner_battery"),
                    ) { Text("Liberar o app") }
                }
            }
        }
    }
}


/** "Versão 1.7.4 (12)" from BuildConfig, shown in Ajustes and as a Home footer. */
fun appVersionLabel(name: String = com.example.BuildConfig.VERSION_NAME, code: Int = com.example.BuildConfig.VERSION_CODE): String =
    "Versão $name ($code)"

@Composable
fun AppVersionFooter(modifier: Modifier = Modifier) {
    Text(
        appVersionLabel(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.fillMaxWidth().testTag("app_version_label"),
    )
}
