package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.ui.theme.MyApplicationTheme
import com.example.data.local.HBandSensorMetricEntity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientHistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w720dp-h900dp-mdpi")
    fun wide_history_places_day_controls_side_by_side_and_can_return_directly_to_today() {
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { MyApplicationTheme { RechartsSensorDashboard(emptyList(), {}) } }
        val previous = compose.onNodeWithTag("history_previous_day").fetchSemanticsNode()
        val next = compose.onNodeWithTag("history_next_day").fetchSemanticsNode()
        assertEquals(previous.positionInRoot.y, next.positionInRoot.y, 1f)
        assertTrue(next.positionInRoot.x >= previous.positionInRoot.x + previous.size.width)
        val today = compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text]
        repeat(6) { compose.onNodeWithTag("history_previous_day").performClick() }
        compose.onNodeWithTag("history_previous_day").assertIsNotEnabled()
        compose.onNodeWithTag("history_today").performScrollTo().performClick()
        assertEquals(today, compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithTag("history_next_day").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w520dp-h1000dp-mdpi")
    fun enlarged_labels_stack_when_two_buttons_would_not_fit() {
        RuntimeEnvironment.setFontScale(2.5f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2.5f)) {
                MyApplicationTheme { RechartsSensorDashboard(emptyList(), {}) }
            }
        }
        val previous = compose.onNodeWithTag("history_previous_day").fetchSemanticsNode()
        val next = compose.onNodeWithTag("history_next_day").fetchSemanticsNode()
        assertEquals(previous.positionInRoot.x, next.positionInRoot.x, 1f)
        assertTrue(next.positionInRoot.y >= previous.positionInRoot.y + previous.size.height)
        compose.onNodeWithTag("history_previous_day").performScrollTo().performClick()
        compose.onNodeWithTag("history_today").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("history_next_day").performScrollTo().assertIsNotEnabled()
    }

    @Test fun empty_history_supports_large_text_and_day_navigation_without_generating_data() {
        RuntimeEnvironment.setFontScale(1.6f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                MyApplicationTheme {
                    Surface(Modifier.fillMaxSize()) {
                        RechartsSensorDashboard(emptyList(), onSimulateBatch = { error("Patient history must not generate data") })
                    }
                }
            }
        }
        val initialDate = compose.onNodeWithTag("history_selected_date")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("history_next_day").performScrollTo().assertIsNotEnabled()
        repeat(6) {
            compose.onNodeWithTag("history_previous_day").performScrollTo().assertIsEnabled().performClick()
        }
        assertNotEquals(initialDate, compose.onNodeWithTag("history_selected_date")
            .fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithTag("history_previous_day").assertIsNotEnabled()
        repeat(6) {
            compose.onNodeWithTag("history_next_day").performScrollTo().assertIsEnabled().performClick()
        }
        compose.onNodeWithTag("history_next_day").assertIsNotEnabled()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/history_day_controls_large.png")
        compose.onNodeWithText("Sem registros neste dia").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/history_empty_large_text.png")
    }

    @Test fun metric_selection_explains_unavailable_measurement() {
        compose.setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize()) {
                    RechartsSensorDashboard(emptyList(), onSimulateBatch = { error("Unexpected simulation") })
                }
            }
        }
        compose.onNodeWithTag("history_metric_menu").performClick()
        compose.onNodeWithText("Pressão arterial").performClick()
        compose.onNodeWithText("Sem medições disponíveis").assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/history_pressure_empty.png")
    }

    @Test fun stored_reading_exposes_its_date_and_value_together() {
        val timestamp = System.currentTimeMillis() - 60_000
        val record = HBandSensorMetricEntity(
            deviceId = "watch-fixture", timestamp = "fixture", timestampMillis = timestamp,
            heartRate = 72, systolicBp = 0, diastolicBp = 0, spO2 = 0,
            temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
            hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
        )
        compose.setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize()) { RechartsSensorDashboard(listOf(record), {}) }
            }
        }
        compose.onNodeWithTag("history_metric_menu").performClick()
        compose.onNodeWithText("Batimentos").performClick()
        val date = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
        compose.onNode(hasText(date) and hasText("72 bpm")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Histórico").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun daily_values_keep_their_labels_in_the_same_accessible_node() {
        compose.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    SavedDayValues(SavedDaySummary("Dia de teste", 2, 72, null, 25))
                }
            }
        }
        compose.onNode(hasText("Média dos batimentos por minuto") and hasText("72 bpm")).assertExists()
        compose.onNode(hasText("Passos — maior valor salvo") and hasText("Sem medição disponível")).assertExists()
        compose.onNode(hasText("Calorias — maior valor salvo") and hasText("25 kcal")).assertExists()
    }

    @Test fun history_refresh_uses_the_current_phone_time_zone_for_the_same_record() {
        val originalZone = TimeZone.getDefault()
        val timestamp = 1_600_000_000_000L
        val record = HBandSensorMetricEntity(
            deviceId = "watch-fixture", timestamp = "fixture", timestampMillis = timestamp,
            heartRate = 72, systolicBp = 0, diastolicBp = 0, spO2 = 0,
            temperatureCelsius = 0f, steps = 0, calories = 0f, distanceMeters = 0f,
            hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
        )
        fun dateIn(zone: String) = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
            .apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(timestamp))
        val refresh = mutableIntStateOf(0)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            compose.setContent {
                MyApplicationTheme {
                    Surface(Modifier.fillMaxSize()) {
                        RechartsSensorDashboard(listOf(record), {}, Modifier.testTag("refresh_${refresh.intValue}"))
                    }
                }
            }
            compose.onNodeWithTag("history_metric_menu").performClick()
            compose.onNodeWithText("Batimentos").performClick()
            compose.onNode(hasText(dateIn("UTC")) and hasText("72 bpm")).performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
                refresh.intValue++
            }
            compose.onNode(hasText(dateIn("America/Sao_Paulo")) and hasText("72 bpm"))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(dateIn("UTC")).assertDoesNotExist()
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
}
