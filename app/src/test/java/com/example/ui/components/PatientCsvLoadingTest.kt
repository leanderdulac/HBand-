package com.example.ui.components

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.shareMetricsState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = Application::class)
class PatientCsvLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pending_query_is_not_presented_as_zero_records() {
        pendingCsv()
        compose.onNodeWithText("0 registros disponíveis neste celular.").assertDoesNotExist()
        compose.onNodeWithText("Carregando registros…").assertExists()
    }

    @Test fun pending_query_cannot_open_a_header_only_preview() {
        pendingCsv()
        compose.onNodeWithTag("toggle_csv_preview").assertIsNotEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("csv_preview").assertDoesNotExist()
    }

    @Test fun confirmed_empty_can_show_header_but_cannot_copy_or_share_even_via_callback() {
        val clipboard = clipboardWithSentinel()
        var notices = 0
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { CsvExportCard(emptyList(), { notices++ }) }
        } }
        compose.onNodeWithText("Arquivo para suporte").performClick()
        compose.onNodeWithText("0 registros disponíveis neste celular.").assertExists()
        compose.onNodeWithText("Carregando registros…").assertDoesNotExist()
        for (tag in listOf("copy_csv_button", "share_csv_button")) {
            compose.onNodeWithTag(tag).assertIsNotEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        }
        compose.onNodeWithTag("toggle_csv_preview").performScrollTo().performClick()
        compose.onNodeWithTag("csv_preview").assertTextContains("ID,Device ID,Timestamp,", substring = true)
        compose.runOnIdle {
            assertEquals("untouched", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals(0, notices)
        }
    }

    @Test fun open_preview_hides_while_reloading_and_uses_new_response_without_implicit_copy() {
        val clipboard = clipboardWithSentinel()
        val metrics = mutableStateOf<List<HBandSensorMetricEntity>?>(listOf(row(91)))
        var notices = 0
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { CsvExportCard(metrics.value, { notices++ }) }
        } }
        compose.onNodeWithText("Arquivo para suporte").performClick()
        compose.onNodeWithTag("toggle_csv_preview").performScrollTo().performClick()
        compose.onNodeWithTag("csv_preview").assertTextContains("synthetic-91", substring = true)
        compose.runOnIdle { metrics.value = null }
        compose.onNodeWithText("Carregando registros…").assertExists()
        compose.onNodeWithText("0 registros disponíveis neste celular.").assertDoesNotExist()
        compose.onNodeWithTag("csv_preview").assertDoesNotExist()
        for (tag in listOf("copy_csv_button", "share_csv_button", "toggle_csv_preview")) {
            compose.onNodeWithTag(tag).assertIsNotEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        }
        compose.runOnIdle {
            assertEquals("untouched", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals(0, notices)
            metrics.value = listOf(row(92))
        }
        compose.onNodeWithTag("csv_preview").assertTextContains("synthetic-92", substring = true)
            .assert(!hasText("synthetic-91", substring = true))
        compose.onNodeWithTag("copy_csv_button").assertIsEnabled()
        compose.onNodeWithTag("share_csv_button").assertIsEnabled()
        compose.runOnIdle { assertEquals("untouched", clipboard.primaryClip!!.getItemAt(0).text.toString()) }
    }

    private fun clipboardWithSentinel(): ClipboardManager =
        (RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).also {
            it.setPrimaryClip(ClipData.newPlainText("test", "untouched"))
        }

    private fun row(id: Long) = HBandSensorMetricEntity(
        id = id, deviceId = "synthetic-$id", timestamp = "saved", timestampMillis = 1234,
        heartRate = 72, systolicBp = 0, diastolicBp = 0, spO2 = 97, temperatureCelsius = 0f,
        steps = 120, calories = 5f, distanceMeters = 3f, hrvScore = 0,
        deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
    )

    private fun pendingCsv() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scopes += scope
        val source = flow<List<HBandSensorMetricEntity>> { awaitCancellation() }
        // Production loaded-metrics policy, shared by the card and CSV; no operational startup.
        val state = source.shareMetricsState(scope)
        compose.setContent { MyApplicationTheme {
            val metrics = state.collectAsState().value
            Column(Modifier.verticalScroll(rememberScrollState())) { CsvExportCard(metrics, {}) }
        } }
        compose.onNodeWithText("Arquivo para suporte").performClick()
    }

    private val scopes = mutableListOf<CoroutineScope>()
    @org.junit.After fun closeScopes() { scopes.forEach { it.cancel() } }
}
