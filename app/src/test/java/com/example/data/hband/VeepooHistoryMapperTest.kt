package com.example.data.hband

import com.veepoo.protocol.model.datas.HRVOriginData
import com.veepoo.protocol.model.datas.OriginData
import com.veepoo.protocol.model.datas.SleepData
import com.veepoo.protocol.model.datas.Spo2hOriginData
import com.veepoo.protocol.model.datas.TimeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooHistoryMapperTest {

    private val time = TimeData(2026, 9, 10, 8, 15, 0)

    @Test
    fun `empty origin sample is discarded instead of fabricating vitals`() {
        val origin = OriginData().apply {
            date = "2026-09-10"
            setmTime(time)
            rateValue = 0
            highValue = 0
            lowValue = 0
            stepValue = 0
        }
        assertNull(VeepooHistoryMapper.fromOrigin(origin, "AA:BB", "VE30"))
    }

    @Test
    fun `origin maps only real HR and steps`() {
        val origin = OriginData().apply {
            date = "2026-09-10"
            setmTime(time)
            rateValue = 74
            stepValue = 320
            highValue = 0
            lowValue = 0
        }
        val mapped = VeepooHistoryMapper.fromOrigin(origin, "AA:BB", "VE30")
        assertNotNull(mapped)
        assertEquals(74, mapped!!.telemetry.heartRate)
        assertEquals(320, mapped.telemetry.steps)
        assertEquals(0, mapped.telemetry.bloodPressure.systolic)
        assertEquals(0, mapped.telemetry.spO2)
        assertEquals(0, mapped.telemetry.hrvScore)
        assertTrue(mapped.telemetry.isRealSensorData)
        assertTrue(mapped.telemetry.timestamp.endsWith("Z"))
    }

    @Test
    fun `sleep zeros are discarded and rem is never invented`() {
        val empty = SleepData().apply {
            date = "2026-09-10"
            deepSleepTime = 0
            lowSleepTime = 0
            allSleepTime = 0
            sleepDown = time
        }
        assertNull(VeepooHistoryMapper.fromSleep(empty, "AA:BB", "VE30"))

        val real = SleepData().apply {
            date = "2026-09-10"
            deepSleepTime = 90
            lowSleepTime = 210
            allSleepTime = 320
            sleepDown = time
        }
        val mapped = VeepooHistoryMapper.fromSleep(real, "AA:BB", "VE30")
        assertNotNull(mapped)
        assertEquals(90, mapped!!.telemetry.sleepSummary.deepSleepMinutes)
        assertEquals(210, mapped.telemetry.sleepSummary.lightSleepMinutes)
        assertEquals(20, mapped.telemetry.sleepSummary.awakeMinutes)
        assertEquals(0, mapped.telemetry.heartRate)
    }

    @Test
    fun `hrv and spo2 require a real value`() {
        val emptyHrv = HRVOriginData().apply {
            date = "2026-09-10"
            setmTime(time)
            hrvValue = 0
        }
        assertNull(VeepooHistoryMapper.fromHrv(emptyHrv, "AA:BB", "VE30"))

        val hrv = HRVOriginData().apply {
            date = "2026-09-10"
            setmTime(time)
            hrvValue = 62
            rate = "71"
        }
        val mappedHrv = VeepooHistoryMapper.fromHrv(hrv, "AA:BB", "VE30")
        assertEquals(62, mappedHrv!!.telemetry.hrvScore)
        assertEquals(71, mappedHrv.telemetry.heartRate)

        val emptySpo2 = Spo2hOriginData().apply {
            date = "2026-09-10"
            setmTime(time)
            oxygenValue = 0
            heartValue = 0
        }
        assertNull(VeepooHistoryMapper.fromSpo2(emptySpo2, "AA:BB", "VE30"))

        val spo2 = Spo2hOriginData().apply {
            date = "2026-09-10"
            setmTime(time)
            oxygenValue = 97
            heartValue = 68
        }
        val mappedSpo2 = VeepooHistoryMapper.fromSpo2(spo2, "AA:BB", "VE30")
        assertEquals(97, mappedSpo2!!.telemetry.spO2)
        assertEquals(68, mappedSpo2.telemetry.heartRate)
        assertFalse(mappedSpo2.telemetry.bloodPressure.systolic > 0)
    }

    @Test
    fun `iso parse roundtrip does not invent a timestamp`() {
        assertEquals(0L, VeepooHistoryMapper.parseIsoToMillis(""))
        val iso = VeepooHistoryMapper.isoUtc(1_725_000_000_000L)
        assertEquals(1_725_000_000_000L, VeepooHistoryMapper.parseIsoToMillis(iso))
    }
}
