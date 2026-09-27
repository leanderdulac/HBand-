package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.local.UserProfileEntity
import com.example.data.model.HBandDevice
import com.example.data.hband.DetectSessionUiState
import com.example.util.ProgressImageGenerator
import com.example.util.ShareProgressData
import com.example.util.weeklyShareSummary
import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Component captures only. These do not claim a full-app, physical-watch or patient test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientVisualReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun header_large_text() = capture("header_large_text", 1.6f) {
        HomeWelcomeHeader("Maria de Oliveira — exemplo", "patient-fixture", {})
    }

    @Test fun navigation_large_text() = capture("navigation_large_text", 1.6f) {
        PatientNavigationBar(0, 5, {})
    }

    @Test fun home_connection_action_is_visible_with_large_text() {
        capture("home_connection_large_text", 1.6f) {
            Scaffold(bottomBar = { PatientNavigationBar(0, 0, {}) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    HomeWelcomeHeader("Maria de Oliveira — exemplo", "patient-fixture", {})
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        DeviceControlCard(null, true, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("connect_watch_button").assertIsDisplayed()
    }

    @Test fun profile_large_text() = capture("profile_large_text", 1.6f) {
        UserProfileDialog(UserProfileEntity(fullName = "Pessoa de teste", patientId = "patient-fixture"), {}, {})
    }

    @Test fun unsaved_profile_keeps_both_choices_readable_with_large_text() {
        capture("profile_before_discard_large_text", 1.6f) {
            UserProfileDialog(UserProfileEntity(fullName = "Pessoa de teste", patientId = "patient-fixture"), {}, {})
        }
        compose.onNodeWithTag("input_profile_name").performTextReplacement("Nome corrigido")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("profile_continue_editing").assertIsDisplayed()
        compose.onNodeWithTag("profile_discard_changes").assertIsDisplayed()
        // Multiple Dialog windows are drawn in the wrong order by this Robolectric
        // capture stack. Check the actual flow above; inspect the same component
        // separately below, without claiming a full-window integration capture.
    }

    @Test fun profile_discard_confirmation_component_large_text() {
        capture("profile_discard_component_large_text", 1.6f) {
            ProfileDiscardConfirmation({}, {})
        }
        compose.onNodeWithTag("profile_continue_editing").assertIsDisplayed()
        compose.onNodeWithTag("profile_discard_changes").assertIsDisplayed()
    }

    @Test fun missing_profile_guidance_large_text() = capture("profile_missing_large_text", 1.6f) {
        UserProfileDialog(null, {}, {})
    }

    @Test fun queue_empty() = capture("queue_empty") {
        Box(Modifier.padding(16.dp)) {
            QueueInspector(0, 0, 0, emptyList(), emptyList(), false, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test fun hydration_large_text() = capture("hydration_large_text", 1.6f) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            HydrationCard(250, 2000, emptyList(), {}, {})
        }
    }

    @Test fun sleep_empty_large_text() = capture("sleep_empty_large_text", 1.6f) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SleepAnalysisCard(emptyList())
        }
    }

    @Test fun breathing_large_text() = capture("breathing_large_text", 1.6f) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            BreathingExerciseCard(120, { _, _ -> })
        }
    }

    @Test fun low_battery_large_text() = capture("low_battery_large_text", 1.6f) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            LowBatteryWarningCard(HBandDevice(name = "Relógio de teste", isConnected = false, batteryLevel = 18))
        }
    }

    @Test fun watch_initial() = capture("watch_initial") {
        Box(Modifier.padding(16.dp)) { PatientWatchContent(emptyList(), null, false, null, {}, {}, {}, {}, {}) }
    }

    @Test fun watch_search_is_visible_with_navigation_and_large_text() {
        capture("watch_search_large_text", 1.6f) {
            Scaffold(bottomBar = { PatientNavigationBar(2, 0, {}) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    HomeWelcomeHeader("", "patient-fixture", {}, showGreeting = false)
                    Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        PatientWatchContent(emptyList(), null, false, null, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("scan_ble_button").assertIsDisplayed()
    }

    @Test fun active_stop_is_visible_before_a_long_watch_summary() {
        capture("measurement_stop_large_text", 1.6f) {
            Column(Modifier.height(360.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                DetectActionBlock(
                    "ECG", DetectSessionUiState(supported = true, running = true, progress = 25,
                        lastSummary = "Informação de teste longa. ".repeat(40)),
                    true, "Iniciar ECG", "Parar ECG", "Ler ECG gravado", "ecg", {}, {}, {},
                )
            }
        }
        compose.onNodeWithTag("ecg_stop").assertIsDisplayed()
    }

    @Test fun share_dialog_close_stays_visible_after_scrolling_with_large_text() {
        var dismissed = 0
        capture("share_dialog_large_text", 1.6f) {
            val context = LocalContext.current
            val data = remember {
                val summary = weeklyShareSummary(emptyList(), 0, 0, 1789473600000L)
                ShareProgressData(
                    ProgressImageGenerator.renderWeeklyProgressBitmap(context, summary),
                    File(context.cacheDir, "unused-demo-share.png"), Uri.EMPTY, summary.text,
                )
            }
            ShareProgressDialog(data, { dismissed++ }, {})
        }
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed()
        compose.onNodeWithTag("copy_progress_summary_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dismiss_share_progress_button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun settings_closed() = capture("settings_closed") {
        Box(Modifier.padding(16.dp)) {
            SettingsTab(
                userProfile = UserProfileEntity(fullName = "Pessoa de teste"),
                upperThreshold = 120, lowerThreshold = 50, alertsEnabled = false,
                onUpperThresholdChange = {}, onLowerThresholdChange = {}, onAlertsEnabledChange = {},
                onTestHighAlert = {}, onTestLowAlert = {},
            )
        }
    }

    private fun capture(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme { Surface(Modifier.fillMaxSize()) { Box { content() } } }
            }
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/$name.png")
    }
}
