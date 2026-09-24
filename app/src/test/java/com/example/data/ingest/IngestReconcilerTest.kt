package com.example.data.ingest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestReconcilerTest {

    private val ids = listOf(
        "11111111-1111-1111-1111-111111111111",
        "22222222-2222-2222-2222-222222222222",
    )

    @Test
    fun `accepted and duplicate are synced`() {
        val body = """
            {
              "status": "success",
              "processed_count": 2,
              "results": [
                {"index": 0, "status": "accepted", "client_reading_id": "${ids[0]}"},
                {"index": 1, "status": "duplicate", "client_reading_id": "${ids[1]}"}
              ]
            }
        """.trimIndent()
        val decisions = IngestReconciler.decisionsForHttp(ids, 200, body)
        assertEquals(2, decisions.size)
        assertTrue(decisions.all { it.markSynced })
        assertFalse(decisions.any { it.keepQueued })
        assertEquals(IngestItemOutcome.ACCEPTED, decisions[0].outcome)
        assertEquals(IngestItemOutcome.DUPLICATE, decisions[1].outcome)
    }

    @Test
    fun `rejected stays local with reason and is not synced`() {
        val body = """
            {
              "status": "partial",
              "processed_count": 1,
              "results": [
                {"index": 0, "status": "accepted", "client_reading_id": "${ids[0]}"},
                {"index": 1, "status": "rejected", "client_reading_id": "${ids[1]}", "error": "heart_rate out of range"}
              ]
            }
        """.trimIndent()
        val decisions = IngestReconciler.decisionsForHttp(ids, 200, body)
        assertTrue(decisions[0].markSynced)
        assertFalse(decisions[1].markSynced)
        assertFalse(decisions[1].keepQueued)
        assertEquals(IngestItemOutcome.REJECTED, decisions[1].outcome)
        assertEquals("heart_rate out of range", decisions[1].errorMessage)
    }

    @Test
    fun `401 is invalid key and stays queued`() {
        val decisions = IngestReconciler.decisionsForHttp(ids, 401, """{"detail":"no"}""")
        assertTrue(decisions.all { it.outcome == IngestItemOutcome.AUTH_INVALID })
        assertTrue(decisions.all { it.keepQueued })
        assertFalse(decisions.any { it.markSynced })
        assertEquals(IngestReconciler.AUTH_INVALID_MESSAGE, decisions[0].errorMessage)
    }

    @Test
    fun `403 is write-scope denial and stays queued`() {
        val decisions = IngestReconciler.decisionsForHttp(ids, 403, null)
        assertTrue(decisions.all { it.outcome == IngestItemOutcome.AUTH_FORBIDDEN })
        assertTrue(decisions.all { it.keepQueued })
        assertEquals(IngestReconciler.AUTH_FORBIDDEN_MESSAGE, decisions[0].errorMessage)
    }

    @Test
    fun `5xx and network keep the row queued`() {
        val server = IngestReconciler.decisionsForHttp(ids, 503, "unavailable")
        assertTrue(server.all { it.outcome == IngestItemOutcome.TRANSIENT && it.keepQueued && !it.markSynced })
        val net = IngestReconciler.decisionsForHttp(ids, 0, null, networkError = true)
        assertTrue(net.all { it.outcome == IngestItemOutcome.TRANSIENT && it.keepQueued })
    }

    @Test
    fun `legacy 200 without results still marks synced`() {
        val decisions = IngestReconciler.decisionsForHttp(ids, 200, """{"processed_count":2,"status":"success"}""")
        assertTrue(decisions.all { it.markSynced })
    }

    @Test
    fun `single ingest_status duplicate is synced`() {
        val decisions = IngestReconciler.decisionsForHttp(
            listOf(ids[0]),
            200,
            """{"ingest_status":"duplicate","duplicate":true}""",
        )
        assertEquals(IngestItemOutcome.DUPLICATE, decisions.single().outcome)
        assertTrue(decisions.single().markSynced)
    }

    @Test
    fun `ensureReadingPayload writes id timestamp and device only when missing`() {
        val raw = """{"patient_id":"PAT-1","heart_rate":78}"""
        val out = JSONObject(
            IngestReconciler.ensureReadingPayload(
                raw,
                clientReadingId = ids[0],
                fallbackDeviceId = "AA:BB:CC:DD:EE:FF",
                nowMs = 1_725_000_000_000L,
            )
        )
        assertEquals(ids[0], out.getString("client_reading_id"))
        assertEquals("AA:BB:CC:DD:EE:FF", out.getString("device_id"))
        assertTrue(out.getString("timestamp").endsWith("Z"))

        val already = """
            {
              "patient_id":"PAT-1",
              "device_id":"C4:E3:42:AA:30:A4",
              "timestamp":"2026-09-24T12:00:00Z",
              "client_reading_id":"${ids[1]}",
              "heart_rate":80
            }
        """.trimIndent()
        val kept = JSONObject(IngestReconciler.ensureReadingPayload(already, ids[0], nowMs = 1L))
        assertEquals(ids[1], kept.getString("client_reading_id"))
        assertEquals("2026-09-24T12:00:00Z", kept.getString("timestamp"))
        assertEquals("C4:E3:42:AA:30:A4", kept.getString("device_id"))
    }

    @Test
    fun `idempotency key is stable for the same chunk and does not change order`() {
        val a = IngestReconciler.flushIdempotencyKey(ids)
        val b = IngestReconciler.flushIdempotencyKey(ids.reversed())
        assertEquals(a, b)
        assertTrue(a.matches(Regex("^[A-Za-z0-9._:-]{1,128}$")))
    }

    @Test
    fun `auth backoff stops retry storm until key fingerprint changes`() {
        val fp = IngestReconciler.keyFingerprint("usable-key-value-not-logged")
        val backoff = AuthBackoffState(lastAuthHttp = 401, lastAuthAtMs = 1_000L, keyFingerprint = fp)
        assertTrue(
            IngestReconciler.shouldSkipServerCall(
                nowMs = 1_000L + 60_000L,
                keyFingerprint = fp,
                backoff = backoff,
            )
        )
        assertFalse(
            IngestReconciler.shouldSkipServerCall(
                nowMs = 1_000L + IngestReconciler.AUTH_BACKOFF_MS + 1,
                keyFingerprint = fp,
                backoff = backoff,
            )
        )
        assertFalse(
            IngestReconciler.shouldSkipServerCall(
                nowMs = 1_000L + 60_000L,
                keyFingerprint = IngestReconciler.keyFingerprint("replacement-key"),
                backoff = backoff,
            )
        )
    }

    @Test
    fun `placeholder key is a configuration error not a silent 401`() {
        assertFalse(IngestApiKey.isUsable(""))
        assertFalse(IngestApiKey.isUsable(IngestApiKey.PLACEHOLDER))
        assertFalse(IngestApiKey.isUsable("YOUR_HEALTHTECH_API_KEY_HERE"))
        assertTrue(IngestApiKey.isUsable("ht_live_not_a_real_key_but_shaped"))
        val decisions = IngestReconciler.decisionsForConfigError(ids)
        assertTrue(decisions.all { it.outcome == IngestItemOutcome.CONFIG_ERROR && it.keepQueued && !it.markSynced })
        assertEquals(IngestReconciler.AUTH_INVALID_MESSAGE, decisions[0].errorMessage)
    }
}
