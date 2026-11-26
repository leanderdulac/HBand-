package com.example.data.hband

import java.util.UUID

/**
 * Decides whether a BLE peripheral is a VE30/HBand we may talk to with the
 * Veepoo SDK, and whether it belongs on the patient scan list.
 *
 * Galaxy Watch / Wear OS / Gear must never receive Veepoo writes.
 * Headphones, TVs and phones must not appear in the patient's list.
 *
 * Identity used by the Veepoo SDK (VPBleCentralManage / VPPeripheralModel):
 * - scan services FFFF, FEE7 and 0001, plus the Nordic UART this project already accepts
 * - manufacturer ID `f8f8` when `manufacturerIDFilter` is on (first two advertisement
 *   bytes). `f8f9` is the same parser with the MAC at a later offset, so it is kept
 *   too — dropping it would hide a real Veepoo layout.
 * The SDK only reads that ID when the manufacturer payload is at least 8 bytes.
 */
object VeepooDeviceSupport {
    val HBAND_PRIMARY_SERVICE_UUID: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
    val VEEPOO_NORDIC_UART_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val VEEPOO_FFFF_SERVICE_UUID: UUID = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")
    val VEEPOO_0001_SERVICE_UUID: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")

    val VEEPOO_SERVICE_UUIDS: Set<UUID> = setOf(HBAND_PRIMARY_SERVICE_UUID, VEEPOO_NORDIC_UART_UUID)

    /** Services the SDK scans for, plus the Nordic UART already treated as Veepoo here. */
    val VEEPOO_ADVERTISED_SERVICE_UUIDS: Set<UUID> = VEEPOO_SERVICE_UUIDS + setOf(
        VEEPOO_FFFF_SERVICE_UUID,
        VEEPOO_0001_SERVICE_UUID,
    )

    /**
     * Bluetooth company ID is little-endian. Bytes F8 F8 → 0xF8F8. Bytes F8 F9 → 0xF9F8.
     * [MANUFACTURER_MIN_PAYLOAD_BYTES] is the payload after those two bytes (8 total).
     */
    const val MANUFACTURER_F8F8: Int = 0xF8F8
    const val MANUFACTURER_F8F9: Int = 0xF9F8
    const val MANUFACTURER_MIN_PAYLOAD_BYTES: Int = 6

    const val INCOMPATIBLE_CARD_TEXT = "Este relógio não é compatível. Use o VE30."
    const val FORGET_WATCH_LABEL = "Esquecer relógio"
    const val COMPATIBLE_ONLY_HINT = "A lista mostra só relógios compatíveis com este aplicativo."
    const val UNNAMED_WATCH_LABEL = "Relógio"

    enum class Kind { SUPPORTED_VEEPOO, UNSUPPORTED, UNKNOWN }

    fun classify(
        name: String?,
        serviceUuids: Collection<UUID> = emptyList(),
        hasVeepooManufacturer: Boolean = false,
    ): Kind {
        val uuids = serviceUuids.toSet()
        if (hasVeepooManufacturer || uuids.any { it in VEEPOO_ADVERTISED_SERVICE_UUIDS }) {
            return Kind.SUPPORTED_VEEPOO
        }
        if (looksLikeUnsupportedWatch(name)) return Kind.UNSUPPORTED
        if (looksLikeVeepooName(name)) return Kind.SUPPORTED_VEEPOO
        if (uuids.isNotEmpty()) return Kind.UNSUPPORTED
        return Kind.UNKNOWN
    }

    fun maySendVeepooCommand(
        name: String?,
        serviceUuids: Collection<UUID> = emptyList(),
        hasVeepooManufacturer: Boolean = false,
    ): Boolean = classify(name, serviceUuids, hasVeepooManufacturer) == Kind.SUPPORTED_VEEPOO

    /**
     * Patient-facing scan row. A saved watch stays visible so a renamed band can
     * still be chosen and reconnected. Everything else must look like a Veepoo.
     */
    fun shouldListForPatient(
        name: String?,
        serviceUuids: Collection<UUID> = emptyList(),
        hasVeepooManufacturer: Boolean = false,
        address: String? = null,
        knownAddresses: Collection<String> = emptyList(),
    ): Boolean {
        if (matchesKnownAddress(address, knownAddresses)) return true
        return classify(name, serviceUuids, hasVeepooManufacturer) == Kind.SUPPORTED_VEEPOO
    }

    fun isVeepooManufacturer(companyId: Int, payloadLength: Int): Boolean {
        if (payloadLength < MANUFACTURER_MIN_PAYLOAD_BYTES) return false
        return companyId == MANUFACTURER_F8F8 || companyId == MANUFACTURER_F8F9
    }

    fun matchesKnownAddress(address: String?, knownAddresses: Collection<String>): Boolean {
        val needle = address?.trim().orEmpty()
        if (needle.isEmpty()) return false
        return knownAddresses.any { known ->
            val candidate = known.trim()
            candidate.isNotEmpty() && candidate.equals(needle, ignoreCase = true)
        }
    }

    fun looksLikeVeepooName(name: String?): Boolean {
        val compact = compactName(name)
        if (compact.isEmpty()) return false
        return compact.contains("ve30") ||
            compact.contains("hband") ||
            compact.contains("veepoo")
    }

    fun looksLikeUnsupportedWatch(name: String?): Boolean {
        val n = name?.trim()?.lowercase().orEmpty()
        if (n.isEmpty()) return false
        val compact = n.replace(" ", "")
        return n.contains("galaxy watch") ||
            compact.contains("galaxywatch") ||
            n.contains("watch8") ||
            n.contains("watch 8") ||
            n.contains("gear s") ||
            n.contains("wearos") ||
            n.contains("wear os") ||
            n.contains("samsung watch") ||
            compact.startsWith("sm-l") ||
            compact.startsWith("sm-r")
    }

    fun patientDisplayName(rawName: String?): String {
        val trimmed = rawName?.trim().orEmpty()
        return trimmed.ifBlank { UNNAMED_WATCH_LABEL }
    }

    private fun compactName(name: String?): String =
        name?.trim()?.lowercase().orEmpty().filter { it.isLetterOrDigit() }
}
