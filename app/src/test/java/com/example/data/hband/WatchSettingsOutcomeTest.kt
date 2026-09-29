package com.example.data.hband

import android.app.Application
import android.os.Looper
import com.example.data.model.HBandDevice
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.*
import com.veepoo.protocol.model.datas.*
import com.veepoo.protocol.model.enums.*
import com.veepoo.protocol.model.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

/** Production manager with synthetic connection/SDK only; no real watch or backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [WatchSettingsOutcomeTest.Sdk::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WatchSettingsOutcomeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var manager: HBandBleManager
    private lateinit var original: AutoMeasureData
    @Before fun setup() {
        singletonReset(); Sdk.auto = null; Sdk.spo2 = null; Sdk.wear = null; Sdk.spo2Read = null; Sdk.autoCalls = 0; Sdk.wearCommands.clear()
        manager = HBandBleManager(RuntimeEnvironment.getApplication(), scope)
        field("isVeepooConnection", true); field("userRequestedDisconnect", false)
        flow("_isHardwareConnected", true)
        flow("_connectedDevice", HBandDevice(macAddress = "00:11:22:33:44:55", isConnected = true))
        flow("_capabilities", DeviceCapabilities(probed = true, isSupportAutoMeasure = true,
            isSupportSpo2AutoDetect = true, isSupportWearDetect = true))
        flow("_autoMeasureState", AutoMeasureUiState(supported = true, spo2AutoSupported = true, lastReadAtMs = 123L))
        flow("_wearDetectState", WearDetectUiState(supported = true))
        original = AutoMeasureData().apply { funType = EAutoMeasureType.PULSE_RATE; isSwitchOpen = false }
        field("lastAutoMeasureSettings", listOf(original))
    }
    @After fun stop() { scope.cancel(); idle(); singletonReset() }
    private fun idle() { shadowOf(Looper.getMainLooper()).idle() }
    private fun singletonReset() { VPOperateManager::class.java.declaredFields.single {
        java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == VPOperateManager::class.java
    }.apply { isAccessible = true }.set(null, null) }
    private fun field(name: String, value: Any) { HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value) }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String, value: T) {
        (HBandBleManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager) as MutableStateFlow<T>).value = value
    }

    @Test fun pending_auto_request_is_not_a_new_confirmed_value() {
        manager.setAutoMeasureEnabled(true); idle()
        assertNotNull(Sdk.auto)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
    }
    @Test fun failed_auto_request_preserves_previous_value_and_read_time() {
        manager.setAutoMeasureEnabled(true); idle(); Sdk.auto!!.onSettingDataChangeFail(); idle()
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertFalse(original.isSwitchOpen)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
    }
    @Test fun missing_spo2_response_does_not_promote_the_requested_value() {
        manager.setSpo2AutoDetectEnabled(true); idle(); Sdk.spo2!!.onAllSetDataChangeListener(null); idle()
        assertFalse(manager.autoMeasureState.value.spo2NightAutoEnabled)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
    }
    @Test fun missing_wear_response_does_not_promote_the_requested_value() {
        manager.setWearDetectEnabled(true); idle(); Sdk.wear!!.onCheckWearDataChange(null); idle()
        assertFalse(manager.wearDetectState.value.enabled)
    }

    private fun startHandshake(advance: Boolean = true) {
        HBandBleManager::class.java.getDeclaredMethod("startPostHandshakeSync", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(manager, false)
        if (advance) idle()
    }
    private fun cancelSession() {
        HBandBleManager::class.java.getDeclaredMethod("cancelHistorySync").apply { isAccessible = true }.invoke(manager)
    }
    private fun spo2Result(status: EAllSetStatus) = AllSetData(EAllSetType.SPO2H_NIGHT_AUTO_DETECT, 22, 0, 8, 0, 1, status, 1)
        .apply { isOpen = 1; openState = 1 }

    @Test fun explicit_ack_updates_value_without_claiming_a_new_settings_read() {
        manager.setAutoMeasureEnabled(true); idle(); Sdk.auto!!.onSettingDataChangeSuccess(); idle()
        assertTrue(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(WatchSettingConfirmation.CONFIRMED, manager.autoMeasureState.value.heartRateConfirmation)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
        assertFalse(original.isSwitchOpen)
    }
    @Test fun sdk_failure_statuses_cannot_confirm_spo2_or_wear() {
        manager.setSpo2AutoDetectEnabled(true); manager.setWearDetectEnabled(true); idle()
        Sdk.spo2!!.onAllSetDataChangeListener(spo2Result(EAllSetStatus.OPEN_FAIL)); idle()
        Sdk.wear!!.onCheckWearDataChange(CheckWearData().apply { checkWearState = ECheckWear.OPEN_FAIL }); idle()
        assertFalse(manager.autoMeasureState.value.spo2NightAutoEnabled)
        assertFalse(manager.wearDetectState.value.enabled)
        assertEquals(WatchSettingConfirmation.UNCONFIRMED, manager.autoMeasureState.value.spo2Confirmation)
        assertEquals(WatchSettingConfirmation.UNCONFIRMED, manager.wearDetectState.value.confirmation)
    }
    @Test fun positive_sdk_settings_results_update_only_their_own_values() {
        manager.setSpo2AutoDetectEnabled(true); manager.setWearDetectEnabled(true); idle()
        Sdk.spo2!!.onAllSetDataChangeListener(spo2Result(EAllSetStatus.OPEN_SUCCESS)); idle()
        Sdk.wear!!.onCheckWearDataChange(CheckWearData().apply { checkWearState = ECheckWear.OPEN_SUCCESS }); idle()
        assertTrue(manager.autoMeasureState.value.spo2NightAutoEnabled)
        assertTrue(manager.wearDetectState.value.enabled)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(WatchSettingConfirmation.CONFIRMED, manager.autoMeasureState.value.spo2Confirmation)
        assertEquals(WatchSettingConfirmation.CONFIRMED, manager.wearDetectState.value.confirmation)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
    }
    @Test fun duplicate_pending_request_is_ignored_and_explicit_retry_is_possible_after_failure() {
        manager.setAutoMeasureEnabled(true); manager.setAutoMeasureEnabled(false); idle()
        assertEquals(1, Sdk.autoCalls)
        Sdk.auto!!.onSettingDataChangeFail(); idle()
        assertEquals(WatchSettingConfirmation.UNCONFIRMED, manager.autoMeasureState.value.heartRateConfirmation)
        manager.setAutoMeasureEnabled(true); idle(); Sdk.auto!!.onSettingDataChangeSuccess(); idle()
        assertEquals(2, Sdk.autoCalls); assertTrue(manager.autoMeasureState.value.heartRateEnabled)
    }
    @Test fun cancelled_scope_before_launch_does_not_leave_a_permanent_pending_setting() {
        manager.setAutoMeasureEnabled(true); scope.cancel(); idle()
        assertEquals(WatchSettingConfirmation.UNCONFIRMED, manager.autoMeasureState.value.heartRateConfirmation)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertNull(Sdk.auto)
    }
    @Test fun no_sdk_callback_times_out_without_success_or_a_new_read_time() {
        manager.setAutoMeasureEnabled(true); idle()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(20))
        assertEquals(WatchSettingConfirmation.UNCONFIRMED, manager.autoMeasureState.value.heartRateConfirmation)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
        assertEquals(1, Sdk.autoCalls)
    }
    @Test fun late_callback_from_cancelled_session_cannot_complete_a_new_request() {
        manager.setAutoMeasureEnabled(true); idle(); val old = Sdk.auto!!
        cancelSession()
        manager.setAutoMeasureEnabled(true); idle()
        old.onSettingDataChangeSuccess(); idle()
        val current = Sdk.auto!!
        assertNotSame(old, current)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(WatchSettingConfirmation.PENDING, manager.autoMeasureState.value.heartRateConfirmation)
        current.onSettingDataChangeSuccess(); idle()
        assertTrue(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(WatchSettingConfirmation.CONFIRMED, manager.autoMeasureState.value.heartRateConfirmation)
    }
    @Test fun older_handshake_completes_before_the_queued_explicit_write_and_cannot_confirm_it() {
        flow("_capabilities", DeviceCapabilities(probed = true, isSupportAutoMeasure = true, isSupportSpo2AutoDetect = true))
        startHandshake()
        assertNotNull(Sdk.spo2Read)
        manager.setAutoMeasureEnabled(true); idle()
        assertNull(Sdk.auto)
        assertEquals(WatchSettingConfirmation.PENDING, manager.autoMeasureState.value.heartRateConfirmation)
        Sdk.spo2Read!!.onAllSetDataChangeListener(spo2Result(EAllSetStatus.READ_SUCCESS)); idle()
        assertNotNull(Sdk.auto)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(WatchSettingConfirmation.PENDING, manager.autoMeasureState.value.heartRateConfirmation)
        Sdk.auto!!.onSettingDataChangeSuccess(); idle()
        assertTrue(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(WatchSettingConfirmation.CONFIRMED, manager.autoMeasureState.value.heartRateConfirmation)
    }
    @Test fun handshake_scheduled_before_a_request_cannot_remove_its_pending_state() {
        flow("_capabilities", DeviceCapabilities(probed = true, isSupportAutoMeasure = true))
        startHandshake(advance = false)
        manager.setAutoMeasureEnabled(true)
        idle()
        assertEquals(WatchSettingConfirmation.PENDING, manager.autoMeasureState.value.heartRateConfirmation)
        assertFalse(manager.autoMeasureState.value.heartRateEnabled)
        assertEquals(123L, manager.autoMeasureState.value.lastReadAtMs)
        Sdk.auto!!.onSettingDataChangeSuccess(); idle()
        assertTrue(manager.autoMeasureState.value.heartRateEnabled)
    }

    @Test fun explicit_wear_change_waits_for_handshake_then_runs_last() {
        startHandshake()
        assertNotNull(Sdk.spo2Read)
        manager.setWearDetectEnabled(false); idle()
        assertNull("The explicit write must wait for handshake settings", Sdk.wear)
        assertEquals(WatchSettingConfirmation.PENDING, manager.wearDetectState.value.confirmation)
        Sdk.spo2Read!!.onAllSetDataChangeListener(spo2Result(EAllSetStatus.READ_SUCCESS)); idle()
        assertEquals(listOf(true), Sdk.wearCommands) // Existing saved preference used by handshake.
        Sdk.wear!!.onCheckWearDataChange(CheckWearData().apply { checkWearState = ECheckWear.OPEN_SUCCESS }); idle()
        assertEquals(listOf(true, false), Sdk.wearCommands)
        assertEquals(WatchSettingConfirmation.PENDING, manager.wearDetectState.value.confirmation)
        Sdk.wear!!.onCheckWearDataChange(CheckWearData().apply { checkWearState = ECheckWear.CLOSE_SUCCESS }); idle()
        assertFalse(manager.wearDetectState.value.enabled)
        assertEquals(WatchSettingConfirmation.CONFIRMED, manager.wearDetectState.value.confirmation)
    }

    @Implements(VPOperateManager::class)
    class Sdk {
        companion object { var auto: IAutoMeasureSettingDataListener? = null; var spo2: IAllSetDataListener? = null; var wear: ICheckWearDataListener? = null; var spo2Read: IAllSetDataListener? = null; var autoCalls = 0; val wearCommands = mutableListOf<Boolean>() }
        @Implementation fun readBattery(ack: IBleWriteResponse, listener: IBatteryDataListener) { listener.onDataChange(BatteryData().apply { batteryPercent = 80 }) }
        @Implementation fun readSportStep(ack: IBleWriteResponse, listener: ISportDataListener) { listener.onSportDataChange(SportData().apply { step = 0; dis = 0.0; kcal = 0.0 }) }
        @Implementation fun readAutoMeasureSettingData(ack: IBleWriteResponse, listener: IAutoMeasureSettingDataListener) {
            listener.onSettingDataChange(mutableListOf(AutoMeasureData().apply { funType = EAutoMeasureType.PULSE_RATE; isSwitchOpen = false }))
        }
        @Implementation fun readSpo2hAutoDetect(ack: IBleWriteResponse, listener: IAllSetDataListener) { spo2Read = listener }
        @Implementation fun readSleepDataFromDay(ack: IBleWriteResponse, listener: ISleepDataListener, days: Int, offset: Int) { listener.onReadSleepComplete() }
        @Implementation fun readSleepDataBySetting(ack: IBleWriteResponse, listener: ISleepDataListener, setting: ReadSleepSetting) { listener.onReadSleepComplete() }
        @Implementation fun setAutoMeasureSettingData(ack: IBleWriteResponse, data: AutoMeasureData, listener: IAutoMeasureSettingDataListener) { autoCalls++; auto = listener }
        @Implementation fun settingSpo2hAutoDetect(ack: IBleWriteResponse, listener: IAllSetDataListener, setting: AllSetSetting) { spo2 = listener }
        @Implementation fun setttingCheckWear(ack: IBleWriteResponse, listener: ICheckWearDataListener, setting: CheckWearSetting) { wearCommands += setting.isOpen; wear = listener }
    }
}
