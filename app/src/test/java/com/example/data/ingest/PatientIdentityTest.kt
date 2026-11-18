package com.example.data.ingest

import android.app.Application
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.IngestQueueEntity
import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PatientIdentityTest {
    private val default = IngestPayloadMapper.DEFAULT_PATIENT_ID
    private val format = Regex("^PAT-[A-Z0-9]{1,12}-[0-9A-F]{4}$")

    @Test fun builds_pat_first_name_and_suffix() {
        assertEquals("PAT-LEANDRO-7F3A", PatientIdentity.build("Leandro França de Mello", "7F3A"))
        assertNull(PatientIdentity.build("   ", "7F3A"))
        assertNull(PatientIdentity.build(null, "7F3A"))
        assertTrue(PatientIdentity.isGenerated("PAT-LEANDRO-7F3A"))
        assertFalse(PatientIdentity.isGenerated(default))
        assertFalse(PatientIdentity.isGenerated("PAT-leandro-7f3a"))
    }

    @Test fun slug_removes_accents_and_symbols_and_caps_at_12() {
        assertEquals("JOAO", PatientIdentity.slug("  joão  da Silva"))
        assertEquals("CONCEICAO", PatientIdentity.slug("Conceição"))
        assertEquals("ANAMARIA", PatientIdentity.slug("Ana-Maria Souza"))
        assertEquals("MARIA2", PatientIdentity.slug("Maria2 Lima"))
        assertEquals("MAXIMILIANOJ", PatientIdentity.slug("Maximilianojosé Pereira"))
        assertEquals(12, PatientIdentity.slug("A".repeat(40))!!.length)
        // A first word with no usable letter falls through to the next one.
        assertEquals("ANA", PatientIdentity.slug("\uD83D\uDE00 Ana"))
        assertNull(PatientIdentity.slug("李 王"))
        assertEquals("PAT-PACIENTE-00AF", PatientIdentity.build("李 王", "00AF"))
    }

    @Test fun random_suffix_is_four_uppercase_hex_and_ids_match_format() {
        repeat(200) {
            val suffix = PatientIdentity.randomSuffix()
            assertTrue(suffix, Regex("^[0-9A-F]{4}$").matches(suffix))
            assertTrue(format.matches(PatientIdentity.build("Ana", suffix)!!))
        }
    }

    @Test fun assigned_once_and_stable_after_name_edits() {
        val blank = UserProfileEntity(patientId = default, fullName = "")
        assertEquals(blank, PatientIdentity.assignIfNeeded(blank) { "AAAA" })
        val named = PatientIdentity.assignIfNeeded(blank.copy(fullName = "Ana Souza")) { "1B2C" }
        assertEquals("PAT-ANA-1B2C", named.patientId)
        val renamed = PatientIdentity.assignIfNeeded(named.copy(fullName = "Beatriz Lima")) { "FFFF" }
        assertEquals("PAT-ANA-1B2C", renamed.patientId)
        assertEquals("PAT-ANA-0001", PatientIdentity.assignIfNeeded(blank.copy(patientId = " ", fullName = "Ana")) { "0001" }.patientId)
        // An ID set by the care team is never replaced.
        val custom = blank.copy(patientId = "CORE-123", fullName = "Ana")
        assertEquals(custom, PatientIdentity.assignIfNeeded(custom) { "AAAA" })
    }

    @Test fun launch_migration_assigns_existing_named_profile_once() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.userProfileDao()
            assertNull(PatientIdentity.migrate(dao) { "AAAA" })
            dao.saveUserProfile(UserProfileEntity(patientId = default, fullName = ""))
            assertEquals(default, PatientIdentity.migrate(dao) { "AAAA" }!!.patientId)
            dao.saveUserProfile(UserProfileEntity(patientId = default, fullName = "Leandro Mello", age = 40))
            assertEquals("PAT-LEANDRO-7F3A", PatientIdentity.migrate(dao) { "7F3A" }!!.patientId)
            val stored = dao.getUserProfile()!!
            assertEquals("PAT-LEANDRO-7F3A", stored.patientId)
            assertEquals(40, stored.age)
            // Second launch: unchanged.
            assertEquals("PAT-LEANDRO-7F3A", PatientIdentity.migrate(dao) { "0000" }!!.patientId)
        } finally {
            db.close()
        }
    }

    private fun item(patient: String, id: String = "A") = IngestQueueEntity(payloadJson = """{"patient_id":"$patient",
        "device_id":"TEST-WATCH","timestamp":"2026-10-17T12:00:00Z","heart_rate":72}""", clientReadingId = id)

    @Test fun send_time_replaces_only_the_default_patient_and_keeps_dedup_key_stable() {
        val sent = IngestReconciler.prepare(item(default), null, "PAT-ANA-1B2C")
        assertEquals("PAT-ANA-1B2C", sent.patientId)
        assertEquals("PAT-ANA-1B2C", JSONObject(sent.json).getString("patient_id"))
        assertEquals("A", JSONObject(sent.json).getString("client_reading_id"))
        // The durable queue payload is not rewritten.
        assertEquals(default, JSONObject(sent.item.payloadJson).getString("patient_id"))
        assertEquals("OTHER", IngestReconciler.prepare(item("OTHER"), null, "PAT-ANA-1B2C").patientId)
        assertEquals(default, IngestReconciler.prepare(item(default), null, null).patientId)
        assertEquals(default, IngestReconciler.prepare(item(default), null, default).patientId)
        // Retries derive the same key; it equals a reading recorded directly under the new ID.
        val again = IngestReconciler.prepare(item(default), null, "PAT-ANA-1B2C")
        assertEquals(IngestReconciler.flushIdempotencyKey(listOf(sent)), IngestReconciler.flushIdempotencyKey(listOf(again)))
        assertEquals(IngestReconciler.flushIdempotencyKey(listOf(sent)),
            IngestReconciler.flushIdempotencyKey(listOf(IngestReconciler.prepare(item("PAT-ANA-1B2C")))))
    }
}
