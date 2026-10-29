package com.example.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.data.local.IngestQueueEntity
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h320dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientInspectorLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val item = IngestQueueEntity(id = 57, payloadJson = "{\n" + (1..80).joinToString(",\n") { "  \"synthetic_$it\": $it" } + "\n}", status = "FAILED")

    @Test fun short_screen_keeps_close_button_visible() = checkClose(1f)
    @Test fun short_screen_with_large_text_keeps_close_button_visible() = checkClose(2f)

    @Test fun long_record_scrolls_without_hiding_close_or_changing_payload_and_identity() {
        RuntimeEnvironment.setFontScale(2f)
        var dismissed = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MyApplicationTheme { JsonPayloadModal(item, { dismissed++ }) }
            }
        }
        compose.onNodeWithText("Item da Fila #57 (FAILED)").assertExists()
        compose.onNodeWithTag("json_payload_text").assertTextEquals(item.payloadJson)
        val scroll = compose.onNodeWithTag("json_payload_scroll")
        assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(range.maxValue(), range.value(), 1f)
        compose.onNodeWithTag("json_payload_text").assertTextEquals(item.payloadJson)
        compose.onNodeWithText("Fechar Inspetor").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissed) }
        compose.onRoot().captureRoboImage(filePath = "build/inspector-scrolled.png")
        compose.onNodeWithText("Fechar Inspetor").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun narrow_screen_keeps_large_text_close_action_inside_dialog() = checkClose(2f)

    @Test
    @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun tablet_keeps_close_action_and_complete_record() = checkClose(1f)

    private fun checkClose(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        var dismissed = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme { JsonPayloadModal(item, { dismissed++ }) }
            }
        }
        compose.onNodeWithText("Fechar Inspetor").assertIsDisplayed()
        val button = compose.onNodeWithTag("close_json_inspector").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("json_payload_modal").fetchSemanticsNode().boundsInRoot
        assertTrue(button.height >= 48f)
        assertTrue(button.left >= card.left && button.right <= card.right && button.bottom <= card.bottom)
        compose.onNodeWithTag("json_payload_text").assertTextEquals(item.payloadJson)
        compose.onRoot().captureRoboImage(filePath = "build/inspector-${card.width.toInt()}-${card.height.toInt()}-$fontScale.png")
        compose.onNodeWithText("Fechar Inspetor").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }
}
