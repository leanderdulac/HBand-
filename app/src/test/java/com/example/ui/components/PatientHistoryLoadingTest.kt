package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.valueOrNull
import com.example.ui.shareMetricsState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.semantics.SemanticsProperties
import java.util.Calendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientHistoryLoadingTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    // Production nullable sharing policy, not operational MainViewModel.
    private fun pending(content: @Composable (List<HBandSensorMetricEntity>?) -> Unit, verify: () -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = flow<List<HBandSensorMetricEntity>> { awaitCancellation() }
            .shareMetricsState(scope)
        try {
            compose.setContent { MyApplicationTheme {
                val metrics = state.collectAsState().value.valueOrNull()
                Column(Modifier.verticalScroll(rememberScrollState())) { content(metrics) }
            } }
            verify()
        } finally { scope.cancel() }
    }

    @Test fun pending_daily_query_does_not_assert_empty_day() = pending({ DailyHealthSummaryCard(it) }) {
        compose.onNodeWithText("Sem registros neste dia").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros…").assertExists()
    }
    @Test fun pending_week_query_does_not_assert_empty_day() = pending({ RechartsSevenDaySummaryCard(it) }) {
        compose.onNodeWithText("Sem registros neste dia").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros…").assertExists()
    }
    @Test fun pending_sleep_query_does_not_assert_missing_sleep() = pending({ SleepAnalysisCard(it) }) {
        compose.onNodeWithText("Sem registro de sono disponível nos últimos 7 dias.").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros de sono…").assertExists()
    }
    @Test fun pending_metric_query_does_not_assert_missing_readings() = pending({ RechartsSensorDashboard(it, {}, isScrollable = false) }) {
        compose.onNodeWithTag("history_metric_menu").performScrollTo().performClick()
        compose.onNodeWithText("Batimentos").performClick()
        compose.onNodeWithText("Sem medições disponíveis").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros…").assertExists()
    }
    private fun row(at: Long = System.currentTimeMillis() - 1000) = HBandSensorMetricEntity(
        deviceId = "history-fixture", timestamp = "synthetic", timestampMillis = at,
        heartRate = 72, systolicBp = 0, diastolicBp = 0, spO2 = 97,
        temperatureCelsius = 0f, steps = 120, calories = 5f, distanceMeters = 0f,
        hrvScore = 0, deepSleepMinutes = 80, lightSleepMinutes = 200, awakeMinutes = 15,
    )

    @Test fun daily_and_sleep_distinguish_empty_rows_and_new_loading() {
        val metrics = mutableStateOf<List<HBandSensorMetricEntity>?>(null)
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DailyHealthSummaryCard(metrics.value)
                SleepAnalysisCard(metrics.value)
            }
        } }
        compose.runOnIdle { metrics.value = emptyList() }
        compose.onNodeWithText("Sem registros neste dia").assertExists()
        compose.onNodeWithTag("sleep_waiting_empty_state").assertExists()
        compose.runOnIdle { metrics.value = listOf(row()) }
        compose.onNodeWithText("1 registro salvo").assertExists()
        compose.onNodeWithTag("saved_sleep_duration").assertTextEquals("4 h 40 min")
        compose.runOnIdle { metrics.value = null }
        compose.onNodeWithText("1 registro salvo").assertDoesNotExist()
        compose.onNodeWithTag("saved_sleep_duration").assertDoesNotExist()
        compose.onNodeWithTag("sleep_waiting_empty_state").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros…").assertExists()
        compose.onNodeWithText("Carregando registros de sono…").assertExists()
    }

    @Test fun week_preserves_selected_day_without_stale_values_during_loading() {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val rows = listOf(row(yesterday))
        val metrics = mutableStateOf<List<HBandSensorMetricEntity>?>(rows)
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { RechartsSevenDaySummaryCard(metrics.value) }
        } }
        compose.onNodeWithTag("history_previous_day").performScrollTo().performClick()
        val date = compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithText("1 registro salvo").assertExists()
        compose.runOnIdle { metrics.value = null }
        compose.onNodeWithText("1 registro salvo").assertDoesNotExist()
        compose.onNodeWithText("Sem registros neste dia").assertDoesNotExist()
        compose.onNodeWithTag("history_previous_day").assertDoesNotExist()
        compose.runOnIdle { metrics.value = rows }
        assertEquals(date, compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithText("1 registro salvo").assertExists()
        compose.runOnIdle { metrics.value = emptyList() }
        compose.onNodeWithText("Sem registros neste dia").assertExists()
    }

    @Test fun metric_and_graph_choice_survive_loading_without_stale_readings() {
        val rows = listOf(row())
        val metrics = mutableStateOf<List<HBandSensorMetricEntity>?>(rows)
        compose.setContent { MyApplicationTheme { RechartsSensorDashboard(metrics.value, {}) } }
        compose.onNodeWithTag("history_metric_menu").performClick()
        compose.onNodeWithText("Batimentos").performClick()
        compose.onNodeWithText("Ver gráfico").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gráfico de Batimentos. Valores e horários disponíveis na lista abaixo.").assertExists()
        compose.runOnIdle { metrics.value = null }
        compose.onNodeWithText("72 bpm").assertDoesNotExist()
        compose.onNodeWithText("Fechar gráfico").assertDoesNotExist()
        compose.onNodeWithContentDescription("Gráfico de Batimentos. Valores e horários disponíveis na lista abaixo.").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros…").assertExists()
        compose.runOnIdle { metrics.value = rows }
        compose.onNodeWithTag("history_metric_menu").assertTextEquals("Ver: Batimentos")
        compose.onNodeWithText("Fechar gráfico").assertExists()
        compose.onNodeWithText("72 bpm").assertExists()
        compose.runOnIdle { metrics.value = emptyList() }
        compose.onNodeWithText("Sem medições disponíveis").assertExists()
        compose.onNodeWithText("Fechar gráfico").assertDoesNotExist()
    }

    @Test fun loading_states_are_readable_with_large_text() {
        RuntimeEnvironment.setFontScale(2f)
        val screen = mutableIntStateOf(0)
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (screen.intValue) {
                    0 -> DailyHealthSummaryCard(null)
                    1 -> SleepAnalysisCard(null)
                    else -> RechartsSensorDashboard(null, {}, isScrollable = false)
                }
            }
        } }
        compose.onNodeWithText("Carregando registros…").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/history-loading-daily.png")
        compose.runOnIdle { screen.intValue = 1 }
        compose.onNodeWithText("Carregando registros de sono…").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/history-loading-sleep.png")
        compose.runOnIdle { screen.intValue = 2 }
        compose.onNodeWithText("Carregando registros…").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/history-loading-week.png")
        compose.onNodeWithTag("history_metric_menu").performScrollTo().performClick()
        compose.onNodeWithText("Batimentos").performClick()
        compose.onNodeWithText("Carregando registros…").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/history-loading-metric.png")
    }

}
