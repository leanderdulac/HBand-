package com.next2u.healthsync.shared.store

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyQueueMigrationTest {
    @BeforeTest
    fun reset() {
        PatientStore.resetForTests(MemorySnapshotCodec())
    }

    @Test
    fun fresh_open_imports_plaintext_queue_then_deletes_it() {
        var file = """[{"clientReadingId":"reading-1","payloadJson":"{\"heart\":72}","status":"PENDING","queuedAtMillis":5}]"""
        var deleted = false
        PatientStore.legacyQueueReader = { file }
        PatientStore.legacyQueueDeleter = {
            deleted = true
            file = "[]"
            true
        }
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertEquals(1, PatientStore.queue().size)
        assertEquals("reading-1", PatientStore.queue().single().clientReadingId)
        assertEquals("PENDING", PatientStore.queue().single().status)
        assertTrue(deleted)
        assertTrue(PatientStore.queue().single().payloadJson.contains("72"))
    }

    @Test
    fun existing_snapshot_absorbs_only_missing_readings_then_deletes_plaintext() {
        val codec = MemorySnapshotCodec(
            """{"schemaVersion":1,"queue":[{"clientReadingId":"already","payloadJson":"{}","status":"PENDING","queuedAtMillis":1}]}""",
        )
        PatientStore.useCodec(codec)
        var file = """[{"clientReadingId":"already","payloadJson":"{}","status":"PENDING","queuedAtMillis":1},{"clientReadingId":"unsent","payloadJson":"{\"spo2\":98}","status":"PENDING","queuedAtMillis":2}]"""
        var deleted = false
        PatientStore.legacyQueueReader = { file }
        PatientStore.legacyQueueDeleter = {
            deleted = true
            file = "[]"
            true
        }
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertEquals(listOf("already", "unsent"), PatientStore.queue().map { it.clientReadingId })
        assertTrue(deleted)
        assertTrue(codec.plaintext.orEmpty().contains("unsent"))
    }

    @Test
    fun unreadable_plaintext_queue_is_not_deleted() {
        PatientStore.legacyQueueReader = { "not-json" }
        var deleted = false
        PatientStore.legacyQueueDeleter = {
            deleted = true
            true
        }
        assertEquals(StoreStatus.READY, PatientStore.open())
        assertEquals(0, PatientStore.queue().size)
        assertFalse(deleted)
    }
}
