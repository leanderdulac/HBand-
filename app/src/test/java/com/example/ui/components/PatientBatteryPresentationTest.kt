package com.example.ui.components

import com.example.data.model.HBandDevice
import org.junit.Assert.*
import org.junit.Test

class PatientBatteryPresentationTest {
    @Test fun release_presentation_rejects_simulation_without_promoting_it_to_a_real_reading() {
        val simulated = HBandDevice(batteryLevel = 12, batteryIsSimulated = true)
        assertNull(visibleWatchBattery(simulated, allowSimulation = false))
        assertEquals(12, visibleWatchBattery(simulated, allowSimulation = true))
    }

    @Test fun invalid_or_absent_battery_stays_unknown_and_zero_remains_a_valid_charge() {
        assertNull(visibleWatchBattery(null))
        assertNull(visibleWatchBattery(HBandDevice(batteryLevel = null)))
        assertNull(visibleWatchBattery(HBandDevice(batteryLevel = -1)))
        assertNull(visibleWatchBattery(HBandDevice(batteryLevel = 101)))
        assertEquals(0, visibleWatchBattery(HBandDevice(batteryLevel = 0), allowSimulation = false))
    }

    @Test fun disconnected_reading_is_preserved_as_the_last_reported_charge() {
        assertEquals(18, visibleWatchBattery(HBandDevice(isConnected = false, batteryLevel = 18), allowSimulation = false))
    }
}
