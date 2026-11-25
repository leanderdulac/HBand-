package com.next2u.healthsync.shared.store

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotAtRestTest {
    @Test
    fun plaintext_snapshot_json_is_migrated() {
        val json = """{"schemaVersion":1,"queue":[{"clientReadingId":"abc","payloadJson":"{}","status":"PENDING","queuedAtMillis":1}]}"""
        assertTrue(SnapshotAtRest.shouldMigratePlaintext(json))
        assertTrue(SnapshotAtRest.shouldMigratePlaintext("  $json\n"))
    }

    @Test
    fun ciphertext_and_incomplete_json_stay_untouched() {
        assertFalse(SnapshotAtRest.shouldMigratePlaintext(null))
        assertFalse(SnapshotAtRest.shouldMigratePlaintext(""))
        assertFalse(SnapshotAtRest.shouldMigratePlaintext("SQLite format 3"))
        assertFalse(SnapshotAtRest.shouldMigratePlaintext("{\"schemaVersion\":1"))
        assertFalse(SnapshotAtRest.shouldMigratePlaintext("{\"name\":\"sem esquema\"}"))
        assertFalse(SnapshotAtRest.shouldMigratePlaintext(ByteArray(32) { 7 }.decodeToString()))
    }
}
