package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.hband.CompetingWatchApp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class CompetingWatchAppCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shows_patient_copy_when_hband_is_installed() {
        compose.setContent {
            MyApplicationTheme {
                CompetingWatchAppCard(warning = CompetingWatchApp.MSG_INSTALLED)
            }
        }
        compose.onNodeWithTag("banner_competing_watch_app").assertIsDisplayed()
        compose.onNodeWithText(CompetingWatchApp.MSG_INSTALLED).assertIsDisplayed()
    }

    @Test
    fun hidden_when_there_is_no_warning() {
        compose.setContent {
            MyApplicationTheme { CompetingWatchAppCard(warning = null) }
        }
        compose.onNodeWithTag("banner_competing_watch_app").assertDoesNotExist()
    }
}
