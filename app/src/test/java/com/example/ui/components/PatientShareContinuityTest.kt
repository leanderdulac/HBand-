package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.ui.PatientSharePreviewViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ShareProgressData
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** UI-only fixtures: no filesystem writes, chooser, clipboard or patient records. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h600dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientShareContinuityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recreated_content_keeps_the_same_card_and_dismissal_stays_closed() {
        val prepared = ShareProgressData(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            File("fixture.png"), Uri.parse("content://test/fixture"), "Resumo de teste preservado")
        var preparations = 0
        lateinit var observed: PatientSharePreviewViewModel
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val preview: PatientSharePreviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            observed = preview
            MyApplicationTheme {
                Button(onClick = { preparations++; preview.open(prepared) }) { Text("Preparar fixture") }
                preview.data?.let { ShareProgressDialog(it, preview::dismiss, { error("Unexpected action") }) }
            }
        }
        compose.onNodeWithText("Preparar fixture").performClick()
        repeat(2) {
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Resumo de teste preservado").assertIsDisplayed()
            compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
            compose.runOnIdle { assertSame(prepared, observed.data); assertEquals(1, preparations) }
        }
        compose.onNodeWithTag("dismiss_share_progress_button").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("share_progress_dialog").assertDoesNotExist()
        compose.runOnIdle { assertNull(observed.data); assertEquals(1, preparations) }
    }

    @Test fun ending_the_owner_releases_the_card_and_a_new_owner_starts_empty() {
        val store = ViewModelStore()
        val provider = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())
        val preview = provider[PatientSharePreviewViewModel::class.java]
        val prepared = ShareProgressData(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            File("fixture.png"), Uri.parse("content://test/fixture"), "Teste")
        preview.open(prepared)
        assertSame(prepared, provider[PatientSharePreviewViewModel::class.java].data)
        store.clear()
        assertNull(preview.data)
        assertNull(provider[PatientSharePreviewViewModel::class.java].data)
        store.clear()
    }
}
