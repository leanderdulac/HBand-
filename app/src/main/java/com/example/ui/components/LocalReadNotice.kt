package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** No destructive action, raw exception or inferred empty/zero state. */
@Composable
fun LocalReadNotice(subject: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("local_read_error").semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Não foi possível ler $subject.", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("retry_local_read")) {
            Text("Tentar a leitura novamente")
        }
    }
}
