package com.next2u.healthsync.shared.ingest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceIdentityTest {
    @Test
    fun uppercase_colon_mac_like_android() {
        assertEquals("E9:11:22:33:44:40", DeviceIdentity.normalizeMac("e9:11:22:33:44:40"))
        assertEquals("E9:11:22:33:44:40", DeviceIdentity.normalizeMac("E9-11-22-33-44-40"))
        assertEquals("E9:11:22:33:44:40", DeviceIdentity.normalizeMac(" e91122334440 "))
        assertEquals("E9:11:22:33:44:40", DeviceIdentity.normalizeMac("E9:11:22:33:44:40"))
    }

    @Test
    fun corebluetooth_uuid_is_not_a_mac() {
        val uuid = "B7D79050-C055-4A1E-9C3D-1234567890AB"
        assertNull(DeviceIdentity.normalizeMac(uuid))
        assertTrue(DeviceIdentity.isCoreBluetoothUuid(uuid))
        assertFalse(DeviceIdentity.isCoreBluetoothUuid("E9:11:22:33:44:40"))
        assertEquals(uuid, DeviceIdentity.ingestDeviceId(null, uuid))
    }

    @Test
    fun sdk_mac_wins_over_uuid() {
        val uuid = "B7D79050-C055-4A1E-9C3D-1234567890AB"
        assertEquals(
            "E9:11:22:33:44:40",
            DeviceIdentity.ingestDeviceId("e9:11:22:33:44:40", uuid),
        )
    }

    @Test
    fun history_ids_change_when_device_id_switches_from_uuid_to_mac() {
        val ts = 1_700_000_000_000L
        val fromUuid = IngestReadingIdentity.historyId(
            "B7D79050-C055-4A1E-9C3D-1234567890AB",
            "origin",
            ts,
        )
        val fromMac = IngestReadingIdentity.historyId("E9:11:22:33:44:40", "origin", ts)
        assertNotEquals(fromUuid, fromMac)
        assertEquals("E9:11:22:33:44:40-origin-1700000000000", fromMac)
    }
}
