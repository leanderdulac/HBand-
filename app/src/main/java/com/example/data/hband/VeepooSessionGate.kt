package com.example.data.hband

import com.example.data.model.HBandTelemetry

/**
 * Decides whether P1 detect / Ajustes writes may run.
 *
 * VE30 can keep streaming HeartData after a spurious GATT
 * `STATUS_DISCONNECTED`. In that case [_isHardwareConnected] may be false
 * while live packets still prove the band is on the air.
 */
object VeepooSessionGate {
    fun actionsEnabled(
        hardwareConnected: Boolean,
        connectedMac: String?,
        telemetry: HBandTelemetry?,
    ): Boolean {
        if (hardwareConnected) return true
        if (telemetry?.isRealSensorData != true) return false
        if (telemetry.heartRate !in 30..240 && telemetry.spO2 !in 50..100) return false
        val mac = connectedMac?.trim().orEmpty()
        if (mac.isEmpty()) return false
        return telemetry.deviceId.equals(mac, ignoreCase = true)
    }

    fun hintWhenDisconnected(actionsEnabled: Boolean): String =
        if (actionsEnabled) LIVE_TELEMETRY_HINT else RECONNECT_HINT

    const val RECONNECT_HINT =
        "Sessão GATT instável. Reconecte a pulseira em Dispositivos para iniciar ECG e sincronizar o histórico."

    const val LIVE_TELEMETRY_HINT =
        "FC ao vivo confirmada nesta pulseira, mas a sessão GATT está instável. Reconecte em Dispositivos se ECG ou o histórico falharem."

    const val FILA_P1_LOCATION_HINT =
        "ECG e medições avançadas ficam em Visão Geral — esta Fila só mostra o envio para a API."
}
