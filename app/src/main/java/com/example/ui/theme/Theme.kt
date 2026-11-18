package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

enum class AppThemeOption(val storageKey: String, val label: String) {
    LIGHT("light", "Claro"),
    DARK_BLUE("dark_blue", "Escuro azul"),
    DARK_INDIGO("dark_indigo", "Escuro índigo"),
    DARK_GRAPHITE("dark_graphite", "Escuro grafite + ciano"),
    FOLLOW_SYSTEM("follow_system", "Seguir o sistema"),
    ;

    companion object {
        const val PREFS_KEY = "app_theme_option"
        const val SETTINGS_PREFS = "hband_settings"

        fun fromStorage(value: String?): AppThemeOption =
            entries.firstOrNull { it.storageKey == value } ?: LIGHT

        /** Theme for the storage-opening frame; does not construct MainViewModel. */
        fun storedIn(context: Context): AppThemeOption =
            fromStorage(
                context.applicationContext
                    .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                    .getString(PREFS_KEY, null),
            )
    }
}

enum class ResolvedAppTheme {
    LIGHT_AZUL,
    DARK_AZUL,
    DARK_INDIGO,
    DARK_GRAFITE,
}

fun resolveAppTheme(option: AppThemeOption, systemDark: Boolean): ResolvedAppTheme =
    when (option) {
        AppThemeOption.LIGHT -> ResolvedAppTheme.LIGHT_AZUL
        AppThemeOption.DARK_BLUE -> ResolvedAppTheme.DARK_AZUL
        AppThemeOption.DARK_INDIGO -> ResolvedAppTheme.DARK_INDIGO
        AppThemeOption.DARK_GRAPHITE -> ResolvedAppTheme.DARK_GRAFITE
        AppThemeOption.FOLLOW_SYSTEM ->
            if (systemDark) ResolvedAppTheme.DARK_AZUL else ResolvedAppTheme.LIGHT_AZUL
    }

internal fun colorSchemeFor(resolved: ResolvedAppTheme): ColorScheme =
    when (resolved) {
        ResolvedAppTheme.LIGHT_AZUL ->
            lightColorScheme(
                primary = AzulPrimaryLight,
                onPrimary = AzulOnPrimaryLight,
                primaryContainer = AzulPrimaryContainerLight,
                onPrimaryContainer = AzulOnPrimaryContainerLight,
                secondary = AzulPrimaryLight,
                onSecondary = AzulOnPrimaryLight,
                secondaryContainer = AzulAiLight,
                onSecondaryContainer = AzulOnPrimaryContainerLight,
                tertiary = VitalPressureLight,
                onTertiary = Color.White,
                tertiaryContainer = AzulAiLight,
                onTertiaryContainer = AzulOnPrimaryContainerLight,
                background = AzulBackgroundLight,
                onBackground = AzulOnSurfaceLight,
                surface = AzulSurfaceLight,
                onSurface = AzulOnSurfaceLight,
                surfaceVariant = AzulAiLight,
                onSurfaceVariant = AzulOnSurfaceVariantLight,
                outline = AzulOutlineLight,
                outlineVariant = AzulOutlineVariantLight,
                error = StatusAlertButtonLight,
                onError = Color.White,
                errorContainer = StatusAlertContainerLight,
                onErrorContainer = StatusAlertForegroundLight,
            )
        ResolvedAppTheme.DARK_AZUL ->
            darkColorScheme(
                primary = AzulPrimaryDark,
                onPrimary = AzulOnPrimaryDark,
                primaryContainer = AzulPrimaryContainerDark,
                onPrimaryContainer = AzulOnPrimaryContainerDark,
                secondary = AzulPrimaryDark,
                onSecondary = AzulOnPrimaryDark,
                secondaryContainer = AzulAiDark,
                onSecondaryContainer = AzulOnPrimaryContainerDark,
                tertiary = VitalPressureDark,
                onTertiary = AzulOnPrimaryDark,
                tertiaryContainer = AzulAiDark,
                onTertiaryContainer = AzulOnPrimaryContainerDark,
                background = AzulBackgroundDark,
                onBackground = AzulOnSurfaceDark,
                surface = AzulSurfaceDark,
                onSurface = AzulOnSurfaceDark,
                surfaceVariant = AzulAiDark,
                onSurfaceVariant = AzulOnSurfaceVariantDark,
                outline = AzulOutlineDark,
                outlineVariant = AzulOutlineVariantDark,
                error = StatusAlertForegroundDark,
                onError = Color(0xFF690005),
                errorContainer = StatusAlertContainerDark,
                onErrorContainer = StatusAlertForegroundDark,
            )
        ResolvedAppTheme.DARK_INDIGO ->
            darkColorScheme(
                primary = IndigoPrimaryDark,
                onPrimary = IndigoOnPrimaryDark,
                primaryContainer = IndigoPrimaryContainerDark,
                onPrimaryContainer = IndigoOnPrimaryContainerDark,
                secondary = IndigoPrimaryDark,
                onSecondary = IndigoOnPrimaryDark,
                secondaryContainer = IndigoAiDark,
                onSecondaryContainer = IndigoOnPrimaryContainerDark,
                tertiary = VitalPressureDark,
                onTertiary = IndigoOnPrimaryDark,
                tertiaryContainer = IndigoAiDark,
                onTertiaryContainer = IndigoOnPrimaryContainerDark,
                background = IndigoBackgroundDark,
                onBackground = IndigoOnSurfaceDark,
                surface = IndigoSurfaceDark,
                onSurface = IndigoOnSurfaceDark,
                surfaceVariant = IndigoAiDark,
                onSurfaceVariant = IndigoOnSurfaceVariantDark,
                outline = IndigoOutlineDark,
                outlineVariant = IndigoOutlineVariantDark,
                error = StatusAlertForegroundDark,
                onError = Color(0xFF690005),
                errorContainer = StatusAlertContainerDark,
                onErrorContainer = StatusAlertForegroundDark,
            )
        ResolvedAppTheme.DARK_GRAFITE ->
            darkColorScheme(
                primary = GrafitePrimaryDark,
                onPrimary = GrafiteOnPrimaryDark,
                primaryContainer = GrafitePrimaryContainerDark,
                onPrimaryContainer = GrafiteOnPrimaryContainerDark,
                secondary = GrafitePrimaryDark,
                onSecondary = GrafiteOnPrimaryDark,
                secondaryContainer = GrafiteAiDark,
                onSecondaryContainer = GrafiteOnPrimaryContainerDark,
                tertiary = VitalSpo2Dark,
                onTertiary = GrafiteOnPrimaryDark,
                tertiaryContainer = GrafiteAiDark,
                onTertiaryContainer = GrafiteOnPrimaryContainerDark,
                background = GrafiteBackgroundDark,
                onBackground = GrafiteOnSurfaceDark,
                surface = GrafiteSurfaceDark,
                onSurface = GrafiteOnSurfaceDark,
                surfaceVariant = GrafiteAiDark,
                onSurfaceVariant = GrafiteOnSurfaceVariantDark,
                outline = GrafiteOutlineDark,
                outlineVariant = GrafiteOutlineVariantDark,
                error = StatusAlertForegroundDark,
                onError = Color(0xFF690005),
                errorContainer = StatusAlertContainerDark,
                onErrorContainer = StatusAlertForegroundDark,
            )
    }

fun isDarkResolvedTheme(resolved: ResolvedAppTheme): Boolean = resolved != ResolvedAppTheme.LIGHT_AZUL

@Composable
fun MyApplicationTheme(
    themeOption: AppThemeOption = AppThemeOption.LIGHT,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val resolved = resolveAppTheme(themeOption, systemDark)
    val scheme = colorSchemeFor(resolved)
    val dark = isDarkResolvedTheme(resolved)
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(
                LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors,
                LocalVitalSignalColors provides if (dark) DarkVitalSignalColors else LightVitalSignalColors,
                LocalBreathingPhaseColors provides if (dark) DarkBreathingPhaseColors else LightBreathingPhaseColors,
                content = content,
            )
        },
    )
}
