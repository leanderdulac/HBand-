package com.example.data.hband

import com.example.ui.components.TelemetryBannerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectWatchdogPolicyTest {
    @Test fun reconnectsOnlyWhenPairedAutoAndDisconnected() {
        assertTrue(ReconnectWatchdogPolicy.shouldReconnect(true, true, false, false))
        assertFalse(ReconnectWatchdogPolicy.shouldReconnect(true, true, true, false))
        assertFalse(ReconnectWatchdogPolicy.shouldReconnect(false, true, false, false))
        assertFalse(ReconnectWatchdogPolicy.shouldReconnect(true, false, false, false))
        assertFalse(ReconnectWatchdogPolicy.shouldReconnect(true, true, false, true))
    }

    @Test fun exactAlarmFallsBackWhenNotGranted() {
        assertTrue(ReconnectWatchdogPolicy.useExactAlarm(30, false))
        assertTrue(ReconnectWatchdogPolicy.useExactAlarm(34, true))
        assertFalse(ReconnectWatchdogPolicy.useExactAlarm(34, false))
    }

    @Test fun bannerRules() {
        assertTrue(TelemetryBannerPolicy.showReconnecting("AA:BB", false, true))
        assertFalse(TelemetryBannerPolicy.showReconnecting("AA:BB", true, true))
        assertFalse(TelemetryBannerPolicy.showReconnecting(null, false, true))
        assertTrue(TelemetryBannerPolicy.showBatteryWarning(true, false))
        assertFalse(TelemetryBannerPolicy.showBatteryWarning(true, true))
    }

    @Test fun versionLabelFormat() {
        org.junit.Assert.assertEquals("Versão 1.7.4 (12)", com.example.ui.components.appVersionLabel("1.7.4", 12))
        org.junit.Assert.assertEquals("Versão 1.7.4 (15)", com.example.ui.components.appVersionLabel())
    }
}
