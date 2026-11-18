package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.PatientStartupApp
import com.example.data.local.StorageStartupState
import com.example.ui.theme.AppThemeOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientStartupAppTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun opening_and_unavailable_do_not_construct_ready_content() {
        val state = mutableStateOf(StorageStartupState.OPENING)
        var readyEntered = false
        compose.setContent {
            PatientStartupApp(
                state = state.value,
                openingTheme = AppThemeOption.LIGHT,
                bleLab = false,
            ) {
                readyEntered = true
                Text("Operational")
            }
        }
        compose.onNodeWithText("Abrindo seus registros").assertIsDisplayed()
        compose.onNodeWithTag("storage_startup_screen").assertIsDisplayed()
        compose.runOnIdle { assertFalse(readyEntered) }

        state.value = StorageStartupState.UNAVAILABLE
        compose.waitForIdle()
        compose.onNodeWithText("Não foi possível abrir seus registros").assertIsDisplayed()
        compose.onNodeWithText("Operational").assertDoesNotExist()
        compose.runOnIdle { assertFalse(readyEntered) }
    }

    @Test
    fun ready_releases_viewmodel_content() {
        var readyEntered = false
        compose.setContent {
            PatientStartupApp(
                state = StorageStartupState.READY,
                openingTheme = AppThemeOption.LIGHT,
                bleLab = false,
            ) {
                readyEntered = true
                Text("Operational")
            }
        }
        compose.onNodeWithText("Operational").assertIsDisplayed()
        compose.onNodeWithTag("storage_startup_screen").assertDoesNotExist()
        compose.runOnIdle { assertTrue(readyEntered) }
    }
}
