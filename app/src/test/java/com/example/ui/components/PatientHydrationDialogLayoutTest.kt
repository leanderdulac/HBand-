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

/** Product card/dialog with synthetic input/callback; no operational VM or storage. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h320dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientHydrationDialogLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val water = mutableStateOf<Int?>(250)
    private var resets = 0
    private val warning = "O total de água de hoje será zerado. Os registros de outros dias serão mantidos."
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    private fun open(fontScale: Float = 2f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HydrationCard(water.value, 2000, emptyList(), {}, { resets++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
    }

    private fun assertFullWarning() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(warning).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Complete warning must not lose lines", layouts.single().hasVisualOverflow)
        val scrolls = compose.onAllNodes(hasScrollAction() and hasAnyAncestor(isDialog()))
        if (scrolls.fetchSemanticsNodes().isNotEmpty()) {
            scrolls.onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 2000f) }
            val viewport = scrolls.onFirst().fetchSemanticsNode().boundsInRoot
            val node = compose.onNodeWithText(warning).fetchSemanticsNode()
            val layout = layouts.single()
            assertTrue(node.positionInRoot.y + layout.getLineBottom(layout.lineCount - 1) <= viewport.bottom + 1f)
            assertTrue(node.positionInRoot.y + layout.getLineTop(layout.lineCount - 1) >= viewport.top - 1f)
        }
    }

    private fun assertActions(height: Float = 320f) {
        val confirm = compose.onNodeWithTag("confirm_hydration_reset").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithText("Cancelar").assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        assertTrue(confirm.top >= 0 && confirm.bottom <= height)
        assertTrue(cancel.top >= 0 && cancel.bottom <= height)
        assertTrue(confirm.bottom <= cancel.top || cancel.bottom <= confirm.top || confirm.right <= cancel.left || cancel.right <= confirm.left)
    }

    @Test fun short_large_text_preserves_complete_warning_and_confirmation() {
        open()
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/hydration-dialog-short-before.png")
        assertFullWarning()
        assertActions()
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/hydration-dialog-short-checked.png")
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithTag("confirm_hydration_reset").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }
    @Test
    @Config(qualifiers = "w320dp-h740dp-mdpi")
    fun portrait_large_text_preserves_warning_and_cancel() {
        open()
        assertFullWarning()
        assertActions(740f)
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/hydration-dialog-portrait.png")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, resets) }
    }

    @Test fun loading_while_confirmation_is_open_disables_reset_but_keeps_cancel() {
        open(1f)
        compose.runOnIdle { water.value = null }
        compose.onNodeWithTag("confirm_hydration_reset").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Cancelar").assertIsEnabled().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, resets); water.value = 250 }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_hydration_reset").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun known_total_returning_to_open_dialog_enables_explicit_confirmation_only() {
        open(1f)
        compose.runOnIdle { water.value = null }
        compose.onNodeWithTag("confirm_hydration_reset").assertIsNotEnabled()
        compose.runOnIdle { water.value = 500 }
        compose.onNodeWithTag("confirm_hydration_reset").assertIsEnabled()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithTag("confirm_hydration_reset").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

}
