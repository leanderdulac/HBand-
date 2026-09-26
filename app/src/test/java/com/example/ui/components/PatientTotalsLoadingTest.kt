package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
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
class PatientTotalsLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hydration_loading_never_displays_zero_or_percentage_and_known_zero_remains_valid() {
        val amount = mutableStateOf<Int?>(null)
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HydrationCard(amount.value, 2000, emptyList(), {}, {})
        } } }
        compose.onNodeWithText("Carregando registros de água…").assertExists()
        compose.onNodeWithText("0 mL").assertDoesNotExist()
        compose.onNodeWithTag("hydration_percentage_badge").assertDoesNotExist()
        compose.onNodeWithTag("reset_hydration_button").assertIsNotEnabled()
        compose.onNodeWithTag("add_250ml_water_button").assertIsEnabled()
        compose.runOnIdle { amount.value = 0 }
        compose.onNodeWithText("0 mL").assertExists()
        compose.onNodeWithText("0% da meta cadastrada").assertExists()
        compose.runOnIdle { amount.value = 500 }
        compose.onNodeWithText("500 mL").assertExists()
        compose.onNodeWithText("25% da meta cadastrada").assertExists()
    }

    @Test fun open_reset_confirmation_cannot_delete_while_new_day_is_loading() {
        val amount = mutableStateOf<Int?>(500)
        var calls = 0
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HydrationCard(amount.value, 2000, emptyList(), {}, { calls++ })
        } } }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.runOnIdle { amount.value = null }
        compose.onNodeWithTag("confirm_hydration_reset").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, calls); amount.value = 250 }
        compose.onNodeWithTag("confirm_hydration_reset").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun breathing_loading_is_distinct_from_confirmed_zero() {
        val seconds = mutableStateOf<Int?>(null)
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            BreathingExerciseCard(seconds.value, {})
        } } }
        compose.onNodeWithTag("total_breathing_duration_badge").assertTextEquals("Carregando tempo salvo…")
        compose.runOnIdle { seconds.value = 0 }
        compose.onNodeWithTag("total_breathing_duration_badge").assertTextEquals("Tempo salvo: 0\u00A0min 0\u00A0s")
    }

    @Test fun share_card_waits_for_both_totals_and_allows_confirmed_zeros() {
        val water = mutableStateOf<Int?>(null)
        val breathing = mutableStateOf<Int?>(120)
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ShareProgressCard(emptyList(), water.value, breathing.value, {})
        } } }
        compose.onNodeWithTag("share_progress_button").assertIsNotEnabled()
        compose.runOnIdle { water.value = 500; breathing.value = null }
        compose.onNodeWithTag("share_progress_button").assertIsNotEnabled()
        compose.runOnIdle { water.value = 0; breathing.value = 0 }
        compose.onNodeWithTag("share_progress_button").assertIsEnabled()
        compose.runOnIdle { water.value = null }
        compose.onNodeWithTag("share_progress_button").assertIsNotEnabled()
    }
}
