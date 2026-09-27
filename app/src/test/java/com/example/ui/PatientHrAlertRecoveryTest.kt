package com.example.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowPausedSystemClock
import kotlinx.coroutines.CancellationException
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import sun.misc.Unsafe

/** Executes actual VM actions without its operational constructor: no database, worker, BLE or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [PatientHrAlertRecoveryTest.Notifications::class])
class PatientHrAlertRecoveryTest {
    private lateinit var vm: MainViewModel
    private val notices = PatientNotificationState()
    private val enabled = MutableStateFlow(true)

    @Before fun setup() {
        Notifications.failure = ""; Notifications.attempts = 0; Notifications.sent.clear()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        vm = unsafe.allocateInstance(MainViewModel::class.java) as MainViewModel
        AndroidViewModel::class.java.declaredFields.single { it.type == Application::class.java }
            .apply { isAccessible = true }.set(vm, RuntimeEnvironment.getApplication())
        fun field(name: String, value: Any) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.set(vm, value)
        field("_upperHrThreshold", MutableStateFlow(100))
        field("_lowerHrThreshold", MutableStateFlow(50))
        field("_hrAlertsEnabled", enabled)
        field("notificationState", notices)
    }

    private fun evaluate(hr: Int) {
        try {
            MainViewModel::class.java.getDeclaredMethod("evaluateHeartRateThresholds", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(vm, hr)
        } catch (failure: InvocationTargetException) { throw failure.targetException }
    }

    @Test fun failed_channel_creation_preserves_local_automatic_notice() {
        Notifications.failure = "channel"
        evaluate(130)
        assertTrue(notices.notification.value!!.message.contains("130 bpm"))
        assertTrue(Notifications.sent.isEmpty())
    }

    @Test fun failed_android_post_preserves_local_notice_and_later_attempt() {
        Notifications.failure = "post"
        evaluate(40)
        assertTrue(notices.notification.value!!.message.contains("40 bpm"))
        Notifications.failure = ""
        ShadowSystemClock.advanceBy(Duration.ofMillis(7999)); evaluate(130)
        assertEquals(1, Notifications.attempts)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1)); evaluate(130)
        assertEquals(2, Notifications.attempts)
        assertEquals(1001, Notifications.sent.single().first)
    }

    @Test fun failed_manual_test_does_not_escape_or_remove_its_simulation_label() {
        Notifications.failure = "post"
        vm.testHighHrAlert()
        assertTrue(notices.notification.value!!.message.contains("Não é uma leitura do relógio"))
        vm.testLowHrAlert()
        assertTrue(notices.notification.value!!.message.startsWith("Teste de aviso:"))
    }

    @Test fun high_and_low_share_the_existing_eight_second_window() {
        evaluate(130)
        ShadowSystemClock.advanceBy(Duration.ofMillis(7999)); evaluate(40)
        assertEquals(listOf(1001), Notifications.sent.map { it.first })
        ShadowSystemClock.advanceBy(Duration.ofMillis(1)); evaluate(40)
        assertEquals(listOf(1001, 1002), Notifications.sent.map { it.first })
    }

    @Test fun first_automatic_notice_is_allowed_during_the_first_eight_seconds_of_uptime() {
        ShadowPausedSystemClock.reset()
        assertTrue(SystemClock.elapsedRealtime() < 8000)
        evaluate(130)
        assertEquals(1, Notifications.sent.size)
    }

    @Test fun elapsed_sleep_counts_towards_the_window_without_advancing_uptime() {
        evaluate(130)
        val before = SystemClock.uptimeMillis()
        ShadowSystemClock.simulateDeepSleep(Duration.ofSeconds(8))
        assertEquals(before, SystemClock.uptimeMillis())
        evaluate(40)
        assertEquals(listOf(1001, 1002), Notifications.sent.map { it.first })
    }

    @Test fun cancellation_is_not_converted_into_an_ordinary_notification_failure() {
        Notifications.failure = "cancel"
        assertThrows(CancellationException::class.java) { vm.testHighHrAlert() }
        assertNull(notices.notification.value)
    }

    @Test fun fatal_errors_are_not_suppressed() {
        Notifications.failure = "fatal"
        assertThrows(AssertionError::class.java) { vm.testLowHrAlert() }
        assertNull(notices.notification.value)
    }

    @Test fun disabled_and_in_range_values_do_not_consume_the_window() {
        enabled.value = false; evaluate(130)
        enabled.value = true; evaluate(100); evaluate(50); evaluate(75)
        assertTrue(Notifications.sent.isEmpty())
        evaluate(130)
        assertEquals(1, Notifications.sent.size)
    }

    @Test fun manual_tests_keep_their_labels_and_do_not_consume_the_automatic_window() {
        vm.testHighHrAlert(); vm.testLowHrAlert(); evaluate(130)
        assertEquals(3, Notifications.sent.size)
        Notifications.sent.take(2).forEach { (_, n) ->
            assertTrue(n.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Teste de aviso:"))
            assertTrue(n.extras.getString(Notification.EXTRA_TEXT)!!.contains("Não é uma leitura do relógio"))
        }
        assertFalse(Notifications.sent.last().second.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Teste"))
    }

    @Implements(NotificationManager::class)
    class Notifications {
        companion object {
            var failure = ""
            var attempts = 0
            val sent = mutableListOf<Pair<Int, Notification>>()
        }
        @Implementation fun createNotificationChannel(channel: NotificationChannel) {
            if (failure == "channel") throw IllegalStateException("SYNTHETIC_PRIVATE_CHANNEL_FAILURE")
        }
        @Implementation fun notify(id: Int, notification: Notification) {
            attempts++
            if (failure == "post") throw SecurityException("SYNTHETIC_PRIVATE_NOTIFICATION_FAILURE")
            if (failure == "cancel") throw CancellationException("SYNTHETIC_CANCEL")
            if (failure == "fatal") throw AssertionError("SYNTHETIC_FATAL")
            sent += id to notification
        }
    }
}
