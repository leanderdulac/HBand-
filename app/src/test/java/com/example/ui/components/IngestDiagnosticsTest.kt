package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.data.ingest.IngestDiagnostics
import com.example.data.ingest.QueueAuthorization
import com.example.data.remote.IngestConfigurationStatus
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
class IngestDiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loading_is_not_reported_as_empty_queue() {
        content(IngestDiagnostics())
        compose.onNodeWithText("Carregando configuração e registros locais…").assertIsDisplayed()
        compose.onNodeWithText("Aguardando envio:", substring = true).assertDoesNotExist()
    }
    @Test fun phone_large_type_keeps_pause_and_counts_readable() {
        content(IngestDiagnostics(loaded = true, configuration = IngestConfigurationStatus(
            origin = "https://service.invalid", keyConfigured = true, usingSettingsOverride = true,
            configurationError = null), pending = 3, failed = 2, synced = 4, authorizationPaused = true), 2f)
        for (label in listOf("Destino: https://service.invalid", "Aguardando envio: 3. Com falha: 2.",
            "Concluídos no aplicativo: 4. Estado desconhecido: 0.", QueueAuthorization.PAUSED_MESSAGE,
            "Configuração e resposta de conexão não comprovam autorização, recebimento das leituras ou visibilidade pela equipe.")) {
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(label, layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        compose.onRoot().captureRoboImage(filePath = "build/patient-ui/ingest_diagnostics_phone_large.png")
    }
    private fun content(diagnostics: IngestDiagnostics, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { IngestDiagnosticsCard(diagnostics) }
                }
            }
        }
    }
}
