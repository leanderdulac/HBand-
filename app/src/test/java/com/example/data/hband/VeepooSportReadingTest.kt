package com.example.data.hband

import com.veepoo.protocol.model.datas.SportData
import com.veepoo.protocol.model.datas.OriginData
import com.veepoo.protocol.model.datas.TimeData
import org.junit.Assert.*
import org.junit.Test

class VeepooSportReadingTest {
    private val observed = 1_790_294_399_000L

    @Test fun copies_current_sdk_counters_and_converts_kilometres_to_metres() {
        val sdk = SportData().apply { step = 2400; kcal = 82.5; dis = 1.25 }
        val reading = VeepooSportReading.fromSdk(sdk, observed)!!
        sdk.step = 5
        sdk.dis = 99.0
        assertEquals(2400, reading.steps)
        assertEquals(82.5f, reading.calories!!, 0f)
        assertEquals(1250f, reading.distanceMeters!!, 0f)
        assertEquals(observed, reading.observedAtMillis)
    }

    @Test fun zeros_are_valid_new_counters_and_are_not_replaced_by_previous_totals() {
        val reading = VeepooSportReading.fromSdk(SportData().apply { step = 0; kcal = 0.0; dis = 0.0 }, observed)!!
        assertEquals(0, reading.steps)
        assertEquals(0f, reading.calories!!, 0f)
        assertEquals(0f, reading.distanceMeters!!, 0f)
    }

    @Test fun invalid_fields_do_not_become_infinity_or_a_fabricated_measurement() {
        for (invalid in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            val reading = VeepooSportReading.fromSdk(SportData().apply {
                step = 100; kcal = invalid; dis = invalid
            }, observed)!!
            assertEquals(100, reading.steps)
            assertNull(reading.calories)
            assertNull(reading.distanceMeters)
        }
        assertNull(VeepooSportReading.fromSdk(SportData().apply { step = -1; kcal = -1.0; dis = -1.0 }, observed))
        assertNull(VeepooSportReading.fromSdk(null, observed))
        assertNull(VeepooSportReading.fromSdk(SportData(), 0))
    }

    @Test fun origin_distance_uses_same_documented_unit_and_keeps_original_measurement_time() {
        val source = OriginData().apply {
            date = "2026-09-23"
            setmTime(TimeData(2026, 9, 23, 8, 15, 0))
            rateValue = 72; stepValue = 50; calValue = 2.5; disValue = 0.025
        }
        val mapped = VeepooHistoryMapper.fromOrigin(source, "TEST-WATCH", "VE30")!!
        assertEquals(25f, mapped.telemetry.distanceMeters, 0f)
        assertEquals(50, mapped.telemetry.steps)
        assertEquals(2.5f, mapped.telemetry.calories, 0f)
        // The existing SDK mapper serializes packet time at whole-second precision.
        assertEquals(mapped.epochMs / 1000, VeepooHistoryMapper.parseIsoToMillis(mapped.telemetry.timestamp) / 1000)
        source.disValue = Double.NaN
        assertEquals(0f, VeepooHistoryMapper.fromOrigin(source, "TEST-WATCH", "VE30")!!.telemetry.distanceMeters, 0f)
    }
}
