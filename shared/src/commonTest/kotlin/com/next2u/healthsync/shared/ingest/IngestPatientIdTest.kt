package com.next2u.healthsync.shared.ingest

import com.next2u.healthsync.shared.model.QueuedReading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IngestPatientIdTest {
    @AfterTest
    fun reset() = IngestRuntime.setPatientId(null)

    private fun patientOf(payloadPatient: String): String {
        val row = QueuedReading(
            "8f3a2c1e-4b0d-4a11-9c22-222222222222",
            """{"patient_id":"$payloadPatient","device_id":"D","timestamp":"2026-09-14T12:00:00Z","heart_rate":70}""",
        )
        val prepared = IngestReconciler.prepare(row)
        val json = Json.parseToJsonElement(prepared.json).jsonObject
        assertEquals(prepared.patientId, (json.getValue("patient_id") as JsonPrimitive).content)
        return prepared.patientId
    }

    @Test
    fun queued_default_patient_is_sent_with_generated_id() {
        IngestRuntime.setPatientId("PAT-LEANDRO-7F3A")
        assertEquals("PAT-LEANDRO-7F3A", patientOf(IngestPayload.DEFAULT_PATIENT_ID))
    }

    @Test
    fun real_queued_patient_is_kept() {
        IngestRuntime.setPatientId("PAT-LEANDRO-7F3A")
        assertEquals("PAT-OUTRO-0001", patientOf("PAT-OUTRO-0001"))
    }

    @Test
    fun without_generated_id_default_is_kept() {
        IngestRuntime.setPatientId(IngestPayload.DEFAULT_PATIENT_ID)
        assertEquals(IngestPayload.DEFAULT_PATIENT_ID, patientOf(IngestPayload.DEFAULT_PATIENT_ID))
    }
}
