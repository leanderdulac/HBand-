package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xhdpi", sdk = [36], application = android.app.Application::class)
class AiInsightHighlightCardTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        text: String = "",
        loading: Boolean = false,
        generatedAt: Long? = null,
        failed: Boolean = false,
        onRefresh: () -> Unit = {},
    ) = compose.setContent {
        MyApplicationTheme {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                AiInsightHighlightCard(text, loading, onRefresh, generatedAtMillis = generatedAt, failed = failed)
            }
        }
    }

    @Test fun content_state_shows_title_icon_text_timestamp_label_and_refresh() {
        var requests = 0
        val generatedAt = 1_790_553_600_000L + (21 * 60 + 15) * 60_000L // 21:15 UTC
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            show("Nos últimos 7 dias, sua frequência cardíaca média foi de 72 BPM.", generatedAt = generatedAt, onRefresh = { requests++ })
            compose.onNodeWithText("Resumo com IA").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithContentDescription("Inteligência artificial").assertExists()
            compose.onNodeWithTag("gemini_insight_text").assertTextContains("72 BPM", substring = true)
            compose.onNodeWithTag("ai_insight_generated_at").assertTextEquals("Gerado às 21:15")
            compose.onNodeWithTag("ai_insight_review_label").assertTextContains("Não é diagnóstico nem orientação médica", substring = true)
            compose.onNodeWithTag("ai_insight_loading").assertDoesNotExist()
            compose.onNodeWithTag("refresh_gemini_insight_button").assertIsEnabled().performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, requests) }
            compose.onRoot().captureRoboImage(filePath = "build/patient-ui/ai_insight_highlight_content.png")
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun loading_state_shows_progress_and_disables_refresh() {
        show(loading = true)
        compose.onNodeWithTag("ai_insight_loading").assertExists()
        compose.onNodeWithText("Gerando o resumo…").assertExists()
        compose.onNodeWithTag("refresh_gemini_insight_button").assertIsNotEnabled()
        compose.onNodeWithTag("gemini_insight_text").assertDoesNotExist()
        compose.onNodeWithTag("ai_insight_review_label").assertExists()
    }

    @Test fun empty_state_is_friendly_and_has_no_timestamp() {
        show()
        compose.onNodeWithTag("ai_insight_empty").assertTextContains("resumo dos últimos 7 dias aparece aqui", substring = true)
        compose.onNodeWithTag("ai_insight_generated_at").assertTextEquals("Últimos 7 dias")
        compose.onNodeWithTag("refresh_gemini_insight_button").assertIsEnabled()
    }

    @Test fun error_state_is_friendly_and_allows_retry() {
        var requests = 0
        show("Não foi possível gerar o texto para revisão. Tente novamente.", failed = true, onRefresh = { requests++ })
        compose.onNodeWithTag("ai_insight_error").assertTextContains("Atualizar resumo", substring = true)
        compose.onNodeWithTag("gemini_insight_text").assertDoesNotExist()
        compose.onNodeWithTag("refresh_gemini_insight_button").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun generated_label_formats_local_time_and_is_null_without_generation() {
        assertNull(insightGeneratedLabel(null))
        assertEquals("Gerado às 18:15", insightGeneratedLabel(1_790_553_600_000L + (21 * 60 + 15) * 60_000L,
            TimeZone.getTimeZone("America/Sao_Paulo")))
    }

    @Test fun text_colors_meet_wcag_aa_on_the_whole_gradient() {
        lateinit var scheme: ColorScheme
        compose.setContent { MyApplicationTheme { scheme = MaterialTheme.colorScheme } }
        compose.runOnIdle {
            val top = scheme.primaryContainer
            val bottom = lerp(scheme.primaryContainer, scheme.surface, 0.45f)
            for (background in listOf(top, bottom)) {
                assertAa(scheme.onPrimaryContainer, background)
                assertAa(scheme.onSurfaceVariant, background)
                assertTrue(contrast(scheme.primary, background) >= 3.0) // icon: non-text 3:1
            }
            assertAa(scheme.onPrimary, scheme.primary) // "Atualizar resumo" button
            assertTrue(contrast(scheme.primary, scheme.surface) >= 3.0) // icon on its white badge
        }
    }

    private fun assertAa(foreground: Color, background: Color) {
        val ratio = contrast(foreground.compositeOver(background), background)
        assertTrue("contrast $ratio < 4.5 for $foreground on $background", ratio >= 4.5)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
