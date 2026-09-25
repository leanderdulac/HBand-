package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ShareProgressData
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientSharePreparationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preparing_blocks_duplicate_requests_and_delivers_only_when_ready() {
        val result = CompletableDeferred<ShareProgressData>()
        var calls = 0
        var received: ShareProgressData? = null
        compose.setContent { MyApplicationTheme { Column {
            SharePreparationButton(prepare = { calls++; result.await() }, onPrepared = { received = it })
        } } }
        compose.onNodeWithTag("share_progress_button").performClick()
        compose.onNodeWithTag("share_progress_button").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("share_preparation_progress").assertExists()
        compose.runOnIdle { assertEquals(1, calls); assertNull(received) }
        val prepared = fixture()
        compose.runOnIdle { result.complete(prepared) }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(prepared, received) }
        compose.onNodeWithTag("share_progress_button").assertIsEnabled()
    }

    @Test fun leaving_the_component_does_not_open_a_late_share_dialog() {
        val result = CompletableDeferred<ShareProgressData>()
        val visible = mutableStateOf(true)
        var delivered = false
        compose.setContent { MyApplicationTheme { if (visible.value) {
            SharePreparationButton(prepare = { result.await() }, onPrepared = { delivered = true })
        } } }
        compose.onNodeWithTag("share_progress_button").performClick()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { result.complete(fixture()) }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(delivered) }
    }

    @Test fun failure_is_readable_and_allows_a_new_attempt() {
        compose.setContent { MyApplicationTheme {
            SharePreparationButton(prepare = { error("INTERNAL_TEST_ERROR") }, onPrepared = {})
        } }
        compose.onNodeWithTag("share_progress_button").performClick()
        compose.onNodeWithTag("share_preparation_error").assertExists()
        compose.onNodeWithText("INTERNAL_TEST_ERROR").assertDoesNotExist()
        compose.onNodeWithTag("share_progress_button").assertIsEnabled()
    }

    private fun fixture() = ShareProgressData(
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), File("share-fixture.png"),
        Uri.parse("content://test/fixture"), "Dados artificiais de teste",
    )
}
