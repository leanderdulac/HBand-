package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeepooReconnectPolicyTest {
    @Test
    fun connecting_without_notify_becomes_stale_after_the_watchdog() {
        assertFalse(
            VeepooReconnectPolicy.isConnectingStale(
                connecting = true,
                startedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + VeepooReconnectPolicy.CONNECT_ATTEMPT_TIMEOUT_MS - 1,
                notifyUp = false,
                liveSession = false,
            ),
        )
        assertTrue(
            VeepooReconnectPolicy.isConnectingStale(
                connecting = true,
                startedAtElapsedMs = 1_000L,
                nowElapsedMs = 1_000L + VeepooReconnectPolicy.CONNECT_ATTEMPT_TIMEOUT_MS,
                notifyUp = false,
                liveSession = false,
            ),
        )
    }

    @Test
    fun live_or_notify_session_is_never_stale() {
        assertFalse(
            VeepooReconnectPolicy.isConnectingStale(
                connecting = true,
                startedAtElapsedMs = 1L,
                nowElapsedMs = 60_000L,
                notifyUp = true,
                liveSession = false,
            ),
        )
        assertFalse(
            VeepooReconnectPolicy.isConnectingStale(
                connecting = true,
                startedAtElapsedMs = 1L,
                nowElapsedMs = 60_000L,
                notifyUp = false,
                liveSession = true,
            ),
        )
    }

    @Test
    fun unknown_start_with_connecting_true_is_stale() {
        assertTrue(
            VeepooReconnectPolicy.isConnectingStale(
                connecting = true,
                startedAtElapsedMs = 0L,
                nowElapsedMs = 10L,
                notifyUp = false,
                liveSession = false,
            ),
        )
        assertFalse(
            VeepooReconnectPolicy.isConnectingStale(
                connecting = false,
                startedAtElapsedMs = 0L,
                nowElapsedMs = 10L,
                notifyUp = false,
                liveSession = false,
            ),
        )
    }

    @Test
    fun process_start_grace_delays_only_the_first_seconds() {
        assertEquals(3_000L, VeepooReconnectPolicy.processStartDelayMs(0L, 0L))
        assertEquals(1_000L, VeepooReconnectPolicy.processStartDelayMs(0L, 2_000L))
        assertEquals(0L, VeepooReconnectPolicy.processStartDelayMs(0L, 3_000L))
        assertEquals(0L, VeepooReconnectPolicy.processStartDelayMs(0L, 10_000L))
    }

    @Test
    fun backoff_grows_then_caps_and_never_gives_up() {
        assertEquals(2_000L, VeepooReconnectPolicy.delayMs(1, notWorn = false))
        assertEquals(4_000L, VeepooReconnectPolicy.delayMs(2, notWorn = false))
        assertEquals(60_000L, VeepooReconnectPolicy.delayMs(8, notWorn = false))
        assertEquals(VeepooReconnectPolicy.MAX_TRACKED_ATTEMPTS, VeepooReconnectPolicy.nextAttemptIndex(8))
        assertEquals(1, VeepooReconnectPolicy.nextAttemptIndex(0))
    }

    @Test
    fun notification_phase_matches_real_session_state() {
        assertEquals(
            BleSessionPhase.CONNECTED,
            VeepooReconnectPolicy.phase(
                hardwareConnected = true, deviceConnected = true, autoReconnect = true,
                hasPersistedSession = true, userStopped = false, connecting = true,
                connectAttemptStale = false, failedAttempts = 0,
            ),
        )
        assertEquals(
            BleSessionPhase.CONNECTING,
            VeepooReconnectPolicy.phase(
                hardwareConnected = false, deviceConnected = false, autoReconnect = true,
                hasPersistedSession = true, userStopped = false, connecting = true,
                connectAttemptStale = false, failedAttempts = 0,
            ),
        )
        assertEquals(
            BleSessionPhase.RECONNECTING,
            VeepooReconnectPolicy.phase(
                hardwareConnected = false, deviceConnected = false, autoReconnect = true,
                hasPersistedSession = true, userStopped = false, connecting = true,
                connectAttemptStale = false, failedAttempts = 2,
            ),
        )
        assertEquals(
            BleSessionPhase.NOT_FOUND,
            VeepooReconnectPolicy.phase(
                hardwareConnected = false, deviceConnected = false, autoReconnect = true,
                hasPersistedSession = true, userStopped = false, connecting = true,
                connectAttemptStale = true, failedAttempts = 1,
            ),
        )
        assertEquals(
            BleSessionPhase.DISCONNECTED,
            VeepooReconnectPolicy.phase(
                hardwareConnected = false, deviceConnected = false, autoReconnect = false,
                hasPersistedSession = true, userStopped = true, connecting = false,
                connectAttemptStale = false, failedAttempts = 3,
            ),
        )
    }
}
