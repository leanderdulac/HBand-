package com.example.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.UserProfileEntity
import com.example.ui.ProfileSaveState
import com.example.ui.ProfileSaveStatus
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production dialog and Android saved-state harness with simulated receipts only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientProfileSaveUiTest {
    @get:Rule val compose = createComposeRule()
    private val original = UserProfileEntity(patientId = "synthetic-patient", fullName = "Pessoa de teste")
    private val profile = mutableStateOf<UserProfileEntity?>(original)
    private val open = mutableStateOf(true)
    private val receipt = mutableStateOf<ProfileSaveState?>(null)
    private val requests = mutableListOf<Pair<String, UserProfileEntity>>()
    @After fun resetFont() { RuntimeEnvironment.setFontScale(1f) }
    private fun content(font: Float = 1f): StateRestorationTester {
        RuntimeEnvironment.setFontScale(font)
        val restore = StateRestorationTester(compose)
        restore.setContent { MyApplicationTheme {
            if (open.value) UserProfileDialog(profile.value, { open.value = false }, { token, row ->
                requests += token to row
                receipt.value = ProfileSaveState(token, row.id, row.patientId, ProfileSaveStatus.SAVING)
            }, receipt.value)
        } }
        return restore
    }
    private fun submit() {
        compose.onNodeWithTag("input_profile_name").performScrollTo().performTextReplacement("Nome corrigido")
        compose.onNodeWithTag("btn_save_profile").performClick()
    }
    private fun result(status: ProfileSaveStatus) = compose.runOnIdle {
        val (token, row) = requests.last()
        receipt.value = ProfileSaveState(token, row.id, row.patientId, status)
    }
    @Test fun pending_write_keeps_edited_profile_open_and_blocks_actions() {
        content(); submit()
        compose.onNodeWithTag("btn_save_profile").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("input_profile_name").assertTextContains("Nome corrigido").assertIsNotEnabled()
        compose.onNodeWithTag("input_age").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(open.value)
            assertEquals(original.copy(fullName = "Nome corrigido"), requests.single().second)
        }
    }
    @Test fun room_emission_is_not_confirmation_and_only_matching_receipt_closes() {
        content(); submit()
        compose.runOnIdle { profile.value = requests.single().second }
        compose.onNodeWithText("Cancelar").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertTrue(open.value) }
        result(ProfileSaveStatus.SAVED)
        compose.onNodeWithTag("profile_editor").assertDoesNotExist()
        compose.runOnIdle { assertFalse(open.value); assertEquals(1, requests.size) }
    }
    @Test fun failure_preserves_draft_and_manual_retry_has_new_token() {
        content(); submit(); result(ProfileSaveStatus.UNCONFIRMED)
        compose.onNodeWithTag("input_profile_name").performScrollTo().assertTextContains("Nome corrigido").assertIsEnabled()
        compose.onNodeWithTag("input_profile_name").performTextReplacement("Nome revisto")
        compose.onNodeWithTag("btn_save_profile").performClick()
        compose.runOnIdle {
            assertTrue(open.value); assertEquals(2, requests.size)
            assertNotEquals(requests[0].first, requests[1].first)
            assertEquals(original.copy(fullName = "Nome revisto"), requests[1].second)
        }
        result(ProfileSaveStatus.SAVED)
        compose.runOnIdle { assertFalse(open.value) }
    }
    @Test fun restored_pending_attempt_waits_for_its_receipt_without_replaying() {
        val restore = content(); submit(); restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("input_profile_name").assertTextContains("Nome corrigido").assertIsNotEnabled()
        result(ProfileSaveStatus.SAVED)
        compose.runOnIdle { assertFalse(open.value); assertEquals(1, requests.size) }
    }
    @Test fun restored_attempt_without_receipt_is_uncertain_and_not_replayed() {
        val restore = content(); submit()
        compose.runOnIdle { receipt.value = null }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("profile_save_feedback").assertTextContains("Gravação não confirmada", substring = true)
        compose.onNodeWithTag("input_profile_name").performScrollTo().assertTextContains("Nome corrigido").assertIsEnabled()
        compose.runOnIdle { assertTrue(open.value); assertEquals(1, requests.size) }
    }
    @Test fun unrelated_token_profile_or_patient_receipt_cannot_close_editor() {
        content(); submit()
        val expected = receipt.value!!
        for (other in listOf(expected.copy(token = "other"), expected.copy(profileId = "other"), expected.copy(patientId = "other"))) {
            compose.runOnIdle { receipt.value = other.copy(status = ProfileSaveStatus.SAVED) }
            compose.runOnIdle { assertTrue(open.value) }
        }
        result(ProfileSaveStatus.UNCONFIRMED)
        compose.onNodeWithTag("input_profile_name").assertTextContains("Nome corrigido").assertIsEnabled()
    }
    @Test fun uncertain_exit_warns_about_already_saved_profile_even_if_flow_matches_draft() {
        content(); submit()
        compose.runOnIdle { profile.value = requests.single().second }
        result(ProfileSaveStatus.UNCONFIRMED)
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Fechar sem salvar novamente?").assertExists()
        compose.onNodeWithText("A gravação anterior não foi confirmada.", substring = true).assertExists()
        compose.onNodeWithTag("profile_continue_editing").performClick()
        compose.runOnIdle { assertTrue(open.value) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("profile_discard_changes").performClick()
        compose.runOnIdle { assertFalse(open.value); assertEquals(1, requests.size) }
    }
    @Test fun changing_canonical_identity_does_not_reuse_draft_or_receipt() {
        content(); submit(); result(ProfileSaveStatus.UNCONFIRMED)
        compose.runOnIdle { profile.value = original.copy(patientId = "another-patient", fullName = "Outro perfil") }
        compose.onNodeWithTag("input_profile_name").assertTextContains("Outro perfil")
        result(ProfileSaveStatus.SAVED)
        compose.runOnIdle { assertTrue(open.value); assertEquals(1, requests.size) }
    }
    @Test fun large_type_keeps_pending_and_uncertain_feedback_accessible() {
        content(2f); submit()
        compose.onNodeWithTag("profile_save_feedback").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/profile-save-pending-large.png")
        result(ProfileSaveStatus.UNCONFIRMED)
        compose.onNodeWithTag("profile_save_feedback").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/profile-save-uncertain-large.png")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onRoot().captureRoboImage(filePath = "build/profile-save-exit-large.png")
    }
}
