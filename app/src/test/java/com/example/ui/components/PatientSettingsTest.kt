package com.example.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun daily_settings_do_not_expose_test_actions_or_a_fabricated_profile() {
        content()
        compose.onNodeWithText("Alex Rivera").assertDoesNotExist()
        compose.onNodeWithTag("reset_all_data_button").assertDoesNotExist()
        compose.onNodeWithTag("backup_to_firestore_button").assertDoesNotExist()
        compose.onNodeWithText("Perfil indisponível no momento").assertIsDisplayed()
    }

    @Test fun development_settings_cannot_start_cloud_copy_or_restore() {
        cloudUnavailableLayout(1f)
    }

    @Test fun development_settings_do_not_expose_fabricated_heart_rate_alerts() {
        content()
        compose.onNodeWithText("Ferramentas de desenvolvimento").performScrollTo().performClick()
        compose.onNodeWithText("Testar avisos").assertDoesNotExist()
        compose.onNodeWithTag("test_high_hr_alert_button").assertDoesNotExist()
        compose.onNodeWithTag("test_low_hr_alert_button").assertDoesNotExist()
    }

    @Test fun cloud_guidance_on_narrow_phone_with_large_type_is_readable() {
        cloudUnavailableLayout(2f)
    }

    @Test @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun cloud_guidance_on_tablet_with_large_type_is_readable() {
        cloudUnavailableLayout(1.6f)
    }

    private fun cloudUnavailableLayout(fontScale: Float) {
        content(fontScale = fontScale)
        compose.onNodeWithText("Ferramentas de desenvolvimento").performScrollTo().performClick()
        compose.onNodeWithText("Cópia dos dados para suporte").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_backup_unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("backup_to_firestore_button").assertDoesNotExist()
        compose.onNodeWithTag("restore_from_firestore_button").assertDoesNotExist()
        compose.onNodeWithTag("firestore_sync_status_badge").assertDoesNotExist()
        compose.onAllNodes(hasAnyAncestor(hasTestTag("cloud_backup_unavailable")) and hasClickAction()).assertCountEquals(0)
        for (label in listOf("Recuperação em nuvem indisponível",
            com.example.data.remote.FirestoreBackupManager.UNAVAILABLE_MESSAGE)) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Cloud guidance must wrap without clipping", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/cloud_unavailable_$fontScale.png")
    }

    @Test fun opening_cleanup_and_cancelling_never_resets_patient_records() {
        var resets = 0
        content { resets++ }
        compose.onNodeWithText("Ferramentas de desenvolvimento").performScrollTo().performClick()
        compose.onNodeWithText("Limpeza para testes").performScrollTo().performClick()
        compose.onNodeWithTag("reset_all_data_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Zerar todos os dados locais?").assertDoesNotExist()
    }

    @Test fun reconnect_description_and_switch_share_one_accessible_action() {
        val changes = mutableListOf<Boolean>()
        content(reconnect = { changes += it })
        val control = compose.onNodeWithTag("auto_reconnect_switch")
        control.assertIsOn().assertHeightIsAtLeast(56.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag("auto_reconnect_switch")) and hasClickAction(),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        compose.onNodeWithText(
            "Tentar conectar novamente quando o relógio perder a conexão.",
            useUnmergedTree = true,
        ).performTouchInput { click() }
        control.assertIsOff()
        control.performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf(false, true), changes) }
    }

    @Test fun narrow_phone_with_large_type_keeps_reconnect_text_and_control_readable() {
        reconnectLayout(fontScale = 2f, screenshot = "settings_reconnect_phone_large")
    }

    @Test
    @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun tablet_with_large_type_keeps_reconnect_text_and_control_readable() {
        reconnectLayout(fontScale = 1.6f, screenshot = "settings_reconnect_tablet_large")
    }

    private fun reconnectLayout(fontScale: Float, screenshot: String) {
        content(fontScale = fontScale)
        val control = compose.onNodeWithTag("auto_reconnect_switch").performScrollTo()
        control.assertIsDisplayed().assertIsOn().assertHeightIsAtLeast(56.dp)
        val bounds = control.fetchSemanticsNode().boundsInRoot
        for (label in listOf("Reconectar relógio", "Tentar conectar novamente quando o relógio perder a conexão.")) {
            val text = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.contains(textBounds.topLeft) && bounds.contains(textBounds.bottomRight))
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$label: " + layouts.map { "size=${it.size}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}, lines=${it.lineCount}, lastBottom=${it.getLineBottom(it.lineCount - 1)}" },
                layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/$screenshot.png")
    }

    private fun content(fontScale: Float = 1f, reconnect: (Boolean) -> Unit = {}, reset: () -> Unit = {}) {
        compose.setContent {
            val enabled = remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme {
                    SettingsTab(
                        autoReconnectBle = enabled.value,
                        onAutoReconnectChange = { enabled.value = it; reconnect(it) },
                        upperThreshold = 120, lowerThreshold = 50, alertsEnabled = false,
                        onUpperThresholdChange = {}, onLowerThresholdChange = {}, onAlertsEnabledChange = {},
                        onResetAllData = reset,
                    )
                }
            }
        }
    }
}
