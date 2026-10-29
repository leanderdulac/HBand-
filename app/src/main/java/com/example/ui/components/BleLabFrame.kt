package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Identification only. The compiled Android manifest enforces network isolation. */
@Composable
fun BleLabFrame(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                "VE30 ENSAIO • SEM ENVIO\nDados deste aplicativo são de teste. Não usar para decisões clínicas.",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        content()
    }
}
