package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.ui.BreathingSaveState
import com.example.ui.BreathingSaveStatus
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production card/SaveableStateHolder, simulated receipts, no operational VM. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientBreathingSaveUiTest {
    @get:Rule val compose = createComposeRule()
    private val receipt = mutableStateOf<BreathingSaveState?>(null)
    private val requests = mutableListOf<BreathingSaveState>()
    private val tab = mutableIntStateOf(0)
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    private fun content(fontScale: Float = 1f): StateRestorationTester {
        // Dialogs use their own window density, outside the card's LocalDensity override.
        RuntimeEnvironment.setFontScale(fontScale)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MyApplicationTheme {
                    PatientTabContent(tab.intValue) {
                        if (tab.intValue == 0) Column(Modifier.verticalScroll(rememberScrollState())) {
                            BreathingExerciseCard(120, { token, seconds ->
                                val pending = BreathingSaveState(token, seconds, BreathingSaveStatus.SAVING)
                                requests += pending
                                receipt.value = pending
                            }, saveState = receipt.value)
                        }
                    }
                }
            }
        }
        return restoration
    }
    private fun timer() = compose.onNodeWithTag("session_timer_text").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    private fun countAndPause(): String {
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(6100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        return timer()
    }
    private fun submit() = compose.onNodeWithTag("save_breathing_session_button").performScrollTo().performClick()
    private fun result(status: BreathingSaveStatus) = compose.runOnIdle { receipt.value = requests.last().copy(status = status) }

    @Test fun submitting_without_confirmation_preserves_draft_and_blocks_second_click() {
        content()
        val before = countAndPause()
        submit()
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithTag("save_breathing_session_button").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("toggle_breathing_exercise_button").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
    @Test fun success_after_restoration_clears_confirmed_draft_without_resubmitting() {
        val restoration = content()
        val before = countAndPause()
        submit()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithText("Salvando…").assertExists()
        result(BreathingSaveStatus.SAVED)
        compose.onNodeWithTag("session_timer_text").assertTextEquals("Tempo neste exercício: 0\u00A0min 0\u00A0s")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("save_breathing_session_button").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
    @Test fun success_on_another_tab_is_applied_on_return_without_another_write() {
        content(); countAndPause(); submit()
        compose.runOnIdle { tab.intValue = 1 }
        result(BreathingSaveStatus.SAVED)
        compose.runOnIdle { tab.intValue = 0 }
        compose.onNodeWithTag("session_timer_text").assertTextEquals("Tempo neste exercício: 0\u00A0min 0\u00A0s")
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
    @Test fun manual_retry_requires_confirmation_and_new_token_with_same_duration() {
        content()
        val before = countAndPause()
        submit(); result(BreathingSaveStatus.UNCONFIRMED)
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithTag("toggle_breathing_exercise_button").assertIsNotEnabled()
        submit()
        compose.onNodeWithText("Voltar").performClick()
        compose.runOnIdle { assertEquals(1, requests.size) }
        submit()
        compose.onNodeWithText("Conferi; salvar novamente").performClick()
        compose.runOnIdle {
            assertEquals(2, requests.size)
            assertNotEquals(requests[0].token, requests[1].token)
            assertEquals(requests[0].seconds, requests[1].seconds)
        }
        result(BreathingSaveStatus.SAVED)
        compose.onNodeWithTag("save_breathing_session_button").assertDoesNotExist()
    }
    @Test fun unrelated_or_different_duration_receipt_never_clears_draft() {
        content()
        val before = countAndPause(); submit()
        compose.runOnIdle { receipt.value = requests.single().copy(token = "unrelated", status = BreathingSaveStatus.SAVED) }
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.runOnIdle { receipt.value = requests.single().copy(seconds = requests.single().seconds + 1, status = BreathingSaveStatus.SAVED) }
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithText("Tentar salvar novamente").assertExists()
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
    @Test fun restored_token_without_retained_receipt_is_uncertain_and_never_replayed() {
        val restoration = content()
        val before = countAndPause(); submit()
        compose.runOnIdle { receipt.value = null }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithText("Tentar salvar novamente").assertExists()
        compose.onNodeWithTag("toggle_breathing_exercise_button").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
    @Test fun explicit_discard_resets_only_draft_without_saving_again() {
        content()
        val before = countAndPause(); submit(); result(BreathingSaveStatus.UNCONFIRMED)
        compose.onNodeWithText("Encerrar sem salvar novamente").performScrollTo().performClick()
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithText("Encerrar sem salvar novamente").performScrollTo().performClick()
        compose.onNodeWithText("Descartar rascunho").performClick()
        compose.onNodeWithTag("session_timer_text").assertTextEquals("Tempo neste exercício: 0\u00A0min 0\u00A0s")
        compose.onNodeWithTag("total_breathing_duration_badge").assertTextEquals("Tempo salvo: 2\u00A0min 0\u00A0s")
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
    @Test fun large_text_exposes_pending_uncertain_and_confirmation_controls() {
        content(2f); countAndPause(); submit()
        compose.onNodeWithTag("save_breathing_session_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onRoot().captureRoboImage(filePath = "build/breathing-save-pending-large.png")
        result(BreathingSaveStatus.UNCONFIRMED)
        compose.onNodeWithText("Encerrar sem salvar novamente").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/breathing-save-uncertain-large.png")
        submit()
        compose.onNodeWithText("Conferi; salvar novamente").assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-save-confirm-large.png")
        compose.onNodeWithText("Voltar").performClick()
        compose.runOnIdle { assertEquals(1, requests.size) }
    }
}
