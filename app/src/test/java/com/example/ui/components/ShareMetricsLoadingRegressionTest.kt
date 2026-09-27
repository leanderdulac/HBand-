package com.example.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.shareMetricsState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = Application::class)
class ShareMetricsLoadingRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pending_metrics_cannot_enable_card_even_when_both_totals_loaded() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val source = flow<List<HBandSensorMetricEntity>> { awaitCancellation() }
        // Production sharing policy; no operational MainViewModel startup.
        val state = source.shareMetricsState(scope)
        var prepared = 0
        try {
            compose.setContent { MyApplicationTheme {
                val metrics = state.collectAsState().value
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ShareProgressCard(metrics, 0, 0, { prepared++ })
                }
            } }
            compose.onNodeWithTag("share_progress_button").assertIsNotEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText("Carregando registros…").assertExists()
            compose.onNodeWithTag("share_preparation_progress").assertDoesNotExist()
            compose.onNodeWithTag("share_preparation_error").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, prepared) }
        } finally { scope.cancel() }
    }

    @Test fun confirmed_empty_metrics_enable_card_and_new_loading_disables_it_again() {
        val metrics = mutableStateOf<List<HBandSensorMetricEntity>?>(null)
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ShareProgressCard(metrics.value, 0, 0, {})
            }
        } }
        compose.onNodeWithTag("share_progress_button").assertIsNotEnabled()
        compose.runOnIdle { metrics.value = emptyList() }
        compose.onNodeWithTag("share_progress_button").assertIsEnabled()
        compose.runOnIdle { metrics.value = null }
        compose.onNodeWithTag("share_progress_button").assertIsNotEnabled()
    }
}
