package com.example.data.hband

import android.app.Application
import com.inuker.bluetooth.library.connect.response.BleWriteResponse
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.IBPDetectDataListener
import com.veepoo.protocol.listener.data.IHrvDetectListener
import com.veepoo.protocol.model.enums.EBPDetectModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode

/** Production BP stage; only the Veepoo write ACK is synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [VeepooBpDetectRetryStageTest.Sdk::class])
@LooperMode(LooperMode.Mode.PAUSED)
class VeepooBpDetectRetryStageTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var manager: HBandBleManager

    @Before fun setup() {
        resetSdkSingleton()
        Sdk.startCount = 0
        Sdk.ackCode = -1
        manager = HBandBleManager(RuntimeEnvironment.getApplication(), scope)
        setField("isConnectingVeepoo", true)
    }

    @After fun stop() {
        val handler = HBandBleManager::class.java.getDeclaredField("mainHandler")
            .apply { isAccessible = true }.get(manager) as android.os.Handler
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        resetSdkSingleton()
    }

    @Test fun failed_startDetectBP_logs_backoff_and_skips_the_next_cycle() {
        invokeBpStage()
        assertEquals(1, Sdk.startCount)
        val retry = bpDetectRetry()
        assertEquals(1, retry.consecutiveFailures)
        assertFalse(retry.shouldAttempt(0L))

        invokeBpStage()
        assertEquals("Backoff must skip a later cycle instead of retrying immediately", 1, Sdk.startCount)
        assertTrue(
            org.robolectric.shadows.ShadowLog.getLogs().any {
                it.tag == "HBandBleManager" && it.msg.contains("startDetectBP falhou no VE30")
            },
        )
    }

    private fun invokeBpStage() {
        HBandBleManager::class.java.getDeclaredMethod("runBpStage").apply { isAccessible = true }.invoke(manager)
    }

    private fun bpDetectRetry(): BpDetectRetry {
        val field = HBandBleManager::class.java.getDeclaredField("bpDetectRetry").apply { isAccessible = true }
        return field.get(manager) as BpDetectRetry
    }

    private fun setField(name: String, value: Any) {
        HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }

    private fun resetSdkSingleton() {
        VPOperateManager::class.java.declaredFields.single {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == VPOperateManager::class.java
        }.apply { isAccessible = true }.set(null, null)
    }

    @Implements(VPOperateManager::class)
    class Sdk {
        companion object {
            var startCount = 0
            var ackCode = -1
        }

        @Implementation
        fun startDetectBP(write: IBleWriteResponse, listener: IBPDetectDataListener, model: EBPDetectModel) {
            startCount++
            write.onResponse(ackCode)
        }

        @Implementation
        fun stopDetectBP(write: IBleWriteResponse, model: EBPDetectModel) {}

        @Implementation
        fun startDetectHrv(write: BleWriteResponse, listener: IHrvDetectListener) {}

        @Implementation
        fun stopDetectHrv(write: BleWriteResponse, listener: IHrvDetectListener) {}
    }
}
