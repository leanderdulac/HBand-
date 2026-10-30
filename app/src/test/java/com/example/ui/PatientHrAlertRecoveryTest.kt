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
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("hr_alert_test", 0)
        prefs.edit().clear().commit()
        field("prefs", prefs)
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

    @Test fun failed_android_post_preserves_local_notice_and_does_not_repeat() {
        Notifications.failure = "post"
        evaluate(40)
        assertTrue(notices.notification.value!!.message.contains("40 bpm"))
        Notifications.failure = ""
        evaluate(38)
        assertEquals(1, Notifications.attempts)
        evaluate(130)
        assertEquals(2, Notifications.attempts)
        assertEquals(1001, Notifications.sent.single().first)
    }

    @Test fun failed_high_and_low_posts_preserve_the_observed_values_locally() {
        Notifications.failure = "post"
        evaluate(130)
        assertTrue(notices.notification.value!!.message.contains("130 bpm"))
        evaluate(40)
        assertTrue(notices.notification.value!!.message.contains("40 bpm"))
        assertEquals(2, Notifications.attempts)
        assertTrue(Notifications.sent.isEmpty())
    }

    @Test fun stays_silent_while_above_and_rearms_only_after_hysteresis() {
        repeat(20) { evaluate(101 + it % 3) }
        assertEquals(1, Notifications.sent.size)
        evaluate(97); evaluate(102)
        assertEquals(1, Notifications.sent.size)
        evaluate(95); evaluate(102)
        assertEquals(listOf(1001, 1001), Notifications.sent.map { it.first })
    }

    @Test fun low_rearms_at_limit_plus_five() {
        evaluate(40); evaluate(52); evaluate(40)
        assertEquals(1, Notifications.sent.size)
        evaluate(55); evaluate(40)
        assertEquals(listOf(1002, 1002), Notifications.sent.map { it.first })
    }

    @Test fun first_automatic_notice_is_allowed_during_the_first_seconds_of_uptime() {
        ShadowPausedSystemClock.reset()
        assertTrue(SystemClock.elapsedRealtime() < 8000)
        evaluate(130)
        assertEquals(1, Notifications.sent.size)
    }

    @Test fun cancellation_is_not_converted_into_an_ordinary_notification_failure() {
        Notifications.failure = "cancel"
        assertThrows(CancellationException::class.java) { evaluate(130) }
        assertNull(notices.notification.value)
    }

    @Test fun fatal_errors_are_not_suppressed() {
        Notifications.failure = "fatal"
        assertThrows(AssertionError::class.java) { evaluate(40) }
        assertNull(notices.notification.value)
    }

    @Test fun disabled_and_in_range_values_do_not_consume_the_window() {
        enabled.value = false; evaluate(130)
        enabled.value = true; evaluate(100); evaluate(50); evaluate(75)
        assertTrue(Notifications.sent.isEmpty())
        evaluate(130)
        assertEquals(1, Notifications.sent.size)
    }

    @Test fun automatic_notices_keep_the_observed_value_without_a_simulation_label() {
        evaluate(130)
        evaluate(40)
        assertEquals(listOf(1001, 1002), Notifications.sent.map { it.first })
        Notifications.sent.zip(listOf(130, 40)).forEach { (sent, value) ->
            val n = sent.second
            assertFalse(n.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Teste"))
            assertTrue(n.extras.getString(Notification.EXTRA_TEXT)!!.contains(value.toString()))
        }
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
