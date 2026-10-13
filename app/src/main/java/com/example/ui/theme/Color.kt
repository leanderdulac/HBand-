package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Visual tokens for "Cuidado claro" / paleta A (Azul confiança) and the selectable dark themes.
 * Status colors (normal / atenção / alerta) are the same in every palette.
 */

// --- Status (identical across palettes; light vs dark variants only) ---

val StatusNormalContainerLight = Color(0xFFD6F2E4)
val StatusNormalForegroundLight = Color(0xFF0B5A3A)
val StatusAttentionContainerLight = Color(0xFFFFE8C2)
val StatusAttentionForegroundLight = Color(0xFF6B3A00)
val StatusAlertContainerLight = Color(0xFFFFDAD6)
val StatusAlertForegroundLight = Color(0xFF8C1D18)
val StatusAlertButtonLight = Color(0xFFBA1A1A)

val StatusNormalContainerDark = Color(0xFF1F4E3A)
val StatusNormalForegroundDark = Color(0xFFB5F0D2)
val StatusAttentionContainerDark = Color(0xFF4A3000)
val StatusAttentionForegroundDark = Color(0xFFFFDDB0)
val StatusAlertContainerDark = Color(0xFF5C0F0B)
val StatusAlertForegroundDark = Color(0xFFFFB4AB)
val StatusAlertButtonDark = Color(0xFFFFB4AB)

val StatusNoDataContainerLight = Color(0xFFE8EDF3)
val StatusNoDataForegroundLight = Color(0xFF4A5568)
val StatusNoDataContainerDark = Color(0xFF2A3444)
val StatusNoDataForegroundDark = Color(0xFFB3BDCC)

// --- Vital signal accents (same hue family in every theme) ---

val VitalHeartLight = Color(0xFFC2334A)
val VitalSpo2Light = Color(0xFF007A86)
val VitalPressureLight = Color(0xFF4F46B5)
val VitalTempLight = Color(0xFFB04A00)

val VitalHeartDark = Color(0xFFFF8A9A)
val VitalSpo2Dark = Color(0xFF4DD0E1)
val VitalPressureDark = Color(0xFFB4B0FF)
val VitalTempDark = Color(0xFFFFB68A)

// --- A · Azul confiança (default light) ---

val AzulPrimaryLight = Color(0xFF1450A3)
val AzulOnPrimaryLight = Color(0xFFFFFFFF)
val AzulPrimaryContainerLight = Color(0xFFD8E6FF)
val AzulOnPrimaryContainerLight = Color(0xFF0B3B7C)
val AzulBackgroundLight = Color(0xFFF5F8FC)
val AzulSurfaceLight = Color(0xFFFFFFFF)
val AzulOnSurfaceLight = Color(0xFF111827)
val AzulOnSurfaceVariantLight = Color(0xFF4A5568)
val AzulAiLight = Color(0xFFEAF1FC)
val AzulOutlineVariantLight = Color(0xFFDCE3EE)
val AzulOutlineLight = Color(0xFF5A6A80)

val AzulPrimaryDark = Color(0xFFA9C7FF)
val AzulOnPrimaryDark = Color(0xFF002F68)
val AzulPrimaryContainerDark = Color(0xFF1D3A66)
val AzulOnPrimaryContainerDark = Color(0xFFD8E6FF)
val AzulBackgroundDark = Color(0xFF0E131B)
val AzulSurfaceDark = Color(0xFF18202C)
val AzulOnSurfaceDark = Color(0xFFE3E8F0)
val AzulOnSurfaceVariantDark = Color(0xFFB3BDCC)
val AzulAiDark = Color(0xFF16243A)
val AzulOutlineVariantDark = Color(0xFF2A3444)
val AzulOutlineDark = Color(0xFFB3BDCC)

// --- C · Índigo moderno (dark option) ---

val IndigoPrimaryDark = Color(0xFFC4BFFF)
val IndigoOnPrimaryDark = Color(0xFF241A85)
val IndigoPrimaryContainerDark = Color(0xFF352C80)
val IndigoOnPrimaryContainerDark = Color(0xFFE3DFFF)
val IndigoBackgroundDark = Color(0xFF121120)
val IndigoSurfaceDark = Color(0xFF1C1A2E)
val IndigoOnSurfaceDark = Color(0xFFE5E3F2)
val IndigoOnSurfaceVariantDark = Color(0xFFBAB7CF)
val IndigoAiDark = Color(0xFF242044)
val IndigoOutlineVariantDark = Color(0xFF302D46)
val IndigoOutlineDark = Color(0xFFBAB7CF)

// --- D · Grafite + ciano (dark option) ---

val GrafitePrimaryDark = Color(0xFF5ED8EA)
val GrafiteOnPrimaryDark = Color(0xFF00363E)
val GrafitePrimaryContainerDark = Color(0xFF0D3F48)
val GrafiteOnPrimaryContainerDark = Color(0xFFB8F1FA)
val GrafiteBackgroundDark = Color(0xFF0B0E10)
val GrafiteSurfaceDark = Color(0xFF161B1F)
val GrafiteOnSurfaceDark = Color(0xFFE4E9EC)
val GrafiteOnSurfaceVariantDark = Color(0xFFB0BBC1)
val GrafiteAiDark = Color(0xFF122A30)
val GrafiteOutlineVariantDark = Color(0xFF283036)
val GrafiteOutlineDark = Color(0xFFB0BBC1)

// Breathing-phase accents (instructional, not clinical status).
val BreathInhaleLight = Color(0xFF1450A3)
val BreathHoldLight = Color(0xFF4B3FBF)
val BreathExhaleLight = Color(0xFF0B5A3A)
val BreathRestLight = Color(0xFF6B3A00)
val BreathInhaleDark = Color(0xFFA9C7FF)
val BreathHoldDark = Color(0xFFC4BFFF)
val BreathExhaleDark = Color(0xFFB5F0D2)
val BreathRestDark = Color(0xFFFFDDB0)
