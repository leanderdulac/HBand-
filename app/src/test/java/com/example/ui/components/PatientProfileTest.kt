package com.example.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientProfileTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editing_name_preserves_canonical_ids_and_decimal_measurements() {
        val original = UserProfileEntity(id = "profile-fixture", patientId = "patient-fixture", fullName = "Pessoa de teste", heightCm = 162.5f, weightKg = 63.7f)
        var saved: UserProfileEntity? = null
        compose.setContent { MyApplicationTheme { UserProfileDialog(original, {}, { saved = it }) } }
        compose.onNodeWithTag("input_profile_name").performTextReplacement("Nome corrigido")
        compose.onNodeWithTag("btn_save_profile").performClick()
        compose.runOnIdle { assertEquals(original.copy(fullName = "Nome corrigido"), saved) }
    }

    @Test fun invalid_input_does_not_silently_save_example_values() {
        var saves = 0
        compose.setContent { MyApplicationTheme { UserProfileDialog(UserProfileEntity(), {}, { saves++ }) } }
        compose.onNodeWithTag("input_age").performScrollTo().performTextReplacement("abc")
        compose.onNodeWithTag("btn_save_profile").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun missing_profile_cannot_save_a_fabricated_identity() {
        compose.setContent { MyApplicationTheme { UserProfileDialog(null, {}, { error("Unexpected profile creation") }) } }
        compose.onNodeWithTag("btn_save_profile").assertDoesNotExist()
        compose.onNodeWithText("Alex Rivera").assertDoesNotExist()
        compose.onNodeWithText("Fechar").assertIsDisplayed()
    }

    @Test fun decimal_input_accepts_comma_without_accepting_nan_or_infinity() {
        assertEquals(63.7f, profileDecimal("63,7"))
        assertEquals(63.7f, profileDecimal("63.7"))
        assertNull(profileDecimal("NaN"))
        assertNull(profileDecimal("Infinity"))
        assertNull(profileDecimal("-10"))
    }

    @Test fun keyboard_moves_to_the_next_field_and_done_does_not_save_automatically() {
        var saves = 0
        compose.setContent { MyApplicationTheme { UserProfileDialog(UserProfileEntity(), {}, { saves++ }) } }
        compose.onNodeWithTag("input_height").performScrollTo().performClick().performImeAction()
        compose.onNodeWithTag("input_weight").assertIsFocused().performImeAction()
        compose.onNodeWithTag("input_weight").assertIsNotFocused()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithTag("btn_save_profile").assertIsEnabled()
    }

    @Test fun unchanged_profile_closes_without_an_extra_step() {
        var dismissals = 0
        compose.setContent {
            MyApplicationTheme { UserProfileDialog(UserProfileEntity(), { dismissals++ }, { error("Unexpected save") }) }
        }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
        compose.onNodeWithTag("profile_discard_changes").assertDoesNotExist()
    }

    @Test fun invalid_draft_needs_explicit_discard_and_is_never_saved() {
        var dismissals = 0
        var saves = 0
        compose.setContent {
            MyApplicationTheme { UserProfileDialog(UserProfileEntity(), { dismissals++ }, { saves++ }) }
        }
        compose.onNodeWithTag("input_age").performScrollTo().performTextReplacement("abc")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Sair sem salvar?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals); assertEquals(0, saves) }
        compose.onNodeWithTag("profile_discard_changes").performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, saves) }
    }

    @Test fun restored_discard_prompt_can_return_to_the_draft_and_save_the_same_identity() {
        val original = UserProfileEntity(id = "profile-fixture", patientId = "patient-fixture", fullName = "Pessoa de teste")
        var saved: UserProfileEntity? = null
        var dismissals = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MyApplicationTheme { UserProfileDialog(original, { dismissals++ }, { saved = it }) }
        }
        compose.onNodeWithTag("input_profile_name").performTextReplacement("Nome corrigido")
        compose.onNodeWithText("Cancelar").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Sair sem salvar?").assertIsDisplayed()
        compose.onNodeWithTag("profile_continue_editing").performClick()
        compose.onNodeWithTag("input_profile_name").assertTextContains("Nome corrigido")
        compose.runOnIdle { assertNull(saved); assertEquals(0, dismissals) }
        compose.onNodeWithTag("btn_save_profile").performClick()
        compose.runOnIdle { assertEquals(original.copy(fullName = "Nome corrigido"), saved); assertEquals(1, dismissals) }
    }
}
