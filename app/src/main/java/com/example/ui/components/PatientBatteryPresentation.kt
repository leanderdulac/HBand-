package com.example.ui.components

import com.example.data.model.HBandDevice

/** Presentation only: simulated values must not reach the patient interface. */
internal fun visibleWatchBattery(device: HBandDevice?): Int? {
    if (device == null || device.batteryIsSimulated) return null
    return device.batteryLevel?.takeIf { it in 0..100 }
}
