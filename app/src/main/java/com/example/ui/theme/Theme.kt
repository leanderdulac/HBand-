package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
  @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
  @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Product UI is always white/light. Following system night mode made
  // OutlinedTextField content near-white on white dialogs.
  MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
