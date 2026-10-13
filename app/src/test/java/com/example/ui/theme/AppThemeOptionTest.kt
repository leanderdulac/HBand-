package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeOptionTest {
    @Test
    fun default_storage_is_claro() {
        assertEquals(AppThemeOption.LIGHT, AppThemeOption.fromStorage(null))
        assertEquals(AppThemeOption.LIGHT, AppThemeOption.fromStorage(""))
        assertEquals(AppThemeOption.LIGHT, AppThemeOption.fromStorage("unknown"))
    }

    @Test
    fun known_keys_round_trip() {
        AppThemeOption.entries.forEach { option ->
            assertEquals(option, AppThemeOption.fromStorage(option.storageKey))
        }
    }

    @Test
    fun follow_system_uses_azul_dark_when_night_and_claro_when_day() {
        assertEquals(ResolvedAppTheme.DARK_AZUL, resolveAppTheme(AppThemeOption.FOLLOW_SYSTEM, systemDark = true))
        assertEquals(ResolvedAppTheme.LIGHT_AZUL, resolveAppTheme(AppThemeOption.FOLLOW_SYSTEM, systemDark = false))
    }

    @Test
    fun explicit_options_ignore_system() {
        assertEquals(ResolvedAppTheme.LIGHT_AZUL, resolveAppTheme(AppThemeOption.LIGHT, systemDark = true))
        assertEquals(ResolvedAppTheme.DARK_AZUL, resolveAppTheme(AppThemeOption.DARK_BLUE, systemDark = false))
        assertEquals(ResolvedAppTheme.DARK_INDIGO, resolveAppTheme(AppThemeOption.DARK_INDIGO, systemDark = false))
        assertEquals(ResolvedAppTheme.DARK_GRAFITE, resolveAppTheme(AppThemeOption.DARK_GRAPHITE, systemDark = false))
    }

    @Test
    fun default_light_scheme_uses_azul_confianca_primary() {
        val scheme = colorSchemeFor(ResolvedAppTheme.LIGHT_AZUL)
        assertEquals(AzulPrimaryLight, scheme.primary)
        assertEquals(AzulBackgroundLight, scheme.background)
        assertEquals(AzulSurfaceLight, scheme.surface)
        assertFalse(isDarkResolvedTheme(ResolvedAppTheme.LIGHT_AZUL))
        assertTrue(isDarkResolvedTheme(ResolvedAppTheme.DARK_AZUL))
    }

    @Test
    fun dark_palettes_use_proposta_tokens() {
        assertEquals(AzulPrimaryDark, colorSchemeFor(ResolvedAppTheme.DARK_AZUL).primary)
        assertEquals(IndigoPrimaryDark, colorSchemeFor(ResolvedAppTheme.DARK_INDIGO).primary)
        assertEquals(GrafitePrimaryDark, colorSchemeFor(ResolvedAppTheme.DARK_GRAFITE).primary)
    }
}
