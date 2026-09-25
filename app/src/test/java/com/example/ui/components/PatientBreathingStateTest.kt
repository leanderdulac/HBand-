package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Local UI timer only; no patient record, database or watch is used. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientBreathingStateTest {
    @get:Rule val compose = createComposeRule()
    private val saved = mutableListOf<Int>()

    @Test fun leaving_the_visible_app_pauses_the_draft_and_returning_requires_a_new_tap() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        content(owner)
        startAndAdvance()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.onNodeWithText("Exercício pausado").assertExists()
        val paused = timerText()
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("session_timer_text").assertTextEquals(paused)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Continuar exercício").assertExists()
        compose.onNodeWithTag("session_timer_text").assertTextEquals(paused)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        startAndAdvance()
        assertTrue(timerText() != paused)
    }

    @Test fun restoration_preserves_counted_time_without_resuming_or_saving() {
        val restoration = content()
        startAndAdvance()
        val before = timerText()
        assertTrue(before != "Tempo neste exercício: 0\u00A0min 0\u00A0s")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithText("Exercício pausado").assertExists()
        compose.onNodeWithText("Continuar exercício").assertExists()
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
    }

    @Test fun saving_the_restored_session_uses_only_the_counted_seconds_once() {
        val restoration = content()
        startAndAdvance()
        val before = timerText()
        val seconds = Regex("Tempo neste exercício: (\\d+) min (\\d+) s").matchEntire(before.replace('\u00A0', ' '))!!
            .destructured.let { (minutes, seconds) -> minutes.toInt() * 60 + seconds.toInt() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("save_breathing_session_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(seconds), saved) }
        compose.onNodeWithTag("save_breathing_session_button").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("session_timer_text").assertTextEquals("Tempo neste exercício: 0\u00A0min 0\u00A0s")
        compose.runOnIdle { assertEquals(listOf(seconds), saved) }
    }

    private fun startAndAdvance() {
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(6100)
        // Restoration removes then recreates content in separate frames. Let
        // the test rule process both; a frozen clock would keep the old tree.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun timerText(): String = compose.onNodeWithTag("session_timer_text")
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    private fun content(owner: LifecycleOwner? = null): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides (owner ?: LocalLifecycleOwner.current)) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BreathingExerciseCard(120, { saved += it })
                    }
                }
            }
        }
        return restoration
    }
}
