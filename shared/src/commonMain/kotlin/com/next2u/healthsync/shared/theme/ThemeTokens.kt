package com.next2u.healthsync.shared.theme

/**
 * Visual tokens from PR #17 (Cuidado claro / Azul confiança).
 * Hex values match `app/src/main/java/com/example/ui/theme/Color.kt`.
 * Default option is [AppThemeOption.LIGHT] — Claro.
 */
enum class AppThemeOption(val storageKey: String, val label: String) {
    LIGHT("light", "Claro"),
    DARK_BLUE("dark_blue", "Escuro azul"),
    DARK_INDIGO("dark_indigo", "Escuro índigo"),
    DARK_GRAPHITE("dark_graphite", "Escuro grafite + ciano"),
    FOLLOW_SYSTEM("follow_system", "Seguir o sistema"),
    ;

    companion object {
        const val PREFS_KEY = "app_theme_option"

        fun fromStorage(value: String?): AppThemeOption =
            entries.firstOrNull { it.storageKey == value } ?: LIGHT
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

fun isDarkResolvedTheme(resolved: ResolvedAppTheme): Boolean = resolved != ResolvedAppTheme.LIGHT_AZUL

/** ARGB hex without `#`, same integers Compose `Color(0x…)` uses. */
data class ThemePalette(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val background: Long,
    val surface: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val outlineVariant: Long,
    val ai: Long,
    val error: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
)

object ThemeTokens {
    const val STATUS_NORMAL_CONTAINER_LIGHT = 0xFFD6F2E4
    const val STATUS_NORMAL_FOREGROUND_LIGHT = 0xFF0B5A3A
    const val STATUS_ATTENTION_CONTAINER_LIGHT = 0xFFFFE8C2
    const val STATUS_ATTENTION_FOREGROUND_LIGHT = 0xFF6B3A00
    const val STATUS_ALERT_CONTAINER_LIGHT = 0xFFFFDAD6
    const val STATUS_ALERT_FOREGROUND_LIGHT = 0xFF8C1D18
    const val STATUS_ALERT_BUTTON_LIGHT = 0xFFBA1A1A

    const val STATUS_NORMAL_CONTAINER_DARK = 0xFF1F4E3A
    const val STATUS_NORMAL_FOREGROUND_DARK = 0xFFB5F0D2
    const val STATUS_ATTENTION_CONTAINER_DARK = 0xFF4A3000
    const val STATUS_ATTENTION_FOREGROUND_DARK = 0xFFFFDDB0
    const val STATUS_ALERT_CONTAINER_DARK = 0xFF5C0F0B
    const val STATUS_ALERT_FOREGROUND_DARK = 0xFFFFB4AB
    const val STATUS_ALERT_BUTTON_DARK = 0xFFFFB4AB

    const val VITAL_HEART_LIGHT = 0xFFC2334A
    const val VITAL_SPO2_LIGHT = 0xFF007A86
    const val VITAL_PRESSURE_LIGHT = 0xFF4F46B5
    const val VITAL_TEMP_LIGHT = 0xFFB04A00

    const val AZUL_PRIMARY_LIGHT = 0xFF1450A3
    const val AZUL_ON_PRIMARY_LIGHT = 0xFFFFFFFF
    const val AZUL_PRIMARY_CONTAINER_LIGHT = 0xFFD8E6FF
    const val AZUL_ON_PRIMARY_CONTAINER_LIGHT = 0xFF0B3B7C
    const val AZUL_BACKGROUND_LIGHT = 0xFFF5F8FC
    const val AZUL_SURFACE_LIGHT = 0xFFFFFFFF
    const val AZUL_ON_SURFACE_LIGHT = 0xFF111827
    const val AZUL_ON_SURFACE_VARIANT_LIGHT = 0xFF4A5568
    const val AZUL_AI_LIGHT = 0xFFEAF1FC
    const val AZUL_OUTLINE_LIGHT = 0xFF5A6A80
    const val AZUL_OUTLINE_VARIANT_LIGHT = 0xFFDCE3EE

    const val AZUL_PRIMARY_DARK = 0xFFA9C7FF
    const val AZUL_ON_PRIMARY_DARK = 0xFF002F68
    const val AZUL_PRIMARY_CONTAINER_DARK = 0xFF1D3A66
    const val AZUL_ON_PRIMARY_CONTAINER_DARK = 0xFFD8E6FF
    const val AZUL_BACKGROUND_DARK = 0xFF0E131B
    const val AZUL_SURFACE_DARK = 0xFF18202C
    const val AZUL_ON_SURFACE_DARK = 0xFFE3E8F0
    const val AZUL_ON_SURFACE_VARIANT_DARK = 0xFFB3BDCC
    const val AZUL_AI_DARK = 0xFF16243A
    const val AZUL_OUTLINE_DARK = 0xFFB3BDCC
    const val AZUL_OUTLINE_VARIANT_DARK = 0xFF2A3444

    const val INDIGO_PRIMARY_DARK = 0xFFC4BFFF
    const val INDIGO_ON_PRIMARY_DARK = 0xFF241A85
    const val INDIGO_PRIMARY_CONTAINER_DARK = 0xFF352C80
    const val INDIGO_ON_PRIMARY_CONTAINER_DARK = 0xFFE3DFFF
    const val INDIGO_BACKGROUND_DARK = 0xFF121120
    const val INDIGO_SURFACE_DARK = 0xFF1C1A2E
    const val INDIGO_ON_SURFACE_DARK = 0xFFE5E3F2
    const val INDIGO_ON_SURFACE_VARIANT_DARK = 0xFFBAB7CF
    const val INDIGO_AI_DARK = 0xFF242044
    const val INDIGO_OUTLINE_DARK = 0xFFBAB7CF
    const val INDIGO_OUTLINE_VARIANT_DARK = 0xFF302D46

    const val GRAFITE_PRIMARY_DARK = 0xFF5ED8EA
    const val GRAFITE_ON_PRIMARY_DARK = 0xFF00363E
    const val GRAFITE_PRIMARY_CONTAINER_DARK = 0xFF0D3F48
    const val GRAFITE_ON_PRIMARY_CONTAINER_DARK = 0xFFB8F1FA
    const val GRAFITE_BACKGROUND_DARK = 0xFF0B0E10
    const val GRAFITE_SURFACE_DARK = 0xFF161B1F
    const val GRAFITE_ON_SURFACE_DARK = 0xFFE4E9EC
    const val GRAFITE_ON_SURFACE_VARIANT_DARK = 0xFFB0BBC1
    const val GRAFITE_AI_DARK = 0xFF122A30
    const val GRAFITE_OUTLINE_DARK = 0xFFB0BBC1
    const val GRAFITE_OUTLINE_VARIANT_DARK = 0xFF283036

    const val BODY_LARGE_SP = 17
    const val BODY_MEDIUM_SP = 15
    const val DISPLAY_MEDIUM_SP = 40
    const val MIN_TOUCH_DP = 48
    const val PRIMARY_BUTTON_DP = 56
    const val MARGIN_DP = 16
    const val CARD_GAP_DP = 12

    fun palette(resolved: ResolvedAppTheme): ThemePalette = when (resolved) {
        ResolvedAppTheme.LIGHT_AZUL -> ThemePalette(
            primary = AZUL_PRIMARY_LIGHT,
            onPrimary = AZUL_ON_PRIMARY_LIGHT,
            primaryContainer = AZUL_PRIMARY_CONTAINER_LIGHT,
            onPrimaryContainer = AZUL_ON_PRIMARY_CONTAINER_LIGHT,
            background = AZUL_BACKGROUND_LIGHT,
            surface = AZUL_SURFACE_LIGHT,
            onSurface = AZUL_ON_SURFACE_LIGHT,
            onSurfaceVariant = AZUL_ON_SURFACE_VARIANT_LIGHT,
            outline = AZUL_OUTLINE_LIGHT,
            outlineVariant = AZUL_OUTLINE_VARIANT_LIGHT,
            ai = AZUL_AI_LIGHT,
            error = STATUS_ALERT_BUTTON_LIGHT,
            errorContainer = STATUS_ALERT_CONTAINER_LIGHT,
            onErrorContainer = STATUS_ALERT_FOREGROUND_LIGHT,
        )
        ResolvedAppTheme.DARK_AZUL -> ThemePalette(
            primary = AZUL_PRIMARY_DARK,
            onPrimary = AZUL_ON_PRIMARY_DARK,
            primaryContainer = AZUL_PRIMARY_CONTAINER_DARK,
            onPrimaryContainer = AZUL_ON_PRIMARY_CONTAINER_DARK,
            background = AZUL_BACKGROUND_DARK,
            surface = AZUL_SURFACE_DARK,
            onSurface = AZUL_ON_SURFACE_DARK,
            onSurfaceVariant = AZUL_ON_SURFACE_VARIANT_DARK,
            outline = AZUL_OUTLINE_DARK,
            outlineVariant = AZUL_OUTLINE_VARIANT_DARK,
            ai = AZUL_AI_DARK,
            error = STATUS_ALERT_FOREGROUND_DARK,
            errorContainer = STATUS_ALERT_CONTAINER_DARK,
            onErrorContainer = STATUS_ALERT_FOREGROUND_DARK,
        )
        ResolvedAppTheme.DARK_INDIGO -> ThemePalette(
            primary = INDIGO_PRIMARY_DARK,
            onPrimary = INDIGO_ON_PRIMARY_DARK,
            primaryContainer = INDIGO_PRIMARY_CONTAINER_DARK,
            onPrimaryContainer = INDIGO_ON_PRIMARY_CONTAINER_DARK,
            background = INDIGO_BACKGROUND_DARK,
            surface = INDIGO_SURFACE_DARK,
            onSurface = INDIGO_ON_SURFACE_DARK,
            onSurfaceVariant = INDIGO_ON_SURFACE_VARIANT_DARK,
            outline = INDIGO_OUTLINE_DARK,
            outlineVariant = INDIGO_OUTLINE_VARIANT_DARK,
            ai = INDIGO_AI_DARK,
            error = STATUS_ALERT_FOREGROUND_DARK,
            errorContainer = STATUS_ALERT_CONTAINER_DARK,
            onErrorContainer = STATUS_ALERT_FOREGROUND_DARK,
        )
        ResolvedAppTheme.DARK_GRAFITE -> ThemePalette(
            primary = GRAFITE_PRIMARY_DARK,
            onPrimary = GRAFITE_ON_PRIMARY_DARK,
            primaryContainer = GRAFITE_PRIMARY_CONTAINER_DARK,
            onPrimaryContainer = GRAFITE_ON_PRIMARY_CONTAINER_DARK,
            background = GRAFITE_BACKGROUND_DARK,
            surface = GRAFITE_SURFACE_DARK,
            onSurface = GRAFITE_ON_SURFACE_DARK,
            onSurfaceVariant = GRAFITE_ON_SURFACE_VARIANT_DARK,
            outline = GRAFITE_OUTLINE_DARK,
            outlineVariant = GRAFITE_OUTLINE_VARIANT_DARK,
            ai = GRAFITE_AI_DARK,
            error = STATUS_ALERT_FOREGROUND_DARK,
            errorContainer = STATUS_ALERT_CONTAINER_DARK,
            onErrorContainer = STATUS_ALERT_FOREGROUND_DARK,
        )
    }
}

/** Frozen clinical copy for heart-rate notices. Do not rewrite without clinical review. */
object AlertCopy {
    const val CHANNEL_NAME = "Avisos de batimentos"
    const val CHANNEL_DESC = "Avisos no celular quando os batimentos ficam fora dos limites cadastrados"
    const val TITLE_HIGH = "Batimentos acima do limite cadastrado"
    const val TITLE_LOW = "Batimentos abaixo do limite cadastrado"

    /** Function accessors so Swift does not depend on Kotlin `const val` Obj-C names. */
    fun channelName(): String = CHANNEL_NAME
    fun titleHigh(): String = TITLE_HIGH
    fun titleLow(): String = TITLE_LOW

    fun body(currentHr: Int, threshold: Int): String =
        "Batimentos: $currentHr bpm. Limite cadastrado: $threshold bpm."
    fun snackbarHigh(currentHr: Int, threshold: Int): String =
        "Batimentos: $currentHr bpm. Acima do limite cadastrado de $threshold bpm."
    fun snackbarLow(currentHr: Int, threshold: Int): String =
        "Batimentos: $currentHr bpm. Abaixo do limite cadastrado de $threshold bpm."
}
