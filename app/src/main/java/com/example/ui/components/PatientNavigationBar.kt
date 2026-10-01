package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class PatientDestination(val id: Int, val label: String, val tag: String, val icon: ImageVector)
private val destinations = listOf(
    PatientDestination(0, "Início", "tab_dashboard", Icons.Default.Favorite),
    PatientDestination(2, "Relógio", "tab_ble", Icons.Default.Watch),
    PatientDestination(4, "Ajustes", "tab_settings", Icons.Default.Settings),
)

@Composable
internal fun PatientNavigationRail(selectedTab: Int, onSelect: (Int) -> Unit) {
    val style = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = destinations.maxOf { measurer.measure(it.label, style).size.width }
    val railWidth = with(density) { labelWidth.toDp() }.plus(32.dp).coerceAtLeast(104.dp)
    Column(
        Modifier.width(railWidth).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 16.dp)
            .selectableGroup().testTag("patient_navigation_rail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        destinations.forEach { destination ->
            val selected = selectedTab == destination.id
            Column(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(20.dp))
                    .selectable(selected, role = Role.Tab, onClick = { onSelect(destination.id) })
                    .padding(horizontal = 4.dp, vertical = 12.dp)
                    .testTag(destination.tag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(destination.label, style = style, textAlign = TextAlign.Center,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun PatientNavigationBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    // Measure at the user's font scale. Never shrink type to squeeze destinations into one row.
    val labelWidth = destinations.maxOf { measurer.measure(it.label, labelStyle).size.width }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("main_tab_row")) {
        val labelWidthDp = with(density) { labelWidth.toDp() }
        val normalRowFits = maxWidth >= (labelWidthDp + 24.dp) * destinations.size
        val fittedColumns = ((maxWidth + 4.dp) / (labelWidthDp + 12.dp)).toInt().coerceIn(1, destinations.size)
        val columns = if (fittedColumns == 4) 3 else fittedColumns
        // Three or more icon rows crowd out the primary action on small screens.
        // Keep every named destination in shorter text rows.
        val compactRows = destinations.size > columns * 2
        if (normalRowFits) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedTab == destination.id,
                        onClick = { onSelect(destination.id) },
                        icon = { Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(destination.label, style = labelStyle) },
                        modifier = Modifier.testTag(destination.tag),
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .selectableGroup().testTag("adaptive_patient_navigation"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                destinations.indices.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { index ->
                            val destination = destinations[index]
                            val selected = selectedTab == destination.id
                            Column(
                                Modifier.weight(1f).heightIn(min = if (compactRows) 56.dp else 64.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .selectable(selected, role = Role.Tab, onClick = { onSelect(destination.id) })
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag(destination.tag),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = if (compactRows) Arrangement.Center else Arrangement.spacedBy(4.dp),
                            ) {
                                if (!compactRows) Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        destination.label, style = labelStyle, textAlign = TextAlign.Center,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Removed destinations saved by older versions return to Início. */
internal fun patientMainTab(savedTab: Int): Int = if (savedTab in listOf(0, 2, 4)) savedTab else 0
