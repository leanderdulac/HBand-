package com.example.data.hband

import android.util.Log
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.model.datas.FunctionDeviceSupportData
import com.veepoo.protocol.model.enums.EFunctionStatus
import com.veepoo.protocol.util.FunctionCheckUtil

/**
 * Maps official SDK support packets / FunctionCheck helpers into [DeviceCapabilities].
 *
 * `isSupportAutoMeasure` and `isSupportPreciseSleep` are not methods on
 * [FunctionCheckUtil]; they come from [FunctionDeviceSupportData] status flags.
 * Wear detect is [FunctionCheckUtil.checkCheckWear]. SpO2 night auto is
 * [FunctionCheckUtil.isSupportAutoDetect].
 */
object VeepooCapabilityProbe {
    private const val TAG = "VeepooCapabilityProbe"

    fun fromSupport(
        support: FunctionDeviceSupportData?,
        check: FunctionCheckUtil? = null,
    ): DeviceCapabilities {
        val historyDays = (support?.wathcDay ?: 0).coerceAtLeast(1)
        val originVersion = support?.originProtcolVersion ?: 0
        return DeviceCapabilities(
            historyDays = historyDays,
            originProtocolVersion = originVersion,
            isSupportAutoMeasure = support?.autoMeasure.isSupported(),
            isSupportPreciseSleep = support?.precisionSleep.isSupported(),
            isSupportWearDetect = safeCheck(check) { it.checkCheckWear() },
            isSupportSpo2 = support?.spo2H.isSupported(),
            isSupportSpo2AutoDetect = safeCheck(check) { it.isSupportAutoDetect() },
            isSupportHrv = support?.hrvFunction.isSupported() ||
                support?.allDayHrvFunc.isSupported() ||
                safeCheck(check) { it.checkHRV(true) },
            isSupportAllDayHrv = support?.allDayHrvFunc.isSupported(),
            isSupportHeart = support?.heartDetect.isSupported(),
            isSupportBp = support?.bp.isSupported() || safeCheck(check) { it.checkBp() },
            isSupportTemperature = support?.temperatureFunction.isSupported() ||
                safeCheck(check) { it.isSupportReadTemperature() },
            probed = support != null || check != null,
        )
    }

    fun fromManager(
        vpManager: VPOperateManager,
        support: FunctionDeviceSupportData?,
    ): DeviceCapabilities {
        val check = try {
            vpManager.functionCheck
        } catch (e: Exception) {
            Log.w(TAG, "FunctionCheck unavailable: ${e.message}")
            null
        }
        return fromSupport(support, check)
    }

    private fun EFunctionStatus?.isSupported(): Boolean {
        if (this == null) return false
        if (this == EFunctionStatus.UNSUPPORT || this == EFunctionStatus.UNKONW) return false
        return isHaveFunction()
    }

    private fun safeCheck(check: FunctionCheckUtil?, block: (FunctionCheckUtil) -> Boolean): Boolean {
        if (check == null) return false
        return try {
            block(check)
        } catch (e: Exception) {
            Log.w(TAG, "FunctionCheck query failed: ${e.message}")
            false
        }
    }
}
