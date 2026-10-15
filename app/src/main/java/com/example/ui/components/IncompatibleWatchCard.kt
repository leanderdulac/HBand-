package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.hband.VeepooDeviceSupport
import com.example.data.model.HBandDevice
import com.example.ui.theme.LocalStatusColors

@Composable
fun IncompatibleWatchCard(
    device: HBandDevice?,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = device ?: return
    val status = LocalStatusColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("incompatible_watch_card"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, status.attentionForeground),
        colors = CardDefaults.cardColors(containerColor = status.attentionContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                VeepooDeviceSupport.INCOMPATIBLE_CARD_TEXT,
                style = MaterialTheme.typography.titleLarge,
                color = status.attentionForeground,
                modifier = Modifier.testTag("incompatible_watch_message"),
            )
            if (current.name.isNotBlank()) {
                Text(current.name, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = onForget,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("btn_forget_watch"),
            ) {
                Text(VeepooDeviceSupport.FORGET_WATCH_LABEL)
            }
        }
    }
}
