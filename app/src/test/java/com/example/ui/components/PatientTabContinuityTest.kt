package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientTabContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returning_home_keeps_counted_breathing_time_paused_without_saving() {
        val tab = mutableIntStateOf(0)
        var saves = 0
        compose.setContent {
            MyApplicationTheme {
                PatientTabContent(tab.intValue) {
                    if (tab.intValue == 0) Column(Modifier.verticalScroll(rememberScrollState())) {
                        BreathingExerciseCard(0, { _, _ -> saves++ })
                    } else Text("Outra aba")
                }
            }
        }
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(6100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val counted = compose.onNodeWithTag("session_timer_text").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue(counted != "Tempo neste exercício: 0\u00A0min 0\u00A0s")
        compose.runOnIdle { tab.intValue = 1 }
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle { tab.intValue = 0 }
        compose.onNodeWithText("Exercício pausado").assertExists()
        val restored = compose.onNodeWithTag("session_timer_text").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        fun seconds(text: String): Int = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList().let { it[0] * 60 + it[1] }
        // Automatic animation frames can count a final tick before departure settles.
        // They must not add the six seconds spent on the other destination.
        assertTrue(seconds(restored) in seconds(counted)..seconds(counted) + 1)
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("session_timer_text").assertTextEquals(restored)
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun returning_to_history_preserves_the_chosen_day() {
        val tab = mutableIntStateOf(1)
        compose.setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize()) {
                    PatientTabContent(tab.intValue) {
                        if (tab.intValue == 1) RechartsSensorDashboard(emptyList()) else Text("Outra aba")
                    }
                }
            }
        }
        val today = compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("history_previous_day").performScrollTo().performClick()
        val yesterday = compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text]
        assertNotEquals(today, yesterday)
        compose.runOnIdle { tab.intValue = 0 }
        compose.onNodeWithTag("history_selected_date").assertDoesNotExist()
        compose.runOnIdle { tab.intValue = 1 }
        assertEquals(yesterday, compose.onNodeWithTag("history_selected_date").fetchSemanticsNode().config[SemanticsProperties.Text])
    }

    @Test fun returning_to_history_preserves_the_measurement_filter() {
        val tab = mutableIntStateOf(1)
        compose.setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize()) {
                    PatientTabContent(tab.intValue) {
                        if (tab.intValue == 1) RechartsSensorDashboard(emptyList()) else Text("Outra aba")
                    }
                }
            }
        }
        compose.onNodeWithTag("history_metric_menu").performClick()
        compose.onNodeWithText("Pressão arterial").performClick()
        compose.runOnIdle { tab.intValue = 0 }
        compose.runOnIdle { tab.intValue = 1 }
        compose.onNodeWithTag("history_metric_menu").assertTextContains("Ver: Pressão arterial")
        compose.onNodeWithText("Sem medições disponíveis").assertExists()
    }

    @Test fun watch_scanner_restores_without_starting_scan_or_connection() {
        val tab = mutableIntStateOf(2)
        val restoration = StateRestorationTester(compose)
        var commands = 0
        restoration.setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize()) {
                    PatientTabContent(tab.intValue) {
                        if (tab.intValue == 2) PatientWatchContent(
                            emptyList(), null, false, null,
                            { commands++ }, { commands++ }, { commands++ }, { commands++ }, {},
                        ) else Text("Outra aba")
                    }
                }
            }
        }
        compose.runOnIdle { tab.intValue = 0 }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { tab.intValue = 2 }
        compose.onNodeWithTag("scan_ble_button").assertIsDisplayed()
        compose.onNodeWithTag("custom_mac_input").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, commands) }
    }
}
