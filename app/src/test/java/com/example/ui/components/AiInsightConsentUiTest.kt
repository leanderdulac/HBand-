package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi", sdk = [36], application = android.app.Application::class)
class AiInsightConsentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabled_card_explains_and_opens_nothing_until_enable_is_tapped() {
        var requests = 0
        var consentRequests = 0
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    AiInsightHighlightCard(
                        insightText = "NÃO DEVE APARECER",
                        isLoading = false,
                        onRefreshInsight = { requests++ },
                        consented = false,
                        onRequestConsent = { consentRequests++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Resumo com IA").assertExists()
        compose.onNodeWithTag("ai_insight_consent_needed").assertTextContains("opcional", substring = true)
        compose.onNodeWithTag("gemini_insight_text").assertDoesNotExist()
        compose.onNodeWithTag("refresh_gemini_insight_button").assertDoesNotExist()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/ai_insight_consent_disabled_card.png")
        compose.onNodeWithTag("ai_insight_enable_button").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, consentRequests)
            assertEquals(0, requests)
        }
    }

    @Test fun consent_dialog_lists_data_destination_optionality_and_privacy_links() {
        var authorized = 0
        var dismissed = 0
        compose.setContent {
            MyApplicationTheme {
                AiInsightConsentDialog(onAuthorize = { authorized++ }, onNotNow = { dismissed++ })
            }
        }
        compose.onNodeWithTag("ai_insight_consent_dialog").assertExists()
        compose.onNodeWithText(AiInsightConsentCopy.DIALOG_INTRO).assertExists()
        compose.onNodeWithText(AiInsightConsentCopy.DATA_SENT).assertExists()
        for (item in AiInsightConsentCopy.DATA_ITEMS) {
            compose.onNodeWithText("• $item").assertExists()
        }
        compose.onNodeWithText(AiInsightConsentCopy.PURPOSE, substring = true).assertExists()
        compose.onNodeWithText("opcional", substring = true).assertExists()
        compose.onNodeWithText("diagnóstico", substring = true).assertExists()
        compose.onNodeWithText("Ajustes", substring = true).assertExists()
        compose.onNodeWithTag("ai_insight_privacy_link").assertExists()
        compose.onNodeWithTag("ai_insight_deletion_link").assertExists()
        compose.onNodeWithTag("btn_ai_insight_not_now").assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/ai_insight_consent_dialog.png")
        compose.onNodeWithTag("btn_ai_insight_authorize").performClick()
        compose.runOnIdle {
            assertEquals(1, authorized)
            assertEquals(0, dismissed)
        }
    }

    @Test fun agora_nao_does_not_grant_consent() {
        var authorized = 0
        var dismissed = 0
        compose.setContent {
            MyApplicationTheme {
                AiInsightConsentDialog(onAuthorize = { authorized++ }, onNotNow = { dismissed++ })
            }
        }
        compose.onNodeWithTag("btn_ai_insight_not_now").performClick()
        compose.runOnIdle {
            assertEquals(0, authorized)
            assertEquals(1, dismissed)
        }
    }

    @Test fun settings_toggle_off_is_the_default_and_turning_on_asks_before_granting() {
        val consented = mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            MyApplicationTheme {
                SettingsTab(
                    upperThreshold = 120, lowerThreshold = 50, alertsEnabled = false,
                    onUpperThresholdChange = {}, onLowerThresholdChange = {}, onAlertsEnabledChange = {},
                    aiInsightFeatureEnabled = true,
                    aiInsightConsented = consented.value,
                    onAiInsightConsentChange = { consented.value = it; changes += it },
                )
            }
        }
        val control = compose.onNodeWithTag("ai_insight_settings_switch").performScrollTo()
        control.assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNodeWithText("só se você autorizar", substring = true, useUnmergedTree = true)
            .assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/ai_insight_settings_toggle_off.png")
        control.performClick()
        compose.onNodeWithTag("ai_insight_consent_dialog").assertExists()
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        compose.onNodeWithTag("btn_ai_insight_authorize").performClick()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
        compose.onNodeWithTag("ai_insight_consent_dialog").assertDoesNotExist()
    }

    @Test fun settings_toggle_on_withdraws_immediately_without_a_dialog() {
        val consented = mutableStateOf(true)
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            MyApplicationTheme {
                SettingsTab(
                    upperThreshold = 120, lowerThreshold = 50, alertsEnabled = false,
                    onUpperThresholdChange = {}, onLowerThresholdChange = {}, onAlertsEnabledChange = {},
                    aiInsightFeatureEnabled = true,
                    aiInsightConsented = consented.value,
                    onAiInsightConsentChange = { consented.value = it; changes += it },
                )
            }
        }
        val control = compose.onNodeWithTag("ai_insight_settings_switch").performScrollTo()
        control.assertIsOn()
        compose.onNodeWithText("Autorizado", substring = true, useUnmergedTree = true)
            .assertExists()
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/ai_insight_settings_toggle_on.png")
        control.performClick()
        compose.onNodeWithTag("ai_insight_consent_dialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(false), changes)
            assertFalse(consented.value)
        }
    }
}
