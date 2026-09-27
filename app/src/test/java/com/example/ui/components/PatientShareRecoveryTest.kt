package com.example.ui.components

import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ShareProgressData
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic PNGs and intercepted chooser; no receiver, BLE, records or operational VM. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientShareRecoveryTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val intents = mutableListOf<Intent>()
    private val messages = mutableListOf<String>()

    private fun card(name: String): ShareProgressData {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val file = temporary.newFile("$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return ShareProgressData(bitmap, file, Uri.parse("content://test/$name"), "Resumo artificial $name")
    }

    private fun content(rememberSnapshot: Boolean = true, data: () -> ShareProgressData): StateRestorationTester {
        val restore = StateRestorationTester(compose)
        restore.setContent {
            val selected = if (rememberSnapshot) remember { data() } else data()
            val base = LocalContext.current
            val context = remember(base) { object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) { intents += intent }
            } }
            CompositionLocalProvider(LocalContext provides context) {
                MyApplicationTheme { ShareProgressDialog(selected, {}, { messages += it }) }
            }
        }
        return restore
    }

    @Test fun deleted_card_does_not_launch_a_broken_share_and_keeps_text_available() {
        val data = card("deleted")
        val restore = content { data }
        assertTrue(data.file.delete()) // Only this test-owned fixture; models unavailable cached bytes.
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(intents.isEmpty()); assertEquals(1, messages.size) }
        compose.onNodeWithText("A imagem deste cartão não está mais disponível.", substring = true).assertExists()
        compose.onNodeWithText(data.summaryText).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().performClick()
        compose.onNodeWithText("Texto dos registros copiado.").assertExists()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
    }

    @Test fun empty_card_does_not_launch_a_broken_share() {
        val data = card("empty")
        content { data }
        data.file.writeBytes(byteArrayOf())
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
        compose.onNodeWithText("A imagem deste cartão não está mais disponível.", substring = true).assertExists()
    }

    @Test fun available_card_uses_the_original_snapshot_without_claiming_delivery() {
        val data = card("available")
        content { data }
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(messages.isEmpty())
            val chooser = intents.single()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(data.uri, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals(data.summaryText, send.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("image/png", send.type)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        compose.onNodeWithTag("share_action_feedback").assertDoesNotExist()
    }

    @Test fun directory_is_not_a_shareable_image() {
        val data = card("directory").copy(file = temporary.newFolder("not-an-image"))
        content { data }
        compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
        compose.onNodeWithText("A imagem deste cartão não está mais disponível.", substring = true).assertExists()
    }

    @Test fun unreadable_file_does_not_launch_a_chooser() {
        val original = card("unreadable")
        // Deterministic capability failure; Windows filesystem ACLs are not simulated.
        val data = original.copy(file = object : File(original.file.path) { override fun canRead() = false })
        content { data }
        repeat(2) {
            compose.onNodeWithTag("share_progress_intent_button").performScrollTo().performClick()
            compose.onNodeWithTag("share_action_feedback").assertIsDisplayed()
        }
        compose.runOnIdle { assertTrue(intents.isEmpty()); assertEquals(2, messages.size) }
    }

    @Test fun changing_a_card_in_composition_clears_previous_feedback() {
        val selected = mutableStateOf(card("live-first"))
        content(rememberSnapshot = false) { selected.value }
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().performClick()
        compose.onNodeWithText("Texto dos registros copiado.").assertExists()
        val next = card("live-second")
        compose.runOnIdle { selected.value = next }
        compose.onNodeWithTag("share_action_feedback").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, messages.size); assertTrue(intents.isEmpty()) }
    }

    @Test fun restoration_into_another_card_does_not_transfer_a_copy_receipt() =
        assertRestoredReceiptIsolation { _, second -> second }

    @Test fun restoration_with_changed_uri_only_does_not_transfer_a_copy_receipt() =
        assertRestoredReceiptIsolation { first, second -> second.copy(summaryText = first.summaryText) }

    @Test fun restoration_with_changed_summary_only_does_not_transfer_a_copy_receipt() =
        assertRestoredReceiptIsolation { first, second -> first.copy(summaryText = second.summaryText) }

    private fun assertRestoredReceiptIsolation(change: (ShareProgressData, ShareProgressData) -> ShareProgressData) {
        val first = card("first")
        val second = change(first, card("second"))
        var selected = first
        val restore = content { selected }
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().performClick()
        compose.onNodeWithText("Texto dos registros copiado.").assertExists()
        compose.runOnIdle { selected = second }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(second.summaryText).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("share_action_feedback").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, messages.size); assertTrue(intents.isEmpty()) }
    }
}
