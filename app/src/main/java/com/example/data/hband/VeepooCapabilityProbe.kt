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
            isSupportEcg = support?.ecg.isSupported() || safeCheck(check) { it.checkECG() },
            isSupportMultiLeadEcg = safeCheck(check) { it.checkEcgMultiLead() } ||
                safeCheck(check) { it.isEcgMultiLeadDevice() },
            isSupportBloodGlucose = support?.bloodGlucose.isSupported(),
            isSupportBloodGlucoseAdjusting = support?.bloodGlucoseAdjusting.isSupported() ||
                safeCheck(check) { it.checkBgAdjusting() },
            isSupportBloodComponent = support?.bloodComponent.isSupported() ||
                safeCheck(check) { it.checkBloodComponent() },
            isSupportBodyComponent = support?.bodyComponent.isSupported() ||
                safeCheck(check) { it.checkBodyComponent() },
            isSupportEmotion = support?.emotion.isSupported() ||
                safeCheck(check) { it.checkEmotionDetect() },
            isSupportFatigue = support?.fatigue.isSupported() ||
                safeCheck(check) { it.checkFtg() },
            isSupportBreath = support?.beathFunction.isSupported() ||
                safeCheck(check) { it.checkBreath() },
            isSupportAlarm2 = support?.alarm2.isSupported() ||
                safeCheck(check) { it.checkMultiAlarm() },
            isSupportTextAlarm = support?.textAlarm.isSupported() ||
                safeCheck(check) { it.checkTextAlarm() } ||
                safeCheck(check) { it.isSupportTextAlarm() },
            isSupportHeartWarning = support?.heartWaring.isSupported() ||
                safeCheck(check) { it.checkHeartwaring() },
            isSupportHealthRemind = support?.healthRemind.isSupported(),
            isSupportLongSeat = support?.longseat.isSupported() ||
                safeCheck(check) { it.checkLongseat() },
            isSupportNightTurnWrist = support?.nightTurnSetting.isSupported() ||
                safeCheck(check) { it.checkNightturnSetting() },
            isSupportFindDevice = safeCheck(check) { it.checkFindDevice() },
            isSupportFindDeviceByPhone = support?.findDeviceByPhone.isSupported() ||
                safeCheck(check) { it.checFindDeviceByPhone() },
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
