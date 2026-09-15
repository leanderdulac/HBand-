package com.example.data.hband

import com.veepoo.protocol.model.datas.BatteryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VeepooBatteryMapperTest {

    @Test
    fun `null SDK payload is unknown not a placeholder percent`() {
        assertNull(VeepooBatteryMapper.fromSdk(null))
        assertNull(VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = -1))
    }

    @Test
    fun `isPercent uses batteryPercent including zero`() {
        assertEquals(
            18,
            VeepooBatteryMapper.fromSdkFields(isPercent = true, batteryPercent = 18, batteryLevel = 1),
        )
        assertEquals(
            0,
            VeepooBatteryMapper.fromSdkFields(isPercent = true, batteryPercent = 0, batteryLevel = 4),
        )
        assertEquals(
            90,
            VeepooBatteryMapper.fromSdk(
                BatteryData().apply {
                    setPercent(true)
                    batteryPercent = 90
                    batteryLevel = 4
                },
            ),
        )
    }

    @Test
    fun `isPercent rejects out of range percent`() {
        assertNull(
            VeepooBatteryMapper.fromSdkFields(isPercent = true, batteryPercent = 101, batteryLevel = 4),
        )
        assertNull(
            VeepooBatteryMapper.fromSdkFields(isPercent = true, batteryPercent = -3, batteryLevel = 2),
        )
    }

    @Test
    fun `coarse bar is mapped to buckets not displayed as 1 to 4 percent`() {
        assertEquals(25, VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 1))
        assertEquals(50, VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 2))
        assertEquals(75, VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 3))
        assertEquals(100, VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 4))
        assertEquals(0, VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 0))
    }

    @Test
    fun `firmware stuffing a real percent into batteryLevel is kept`() {
        assertEquals(
            67,
            VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 67),
        )
        assertEquals(
            18,
            VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 18, batteryLevel = 1),
        )
    }

    @Test
    fun `display label is unknown until a mapped percent exists`() {
        assertEquals("--", VeepooBatteryMapper.displayLabel(null))
        assertEquals("--", VeepooBatteryMapper.displayLabel(null, simulated = true))
        assertEquals("18%", VeepooBatteryMapper.displayLabel(18))
        assertEquals("14% sim.", VeepooBatteryMapper.displayLabel(14, simulated = true))
        assertEquals("90%", VeepooBatteryMapper.displayLabel(90))
    }

    @Test
    fun `90 percent is only shown when SDK percent field is 90`() {
        assertNull(VeepooBatteryMapper.fromSdk(null))
        assertEquals(
            100,
            VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 0, batteryLevel = 4),
        )
        assertEquals(
            90,
            VeepooBatteryMapper.fromSdkFields(isPercent = true, batteryPercent = 90, batteryLevel = 4),
        )
        assertEquals(
            90,
            VeepooBatteryMapper.fromSdkFields(isPercent = false, batteryPercent = 90, batteryLevel = 4),
        )
    }
}
