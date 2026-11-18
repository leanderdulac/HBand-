package com.example.data.hband

import java.util.UUID

/**
 * Decides whether a BLE peripheral is a VE30/HBand we may talk to with the
 * Veepoo SDK. Galaxy Watch / Wear OS / Gear must never receive Veepoo writes.
 */
object VeepooDeviceSupport {
    val HBAND_PRIMARY_SERVICE_UUID: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
    val VEEPOO_NORDIC_UART_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    val VEEPOO_SERVICE_UUIDS: Set<UUID> = setOf(HBAND_PRIMARY_SERVICE_UUID, VEEPOO_NORDIC_UART_UUID)

    const val INCOMPATIBLE_CARD_TEXT = "Este relógio não é compatível. Use o VE30."
    const val FORGET_WATCH_LABEL = "Esquecer relógio"

    enum class Kind { SUPPORTED_VEEPOO, UNSUPPORTED, UNKNOWN }

    fun classify(name: String?, serviceUuids: Collection<UUID> = emptyList()): Kind {
        val uuids = serviceUuids.map { it }.toSet()
        if (uuids.any { it in VEEPOO_SERVICE_UUIDS }) return Kind.SUPPORTED_VEEPOO
        if (looksLikeUnsupportedWatch(name)) return Kind.UNSUPPORTED
        if (looksLikeVeepooName(name)) return Kind.SUPPORTED_VEEPOO
        if (uuids.isNotEmpty()) return Kind.UNSUPPORTED
        return Kind.UNKNOWN
    }

    fun maySendVeepooCommand(name: String?, serviceUuids: Collection<UUID> = emptyList()): Boolean =
        classify(name, serviceUuids) == Kind.SUPPORTED_VEEPOO

    fun looksLikeVeepooName(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return false
        val compact = n.lowercase().replace(" ", "")
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
}
