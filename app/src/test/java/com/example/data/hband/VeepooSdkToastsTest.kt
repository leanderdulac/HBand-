package com.example.data.hband

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class VeepooSdkToastsTest {
    @Test
    fun disables_sdk_function_not_supported_toasts() {
        VeepooSdkToasts.suppressFunctionNotSupported()
        assertFalse(VeepooSdkToasts.areFunctionNotSupportedToastsEnabled())
    }
}
