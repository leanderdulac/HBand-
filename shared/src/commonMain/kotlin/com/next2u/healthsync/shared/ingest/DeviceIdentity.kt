package com.next2u.healthsync.shared.ingest

/**
 * Watch identity for Core ingest. Android uses the Bluetooth MAC
 * (`AA:BB:CC:DD:EE:FF`). iOS CoreBluetooth otherwise exposes a UUID that
 * is not the radio address.
 */
object DeviceIdentity {
    private val uuid = Regex(
        "^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$",
    )

    /**
     * Uppercase colon MAC (`E9:11:22:33:44:40`). Accepts colons, dashes or
     * 12 hex digits. CoreBluetooth UUIDs return null — they are not MACs.
     */
    fun normalizeMac(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || uuid.matches(trimmed)) return null
        val hex = trimmed.uppercase().filter { it in "0123456789ABCDEF" }
        if (hex.length != 12) return null
        return hex.chunked(2).joinToString(":")
    }

    fun isCoreBluetoothUuid(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        return trimmed.isNotEmpty() && uuid.matches(trimmed)
    }

    /**
     * Prefer the SDK MAC; fall back to the CoreBluetooth UUID only when the
     * SDK did not expose an address. Switching UUID → MAC changes
     * `client_reading_id` (Core dedup is per id).
     */
    fun ingestDeviceId(sdkAddress: String?, coreBluetoothUuid: String?): String {
        normalizeMac(sdkAddress)?.let { return it }
        normalizeMac(coreBluetoothUuid)?.let { return it }
        return coreBluetoothUuid?.trim().orEmpty()
    }
}
