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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ShareProgressData
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** No chooser opens or clipboard writes: synthetic data and intercepted Android actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h320dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientShareFeedbackLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var attempts = 0
    private var dismissals = 0
    private val messages = mutableListOf<String>()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    private fun open(fontScale: Float = 2f): StateRestorationTester {
        RuntimeEnvironment.setFontScale(fontScale)
        val data = ShareProgressData(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            File("share-layout-fixture.png"), Uri.parse("content://test/layout"), "Dados artificiais de teste")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val base = LocalContext.current
            val unavailable = remember(base) { object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) { attempts++; throw ActivityNotFoundException("Synthetic") }
                override fun getSystemService(name: String): Any? {
                    if (name == Context.CLIPBOARD_SERVICE) { attempts++; throw SecurityException("Synthetic") }
                    return super.getSystemService(name)
                }
            } }
            CompositionLocalProvider(LocalContext provides unavailable) {
                MyApplicationTheme { ShareProgressDialog(data, { dismissals++ }, { messages.add(it) }) }
            }
        }
        return restoration
    }

    private fun assertFeedbackReadable() {
        val feedback = compose.onNodeWithTag("share_action_feedback").assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("share_action_feedback").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        assertTrue("Feedback should be brought fully into view in this fixture", feedback.boundsInRoot.height >= feedback.size.height - 1f)
    }

    private fun failAndCheck(action: String, name: String) {
        open()
        compose.onNodeWithTag(action).performScrollTo().performClick()
        assertFeedbackReadable()
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/share-feedback-$name-error.png")
        compose.onNodeWithText("Compartilhe somente com quem você escolher.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dados artificiais de teste").performScrollTo().assertIsDisplayed()
        val button = compose.onNodeWithTag(action).performScrollTo().assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        assertTrue("Entire action must fit after scrolling: visible=${button.boundsInRoot.height}, full=${button.size.height}",
            button.boundsInRoot.height >= button.size.height - 1f)
        compose.onNode(isDialog()).captureRoboImage(filePath = "build/share-feedback-$name-actions.png")
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, attempts); assertEquals(1, messages.size); assertEquals(1, dismissals) }
    }

    @Test fun chooser_error_keeps_summary_and_actions_reachable_on_short_screen() =
        failAndCheck("share_progress_intent_button", "chooser-short")

    @Test fun clipboard_error_keeps_summary_and_actions_reachable_on_short_screen() =
        failAndCheck("copy_progress_summary_button", "clipboard-short")
    @Test
    @Config(qualifiers = "w320dp-h740dp-mdpi")
    fun portrait_large_text_keeps_feedback_and_full_actions() =
        failAndCheck("share_progress_intent_button", "portrait")

    @Test fun repeated_identical_error_returns_feedback_to_view_without_extra_actions() {
        open()
        repeat(2) {
            compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().performClick()
            assertFeedbackReadable()
            compose.runOnIdle { assertEquals(it + 1, attempts); assertEquals(it + 1, messages.size) }
        }
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("dismiss_share_progress_button").performClick()
        compose.runOnIdle { assertEquals(2, attempts); assertEquals(1, dismissals) }
    }

    @Test fun restoring_feedback_does_not_repeat_share_or_notification() {
        val restoration = open()
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        assertFeedbackReadable()
        restoration.emulateSavedInstanceStateRestore()
        assertFeedbackReadable()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, attempts); assertEquals(1, messages.size); assertEquals(1, dismissals) }
    }

}
