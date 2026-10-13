package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

val PillShape = RoundedCornerShape(percent = 50)

object Spacing {
    val grid = 4.dp
    val margin = 16.dp
    val cardGap = 12.dp
    val minTouch = 48.dp
    val primaryButton = 56.dp
}
