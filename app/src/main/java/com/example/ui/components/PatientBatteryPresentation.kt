package com.example.ui.components

import com.example.BuildConfig
import com.example.data.model.HBandDevice

/** Presentation only: simulated values must not reach the patient's release interface. */
internal fun visibleWatchBattery(device: HBandDevice?, allowSimulation: Boolean = BuildConfig.DEBUG): Int? {
    if (device == null || (device.batteryIsSimulated && !allowSimulation)) return null
    return device.batteryLevel?.takeIf { it in 0..100 }
}
