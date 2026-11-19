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
        assertNotSame(before, Sdk.sent.first())
        assertTrue(Sdk.sent.first().isSwitchOpen)
        assertEquals(30, Sdk.sent.first().measureInterval)
        assertEquals(120, Sdk.sent.first().currentStartMinute)
        assertEquals(1080, Sdk.sent.first().currentEndMinute)
        val short = original().apply { measureInterval = 5 }
        client.setAutoMeasureEnabled(listOf(short), true)
        assertEquals(30, Sdk.sent.last().measureInterval)
        assertEquals(5, short.measureInterval)
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

    @Test fun battery_policy_sets_hr_every_30_min_and_turns_spo2_off() = runTest {
        Sdk.write = { _, listener -> listener.onSettingDataChangeSuccess() }
        val hr = original().apply { isSwitchOpen = true; measureInterval = 5 }
        val spo2 = original(EAutoMeasureType.BLOOD_OXYGEN).apply { isSwitchOpen = true }
        val result = client.applyBatteryPolicy(listOf(hr, spo2), heartRateUserOff = false)!!
        assertEquals(2, Sdk.sent.size)
        val sentHr = Sdk.sent.first { it.funType == EAutoMeasureType.PULSE_RATE }
        assertTrue(sentHr.isSwitchOpen)
        assertEquals(30, sentHr.measureInterval)
        assertFalse(Sdk.sent.first { it.funType == EAutoMeasureType.BLOOD_OXYGEN }.isSwitchOpen)
        assertEquals(2, result.size)
        assertEquals(5, hr.measureInterval) // não muta a última leitura
    }
    @Test fun battery_policy_does_not_write_compliant_settings() = runTest {
        val hr = original().apply { isSwitchOpen = true; measureInterval = 30 }
        val spo2 = original(EAutoMeasureType.BLOOD_OXYGEN).apply { isSwitchOpen = false }
        assertNull(client.applyBatteryPolicy(listOf(hr, spo2), heartRateUserOff = false))
        assertTrue(Sdk.sent.isEmpty())
    }
    @Test fun battery_policy_failed_write_is_not_a_confirmation() = runTest {
        val hr = original().apply { isSwitchOpen = false }
        assertNull(client.applyBatteryPolicy(listOf(hr), heartRateUserOff = false))
        assertEquals(1, Sdk.sent.size)
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
