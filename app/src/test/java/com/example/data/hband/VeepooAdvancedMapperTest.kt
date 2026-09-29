package com.example.data.hband

import com.example.data.local.AdvancedMeasurementKind
import com.veepoo.protocol.model.datas.BloodComponent
import com.veepoo.protocol.model.datas.BodyComponent
import com.veepoo.protocol.model.datas.BreathData
import com.veepoo.protocol.model.datas.EcgDetectResult
import com.veepoo.protocol.model.datas.FatigueData
import com.veepoo.protocol.model.enums.EDeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooAdvancedMapperTest {

    @Test
    fun `empty unsuccessful ECG is discarded instead of inventing a waveform`() {
        val result = EcgDetectResult().apply {
            isSuccess = false
            aveHeart = 0
            aveHrv = 0
            duration = 0
            originSign = intArrayOf()
            filterSignals = intArrayOf()
        }
        assertNull(VeepooAdvancedMapper.fromEcgResult(result, "AA:BB"))
    }

    @Test
    fun `successful ECG keeps real average HR and ADC samples`() {
        val result = EcgDetectResult().apply {
            isSuccess = true
            aveHeart = 68
            aveHrv = 42
            duration = 30
            filterSignals = intArrayOf(12, 18, 9, 21)
        }
        val mapped = VeepooAdvancedMapper.fromEcgResult(result, "AA:BB")
        assertNotNull(mapped)
        assertEquals(AdvancedMeasurementKind.ECG, mapped!!.kind)
        assertEquals(68f, mapped.numericValue)
        assertEquals(42f, mapped.secondaryValue)
        assertEquals(4, mapped.sampleCount)
        assertTrue(mapped.isReal)
        assertTrue(mapped.summary.contains("68"))
        assertFalse(mapped.payloadJson.contains("sine"))
    }

    @Test
    fun `glucose in progress or zero is not persisted`() {
        assertNull(VeepooAdvancedMapper.fromGlucose(5.4f, progress = 40, riskName = null, deviceId = "AA:BB"))
        assertNull(VeepooAdvancedMapper.fromGlucose(0f, progress = 100, riskName = null, deviceId = "AA:BB"))
        assertNull(VeepooAdvancedMapper.fromGlucose(999f, progress = 100, riskName = null, deviceId = "AA:BB"))
    }

    @Test
    fun `completed glucose keeps the firmware value without unit conversion`() {
        val mmol = VeepooAdvancedMapper.fromGlucose(5.4f, progress = 100, riskName = "LOW", deviceId = "AA:BB")
        assertNotNull(mmol)
        assertEquals(5.4f, mmol!!.numericValue)
        assertEquals(AdvancedMeasurementKind.GLUCOSE, mmol.kind)

        val mgdl = VeepooAdvancedMapper.fromGlucose(96f, progress = 100, riskName = null, deviceId = "AA:BB")
        assertNotNull(mgdl)
        assertEquals(96f, mgdl!!.numericValue)
    }

    @Test
    fun `blood and body composition require at least one real field`() {
        assertNull(VeepooAdvancedMapper.fromBloodComponent(BloodComponent(0f, 0f, 0f, 0f, 0f), "AA:BB"))
        val blood = VeepooAdvancedMapper.fromBloodComponent(BloodComponent(0.32f, 4.1f, 0f, 0f, 0f), "AA:BB")
        assertNotNull(blood)
        assertEquals(0.32f, blood!!.numericValue, 0.001f)

        assertNull(VeepooAdvancedMapper.fromBodyComponent(BodyComponent(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), "AA:BB"))
        val body = BodyComponent(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).apply {
            BMI = 24.1f
            bodyFatRate = 18.2f
        }
        val mappedBody = VeepooAdvancedMapper.fromBodyComponent(body, "AA:BB")
        assertNotNull(mappedBody)
        assertEquals(24.1f, mappedBody!!.numericValue, 0.001f)
        assertEquals(18.2f, mappedBody.secondaryValue, 0.001f)
    }

    @Test
    fun `emotion fatigue and breath ignore unfinished packets`() {
        assertNull(VeepooAdvancedMapper.fromEmotion(progress = 50, value = 12, deviceId = "AA:BB"))
        assertNull(VeepooAdvancedMapper.fromEmotion(progress = 100, value = 0, deviceId = "AA:BB"))
        val emotion = VeepooAdvancedMapper.fromEmotion(progress = 100, value = 12, deviceId = "AA:BB")
        assertEquals(12f, emotion!!.numericValue)

        val unfinished = FatigueData().apply {
            progress = 40
            value = 8
            deviceState = EDeviceStatus.BUSY
        }
        assertNull(VeepooAdvancedMapper.fromFatigue(unfinished, "AA:BB"))
        val fatigue = FatigueData().apply {
            progress = 100
            value = 8
            deviceState = EDeviceStatus.FINISH
        }
        assertEquals(8f, VeepooAdvancedMapper.fromFatigue(fatigue, "AA:BB")!!.numericValue)

        val breathBusy = BreathData().apply {
            progressValue = 20
            value = 16
        }
        assertNull(VeepooAdvancedMapper.fromBreath(breathBusy, "AA:BB"))
        val breath = BreathData().apply {
            progressValue = 100
            value = 16
        }
        assertEquals(16f, VeepooAdvancedMapper.fromBreath(breath, "AA:BB")!!.numericValue)
    }
}
