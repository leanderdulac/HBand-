package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.hband.*
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientWatchSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unprobed_watch_is_unknown_and_does_not_claim_missing_support() {
        content()
        compose.onAllNodesWithText("Ainda não foi verificado.").assertCountEquals(3)
        compose.onNodeWithText("Não disponível neste relógio.").assertDoesNotExist()
        compose.onNodeWithTag("auto_measure_switch").assertIsNotEnabled()
    }

    @Test fun stale_support_does_not_allow_changes_while_disconnected() {
        content(probed = true, supported = true)
        compose.onNodeWithTag("auto_measure_switch").assertIsNotEnabled()
        compose.onNodeWithTag("sync_history_button").assertIsNotEnabled()
    }

    @Test fun connected_switch_has_a_readable_name_and_keeps_existing_action() {
        var enabled = false
        content(probed = true, supported = true, connected = true, onAuto = { enabled = it })
        compose.onNodeWithContentDescription("Batimentos automáticos").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(enabled) }
    }

    @Test fun failed_empty_history_query_does_not_claim_the_watch_has_no_records() {
        content(connected = true, history = HistorySyncUiState(phase = "incomplete", lastError = "timeout Origin"))
        compose.onNodeWithTag("history_sync_status").performScrollTo()
            .assertTextContains("Isso não confirma ausência de dados no relógio.", substring = true)
        compose.onNodeWithTag("sync_history_button").assertIsEnabled()
    }

    @Test fun interrupted_history_keeps_received_counts_without_claiming_completion() {
        content(history = HistorySyncUiState(phase = "cancelado", originSamples = 12))
        compose.onNodeWithTag("history_sync_status").performScrollTo()
            .assertTextContains("interrompida", substring = true)
            .assertTextContains("12 registros gerais", substring = true)
        compose.onNodeWithText("Leitura do relógio concluída", substring = true).assertDoesNotExist()
    }

    @Test fun partial_history_explains_the_failure_even_when_some_records_were_received() {
        content(history = HistorySyncUiState(phase = "incomplete", originSamples = 12, lastError = "timeout sono"))
        compose.onNodeWithTag("history_sync_status").performScrollTo()
            .assertTextContains("Não foi possível concluir", substring = true)
            .assertTextContains("12 registros gerais", substring = true)
    }

    private fun content(probed: Boolean = false, supported: Boolean = false, connected: Boolean = false,
                        history: HistorySyncUiState = HistorySyncUiState(), onAuto: (Boolean) -> Unit = {}) {
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BandSdkSettingsCard(
                        capabilities = DeviceCapabilities(probed = probed),
                        autoMeasure = AutoMeasureUiState(supported = supported),
                        wearDetect = WearDetectUiState(), historySync = history,
                        hardwareConnected = connected, actionsEnabled = connected,
                        onAutoMeasureChange = onAuto, onSpo2AutoChange = {}, onWearDetectChange = {}, onSyncHistory = {},
                    )
                }
            }
        }
    }
}
