package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WatchScanFilterTest {
    private val fee7 = VeepooDeviceSupport.HBAND_PRIMARY_SERVICE_UUID
    private val nordic = VeepooDeviceSupport.VEEPOO_NORDIC_UART_UUID
    private val ffff = VeepooDeviceSupport.VEEPOO_FFFF_SERVICE_UUID
    private val service0001 = VeepooDeviceSupport.VEEPOO_0001_SERVICE_UUID
    private val heartRate = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val appleCompanyId = 0x004C
    private val savedMac = "AA:BB:CC:DD:EE:FF"

    @Test
    fun veepoo_names_are_listed_regardless_of_case_or_separators() {
        for (name in listOf(
            "VE30",
            "ve30",
            "Ve30",
            "VE 30",
            "VE-30",
            "VE_30",
            "  VE30  ",
            "HBand",
            "H Band",
            "H-Band",
            "h_band",
            "Veepoo",
            "veepoo band",
            "VEEPOO",
        )) {
            assertTrue(name, VeepooDeviceSupport.shouldListForPatient(name))
        }
    }

    @Test
    fun everyday_bluetooth_devices_are_not_listed() {
        for (name in listOf(
            "AirPods Pro",
            "AIRPODS",
            "Galaxy Buds",
            "JBL Flip 6",
            "Fone Bluetooth",
            "Samsung TV",
            "[TV] Samsung",
            "Smart TV LG",
            "iPhone",
            "iPhone de Maria",
            "Galaxy Watch8",
            "Galaxy Watch 8",
            "Gear S3",
            "Wear OS",
            "SM-R860",
            "Pulseira",
            "Relógio",
        )) {
            assertFalse(name, VeepooDeviceSupport.shouldListForPatient(name))
        }
    }

    @Test
    fun null_blank_and_empty_names_are_not_listed_without_another_signal() {
        assertFalse(VeepooDeviceSupport.shouldListForPatient(null))
        assertFalse(VeepooDeviceSupport.shouldListForPatient(""))
        assertFalse(VeepooDeviceSupport.shouldListForPatient("   "))
        assertFalse(VeepooDeviceSupport.shouldListForPatient("\n"))
        assertEquals("Relógio", VeepooDeviceSupport.patientDisplayName(null))
        assertEquals("Relógio", VeepooDeviceSupport.patientDisplayName("  "))
        assertEquals("VE30", VeepooDeviceSupport.patientDisplayName(" VE30 "))
    }

    @Test
    fun veepoo_service_uuids_list_a_watch_even_without_a_name() {
        for (uuid in listOf(fee7, nordic, ffff, service0001)) {
            assertTrue(
                uuid.toString(),
                VeepooDeviceSupport.shouldListForPatient(null, listOf(uuid)),
            )
            assertEquals(
                VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO,
                VeepooDeviceSupport.classify("Ana", listOf(uuid)),
            )
        }
        assertTrue(
            VeepooDeviceSupport.shouldListForPatient(
                "Watch",
                listOf(UUID.fromString("0000FEE7-0000-1000-8000-00805F9B34FB")),
            ),
        )
    }

    @Test
    fun standard_heart_rate_uuid_does_not_list_a_common_peripheral() {
        assertFalse(VeepooDeviceSupport.shouldListForPatient("Polar H10", listOf(heartRate)))
        assertFalse(VeepooDeviceSupport.shouldListForPatient(null, listOf(heartRate)))
        assertEquals(
            VeepooDeviceSupport.Kind.UNSUPPORTED,
            VeepooDeviceSupport.classify("Pulseira", listOf(heartRate)),
        )
    }

    @Test
    fun veepoo_manufacturer_id_lists_a_renamed_watch() {
        assertTrue(VeepooDeviceSupport.isVeepooManufacturer(VeepooDeviceSupport.MANUFACTURER_F8F8, 6))
        assertTrue(VeepooDeviceSupport.isVeepooManufacturer(VeepooDeviceSupport.MANUFACTURER_F8F9, 20))
        assertFalse(VeepooDeviceSupport.isVeepooManufacturer(VeepooDeviceSupport.MANUFACTURER_F8F8, 5))
        assertFalse(VeepooDeviceSupport.isVeepooManufacturer(appleCompanyId, 20))
        assertFalse(VeepooDeviceSupport.isVeepooManufacturer(0xFFFF, 8))

        assertTrue(VeepooDeviceSupport.shouldListForPatient("Ana", hasVeepooManufacturer = true))
        assertTrue(VeepooDeviceSupport.shouldListForPatient(null, hasVeepooManufacturer = true))
        assertEquals(
            VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO,
            VeepooDeviceSupport.classify("Ana", hasVeepooManufacturer = true),
        )
        assertTrue(
            VeepooDeviceSupport.maySendVeepooCommand("Ana", hasVeepooManufacturer = true),
        )
    }

    @Test
    fun saved_watch_stays_listed_after_it_was_renamed() {
        assertTrue(
            VeepooDeviceSupport.shouldListForPatient(
                "Ana",
                address = "aa:bb:cc:dd:ee:ff",
                knownAddresses = listOf(savedMac),
            ),
        )
        assertTrue(
            VeepooDeviceSupport.shouldListForPatient(
                "Samsung TV",
                address = savedMac,
                knownAddresses = listOf("  $savedMac  "),
            ),
        )
        assertFalse(
            VeepooDeviceSupport.shouldListForPatient(
                "Samsung TV",
                address = "11:22:33:44:55:66",
                knownAddresses = listOf(savedMac),
            ),
        )
        assertFalse(
            VeepooDeviceSupport.shouldListForPatient(
                null,
                address = "",
                knownAddresses = listOf(""),
            ),
        )
        assertFalse(
            VeepooDeviceSupport.shouldListForPatient(
                "AirPods Pro",
                address = null,
                knownAddresses = listOf(savedMac),
            ),
        )
    }
}
