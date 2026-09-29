package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
class PatientAutomaticSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unverified_output_is_separate_from_patient_information_and_does_not_trigger_requests() {
        var requests = 0
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeminiHealthInsightCard("Saída de teste não validada", false, { requests++ }, showInsight = false)
                }
            }
        }
        compose.onNodeWithText("Resumo automático em validação").assertExists()
        compose.onNodeWithTag("gemini_insight_text").assertDoesNotExist()
        compose.onNodeWithTag("refresh_gemini_insight_button").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Resumo para revisão técnica").performScrollTo().performClick()
        compose.onNodeWithTag("gemini_insight_text").assertTextEquals("Saída de teste não validada")
        compose.onNodeWithTag("refresh_gemini_insight_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun enabled_flag_shows_summary_with_review_label_and_manual_refresh() {
        var requests = 0
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeminiHealthInsightCard("Resumo sintético de teste", false, { requests++ }, showInsight = true)
                }
            }
        }
        compose.onNodeWithText("Resumo automático em validação").assertDoesNotExist()
        compose.onNodeWithTag("ai_insight_review_label").assertExists()
        compose.onNodeWithText("Não é diagnóstico", substring = true).assertExists()
        compose.onNodeWithTag("gemini_insight_text").assertTextEquals("Resumo sintético de teste")
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("refresh_gemini_insight_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }
}
