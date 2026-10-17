package com.example.data.ingest

import android.app.Application
import com.example.data.local.IngestQueueEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DeviceLabelTest {
    private fun item(id: String = "A") = IngestQueueEntity(payloadJson = """{"patient_id":"TEST-PATIENT",
        "device_id":"TEST-WATCH","timestamp":"2026-10-17T12:00:00Z","heart_rate":72}""", clientReadingId = id)

    @Test fun normalize_trims_caps_at_core_limit_and_maps_blank_to_null() {
        assertEquals("Ana Souza", DeviceLabel.normalize("  Ana Souza  "))
        assertNull(DeviceLabel.normalize("   "))
        assertNull(DeviceLabel.normalize(null))
        assertEquals(64, DeviceLabel.normalize("x".repeat(100))!!.length)
        // A 64-char cut must not leave half of an emoji (surrogate pair) on the wire.
        val cut = DeviceLabel.normalize("a".repeat(63) + "\uD83D\uDE00")!!
        assertEquals("a".repeat(63), cut)
    }

    @Test fun suggestion_is_pulseira_de_profile_name_capped_at_core_limit() {
        assertEquals("Pulseira de Ana Souza", DeviceLabel.suggestion("  Ana Souza "))
        assertNull(DeviceLabel.suggestion("  "))
        assertNull(DeviceLabel.suggestion(null))
        val long = DeviceLabel.suggestion("A".repeat(80))!!
        assertEquals(64, long.length)
        assertTrue(long.startsWith("Pulseira de "))
    }

    @Test fun resolve_order_is_saved_then_profile_name_then_phone_model() {
        assertEquals("Pulseira 3", DeviceLabel.resolve(" Pulseira 3 ", "Ana Souza", "SM-G996B"))
        assertEquals("Pulseira de Ana Souza", DeviceLabel.resolve(null, "Ana Souza", "SM-G996B"))
        assertEquals("Pulseira de Ana Souza", DeviceLabel.resolve("  ", " Ana Souza ", "SM-G996B"))
        assertEquals("SM-G996B", DeviceLabel.resolve(null, "", "SM-G996B"))
        assertEquals("SM-G996B", DeviceLabel.resolve(null, null, "SM-G996B"))
        assertNull(DeviceLabel.resolve(null, null, " "))
    }

    @Test fun prefill_uses_saved_name_else_pulseira_de_patient_name() {
        assertEquals("Pulseira 3", DeviceLabel.prefill("Pulseira 3", "Ana Souza"))
        assertEquals("Pulseira de Ana Souza", DeviceLabel.prefill(null, " Ana Souza "))
        assertEquals("", DeviceLabel.prefill(null, ""))
    }

    @Test fun save_is_local_and_blank_or_suggestion_follows_profile_name() {
        val app = RuntimeEnvironment.getApplication()
        DeviceLabel.save(app, "  Pulseira 3 ", "Ana Souza")
        assertEquals("Pulseira 3", DeviceLabel.read(app))
        assertEquals("Pulseira 3", DeviceLabel.current(app, "Ana Souza"))
        // Equal to the suggestion: not pinned, so a later name change is followed.
        DeviceLabel.save(app, "Pulseira de Ana Souza", "Ana Souza")
        assertNull(DeviceLabel.read(app))
        assertEquals("Pulseira de Ana Lima", DeviceLabel.current(app, "Ana Lima"))
        DeviceLabel.save(app, "  ", "Ana Souza")
        assertNull(DeviceLabel.read(app))
        assertEquals("Pulseira de Ana Souza", DeviceLabel.current(app, "Ana Souza"))
        assertEquals(DeviceLabel.normalize(android.os.Build.MODEL), DeviceLabel.current(app, null as String?))
    }

    @Test fun send_time_label_reads_saved_profile_name_from_database() = kotlinx.coroutines.runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.example.data.local.AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            DeviceLabel.save(app, "", null)
            assertEquals(DeviceLabel.normalize(android.os.Build.MODEL), DeviceLabel.current(app, db.userProfileDao()))
            db.userProfileDao().saveUserProfile(com.example.data.local.UserProfileEntity(fullName = " Bruno Lima "))
            assertEquals("Pulseira de Bruno Lima", DeviceLabel.current(app, db.userProfileDao()))
            DeviceLabel.save(app, "Pulseira 7", "Bruno Lima")
            assertEquals("Pulseira 7", DeviceLabel.current(app, db.userProfileDao()))
        } finally {
            db.close()
        }
    }

    @Test fun prepare_adds_device_label_only_when_present_and_keeps_identity() {
        val labeled = IngestReconciler.prepare(item(), "  Pulseira da Ana ")
        assertEquals("Pulseira da Ana", JSONObject(labeled.json).getString("device_label"))
        assertEquals("TEST-WATCH", JSONObject(labeled.json).getString("device_id"))
        val plain = IngestReconciler.prepare(item(), null)
        assertFalse(JSONObject(plain.json).has("device_label"))
        assertFalse(JSONObject(IngestReconciler.prepare(item(), " ").json).has("device_label"))
        // Renaming must not change the flush idempotency key (patient + reading ids only).
        assertEquals(IngestReconciler.flushIdempotencyKey(listOf(plain)),
            IngestReconciler.flushIdempotencyKey(listOf(labeled)))
    }
}
