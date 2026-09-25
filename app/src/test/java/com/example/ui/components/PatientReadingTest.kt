package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.BloodPressure
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientReadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missing_telemetry_has_no_invented_reading() {
        compose.setContent { MyApplicationTheme { TelemetryGauges(null) } }
        compose.onNodeWithText("Ainda não há uma leitura disponível.").assertIsDisplayed()
        compose.onNodeWithTag("gauge_heart_rate").assertDoesNotExist()
    }

    @Test fun demo_origin_remains_visible_and_missing_oxygen_is_not_called_normal() {
        compose.setContent {
            MyApplicationTheme {
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { TelemetryGauges(reading()) }
                }
            }
        }
        compose.onNodeWithTag("telemetry_demo_label").assertIsDisplayed()
        compose.onNode(hasText("Batimentos por minuto") and hasText("72 bpm")).assertIsDisplayed()
        compose.onNodeWithTag("gauge_heart_rate", useUnmergedTree = true).assertTextEquals("72 bpm")
        compose.onNodeWithTag("telemetry_details_button").performClick()
        compose.onNodeWithTag("gauge_spo2").performScrollTo()
        compose.onNode(hasText("Oxigênio no sangue") and hasText("Sem medição disponível")).assertIsDisplayed()
        compose.onNodeWithText("Oxigenação Normal").assertDoesNotExist()
        compose.onNodeWithText("Oxigenação Baixa").assertDoesNotExist()
        compose.onAllNodesWithText("Sem medição disponível").assertCountEquals(7)
        compose.onNodeWithText("Leitura de hardware ao vivo", substring = true).assertDoesNotExist()
    }

    @Test fun active_measurement_cannot_be_collapsed_and_stop_remains_reachable() {
        var stopped = 0
        compose.setContent {
            MyApplicationTheme {
                PatientSection("Medições do relógio", forceExpanded = true) {
                    Button(onClick = { stopped++ }) { Text("Parar medição") }
                }
            }
        }
        compose.onNodeWithText("Medições do relógio — em andamento").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Aberta durante a atividade em andamento"))
        compose.onNodeWithText("Parar medição").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, stopped) }
    }

    @Test fun section_exposes_heading_and_updates_open_state() {
        compose.setContent {
            MyApplicationTheme { PatientSection("Mais informações") { Text("Conteúdo da seção") } }
        }
        compose.onNodeWithText("Mais informações")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Fechada"))
            .performClick()
        compose.onNodeWithText("Fechar: Mais informações")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Aberta"))
            .performClick()
        compose.onNodeWithText("Conteúdo da seção").assertDoesNotExist()
    }

    @Test fun long_section_heading_wraps_at_large_font_and_remains_one_action() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MyApplicationTheme {
                    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        PatientSection("Identificação do cadastro") { Text("Detalhes do cadastro") }
                    }
                }
            }
        }
        for (label in listOf("Identificação do cadastro", "Fechar: Identificação do cadastro")) {
            val heading = compose.onNodeWithText(label)
            heading.assertIsDisplayed().assertHeightIsAtLeast(56.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertFalse(layouts.single().hasVisualOverflow)
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            heading.performClick()
        }
        compose.onNodeWithText("Detalhes do cadastro").assertDoesNotExist()
    }

    @Test fun expanded_section_survives_restoration_and_can_be_closed() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MyApplicationTheme { PatientSection("Ver registros da fila") { Text("Lista de teste") } }
        }
        compose.onNodeWithText("Ver registros da fila").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Lista de teste").assertIsDisplayed()
        compose.onNodeWithText("Fechar: Ver registros da fila")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Aberta"))
            .performClick()
        compose.onNodeWithText("Lista de teste").assertDoesNotExist()
    }

    private fun reading() = HBandTelemetry(
        timestamp = "2026-09-01T12:00:00Z", heartRate = 72, bloodPressure = BloodPressure(),
        spO2 = 0, temperatureCelsius = 0f, steps = 0, calories = 0f,
        distanceMeters = 0f, hrvScore = 0, sleepSummary = SleepSummary(), isRealSensorData = false,
    )
}
