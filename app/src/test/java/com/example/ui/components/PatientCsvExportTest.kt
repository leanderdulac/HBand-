package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.local.HBandSensorMetricEntity
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientCsvExportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preview_is_limited_but_explicit_copy_keeps_all_original_records() {
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "untouched"))
        var notices = 0
        val records = (1..7).map { index ->
            HBandSensorMetricEntity(
                id = index.toLong(), deviceId = "watch-fixture-$index", timestamp = "2026-09-15T12:00:00Z",
                timestampMillis = 1789473600000L, heartRate = 72, systolicBp = 0, diastolicBp = 0,
                spO2 = 0, temperatureCelsius = 0f, steps = index, calories = 0f, distanceMeters = 0f,
                hrvScore = 0, deepSleepMinutes = 0, lightSleepMinutes = 0, awakeMinutes = 0,
            )
        }
        compose.setContent { MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { CsvExportCard(records, { notices++ }) }
        } }
        compose.onNodeWithTag("toggle_csv_preview").assertDoesNotExist()
        compose.onNodeWithText("Arquivo para suporte").performClick()
        compose.onNodeWithTag("toggle_csv_preview").performScrollTo().performClick()
        compose.onNodeWithTag("csv_preview").assertTextContains("watch-fixture-5", substring = true)
            .assert(!hasText("watch-fixture-6", substring = true))
        compose.runOnIdle {
            assertEquals("untouched", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals(0, notices)
        }
        compose.onNodeWithTag("copy_csv_button").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5000) { clipboard.primaryClip?.getItemAt(0)?.text?.contains("watch-fixture-7") == true }
        compose.runOnIdle {
            val csv = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertEquals(8, csv.lineSequence().filter { it.isNotEmpty() }.count())
            assertTrue(csv.startsWith("ID,Device ID,Timestamp,Timestamp_ms,"))
            assertEquals(1, notices)
            assertTrue(clipboard.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
    }
}
