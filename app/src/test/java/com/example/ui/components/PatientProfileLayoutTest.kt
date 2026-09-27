package com.example.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic profiles only; no real profile is created or changed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h600dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientProfileLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val original = UserProfileEntity(id = "profile-fixture", patientId = "patient-fixture",
        fullName = "Pessoa de teste", heightCm = 162.5f, weightKg = 63.7f)

    @Test fun tablet_shows_two_groups_and_saves_only_the_edited_fields() {
        RuntimeEnvironment.setFontScale(1f)
        var saved: UserProfileEntity? = null
        compose.setContent { MyApplicationTheme { UserProfileDialog(original, {}, { _, profile -> saved = profile }) } }
        val personal = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val additional = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        assertEquals(personal.positionInRoot.y, additional.positionInRoot.y, 1f)
        assertTrue(additional.positionInRoot.x >= personal.positionInRoot.x + personal.size.width)
        compose.onNodeWithText("Metas e contato").performClick()
        compose.onNodeWithTag("input_profile_name").assertIsDisplayed()
        compose.onNodeWithTag("input_step_goal").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithTag("btn_save_profile").assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/profile_tablet_columns.png")
        compose.onNodeWithTag("input_profile_name").performTextReplacement("Nome corrigido")
        compose.onNodeWithTag("btn_save_profile").performClick()
        compose.runOnIdle { assertEquals(original.copy(fullName = "Nome corrigido"), saved) }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-mdpi")
    fun short_phone_with_extreme_type_keeps_actions_and_fields_reachable() {
        RuntimeEnvironment.setFontScale(2f)
        var saves = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MyApplicationTheme { UserProfileDialog(original, {}, { _, _ -> saves++ }) }
            }
        }
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithTag("btn_save_profile").assertIsDisplayed()
        compose.onNodeWithTag("input_weight").performScrollTo().performTextReplacement("abc")
        compose.onNodeWithTag("btn_save_profile").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        val save = compose.onNodeWithTag("btn_save_profile").fetchSemanticsNode()
        assertTrue(save.positionInRoot.y + save.size.height <= 320f)
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/profile_phone_landscape_extreme.png")
    }

    @Test fun large_text_restoration_keeps_sections_draft_and_canonical_identity() {
        RuntimeEnvironment.setFontScale(1.6f)
        var saved: UserProfileEntity? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                MyApplicationTheme { UserProfileDialog(original, {}, { _, profile -> saved = profile }) }
            }
        }
        compose.onNodeWithTag("input_profile_name").performTextReplacement("Nome preservado")
        compose.onNodeWithText("Metas e contato").performScrollTo().performClick()
        compose.onNodeWithTag("input_water_goal").performScrollTo().performTextReplacement("1800")
        compose.onNodeWithText("Identificação do cadastro").performScrollTo().performClick()
        compose.onNodeWithTag("input_patient_id").performScrollTo()
        val scrollBefore = compose.onNodeWithTag("profile_editor_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(scrollBefore > 0f)
        restoration.emulateSavedInstanceStateRestore()
        val scrollAfter = compose.onNodeWithTag("profile_editor_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(scrollBefore, scrollAfter, 1f)
        compose.onNodeWithText("Fechar: Metas e contato").assertExists()
        compose.onNodeWithText("Fechar: Identificação do cadastro").assertExists()
        val personal = compose.onNodeWithTag("patient_summary_first").fetchSemanticsNode()
        val additional = compose.onNodeWithTag("patient_summary_second").fetchSemanticsNode()
        assertEquals(personal.positionInRoot.x, additional.positionInRoot.x, 1f)
        assertTrue(additional.positionInRoot.y >= personal.positionInRoot.y + personal.size.height)
        compose.onNodeWithTag("input_profile_name").performScrollTo().assertTextContains("Nome preservado")
        compose.onNodeWithTag("input_water_goal").performScrollTo().assertTextContains("1800")
        compose.onNodeWithTag("input_patient_id").performScrollTo().assertTextContains(original.patientId)
        compose.onNodeWithTag("btn_save_profile").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(original.copy(fullName = "Nome preservado", targetWaterMl = 1800), saved) }
    }

    @Test fun opening_sections_does_not_edit_the_profile_or_require_discard() {
        var dismissals = 0
        compose.setContent {
            MyApplicationTheme { UserProfileDialog(original, { dismissals++ }, { _, _ -> error("Unexpected save") }) }
        }
        compose.onNodeWithText("Metas e contato").performScrollTo().performClick()
        compose.onNodeWithText("Identificação do cadastro").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("profile_discard_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
