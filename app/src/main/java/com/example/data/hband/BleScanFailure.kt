package com.example.data.hband

/** Local scan outcome only; never represents connection or telemetry delivery. */
enum class BleScanFailure {
    PERMISSION_REQUIRED,
    BLUETOOTH_OFF,
    SCANNER_UNAVAILABLE,
    SCAN_FAILED,
    STOP_FAILED,
}

internal class BleScanUnavailableException(val reason: BleScanFailure) : IllegalStateException()
