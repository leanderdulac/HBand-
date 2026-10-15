package com.example.data.hband

import android.app.Application
import android.content.Context
import android.content.Intent
import com.example.HBandHealthSyncApp
import com.example.resolveRuntimeBleManager
import com.example.ui.theme.AppThemeOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BleRuntimeInitTest {

    @Test
    fun unready_app_throws_the_device_lateinit_crash_only_on_direct_getter() {
        val app = HBandHealthSyncApp()
        try {
            assertNull(app.readyBleManagerOrNull())
            try {
                app.bleManager
                fail("expected UninitializedPropertyAccessException")
            } catch (error: UninitializedPropertyAccessException) {
                assertTrue(error.message!!.contains("bleManager"))
            }
        } finally {
            app.bleScope.cancel()
        }
    }

    @Test
    fun resolve_uses_fallback_instead_of_lateinit_getter_before_runtime() {
        val app = HBandHealthSyncApp()
        try {
            val fallback = HBandBleManager(RuntimeEnvironment.getApplication(), app.bleScope)
            var fallbacks = 0
            val resolved = resolveRuntimeBleManager(app) {
                fallbacks++
                fallback
            }
            assertEquals(1, fallbacks)
            assertSame(fallback, resolved)
            assertNull(app.readyBleManagerOrNull())
        } finally {
            app.bleScope.cancel()
        }
    }

    @Test
    fun ready_runtime_is_preferred_over_fallback() = runBlocking {
        val app = HBandHealthSyncApp()
        try {
            val runtime = HBandBleManager(RuntimeEnvironment.getApplication(), app.bleScope)
            app.storageStartup.initialize({}, { app.installRuntimeBleManager(runtime) })
            assertTrue(app.storageStartup.isReady)
            val resolved = resolveRuntimeBleManager(app) { error("must use the installed runtime manager") }
            assertSame(runtime, resolved)
            assertSame(runtime, app.readyBleManagerOrNull())
        } finally {
            app.bleScope.cancel()
        }
    }

    @Test
    fun boot_and_replace_do_not_throw_when_runtime_is_unready() {
        val receiver = HBandBootReceiver()
        val context = RuntimeEnvironment.getApplication()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun stored_theme_does_not_need_viewmodel_or_ble_manager() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(AppThemeOption.SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(AppThemeOption.PREFS_KEY, AppThemeOption.DARK_INDIGO.storageKey)
            .commit()
        assertEquals(AppThemeOption.DARK_INDIGO, AppThemeOption.storedIn(context))
    }

    @Test
    fun initialize_failure_does_not_install_a_manager() = runBlocking {
        val app = HBandHealthSyncApp()
        try {
            app.storageStartup.initialize({ throw IllegalStateException("storage") }, { error("runtime") })
            assertNull(app.readyBleManagerOrNull())
            try {
                app.bleManager
                fail("expected UninitializedPropertyAccessException")
            } catch (_: UninitializedPropertyAccessException) {
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        } finally {
            app.bleScope.cancel()
        }
    }
}
