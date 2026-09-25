package com.example.data.ingest

import com.example.data.local.IngestQueueEntity
import com.example.data.model.IngestResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IngestReconcilerTest {
    private fun reading(id: String, patient: String = "TEST-PATIENT") = IngestReconciler.prepare(
        IngestQueueEntity(payloadJson = """{"patient_id":"$patient","device_id":"TEST-WATCH",
            "timestamp":"2026-09-24T12:00:00Z","heart_rate":72}""", clientReadingId = id))

    private fun entry(index: Int, id: String, status: String = "accepted") = JSONObject()
        .put("index", index).put("client_reading_id", id).put("status", status)
        .put("result", JSONObject().put("patient_id", "TEST-PATIENT")
            .put("reading_id", "stored-$id").put("ingest_status", status))

    private fun body(vararg entries: JSONObject) = JSONObject().put("patient_id", "TEST-PATIENT")
        .put("status", "success").put("results", JSONArray(entries.toList())).toString()

    private val readings get() = listOf(reading("A"), reading("B"))

    @Test fun empty_malformed_legacy_counters_and_unknown_status_never_confirm() {
        for (raw in listOf(null, "", "not-json", "{}", "[]", """{"processed_count":2}""",
            body(), body(entry(0, "A", "unknown"), entry(1, "B", "unknown")))) {
            assertTrue(IngestReconciler.batch(readings, 200, raw).none { it.confirmed })
        }
    }

    @Test fun partial_reply_confirms_only_identified_item_never_array_neighbor() {
        val result = IngestReconciler.batch(readings, 200, body(entry(1, "B")))
        assertFalse(result[0].confirmed)
        assertTrue(result[1].confirmed)
    }

    @Test fun reordered_reply_uses_matching_explicit_index_and_identity() {
        assertTrue(IngestReconciler.batch(readings, 200,
            body(entry(1, "B", "duplicate"), entry(0, "A"))).all { it.confirmed })
    }

    @Test fun mismatched_duplicate_unknown_or_invalid_indices_are_conservative() {
        for (raw in listOf(body(entry(0, "B")), body(entry(0, "A"), entry(0, "A")),
            body(entry(0, "UNKNOWN")), body(entry(2, "A")), body(entry(-1, "A")),
            body(entry(0, "A").put("index", "0")), body(entry(0, "A").put("index", 0.5)),
            body(entry(0, "A").apply { remove("index") }))) {
            assertTrue(IngestReconciler.batch(readings, 200, raw).none { it.confirmed })
        }
    }

    @Test fun wrong_patient_or_missing_persisted_result_never_confirms() {
        val valid = body(entry(0, "A"), entry(1, "B"))
        val wrongPatient = JSONObject(valid).put("patient_id", "OTHER").toString()
        val wrongResult = entry(0, "A").put("result", JSONObject().put("patient_id", "OTHER"))
        for (raw in listOf(wrongPatient, body(wrongResult), body(entry(0, "A").apply { remove("result") }))) {
            assertTrue(IngestReconciler.batch(readings, 200, raw).none { it.confirmed })
        }
    }

    @Test fun accepted_duplicate_and_rejected_have_distinct_outcomes_without_error_echo() {
        val rejected = entry(1, "B", "rejected").put("error", "synthetic-private-value")
        val result = IngestReconciler.batch(readings, 200, body(entry(0, "A", "duplicate"), rejected))
        assertEquals(IngestItemOutcome.DUPLICATE, result[0].outcome)
        assertEquals(IngestItemOutcome.REJECTED, result[1].outcome)
        assertFalse(result[1].message!!.contains("synthetic-private-value"))
    }

    @Test fun single_requires_status_identity_patient_and_receipt() {
        val expected = reading("A")
        val valid = IngestResponse(ingest_status = "accepted", client_reading_id = "A",
            reading_id = "stored-A", patient_id = expected.patientId)
        assertTrue(IngestReconciler.single(expected, 200, valid).confirmed)
        assertTrue(IngestReconciler.single(expected, 200, valid.copy(ingest_status = "duplicate", duplicate = true)).confirmed)
        for (response in listOf(null, IngestResponse(), valid.copy(client_reading_id = "B"),
            valid.copy(patient_id = "OTHER"), valid.copy(reading_id = null),
            valid.copy(ingest_status = "unknown"), valid.copy(duplicate = true), valid.copy(success = false))) {
            assertFalse(IngestReconciler.single(expected, 200, response).confirmed)
        }
        for (code in listOf(201, 202, 204, 301, 401, 403, 404, 422, 429, 503)) {
            assertFalse(IngestReconciler.single(expected, code, valid).confirmed)
        }
    }

    @Test fun flush_key_preserves_order_patient_and_stays_stable_for_replay() {
        assertEquals(IngestReconciler.flushIdempotencyKey(readings), IngestReconciler.flushIdempotencyKey(readings))
        assertNotEquals(IngestReconciler.flushIdempotencyKey(readings), IngestReconciler.flushIdempotencyKey(readings.reversed()))
        assertNotEquals(IngestReconciler.flushIdempotencyKey(readings),
            IngestReconciler.flushIdempotencyKey(listOf(reading("A", "OTHER"), reading("B", "OTHER"))))
    }

    @Test fun mixed_patient_batch_is_rejected_and_planning_separates_it() {
        val mixed = listOf(reading("A"), reading("B", "OTHER"), reading("C"))
        assertTrue(runCatching { IngestReconciler.batchBody(mixed) }.isFailure)
        val chunks = IngestReconciler.chunks(mixed)
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.map { row -> row.patientId }.distinct().size == 1 })
    }

    @Test fun missing_time_patient_or_conflicting_identity_is_not_repaired_with_defaults() {
        val row = reading("A").item
        for (field in listOf("timestamp", "patient_id", "device_id")) {
            val malformed = JSONObject(row.payloadJson).apply { remove(field) }.toString()
            assertTrue(runCatching { IngestReconciler.prepare(row.copy(payloadJson = malformed)) }.isFailure)
        }
        val conflict = JSONObject(row.payloadJson).put("client_reading_id", "OTHER").toString()
        assertTrue(runCatching { IngestReconciler.prepare(row.copy(payloadJson = conflict)) }.isFailure)
        val first = IngestReconciler.prepare(row).json
        assertEquals(first, IngestReconciler.prepare(row.copy(retries = 4, lastAttemptAt = 99)).json)
        assertEquals("2026-09-24T12:00:00Z", JSONObject(first).getString("timestamp"))
    }

    @Test fun invalid_or_ambiguous_calendar_time_stays_local_while_offset_and_precision_are_preserved() {
        val row = reading("A").item
        for (time in listOf("T", "2026-02-30T12:00:00Z", "2026-09-24T25:00:00Z",
            "2026-09-24T12:00:00", "2026-09-24", "2026-09-24T12:00:00+25:00")) {
            val raw = JSONObject(row.payloadJson).put("timestamp", time).toString()
            assertTrue(time, runCatching { IngestReconciler.prepare(row.copy(payloadJson = raw)) }.isFailure)
        }
        for (time in listOf("2026-09-24T12:00:00.123456789Z", "2026-09-24T09:00:00-03:00", "2024-02-29T12:00:00Z")) {
            val raw = JSONObject(row.payloadJson).put("timestamp", time).toString()
            assertEquals(time, JSONObject(IngestReconciler.prepare(row.copy(payloadJson = raw)).json).getString("timestamp"))
        }
    }

    @Test fun legacy_flat_device_alias_is_materialized_and_conflicting_aliases_stay_local() {
        val row = reading("A").item
        val legacy = JSONObject(row.payloadJson).apply { remove("device_id"); put("deviceId", "LEGACY-WATCH") }
        val prepared = IngestReconciler.prepare(row.copy(payloadJson = legacy.toString()))
        assertEquals("LEGACY-WATCH", JSONObject(prepared.json).getString("device_id"))
        val conflicting = legacy.put("device_id", "OTHER-WATCH").toString()
        assertTrue(runCatching { IngestReconciler.prepare(row.copy(payloadJson = conflicting)) }.isFailure)
    }
}
