package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientHeartAlertSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun phone_large_text_label_toggles_only_alert_preference() {
        labelledAlertControl(fontScale = 2f, screenshot = "alerts_touch_phone_large")
    }

    @Test
    @Config(qualifiers = "w960dp-h600dp-mdpi")
    fun tablet_large_text_label_toggles_only_alert_preference() {
        labelledAlertControl(fontScale = 1.6f, screenshot = "alerts_touch_tablet_large")
    }

    private fun labelledAlertControl(fontScale: Float, screenshot: String) {
        val enabled = mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        var otherActions = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme {
                    SettingsTab(
                        upperThreshold = 120, lowerThreshold = 50, alertsEnabled = enabled.value,
                        onUpperThresholdChange = { otherActions++ }, onLowerThresholdChange = { otherActions++ },
                        onAlertsEnabledChange = { enabled.value = it; changes += it },
                        onTestHighAlert = { otherActions++ }, onTestLowAlert = { otherActions++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Avisos de batimentos").performScrollTo().performClick()
        val control = compose.onNodeWithTag("hr_alerts_toggle_switch").performScrollTo()
        control.assertIsOff().assertHeightIsAtLeast(56.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag("hr_alerts_toggle_switch")) and hasClickAction(),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        val label = compose.onNodeWithText("Ativar avisos neste aparelho", useUnmergedTree = true)
        label.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/$screenshot.png")
        label.performTouchInput { click() }
        control.assertIsOn()
        compose.onNodeWithTag("upper_threshold_display").assertTextEquals("120 bpm")
        compose.onNodeWithTag("lower_threshold_display").assertTextEquals("50 bpm")
        control.performClick().assertIsOff()
        compose.onNodeWithTag("preset_upper_90").assertIsNotEnabled()
        compose.onNodeWithTag("preset_lower_40").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf(true, false), changes)
            assertEquals(0, otherActions)
        }
    }

    @Test fun large_text_keeps_switch_and_existing_presets_usable_without_changing_limits() {
        RuntimeEnvironment.setFontScale(1.6f)
        val upper = mutableIntStateOf(120)
        val lower = mutableIntStateOf(50)
        val enabled = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                MyApplicationTheme { Surface(Modifier.fillMaxSize()) { Box {
                    SettingsTab(
                        upperThreshold = upper.intValue, lowerThreshold = lower.intValue, alertsEnabled = enabled.value,
                        onUpperThresholdChange = { upper.intValue = it }, onLowerThresholdChange = { lower.intValue = it },
                        onAlertsEnabledChange = { enabled.value = it }, onTestHighAlert = {}, onTestLowAlert = {},
                    )
                } } }
            }
        }
        compose.onNodeWithText("Avisos de batimentos").performScrollTo().performClick()
        compose.onNodeWithTag("hr_alerts_toggle_switch").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/alerts_switch_large_text.png")
        compose.onNodeWithTag("preset_upper_120").assertIsSelected()
        compose.onNodeWithTag("preset_upper_160").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(160, upper.intValue); assertEquals(50, lower.intValue) }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/alerts_upper_large_text.png")
        compose.onNodeWithTag("preset_lower_45").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(160, upper.intValue); assertEquals(45, lower.intValue) }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/alerts_lower_large_text.png")
        compose.onNodeWithTag("hr_alerts_toggle_switch").performScrollTo().performClick()
        compose.onNodeWithTag("preset_upper_90").assertIsNotEnabled()
        compose.onNodeWithTag("preset_lower_40").assertIsNotEnabled()
    }
}
