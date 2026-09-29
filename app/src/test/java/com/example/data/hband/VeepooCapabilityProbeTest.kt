package com.example.data.hband

import com.veepoo.protocol.model.datas.FunctionDeviceSupportData
import com.veepoo.protocol.model.enums.EFunctionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooCapabilityProbeTest {

    @Test
    fun `unsupport and unknown flags stay closed`() {
        val support = FunctionDeviceSupportData().apply {
            setWathcDay(1)
            setOriginProtcolVersion(0)
            autoMeasure = EFunctionStatus.UNSUPPORT
            precisionSleep = EFunctionStatus.UNKONW
            spo2H = EFunctionStatus.UNSUPPORT
            hrvFunction = EFunctionStatus.UNSUPPORT
            heartDetect = EFunctionStatus.SUPPORT
            bp = EFunctionStatus.SUPPORT_CLOSE
        }
        val caps = VeepooCapabilityProbe.fromSupport(support, check = null)
        assertTrue(caps.probed)
        assertEquals(1, caps.historyDays)
        assertFalse(caps.isSupportAutoMeasure)
        assertFalse(caps.isSupportPreciseSleep)
        assertFalse(caps.isSupportSpo2)
        assertFalse(caps.isSupportWearDetect)
        assertFalse(caps.isSupportSpo2AutoDetect)
        assertTrue(caps.isSupportHeart)
        assertTrue(caps.isSupportBp)
    }

    @Test
    fun `supported auto measure and precise sleep are exposed`() {
        val support = FunctionDeviceSupportData().apply {
            setWathcDay(7)
            setOriginProtcolVersion(3)
            autoMeasure = EFunctionStatus.SUPPORT_OPEN
            precisionSleep = EFunctionStatus.SUPPORT
            spo2H = EFunctionStatus.SUPPORT
            hrvFunction = EFunctionStatus.SUPPORT
            allDayHrvFunc = EFunctionStatus.SUPPORT_OPEN
            temperatureFunction = EFunctionStatus.SUPPORT
        }
        val caps = VeepooCapabilityProbe.fromSupport(support, check = null)
        assertEquals(7, caps.historyDays)
        assertEquals(3, caps.originProtocolVersion)
        assertTrue(caps.isSupportAutoMeasure)
        assertTrue(caps.isSupportPreciseSleep)
        assertTrue(caps.isSupportSpo2)
        assertTrue(caps.isSupportHrv)
        assertTrue(caps.isSupportAllDayHrv)
        assertTrue(caps.isSupportTemperature)
        assertTrue(caps.canReadMultiDayOrigin)
        assertTrue(caps.canReadHrvOrigin)
        assertTrue(caps.canReadSpo2Origin)
    }

    @Test
    fun `P1 flags stay closed unless firmware reports support`() {
        val support = FunctionDeviceSupportData().apply {
            ecg = EFunctionStatus.UNSUPPORT
            bloodGlucose = EFunctionStatus.UNKONW
            bloodComponent = EFunctionStatus.UNSUPPORT
            bodyComponent = EFunctionStatus.UNSUPPORT
            emotion = EFunctionStatus.UNSUPPORT
            fatigue = EFunctionStatus.UNSUPPORT
            beathFunction = EFunctionStatus.UNSUPPORT
            alarm2 = EFunctionStatus.UNSUPPORT
            heartWaring = EFunctionStatus.UNSUPPORT
            longseat = EFunctionStatus.UNSUPPORT
            nightTurnSetting = EFunctionStatus.UNSUPPORT
            healthRemind = EFunctionStatus.UNSUPPORT
            findDeviceByPhone = EFunctionStatus.UNSUPPORT
        }
        val caps = VeepooCapabilityProbe.fromSupport(support, check = null)
        assertFalse(caps.isSupportEcg)
        assertFalse(caps.isSupportMultiLeadEcg)
        assertFalse(caps.isSupportBloodGlucose)
        assertFalse(caps.isSupportBloodComponent)
        assertFalse(caps.isSupportBodyComponent)
        assertFalse(caps.isSupportEmotion)
        assertFalse(caps.isSupportFatigue)
        assertFalse(caps.isSupportBreath)
        assertFalse(caps.isSupportAlarm2)
        assertFalse(caps.isSupportHeartWarning)
        assertFalse(caps.isSupportLongSeat)
        assertFalse(caps.isSupportNightTurnWrist)
        assertFalse(caps.isSupportHealthRemind)
        assertFalse(caps.isSupportFindDeviceByPhone)
        assertFalse(caps.hasAdvancedDetect)
        assertFalse(caps.hasP1Settings)
    }

    @Test
    fun `P1 ECG glucose and alarm flags are exposed when supported`() {
        val support = FunctionDeviceSupportData().apply {
            ecg = EFunctionStatus.SUPPORT
            bloodGlucose = EFunctionStatus.SUPPORT_OPEN
            bloodGlucoseAdjusting = EFunctionStatus.SUPPORT
            bloodComponent = EFunctionStatus.SUPPORT
            bodyComponent = EFunctionStatus.SUPPORT
            emotion = EFunctionStatus.SUPPORT
            fatigue = EFunctionStatus.SUPPORT_OPEN
            beathFunction = EFunctionStatus.SUPPORT
            alarm2 = EFunctionStatus.SUPPORT
            heartWaring = EFunctionStatus.SUPPORT_OPEN
            longseat = EFunctionStatus.SUPPORT
            nightTurnSetting = EFunctionStatus.SUPPORT
            healthRemind = EFunctionStatus.SUPPORT
            findDeviceByPhone = EFunctionStatus.SUPPORT
        }
        val caps = VeepooCapabilityProbe.fromSupport(support, check = null)
        assertTrue(caps.isSupportEcg)
        assertTrue(caps.isSupportBloodGlucose)
        assertTrue(caps.isSupportBloodGlucoseAdjusting)
        assertTrue(caps.isSupportBloodComponent)
        assertTrue(caps.isSupportBodyComponent)
        assertTrue(caps.isSupportEmotion)
        assertTrue(caps.isSupportFatigue)
        assertTrue(caps.isSupportBreath)
        assertTrue(caps.isSupportAlarm2)
        assertTrue(caps.isSupportHeartWarning)
        assertTrue(caps.isSupportLongSeat)
        assertTrue(caps.isSupportNightTurnWrist)
        assertTrue(caps.isSupportHealthRemind)
        assertTrue(caps.isSupportFindDeviceByPhone)
        assertTrue(caps.hasAdvancedDetect)
        assertTrue(caps.hasP1Settings)
    }
}
