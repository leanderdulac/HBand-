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

    @Test fun resolve_prefers_saved_name_and_falls_back_to_phone_model() {
        assertEquals("Pulseira da Ana", DeviceLabel.resolve(" Pulseira da Ana ", "SM-G996B"))
        assertEquals("SM-G996B", DeviceLabel.resolve("", "SM-G996B"))
        assertEquals("SM-G996B", DeviceLabel.resolve(null, "SM-G996B"))
        assertNull(DeviceLabel.resolve(null, " "))
    }

    @Test fun prefill_uses_saved_name_else_patient_name() {
        assertEquals("Pulseira da Ana", DeviceLabel.prefill("Pulseira da Ana", "Ana Souza"))
        assertEquals("Ana Souza", DeviceLabel.prefill(null, " Ana Souza "))
        assertEquals("", DeviceLabel.prefill(null, ""))
    }

    @Test fun save_is_local_and_blank_clears_so_model_is_used() {
        val app = RuntimeEnvironment.getApplication()
        DeviceLabel.save(app, "  Pulseira da Ana ")
        assertEquals("Pulseira da Ana", DeviceLabel.read(app))
        assertEquals("Pulseira da Ana", DeviceLabel.current(app))
        DeviceLabel.save(app, "  ")
        assertNull(DeviceLabel.read(app))
        assertEquals(DeviceLabel.normalize(android.os.Build.MODEL), DeviceLabel.current(app))
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
