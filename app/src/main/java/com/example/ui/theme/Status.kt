package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusColors(
    val normalContainer: Color,
    val normalForeground: Color,
    val attentionContainer: Color,
    val attentionForeground: Color,
    val alertContainer: Color,
    val alertForeground: Color,
    val alertButton: Color,
    val noDataContainer: Color,
    val noDataForeground: Color,
)

@Immutable
data class VitalSignalColors(
    val heartRate: Color,
    val spo2: Color,
    val bloodPressure: Color,
    val temperature: Color,
)

@Immutable
data class BreathingPhaseColors(
    val inhale: Color,
    val hold: Color,
    val exhale: Color,
    val rest: Color,
)

val LocalStatusColors =
    staticCompositionLocalOf {
        StatusColors(
            normalContainer = StatusNormalContainerLight,
            normalForeground = StatusNormalForegroundLight,
            attentionContainer = StatusAttentionContainerLight,
            attentionForeground = StatusAttentionForegroundLight,
            alertContainer = StatusAlertContainerLight,
            alertForeground = StatusAlertForegroundLight,
            alertButton = StatusAlertButtonLight,
            noDataContainer = StatusNoDataContainerLight,
            noDataForeground = StatusNoDataForegroundLight,
        )
    }

val LocalVitalSignalColors =
    staticCompositionLocalOf {
        VitalSignalColors(
            heartRate = VitalHeartLight,
            spo2 = VitalSpo2Light,
            bloodPressure = VitalPressureLight,
            temperature = VitalTempLight,
        )
    }

val LocalBreathingPhaseColors =
    staticCompositionLocalOf {
        BreathingPhaseColors(
            inhale = BreathInhaleLight,
            hold = BreathHoldLight,
            exhale = BreathExhaleLight,
            rest = BreathRestLight,
        )
    }

val LightStatusColors =
    StatusColors(
        normalContainer = StatusNormalContainerLight,
        normalForeground = StatusNormalForegroundLight,
        attentionContainer = StatusAttentionContainerLight,
        attentionForeground = StatusAttentionForegroundLight,
        alertContainer = StatusAlertContainerLight,
        alertForeground = StatusAlertForegroundLight,
        alertButton = StatusAlertButtonLight,
        noDataContainer = StatusNoDataContainerLight,
        noDataForeground = StatusNoDataForegroundLight,
    )

val DarkStatusColors =
    StatusColors(
        normalContainer = StatusNormalContainerDark,
        normalForeground = StatusNormalForegroundDark,
        attentionContainer = StatusAttentionContainerDark,
        attentionForeground = StatusAttentionForegroundDark,
        alertContainer = StatusAlertContainerDark,
        alertForeground = StatusAlertForegroundDark,
        alertButton = StatusAlertButtonDark,
        noDataContainer = StatusNoDataContainerDark,
        noDataForeground = StatusNoDataForegroundDark,
    )

val LightVitalSignalColors =
    VitalSignalColors(
        heartRate = VitalHeartLight,
        spo2 = VitalSpo2Light,
        bloodPressure = VitalPressureLight,
        temperature = VitalTempLight,
    )

val DarkVitalSignalColors =
    VitalSignalColors(
        heartRate = VitalHeartDark,
        spo2 = VitalSpo2Dark,
        bloodPressure = VitalPressureDark,
        temperature = VitalTempDark,
    )

val LightBreathingPhaseColors =
    BreathingPhaseColors(
        inhale = BreathInhaleLight,
        hold = BreathHoldLight,
        exhale = BreathExhaleLight,
        rest = BreathRestLight,
    )

val DarkBreathingPhaseColors =
    BreathingPhaseColors(
        inhale = BreathInhaleDark,
        hold = BreathHoldDark,
        exhale = BreathExhaleDark,
        rest = BreathRestDark,
    )
