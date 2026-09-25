package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.hband.DetectSessionUiState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientMeasurementStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun active_measurement_exposes_stop_and_prevents_another_start() {
        var stopped = 0
        content(DetectSessionUiState(supported = true, running = true), stop = { stopped++ })
        compose.onNodeWithTag("ecg_start").assertDoesNotExist()
        compose.onNodeWithTag("ecg_read").assertDoesNotExist()
        compose.onNodeWithTag("ecg_stop").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, stopped) }
    }

    @Test fun lost_connection_does_not_claim_the_measurement_was_stopped() {
        content(DetectSessionUiState(supported = true, running = true), connected = false)
        compose.onNodeWithTag("ecg_stop").assertIsNotEnabled()
        compose.onNodeWithTag("ecg_status").assertTextContains("A conexão não está disponível", substring = true)
    }

    @Test fun raw_error_is_available_for_support_without_becoming_the_patient_message() {
        content(DetectSessionUiState(supported = true, lastError = "INTERNAL_ERROR_FIXTURE"))
        compose.onNodeWithText("INTERNAL_ERROR_FIXTURE").assertDoesNotExist()
        compose.onNodeWithTag("ecg_status").assertTextContains("informou uma falha", substring = true)
        compose.onNodeWithText("Detalhes para suporte: ECG").performScrollTo().performClick()
        compose.onNodeWithText("INTERNAL_ERROR_FIXTURE").assertExists()
    }

    @Test fun available_result_is_preserved_and_only_explicit_action_requests_a_new_reading() {
        var starts = 0
        content(DetectSessionUiState(supported = true, lastSummary = "ECG • 70 bpm"), start = { starts++ })
        compose.onNodeWithText("Última informação recebida: ECG • 70 bpm").assertExists()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithTag("ecg_start").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    private fun content(state: DetectSessionUiState, connected: Boolean = true, start: () -> Unit = {}, stop: () -> Unit = {}) {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DetectActionBlock("ECG", state, connected, "Iniciar ECG", "Parar ECG", "Ler ECG gravado", "ecg", start, stop, {})
                }
            }
        }
    }
}

class PatientEcgDisplayTest {
    @Test fun constant_samples_remain_a_flat_trace_without_a_zero_denominator() {
        assertEquals(listOf(0.5f, 0.5f), normalizedEcgWaveform(listOf(Int.MAX_VALUE, Int.MAX_VALUE)))
        assertTrue(normalizedEcgWaveform(emptyList()).isEmpty())
    }

    @Test fun large_amplitude_does_not_overflow_or_drop_samples() {
        val plotted = normalizedEcgWaveform(listOf(Int.MIN_VALUE, 0, Int.MAX_VALUE))
        assertEquals(3, plotted.size)
        assertEquals(0f, plotted.first(), 0f)
        assertEquals(0.5f, plotted[1], 0.00001f)
        assertEquals(1f, plotted.last(), 0f)
    }
}
