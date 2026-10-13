package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpectedRangePlaceholder
import com.example.ui.theme.LocalStatusColors
import com.example.ui.theme.PillShape

enum class VitalStatusKind {
    NORMAL,
    ATTENTION,
    ALERT,
    NO_DATA,
    NO_RANGE,
}

@Composable
fun StatusChip(
    status: VitalStatusKind,
    modifier: Modifier = Modifier,
) {
    val colors = LocalStatusColors.current
    val (label, icon, container, foreground) = when (status) {
        VitalStatusKind.NORMAL -> Quad("Normal", Icons.Default.Check, colors.normalContainer, colors.normalForeground)
        VitalStatusKind.ATTENTION -> Quad("Atenção", Icons.Default.WarningAmber, colors.attentionContainer, colors.attentionForeground)
        VitalStatusKind.ALERT -> Quad("Alerta", Icons.Default.PriorityHigh, colors.alertContainer, colors.alertForeground)
        VitalStatusKind.NO_DATA -> Quad("Sem dado", Icons.Default.Remove, colors.noDataContainer, colors.noDataForeground)
        VitalStatusKind.NO_RANGE -> Quad("Sem faixa", Icons.Default.Remove, colors.noDataContainer, colors.noDataForeground)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 32.dp)
            .testTag("status_chip")
            .semantics { contentDescription = "Situação: $label" },
        shape = PillShape,
        color = container,
        contentColor = foreground,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = foreground)
            Text(label, style = MaterialTheme.typography.labelMedium, color = foreground)
        }
    }
}

internal fun classifyVitalStatus(value: Float?, range: ExpectedRangePlaceholder): VitalStatusKind {
    if (value == null) return VitalStatusKind.NO_DATA
    if (!range.isConfigured) return VitalStatusKind.NO_RANGE
    val below = range.min != null && value < range.min
    val above = range.max != null && value > range.max
    return if (below || above) VitalStatusKind.ATTENTION else VitalStatusKind.NORMAL
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
