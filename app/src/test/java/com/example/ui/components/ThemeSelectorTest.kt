package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ThemeSelectorCard(themeOption = chosen, onThemeOptionChange = { chosen = it })
                }
            }
        }
        compose.onNodeWithTag("theme_selector").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Escuro azul").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Escuro índigo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Escuro grafite + ciano").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seguir o sistema").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("theme_option_dark_indigo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppThemeOption.DARK_INDIGO, chosen) }
    }
}
