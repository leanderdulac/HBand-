package com.example.data.hband

import com.veepoo.protocol.model.datas.BatteryData

/**
 * Maps Veepoo [BatteryData] to a display percent.
 *
 * Official SDK: [BatteryData.isPercent] true → [BatteryData.batteryPercent] 0–100;
 * false → [BatteryData.batteryLevel] is a coarse bar 1–4 (4 = full), **not** a percent.
 */
object VeepooBatteryMapper {
    const val UNKNOWN_LABEL = "--"

    fun fromSdk(data: BatteryData?): Int? {
        if (data == null) return null
        return fromSdkFields(
            isPercent = data.isPercent,
            batteryPercent = data.batteryPercent,
            batteryLevel = data.batteryLevel,
        )
    }

    fun fromSdkFields(
        isPercent: Boolean,
        batteryPercent: Int,
        batteryLevel: Int,
    ): Int? {
        if (isPercent) {
            return batteryPercent.takeIf { it in 0..100 }
        }
        if (batteryPercent in 0..100 && batteryPercent > COARSE_BAR_MAX) {
            return batteryPercent
        }
        return when (batteryLevel) {
            0 -> 0
            1 -> 25
            2 -> 50
            3 -> 75
            4 -> 100
            in (COARSE_BAR_MAX + 1)..100 -> batteryLevel
            else -> null
        }
    }

    fun displayLabel(percent: Int?, simulated: Boolean = false): String {
        if (percent == null) return UNKNOWN_LABEL
        return if (simulated) "$percent% sim." else "$percent%"
    }

    private const val COARSE_BAR_MAX = 4
}
