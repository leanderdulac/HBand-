package com.example.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w320dp-h740dp-mdpi")
class PatientHistoryRapidNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun content() { compose.setContent { MyApplicationTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) { RechartsSevenDaySummaryCard(emptyList()) }
    } } }
    @Test fun repeated_previous_callbacks_cannot_escape_the_oldest_day() {
        content()
        repeat(5) { compose.onNodeWithTag("history_previous_day").performScrollTo().performClick() }
        compose.onNodeWithTag("history_previous_day").performSemanticsAction(SemanticsActions.OnClick) { action -> repeat(3) { action() } }
        compose.onNodeWithTag("history_previous_day").assertIsNotEnabled()
        compose.onNodeWithTag("history_next_day").assertIsEnabled()
        compose.onNodeWithTag("history_selected_date").assertExists()
    }
    @Test fun repeated_next_callbacks_cannot_escape_today() {
        content()
        compose.onNodeWithTag("history_previous_day").performScrollTo().performClick()
        compose.onNodeWithTag("history_next_day").performSemanticsAction(SemanticsActions.OnClick) { action -> repeat(3) { action() } }
        compose.onNodeWithTag("history_next_day").assertIsNotEnabled()
        compose.onNodeWithTag("history_previous_day").assertIsEnabled()
        compose.onNodeWithTag("history_selected_date").assertExists()
    }
}
