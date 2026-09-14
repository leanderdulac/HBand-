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
}
