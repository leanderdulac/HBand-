package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooPasswordHandshakeTest {

    @Test
    fun `late timeout after success is ignored and keeps the session`() {
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = 1,
            handshakeSucceeded = true,
            liveSession = false,
            notifyUp = true,
        )
        assertEquals(VeepooPasswordHandshake.Action.IGNORE, decision.action)
        assertTrue(decision.keepLiveSession)
    }

    @Test
    fun `first timeout retries confirm without teardown even without HeartData`() {
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = 1,
            handshakeSucceeded = false,
            liveSession = false,
            notifyUp = true,
        )
        assertEquals(VeepooPasswordHandshake.Action.RETRY_CONFIRM, decision.action)
        assertTrue(decision.keepLiveSession)
        assertEquals(VeepooPasswordHandshake.MSG_TIMEOUT_RETRY, decision.sessionMessage)
    }

    @Test
    fun `first timeout with live HeartData also retries and keeps the session`() {
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = 1,
            handshakeSucceeded = false,
            liveSession = true,
            notifyUp = false,
        )
        assertEquals(VeepooPasswordHandshake.Action.RETRY_CONFIRM, decision.action)
        assertTrue(decision.keepLiveSession)
    }

    @Test
    fun `second timeout with live HeartData does not reconnect`() {
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = 2,
            handshakeSucceeded = false,
            liveSession = true,
            notifyUp = true,
        )
        assertEquals(VeepooPasswordHandshake.Action.KEEP_SESSION, decision.action)
        assertTrue(decision.keepLiveSession)
        assertEquals(VeepooPasswordHandshake.MSG_TIMEOUT_LIVE, decision.sessionMessage)
    }

    @Test
    fun `second timeout with notify still up keeps GATT instead of storming reconnect`() {
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = 2,
            handshakeSucceeded = false,
            liveSession = false,
            notifyUp = true,
        )
        assertEquals(VeepooPasswordHandshake.Action.KEEP_SESSION, decision.action)
        assertTrue(decision.keepLiveSession)
        assertEquals(VeepooPasswordHandshake.MSG_TIMEOUT_KEEP, decision.sessionMessage)
    }

    @Test
    fun `second timeout with no GATT allows a single reconnect`() {
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = 2,
            handshakeSucceeded = false,
            liveSession = false,
            notifyUp = false,
        )
        assertEquals(VeepooPasswordHandshake.Action.RECONNECT, decision.action)
        assertFalse(decision.keepLiveSession)
    }

    @Test
    fun `CHECK_SUCCESS is accepted and CHECK_FAIL is not`() {
        assertTrue(VeepooPasswordHandshake.isPwdAccepted("CHECK_SUCCESS"))
        assertTrue(VeepooPasswordHandshake.isPwdAccepted("CHECK_AND_TIME_SUCCESS"))
        assertFalse(VeepooPasswordHandshake.isPwdAccepted("CHECK_FAIL"))
        assertFalse(VeepooPasswordHandshake.isPwdAccepted(null))
    }

    @Test
    fun `duplicate connect is skipped only for in-flight connect or live notify plus PPG`() {
        assertTrue(
            VeepooPasswordHandshake.shouldSkipDuplicateConnect(
                connecting = true,
                liveSession = false,
                notifyUp = false,
                confirmInFlight = false,
            ),
        )
        assertTrue(
            VeepooPasswordHandshake.shouldSkipDuplicateConnect(
                connecting = false,
                liveSession = true,
                notifyUp = true,
                confirmInFlight = false,
            ),
        )
        assertTrue(
            VeepooPasswordHandshake.shouldSkipDuplicateConnect(
                connecting = false,
                liveSession = false,
                notifyUp = true,
                confirmInFlight = true,
            ),
        )
        assertFalse(
            VeepooPasswordHandshake.shouldSkipDuplicateConnect(
                connecting = false,
                liveSession = false,
                notifyUp = false,
                confirmInFlight = false,
            ),
        )
    }

    @Test
    fun `stale telemetry without notify must not skip Desconectar then Conectar`() {
        assertFalse(
            VeepooPasswordHandshake.shouldSkipDuplicateConnect(
                connecting = false,
                liveSession = true,
                notifyUp = false,
                confirmInFlight = false,
            ),
        )
    }
}
