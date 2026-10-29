package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Keeps active work expanded so its controls remain reachable. */
@Composable
internal fun PatientSection(title: String, forceExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(forceExpanded) }
    PatientSection(title, expanded, { expanded = it }, forceExpanded, content)
}

/** Allows an editor to own section state outside a recreated dialog window. */
@Composable
internal fun PatientSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    forceExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(forceExpanded) { if (forceExpanded) onExpandedChange(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onExpandedChange(!expanded) },
            enabled = !forceExpanded,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                heading()
                stateDescription = when {
                    forceExpanded -> "Aberta durante a atividade em andamento"
                    expanded -> "Aberta"
                    else -> "Fechada"
                }
            },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (forceExpanded) "$title — em andamento" else if (expanded) "Fechar: $title" else title,
                    modifier = Modifier.weight(1f),
                )
                // The button already announces its state; this is only a visual cue.
                // Active work cannot be collapsed, so do not suggest a collapse action.
                if (!forceExpanded) Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (expanded || forceExpanded) content()
    }
}
