package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SystemSans = FontFamily.Default

val Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = SystemSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
    )
