package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientHydrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun progress_uses_the_saved_goal_without_substituting_another_value() {
        content(goal = 500)
        compose.onNodeWithText("50% da meta cadastrada").assertExists()
    }

    @Test fun absent_goal_does_not_create_a_percentage() {
        content(goal = 0)
        compose.onNodeWithText("Nenhuma meta de água cadastrada.").assertExists()
        compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
    }

    @Test fun quantity_is_preserved_and_reset_requires_confirmation() {
        var added = 0
        var resets = 0
        content(add = { added = it }, reset = { resets++ })
        compose.onNodeWithTag("add_500ml_water_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(500, added) }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_hydration_reset").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

    private fun content(goal: Int = 2000, add: (Int) -> Unit = {}, reset: () -> Unit = {}) {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HydrationCard(250, goal, emptyList(), add, reset)
                }
            }
        }
    }
}
