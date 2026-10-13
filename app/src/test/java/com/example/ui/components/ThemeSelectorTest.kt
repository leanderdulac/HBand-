package com.example.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ThemeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selector_lists_all_options_and_defaults_to_claro() {
        var chosen = AppThemeOption.LIGHT
        compose.setContent {
            MyApplicationTheme {
                ThemeSelectorCard(themeOption = chosen, onThemeOptionChange = { chosen = it })
            }
        }
        compose.onNodeWithTag("theme_selector").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Escuro azul").assertIsDisplayed()
        compose.onNodeWithText("Escuro índigo").assertIsDisplayed()
        compose.onNodeWithText("Escuro grafite + ciano").assertIsDisplayed()
        compose.onNodeWithText("Seguir o sistema").assertIsDisplayed()
        compose.onNodeWithTag("theme_option_dark_indigo").performClick()
        compose.runOnIdle { assertEquals(AppThemeOption.DARK_INDIGO, chosen) }
    }
}
