package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.local.shouldPromptAiInsightConsent
import com.example.data.power.BatteryOptimization
import com.example.data.power.BatteryOptimizationPolicy
import com.example.ui.theme.MyApplicationTheme

@Composable
internal fun AiInsightConsentDialog(
    onAuthorize: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        modifier = modifier.testTag("ai_insight_consent_dialog"),
        onDismissRequest = onNotNow,
        title = {
            Text(
                AiInsightConsentCopy.TITLE,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(AiInsightConsentCopy.DIALOG_INTRO, style = MaterialTheme.typography.bodyLarge)
                Text(AiInsightConsentCopy.DATA_SENT, style = MaterialTheme.typography.bodyLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiInsightConsentCopy.DATA_ITEMS.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(AiInsightConsentCopy.PURPOSE, style = MaterialTheme.typography.bodyLarge)
                Text(AiInsightConsentCopy.OPTIONAL, style = MaterialTheme.typography.bodyLarge)
                Text(AiInsightConsentCopy.NOT_DIAGNOSIS, style = MaterialTheme.typography.bodyLarge)
                Text(AiInsightConsentCopy.WITHDRAW, style = MaterialTheme.typography.bodyLarge)
                TextButton(
                    onClick = { uriHandler.openUri(AiInsightConsentCopy.PRIVACY_URL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("ai_insight_privacy_link"),
                ) { Text(AiInsightConsentCopy.PRIVACY_LABEL) }
                TextButton(
                    onClick = { uriHandler.openUri(AiInsightConsentCopy.DELETION_URL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("ai_insight_deletion_link"),
                ) { Text(AiInsightConsentCopy.DELETION_LABEL) }
            }
        },
        confirmButton = {
            Button(
                onClick = onAuthorize,
                modifier = Modifier.heightIn(min = 56.dp).testTag("btn_ai_insight_authorize"),
            ) { Text(AiInsightConsentCopy.AUTHORIZE) }
        },
        dismissButton = {
            TextButton(
                onClick = onNotNow,
                modifier = Modifier.heightIn(min = 56.dp).testTag("btn_ai_insight_not_now"),
            ) { Text(AiInsightConsentCopy.NOT_NOW) }
        },
    )
}

/**
 * Optional one-time prompt after the watch is paired. Waits if the battery-optimization
 * dialog is already on screen so the patient is not asked two things at once.
 */
@Composable
internal fun AiInsightFirstConnectionPrompt(
    featureEnabled: Boolean,
    consented: Boolean,
    promptShown: Boolean,
    hasPairedWatch: Boolean,
    onAuthorize: () -> Unit,
    onNotNow: () -> Unit,
) {
    val ignoring = rememberIgnoringBatteryOptimizations()
    val context = LocalContext.current
    val batteryShowing = BatteryOptimizationPolicy.shouldPromptOnce(
        ignoring, BatteryOptimization.wasPrompted(context), hasPairedWatch,
    )
    if (!shouldPromptAiInsightConsent(
            featureEnabled = featureEnabled,
            consented = consented,
            promptShown = promptShown,
            hasPairedWatch = hasPairedWatch,
            anotherPromptVisible = batteryShowing,
        )
    ) return
    AiInsightConsentDialog(onAuthorize = onAuthorize, onNotNow = onNotNow)
}

@Preview(showBackground = true, name = "Consent dialog")
@Composable
private fun AiInsightConsentDialogPreview() {
    MyApplicationTheme {
        AiInsightConsentDialog(onAuthorize = {}, onNotNow = {})
    }
}
