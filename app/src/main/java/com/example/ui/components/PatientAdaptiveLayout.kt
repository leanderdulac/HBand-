package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Respond to the available window, including split screen, rather than the device model. */
@Composable
internal fun PatientAdaptiveScaffold(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        // Large type needs more width; short phone windows keep the compact bottom bar.
        val rail = maxWidth >= (600 * fontScale.coerceAtLeast(1f)).dp && maxHeight >= 480.dp
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFFF8F9FF),
            snackbarHost = snackbarHost,
            bottomBar = { if (!rail) PatientNavigationBar(selectedTab, onSelect) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (rail) PatientNavigationRail(selectedTab, onSelect)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = if (selectedTab == 0) 1120.dp else 840.dp)
                            .fillMaxSize().testTag("patient_content"),
                    ) {
                        header()
                        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    }
                }
            }
        }
    }
}

/** Stable composition slots preserve local UI state when the two groups reflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatientSummaryLayout(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= (760 * fontScale).dp
        val columnWidth = if (twoColumns) (maxWidth - 24.dp) / 2 else maxWidth
        FlowRow(
            Modifier.fillMaxWidth().testTag("patient_summary"),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = if (twoColumns) 2 else 1,
        ) {
            Column(Modifier.width(columnWidth).testTag("patient_summary_first"), verticalArrangement = Arrangement.spacedBy(16.dp)) { first() }
            Column(Modifier.width(columnWidth).testTag("patient_summary_second"), verticalArrangement = Arrangement.spacedBy(16.dp)) { second() }
        }
    }
}
