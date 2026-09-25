package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ShareProgressData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixtures only. No chooser is opened, recipient selected or patient data copied. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientShareDialogFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private val messages = mutableListOf<String>()

    @Test
    @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun tablet_places_preview_beside_summary_without_starting_a_share() {
        content(fontScale = 1f)
        val preview = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val summary = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        assertEquals(preview.positionInRoot.y, summary.positionInRoot.y, 1f)
        assertTrue(summary.positionInRoot.x >= preview.positionInRoot.x + preview.size.width)
        compose.onNodeWithText("Dados artificiais de teste").assertIsDisplayed()
        compose.onNodeWithTag("share_progress_intent_button").assertIsDisplayed()
        compose.onNodeWithTag("copy_progress_summary_button").assertIsDisplayed()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
        compose.runOnIdle { assertTrue(messages.isEmpty()) }
    }

    @Test fun phone_with_large_type_stacks_preview_and_keeps_actions_reachable() {
        content(fontScale = 1.6f)
        val preview = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val summary = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        assertEquals(preview.positionInRoot.x, summary.positionInRoot.x, 1f)
        assertTrue(summary.positionInRoot.y >= preview.positionInRoot.y + preview.size.height)
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
        compose.runOnIdle { assertTrue(messages.isEmpty()) }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-mdpi")
    fun short_screen_with_extreme_text_keeps_close_available_after_an_error() {
        var dismissals = 0
        content(fontScale = 2f, onDismiss = { dismissals++ })
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        compose.onNodeWithTag("share_action_feedback").assertExists()
        val close = compose.onNodeWithTag("dismiss_share_progress_button").fetchSemanticsNode()
        assertTrue("The complete close action must fit in the 320 dp high dialog window: ${close.positionInRoot}, ${close.size}",
            close.positionInRoot.y + close.size.height <= 320f && close.size.height >= 56)
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun unavailable_chooser_explains_the_failure_inside_the_open_dialog() {
        content()
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        compose.onNodeWithTag("share_action_feedback").assertIsDisplayed()
        compose.onNodeWithText("Não foi possível abrir as opções de compartilhamento. Tente novamente.").assertIsDisplayed()
        compose.onNodeWithTag("share_progress_intent_button").assertIsEnabled()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test fun unavailable_clipboard_keeps_the_dialog_open_without_a_false_success() {
        content()
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().performClick()
        compose.onNodeWithTag("share_action_feedback").assertIsDisplayed()
        compose.onNodeWithText("Não foi possível copiar o texto. Tente novamente.").assertIsDisplayed()
        compose.onNodeWithText("Texto dos registros copiado.").assertDoesNotExist()
        compose.onNodeWithTag("copy_progress_summary_button").assertIsEnabled()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test fun restoring_an_error_keeps_feedback_without_repeating_the_action() {
        val restoration = content()
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Não foi possível abrir as opções de compartilhamento. Tente novamente.").assertIsDisplayed()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, messages.size) }
    }

    private fun content(fontScale: Float = 1.6f, onDismiss: () -> Unit = {}): StateRestorationTester {
        val data = ShareProgressData(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), File("share-fixture.png"),
            Uri.parse("content://test/fixture"), "Dados artificiais de teste",
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val baseContext = LocalContext.current
            val unavailableActions = remember(baseContext) {
                object : ContextWrapper(baseContext) {
                    override fun startActivity(intent: Intent) {
                        throw ActivityNotFoundException("Internal test exception")
                    }
                    override fun getSystemService(name: String): Any? {
                        if (name == Context.CLIPBOARD_SERVICE) throw SecurityException("Internal test exception")
                        return super.getSystemService(name)
                    }
                }
            }
            CompositionLocalProvider(
                LocalContext provides unavailableActions,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MyApplicationTheme { ShareProgressDialog(data, onDismiss, { messages += it }) }
            }
        }
        return restoration
    }
}
