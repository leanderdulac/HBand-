package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MinimalPrimaryContainer,
    onPrimary = MinimalOnPrimaryContainer,
    primaryContainer = MinimalPrimaryDark,
    onPrimaryContainer = MinimalPrimaryContainer,
    secondary = MinimalPrimary,
    background = Color(0xFF101418),
    surface = Color(0xFF191C20),
    surfaceVariant = Color(0xFF2B3036),
    onBackground = MinimalBackground,
    onSurface = MinimalBackground,
    outline = MinimalBorder
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = Color.White,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    secondary = MinimalPrimaryDark,
    background = MinimalBackground,
    surface = MinimalSurface,
    surfaceVariant = MinimalSurfaceVariant,
    onBackground = MinimalTextPrimary,
    onSurface = MinimalTextPrimary,
    onSurfaceVariant = MinimalTextSecondary,
    outline = MinimalBorder
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
