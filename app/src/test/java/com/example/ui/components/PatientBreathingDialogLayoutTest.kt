package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.ui.BreathingSaveState
import com.example.ui.BreathingSaveStatus
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production card/dialog, synthetic receipt; no real write or operational startup. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h320dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientBreathingDialogLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val receipt = mutableStateOf<BreathingSaveState?>(null)
    private var requests = 0
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    private fun open(discard: Boolean, fontScale: Float = 2f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BreathingExerciseCard(120, { token, seconds ->
                            requests++
                            receipt.value = BreathingSaveState(token, seconds, BreathingSaveStatus.UNCONFIRMED)
                        }, saveState = receipt.value)
                    }
                }
            }
        }
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(6100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.onNodeWithTag("save_breathing_session_button").performScrollTo().performClick()
        if (discard) compose.onNodeWithText("Encerrar sem salvar novamente").performScrollTo().performClick()
        else compose.onNodeWithTag("save_breathing_session_button").performScrollTo().performClick()
    }

    private fun assertFullExplanation(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Explanation must not lose lines to its measurement constraint", layouts.single().hasVisualOverflow)
        val scrolls = compose.onAllNodes(hasScrollAction() and hasAnyAncestor(isDialog()))
        if (scrolls.fetchSemanticsNodes().isNotEmpty()) {
            scrolls.onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 2000f) }
            val viewport = scrolls.onFirst().fetchSemanticsNode().boundsInRoot
            val content = compose.onNodeWithText(text).fetchSemanticsNode()
            val layout = layouts.single()
            val lastLine = layout.lineCount - 1
            assertTrue("Last warning line must be visible after scrolling", content.positionInRoot.y + layout.getLineBottom(lastLine) <= viewport.bottom + 1f)
            assertTrue(content.positionInRoot.y + layout.getLineTop(lastLine) >= viewport.top - 1f)
        }
    }

    private fun assertAction(text: String, height: Float = 320f) {
        val node = compose.onNodeWithText(text).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        assertTrue("Action must fit viewport", node.boundsInRoot.top >= 0 && node.boundsInRoot.bottom <= height)
    }

    @Test fun short_landscape_retry_keeps_full_warning_and_actions_reachable() {
        open(false)
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-retry-short-before.png")
        assertFullExplanation("O tempo pode já ter sido salvo. Confira o total salvo. Uma nova tentativa pode duplicar esse tempo.")
        assertAction("Conferi; salvar novamente")
        assertAction("Voltar")
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-retry-short-scrolled.png")
        compose.onNodeWithText("Conferi; salvar novamente").performClick()
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun short_landscape_discard_keeps_full_warning_and_actions_reachable() {
        open(true)
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-discard-short-before.png")
        assertFullExplanation("Isso descarta somente o tempo deste exercício na tela. Um registro que já tenha sido salvo continuará no celular.")
        assertAction("Descartar rascunho")
        assertAction("Voltar")
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-discard-short-scrolled.png")
        compose.onNodeWithText("Descartar rascunho").performClick()
        compose.onNodeWithTag("save_breathing_session_button").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    @Config(qualifiers = "w320dp-h740dp-mdpi")
    fun portrait_discard_with_large_text_can_go_back_without_losing_draft() {
        open(true)
        assertFullExplanation("Isso descarta somente o tempo deste exercício na tela. Um registro que já tenha sido salvo continuará no celular.")
        assertAction("Descartar rascunho", 740f)
        assertAction("Voltar", 740f)
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-discard-portrait-large.png")
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithTag("save_breathing_session_button").assertExists()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun short_landscape_standard_type_keeps_warning_and_back_action() {
        open(false, 1f)
        assertFullExplanation("O tempo pode já ter sido salvo. Confira o total salvo. Uma nova tentativa pode duplicar esse tempo.")
        assertAction("Conferi; salvar novamente")
        assertAction("Voltar")
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/breathing-retry-short-standard.png")
        compose.onNodeWithText("Voltar").performClick()
        compose.onNodeWithTag("save_breathing_session_button").assertExists()
        compose.runOnIdle { assertEquals(1, requests) }
    }
}
