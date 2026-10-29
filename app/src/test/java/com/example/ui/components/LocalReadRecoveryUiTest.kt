package com.example.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.UserProfileEntity
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = Application::class)
class LocalReadRecoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failed_history_does_not_render_empty_charts_and_retry_only_calls_the_reader() {
        var reads = 0
        compose.setContent { MyApplicationTheme {
            RechartsSensorDashboard(emptyList(), readFailed = true, onRetryRead = { reads++ })
        } }
        compose.onNodeWithText("Não foi possível ler o histórico.").assertExists()
        compose.onNodeWithText("Sem medições disponíveis").assertDoesNotExist()
        compose.onNodeWithTag("history_metric_menu").assertDoesNotExist()
        compose.onNodeWithTag("retry_local_read").performClick()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test fun failed_metrics_hide_both_exports_and_failed_diary_only_blocks_the_combined_card() {
        val metricsFailed = mutableStateOf(true)
        val diaryFailed = mutableStateOf(false)
        var reads = 0
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PatientShareRecords(emptyList(), 0, 0, metricsFailed.value, diaryFailed.value,
                    { reads++ }, { error("Unexpected export") }, { error("Unexpected write") })
            }
        } }
        compose.onNodeWithTag("share_progress_button").assertDoesNotExist()
        compose.onNodeWithTag("csv_export_card").assertDoesNotExist()
        compose.onNodeWithTag("retry_local_read").performClick()
        compose.runOnIdle { assertEquals(1, reads); metricsFailed.value = false; diaryFailed.value = true }
        compose.onNodeWithTag("share_progress_button").assertDoesNotExist()
        compose.onNodeWithTag("csv_export_card").assertExists()
        compose.runOnIdle { diaryFailed.value = false }
        compose.onNodeWithTag("share_progress_button").assertIsEnabled()
    }

    @Test fun water_read_failure_discards_delete_confirmation_and_never_resets_records() {
        val failed = mutableStateOf(false)
        var writes = 0
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HydrationCard(500, 2000, emptyList(), { writes++ }, { writes++ }, readFailed = failed.value)
            }
        } }
        compose.onNodeWithTag("reset_hydration_button").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_hydration_reset").assertExists()
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithTag("confirm_hydration_reset").assertDoesNotExist()
        compose.onNodeWithTag("hydration_current_ml_text").assertDoesNotExist()
        compose.runOnIdle { failed.value = false }
        compose.onNodeWithTag("confirm_hydration_reset").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, writes) }
    }

    @Test fun profile_draft_survives_read_failure_restoration_and_retry_without_saving() {
        val available = mutableStateOf(true)
        val failed = mutableStateOf(false)
        val row = UserProfileEntity(patientId = "synthetic", fullName = "Pessoa de teste")
        val saved = mutableListOf<UserProfileEntity>()
        var reads = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { MyApplicationTheme {
            UserProfileDialog(row, {}, { _, profile -> saved += profile },
                readAvailable = available.value, readFailed = failed.value,
                onRetryRead = { reads++; failed.value = false })
        } }
        compose.onNodeWithTag("input_profile_name").performScrollTo().performTextReplacement("Rascunho mantido")
        compose.runOnIdle { available.value = false; failed.value = true }
        compose.onNodeWithTag("btn_save_profile").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("input_profile_name").assertTextContains("Rascunho mantido").assertIsNotEnabled()
        compose.onNodeWithTag("retry_local_read").performScrollTo().performClick()
        compose.onNodeWithTag("btn_save_profile").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, reads); assertTrue(saved.isEmpty()); available.value = true }
        compose.onNodeWithTag("btn_save_profile").performClick()
        compose.runOnIdle { assertEquals(listOf(row.copy(fullName = "Rascunho mantido")), saved) }
    }

    @Test fun breathing_read_failure_keeps_the_counted_exercise_draft() {
        val failed = mutableStateOf(false)
        var saves = 0
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BreathingExerciseCard(60, { _, _ -> saves++ }, readFailed = failed.value)
            }
        } }
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5200)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("toggle_breathing_exercise_button").performScrollTo().performClick()
        val before = compose.onNodeWithTag("session_timer_text").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertNotEquals("Tempo neste exercício: 0\u00A0min 0\u00A0s", before)
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithTag("session_timer_text").assertTextEquals(before)
        compose.onNodeWithTag("total_breathing_duration_badge").assertTextEquals("Tempo salvo indisponível")
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun recovered_profile_does_not_submit_twice_before_recomposition() {
        var saves = 0
        compose.setContent { MyApplicationTheme {
            UserProfileDialog(UserProfileEntity(patientId = "synthetic"), {}, { _, _ -> saves++ }, readAvailable = true)
        } }
        compose.onNodeWithTag("btn_save_profile").performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
        compose.runOnIdle { assertEquals(1, saves) }
    }
}
