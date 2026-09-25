package com.example.data.hband

import android.bluetooth.BluetoothDevice

/** Permission can be revoked between receiving a GATT callback and reading the device name. */
internal fun bluetoothDeviceNameOrFallback(device: BluetoothDevice?, knownName: String): String =
    try {
        device?.name ?: knownName
    } catch (_: SecurityException) {
        // Retain the existing label only; never retry a protected read or change device identity.
        knownName
    }
