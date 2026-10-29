package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier

/** Keep only each destination's saveable UI state while its content leaves composition. */
@Composable
internal fun PatientTabContent(selectedTab: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    Box(modifier) {
        stateHolder.SaveableStateProvider(selectedTab) { content() }
    }
}
