package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VeepooDeviceSupportTest {
    @Test
    fun galaxy_watch_8_is_unsupported_even_without_uuids() {
        assertEquals(
            VeepooDeviceSupport.Kind.UNSUPPORTED,
            VeepooDeviceSupport.classify("Galaxy Watch8"),
        )
        assertEquals(
            VeepooDeviceSupport.Kind.UNSUPPORTED,
            VeepooDeviceSupport.classify("Galaxy Watch 8 (Leandro)"),
        )
        assertFalse(VeepooDeviceSupport.maySendVeepooCommand("Galaxy Watch8"))
    }

    @Test
    fun samsung_and_wearos_names_are_unsupported() {
        for (name in listOf("Gear S3", "Wear OS Watch", "WearOS", "Samsung Watch", "SM-L320", "SM-R860")) {
            assertEquals(name, VeepooDeviceSupport.Kind.UNSUPPORTED, VeepooDeviceSupport.classify(name))
        }
    }

    @Test
    fun ve30_name_pattern_is_supported() {
        for (name in listOf("VE30", "VE30 de teste", "VE 30 Smart Band", "HBand V100 Pro", "Veepoo Band")) {
            assertEquals(name, VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO, VeepooDeviceSupport.classify(name))
            assertTrue(VeepooDeviceSupport.maySendVeepooCommand(name))
        }
    }

    @Test
    fun advertised_veepoo_uuid_wins_over_samsung_like_label() {
        assertEquals(
            VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO,
            VeepooDeviceSupport.classify(
                "Watch",
                listOf(VeepooDeviceSupport.HBAND_PRIMARY_SERVICE_UUID),
            ),
        )
        assertEquals(
            VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO,
            VeepooDeviceSupport.classify(
                null,
                listOf(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")),
            ),
        )
    }

    @Test
    fun advertised_non_veepoo_uuid_is_unsupported() {
        assertEquals(
            VeepooDeviceSupport.Kind.UNSUPPORTED,
            VeepooDeviceSupport.classify(
                "Pulseira",
                listOf(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")),
            ),
        )
    }

    @Test
    fun unknown_without_name_or_uuid_does_not_authorize_veepoo_writes() {
        assertEquals(VeepooDeviceSupport.Kind.UNKNOWN, VeepooDeviceSupport.classify(null))
        assertEquals(VeepooDeviceSupport.Kind.UNKNOWN, VeepooDeviceSupport.classify("  "))
        assertFalse(VeepooDeviceSupport.maySendVeepooCommand("Relógio"))
    }

    @Test
    fun card_copy_is_the_approved_portuguese() {
        assertEquals("Este relógio não é compatível. Use o VE30.", VeepooDeviceSupport.INCOMPATIBLE_CARD_TEXT)
        assertEquals("Esquecer relógio", VeepooDeviceSupport.FORGET_WATCH_LABEL)
    }
}
