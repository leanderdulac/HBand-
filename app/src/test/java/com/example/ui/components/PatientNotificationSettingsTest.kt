package com.example.ui.components

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import com.example.util.HrNotificationHelper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h740dp-mdpi", sdk = [36], application = android.app.Application::class)
class PatientNotificationSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returning_from_settings_refreshes_the_observed_permission_without_sending_alerts() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(false)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MyApplicationTheme { PatientNotificationSettings() }
            }
        }
        compose.onNodeWithTag("app_notifications_disabled").assertIsDisplayed()
        compose.onNodeWithTag("open_notification_settings").assertIsDisplayed()
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            shadowOf(manager).setNotificationsEnabled(true)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        compose.onNodeWithTag("app_notifications_disabled").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(shadowOf(manager).getNotification(1001))
            assertNull(shadowOf(manager).getNotification(1002))
        }
    }

    @Test fun settings_intent_targets_this_application() {
        val context = RuntimeEnvironment.getApplication()
        val intent = appNotificationSettingsIntent(context)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun translated_alerts_preserve_existing_channel_ids_values_and_limits() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        HrNotificationHelper.sendHighHrNotification(context, 130, 120)
        HrNotificationHelper.sendLowHrNotification(context, 45, 50)
        val high = shadowOf(manager).getNotification(1001)
        val low = shadowOf(manager).getNotification(1002)
        assertEquals("hr_threshold_alerts", high.channelId)
        assertEquals("hr_threshold_alerts", low.channelId)
        assertEquals("Batimentos acima do limite cadastrado", high.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Batimentos: 130 bpm. Limite cadastrado: 120 bpm.", high.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("Batimentos abaixo do limite cadastrado", low.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Batimentos: 45 bpm. Limite cadastrado: 50 bpm.", low.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel("hr_threshold_alerts").importance)
    }

    @Test fun developer_test_notifications_remain_identifiable_outside_the_app() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        HrNotificationHelper.sendHighHrNotification(context, 138, 120, isTest = true)
        HrNotificationHelper.sendLowHrNotification(context, 42, 50, isTest = true)
        for (id in listOf(1001, 1002)) {
            val notification = shadowOf(manager).getNotification(id)
            assertEquals("hr_threshold_alerts", notification.channelId)
            assertTrue(notification.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Teste de aviso:"))
            assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("Não é uma leitura do relógio."))
        }
    }
}
