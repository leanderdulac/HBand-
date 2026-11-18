package com.next2u.healthsync.shared.store

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmAesGcmSnapshotCodecTest {
    @Test
    fun encrypt_roundtrip_hides_plaintext() {
        val dir = Files.createTempDirectory("next2u-store-").toFile()
        val codec = JvmAesGcmSnapshotCodec.isolated(dir)
        PatientStore.resetForTests(codec)
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertTrue(PatientStore.saveProfile(UserProfile(fullName = "Cipher")))

        val blob = File(dir, "snapshot.bin").readBytes()
        assertTrue(blob.isNotEmpty())
        assertFalse(blob.decodeToString().contains("Cipher"))

        PatientStore.useCodec(JvmAesGcmSnapshotCodec.isolated(dir))
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertEquals("Cipher", PatientStore.profile().fullName)
    }

    @Test
    fun unknown_schema_fails_and_leaves_ciphertext() {
        val dir = Files.createTempDirectory("next2u-store-schema-").toFile()
        val codec = JvmAesGcmSnapshotCodec.isolated(dir)
        PatientStore.resetForTests(codec)
        assertEquals(StoreStatus.READY, PatientStore.open())
        val key = File(dir, "snapshot.key").readBytes()
        val badJson = """{"schemaVersion":7}"""
        val badBlob = JvmAesGcmSnapshotCodec.encrypt(badJson.encodeToByteArray(), key)
        val blob = File(dir, "snapshot.bin")
        blob.writeBytes(badBlob)

        PatientStore.useCodec(JvmAesGcmSnapshotCodec.isolated(dir))
        assertEquals(StoreStatus.FAILED, PatientStore.open())
        assertTrue(badBlob.contentEquals(blob.readBytes()))
        assertTrue(PatientStore.detail.contains("esquema"))
    }

    @Test
    fun missing_key_with_blob_fails_and_does_not_rewrite() {
        val dir = Files.createTempDirectory("next2u-store-nokey-").toFile()
        val codec = JvmAesGcmSnapshotCodec.isolated(dir)
        PatientStore.resetForTests(codec)
        assertEquals(StoreStatus.READY, PatientStore.open())
        val blob = File(dir, "snapshot.bin")
        val before = blob.readBytes()
        File(dir, "snapshot.key").delete()
        PatientStore.useCodec(JvmAesGcmSnapshotCodec.isolated(dir))
        assertEquals(StoreStatus.FAILED, PatientStore.open())
        assertTrue(before.contentEquals(blob.readBytes()))
    }
}
