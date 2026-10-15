package com.next2u.healthsync.shared.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeTokensTest {
    @Test
    fun default_option_is_claro() {
        assertEquals(AppThemeOption.LIGHT, AppThemeOption.fromStorage(null))
        assertEquals(AppThemeOption.LIGHT, AppThemeOption.fromStorage("unknown"))
        assertEquals("light", AppThemeOption.LIGHT.storageKey)
        assertEquals("Claro", AppThemeOption.LIGHT.label)
    }

    @Test
    fun resolve_matches_android_pr17() {
        assertEquals(ResolvedAppTheme.LIGHT_AZUL, resolveAppTheme(AppThemeOption.LIGHT, systemDark = true))
        assertEquals(ResolvedAppTheme.DARK_AZUL, resolveAppTheme(AppThemeOption.DARK_BLUE, systemDark = false))
        assertEquals(ResolvedAppTheme.DARK_INDIGO, resolveAppTheme(AppThemeOption.DARK_INDIGO, systemDark = false))
        assertEquals(ResolvedAppTheme.DARK_GRAFITE, resolveAppTheme(AppThemeOption.DARK_GRAPHITE, systemDark = false))
        assertEquals(ResolvedAppTheme.DARK_AZUL, resolveAppTheme(AppThemeOption.FOLLOW_SYSTEM, systemDark = true))
        assertEquals(ResolvedAppTheme.LIGHT_AZUL, resolveAppTheme(AppThemeOption.FOLLOW_SYSTEM, systemDark = false))
        assertFalse(isDarkResolvedTheme(ResolvedAppTheme.LIGHT_AZUL))
        assertTrue(isDarkResolvedTheme(ResolvedAppTheme.DARK_AZUL))
    }

    @Test
    fun hex_tokens_match_android_color_kt() {
        assertEquals(0xFF1450A3, ThemeTokens.AZUL_PRIMARY_LIGHT)
        assertEquals(0xFFF5F8FC, ThemeTokens.AZUL_BACKGROUND_LIGHT)
        assertEquals(0xFFFFFFFF, ThemeTokens.AZUL_SURFACE_LIGHT)
        assertEquals(0xFFA9C7FF, ThemeTokens.AZUL_PRIMARY_DARK)
        assertEquals(0xFFC4BFFF, ThemeTokens.INDIGO_PRIMARY_DARK)
        assertEquals(0xFF5ED8EA, ThemeTokens.GRAFITE_PRIMARY_DARK)
        assertEquals(0xFFC2334A, ThemeTokens.VITAL_HEART_LIGHT)
        assertEquals(0xFFBA1A1A, ThemeTokens.STATUS_ALERT_BUTTON_LIGHT)
        assertEquals(15, ThemeTokens.BODY_MEDIUM_SP)
        assertEquals(40, ThemeTokens.DISPLAY_MEDIUM_SP)
        assertEquals(56, ThemeTokens.PRIMARY_BUTTON_DP)
        assertEquals(ThemeTokens.AZUL_PRIMARY_LIGHT, ThemeTokens.palette(ResolvedAppTheme.LIGHT_AZUL).primary)
    }

    @Test
    fun alert_copy_is_frozen() {
        assertEquals("Avisos de batimentos", AlertCopy.CHANNEL_NAME)
        assertEquals(AlertCopy.CHANNEL_NAME, AlertCopy.channelName())
        assertEquals("Batimentos acima do limite cadastrado", AlertCopy.TITLE_HIGH)
        assertEquals(AlertCopy.TITLE_HIGH, AlertCopy.titleHigh())
        assertEquals("Batimentos abaixo do limite cadastrado", AlertCopy.TITLE_LOW)
        assertEquals(AlertCopy.TITLE_LOW, AlertCopy.titleLow())
        assertEquals("Batimentos: 42 bpm. Limite cadastrado: 50 bpm.", AlertCopy.body(42, 50))
        assertEquals("Batimentos: 110 bpm. Acima do limite cadastrado de 100 bpm.", AlertCopy.snackbarHigh(110, 100))
        assertEquals("Batimentos: 40 bpm. Abaixo do limite cadastrado de 50 bpm.", AlertCopy.snackbarLow(40, 50))
    }
}
