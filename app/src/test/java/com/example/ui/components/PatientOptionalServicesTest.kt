package com.example.ui.components

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.example.data.local.StorageStartupState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real composables, neutral Application, intercepted services/actions; no operational startup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w320dp-h740dp-mdpi")
class PatientOptionalServicesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun permission_launcher_failure_does_not_remove_ready_content() {
        var attempts = 0
        compose.setContent {
            RequestPermissionsWhenStorageReady(StorageStartupState.READY) {
                attempts++
                throw ActivityNotFoundException("SYNTHETIC_PERMISSION_FAILURE")
            }
            StorageStartupScreen(StorageStartupState.READY) { Text("Conteúdo disponível") }
        }
        compose.onNodeWithText("Conteúdo disponível").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, attempts) }
    }

    @Test fun failed_optional_vibrator_lookup_does_not_prevent_breathing_controls() {
        var saves = 0
        compose.setContent {
            val base = LocalContext.current
            val context = remember(base) { object : ContextWrapper(base) {
                override fun getSystemService(name: String): Any? {
                    if (name == Context.VIBRATOR_MANAGER_SERVICE || name == Context.VIBRATOR_SERVICE)
                        throw SecurityException("SYNTHETIC_VIBRATOR_LOOKUP_FAILURE")
                    return super.getSystemService(name)
                }
            } }
            CompositionLocalProvider(LocalContext provides context) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BreathingExerciseCard(0, { _, _ -> saves++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun failed_initial_request_reports_once_without_retry_after_restore_or_state_changes() {
        val state = mutableStateOf(StorageStartupState.OPENING)
        val restoration = StateRestorationTester(compose)
        var requests = 0
        var failures = 0
        restoration.setContent {
            RequestPermissionsWhenStorageReady(state.value, onRequestFailure = { failures++ }) {
                requests++
                throw SecurityException("SYNTHETIC_PERMISSION_FAILURE")
            }
        }
        compose.runOnIdle { assertEquals(0, requests); state.value = StorageStartupState.READY }
        compose.runOnIdle { assertEquals(1, requests); assertEquals(1, failures) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = StorageStartupState.OPENING }
        compose.runOnIdle { state.value = StorageStartupState.READY }
        compose.runOnIdle { assertEquals(1, requests); assertEquals(1, failures) }
    }

    @Test fun readiness_uses_the_current_failure_callback_without_requesting_while_unavailable() {
        val state = mutableStateOf(StorageStartupState.UNAVAILABLE)
        var obsolete = 0
        var current = 0
        val callback = mutableStateOf<() -> Unit>({ obsolete++ })
        compose.setContent {
            RequestPermissionsWhenStorageReady(state.value, onRequestFailure = callback.value) {
                throw ActivityNotFoundException("SYNTHETIC_PERMISSION_FAILURE")
            }
        }
        compose.runOnIdle { assertEquals(0, obsolete); callback.value = { current++ } }
        compose.runOnIdle { state.value = StorageStartupState.READY }
        compose.runOnIdle { assertEquals(0, obsolete); assertEquals(1, current) }
    }

    @Test fun draft_survives_restoration_without_haptics_and_saves_only_counted_time() {
        val saved = mutableListOf<Int>()
        val restoration = breathingWithoutHaptics(false, saved)
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(6100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val before = compose.onNodeWithTag("session_timer_text").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val numbers = Regex("\\d+").findAll(before).map { it.value.toInt() }.toList()
        val seconds = numbers[0] * 60 + numbers[1]
        assertTrue(seconds > 0)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithText("Exercício pausado").assertExists()
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        compose.onNodeWithTag("save_breathing_session_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(seconds), saved) }
        compose.onNodeWithTag("breathing_save_feedback").assertTextContains("não confirmada", substring = true)
    }

    @Test fun an_absent_optional_service_keeps_the_exercise_available_without_saving() {
        val saved = mutableListOf<Int>()
        breathingWithoutHaptics(true, saved)
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.onNodeWithText("Pausar").assertExists()
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
    }

    private fun breathingWithoutHaptics(absent: Boolean, saved: MutableList<Int>): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val base = LocalContext.current
            val context = remember(base) { object : ContextWrapper(base) {
                override fun getSystemService(name: String): Any? {
                    if (name == Context.VIBRATOR_MANAGER_SERVICE || name == Context.VIBRATOR_SERVICE) {
                        if (absent) return null
                        throw SecurityException("SYNTHETIC_VIBRATOR_LOOKUP_FAILURE")
                    }
                    return super.getSystemService(name)
                }
            } }
            CompositionLocalProvider(LocalContext provides context) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BreathingExerciseCard(0, { _, seconds -> saved += seconds })
                    }
                }
            }
        }
        return restoration
    }
}
