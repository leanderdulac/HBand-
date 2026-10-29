package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic component inputs; HomeScreen's nullable plumbing is reviewed separately. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientHydrationGoalTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun unavailable_profile_must_not_assert_absent_goal() {
        val profile: UserProfileEntity? = null
        // Mirrors the nullable projection, without constructing the operational Home/VM.
        content { HydrationCard(250, profile?.targetWaterMl, emptyList(), {}, {}) }
        compose.onNodeWithText("Meta de água indisponível.").assertExists()
        compose.onNodeWithText("Nenhuma meta de água cadastrada.").assertDoesNotExist()
        compose.onNodeWithText("250 mL").assertExists()
        compose.onNodeWithTag("hydration_progress_bar").assertDoesNotExist()
        compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
    }

    @Test fun known_goal_remains_visible_while_water_records_load() {
        content { HydrationCard(null, 1800, emptyList(), {}, {}) }
        compose.onNodeWithText("Meta cadastrada: 1800 mL por dia").assertExists()
        compose.onNodeWithText("Carregando registros de água…").assertExists()
        compose.onNodeWithText("0 mL").assertDoesNotExist()
        compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
    }

    @Test fun confirmed_nonpositive_goal_preserves_existing_absence_policy() {
        val goal = mutableStateOf<Int?>(0)
        content { HydrationCard(null, goal.value, emptyList(), {}, {}) }
        for (value in listOf(0, -1)) {
            compose.runOnIdle { goal.value = value }
            compose.onNodeWithText("Nenhuma meta de água cadastrada.").assertExists()
            compose.onNodeWithText("Meta de água indisponível.").assertDoesNotExist()
            compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
        }
    }

    @Test fun goal_changes_remove_stale_progress_without_changing_water() {
        val goal = mutableStateOf<Int?>(null)
        content { HydrationCard(500, goal.value, emptyList(), {}, {}) }
        compose.runOnIdle { goal.value = 0 }
        compose.onNodeWithText("Nenhuma meta de água cadastrada.").assertExists()
        compose.runOnIdle { goal.value = 2000 }
        compose.onNodeWithText("25% da meta cadastrada").assertExists()
        compose.runOnIdle { goal.value = 1000 }
        compose.onNodeWithText("50% da meta cadastrada").assertExists()
        compose.onNodeWithText("Meta cadastrada: 2000 mL por dia").assertDoesNotExist()
        compose.runOnIdle { goal.value = null }
        compose.onNodeWithText("Meta de água indisponível.").assertExists()
        compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
        compose.onNodeWithText("500 mL").assertExists()
    }

    @Test fun water_loading_does_not_erase_goal_or_fabricate_zero() {
        val water = mutableStateOf<Int?>(null)
        content { HydrationCard(water.value, 2000, emptyList(), {}, {}) }
        compose.runOnIdle { water.value = 0 }
        compose.onNodeWithText("0% da meta cadastrada").assertExists()
        compose.runOnIdle { water.value = 500 }
        compose.onNodeWithText("25% da meta cadastrada").assertExists()
        compose.runOnIdle { water.value = null }
        compose.onNodeWithText("Meta cadastrada: 2000 mL por dia").assertExists()
        compose.onNodeWithText("Carregando registros de água…").assertExists()
        compose.onNodeWithText("0 mL").assertDoesNotExist()
        compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
        compose.onNodeWithTag("reset_hydration_button").assertIsNotEnabled()
    }

    @Test fun unavailable_goal_preserves_quantities_and_reset_confirmation() {
        val added = mutableListOf<Int>()
        var resets = 0
        content { HydrationCard(250, null, emptyList(), { added.add(it) }, { resets++ }) }
        for (amount in listOf(250, 500, 750)) {
            compose.onNodeWithTag("add_${amount}ml_water_button").performScrollTo().performClick()
        }
        compose.runOnIdle { assertEquals(listOf(250, 500, 750), added) }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_hydration_reset").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun goal_states_and_actions_are_accessible_with_large_text() {
        RuntimeEnvironment.setFontScale(2f)
        val goal = mutableStateOf<Int?>(null)
        val water = mutableStateOf<Int?>(250)
        content { HydrationCard(water.value, goal.value, emptyList(), {}, {}) }
        compose.onNodeWithTag("hydration_goal_text").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/hydration-goal-unavailable.png")
        compose.runOnIdle { goal.value = 2000; water.value = null }
        compose.onNodeWithTag("hydration_goal_text").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/hydration-goal-known-loading.png")
        compose.runOnIdle { water.value = 500 }
        compose.onNodeWithTag("hydration_percentage_badge").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/hydration-goal-known-total.png")
        compose.onNodeWithTag("add_750ml_water_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/hydration-goal-actions.png")
    }

    private fun content(card: @Composable () -> Unit) {
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { card() }
        } }
    }
}
