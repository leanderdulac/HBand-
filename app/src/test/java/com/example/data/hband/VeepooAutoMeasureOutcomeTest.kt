package com.example.data.hband

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.IAutoMeasureSettingDataListener
import com.veepoo.protocol.model.datas.AutoMeasureData
import com.veepoo.protocol.model.enums.EAutoMeasureType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow

/** Production settings client, synthetic SDK callbacks only; no radio or operational records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class,
    instrumentedPackages = ["com.veepoo.protocol.VPOperateManager"],
    shadows = [VeepooAutoMeasureOutcomeTest.Sdk::class])
@LooperMode(LooperMode.Mode.PAUSED)
class VeepooAutoMeasureOutcomeTest {
    private val sdk = Shadow.newInstanceOf(VPOperateManager::class.java)
    private val client = VeepooHistorySync(sdk, Handler(Looper.getMainLooper()))
    private fun original(type: EAutoMeasureType = EAutoMeasureType.PULSE_RATE) = AutoMeasureData().apply {
        funType = type; isSwitchOpen = false; protocolType = 2; stepUnit = 5
        isSlotModify = true; isIntervalModify = true; supportStartMinute = 60
        supportEndMinute = 1200; measureInterval = 30; currentStartMinute = 120; currentEndMinute = 1080
    }
    @Before fun reset() { Sdk.sent.clear(); Sdk.write = { _, listener -> listener.onSettingDataChangeFail() } }

    @Test fun failed_writes_do_not_fabricate_an_updated_result() = runTest {
        assertNull(client.setAutoMeasureEnabled(listOf(original()), true))
        assertEquals(1, Sdk.sent.size)
    }
    @Test fun preparing_a_request_does_not_mutate_the_last_received_configuration() = runTest {
        val before = original()
        client.setAutoMeasureEnabled(listOf(before), true)
        assertFalse(before.isSwitchOpen)
        assertNotSame(before, Sdk.sent.single())
        assertTrue(Sdk.sent.single().isSwitchOpen)
        assertEquals(30, Sdk.sent.single().measureInterval)
        assertEquals(120, Sdk.sent.single().currentStartMinute)
        assertEquals(1080, Sdk.sent.single().currentEndMinute)
    }
    @Test fun partial_failure_does_not_claim_the_whole_request_was_confirmed() = runTest {
        Sdk.write = { data, listener ->
            if (data.funType == EAutoMeasureType.PULSE_RATE) listener.onSettingDataChangeSuccess()
            else listener.onSettingDataChangeFail()
        }
        assertNull(client.setAutoMeasureEnabled(listOf(original(), original(EAutoMeasureType.BLOOD_OXYGEN)), true))
        assertEquals(2, Sdk.sent.size)
    }
    @Test fun null_data_callback_is_not_a_confirmation() = runTest {
        Sdk.write = { _, listener -> listener.onSettingDataChange(null) }
        assertNull(client.setAutoMeasureEnabled(listOf(original()), true))
    }
    @Test fun explicit_sdk_success_preserves_the_existing_ack_contract() = runTest {
        Sdk.write = { _, listener -> listener.onSettingDataChangeSuccess() }
        val result = client.setAutoMeasureEnabled(listOf(original()), true)
        assertTrue(result!!.single().isSwitchOpen)
        assertEquals(EAutoMeasureType.PULSE_RATE, result.single().funType)
    }

    @Implements(VPOperateManager::class)
    class Sdk {
        companion object {
            val sent = mutableListOf<AutoMeasureData>()
            var write: (AutoMeasureData, IAutoMeasureSettingDataListener) -> Unit = { _, _ -> }
        }
        @Implementation fun setAutoMeasureSettingData(ack: IBleWriteResponse, data: AutoMeasureData, listener: IAutoMeasureSettingDataListener) {
            sent += data; write(data, listener)
        }
    }
}
