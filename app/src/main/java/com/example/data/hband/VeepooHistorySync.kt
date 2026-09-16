package com.example.data.hband

import android.os.Handler
import android.util.Log
import com.example.data.model.HBandTelemetry
import com.inuker.bluetooth.library.Constants
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.IAllSetDataListener
import com.veepoo.protocol.listener.data.IAutoMeasureSettingDataListener
import com.veepoo.protocol.listener.data.IBatteryDataListener
import com.veepoo.protocol.listener.data.ICheckWearDataListener
import com.veepoo.protocol.listener.data.IHRVOriginDataListener
import com.veepoo.protocol.listener.data.IOriginData3Listener
import com.veepoo.protocol.listener.data.IOriginDataListener
import com.veepoo.protocol.listener.data.IOriginProgressListener
import com.veepoo.protocol.listener.data.ISleepDataListener
import com.veepoo.protocol.listener.data.ISportDataListener
import com.veepoo.protocol.listener.data.ISpo2hOriginDataListener
import com.veepoo.protocol.model.datas.AllSetData
import com.veepoo.protocol.model.datas.AutoMeasureData
import com.veepoo.protocol.model.datas.BatteryData
import com.veepoo.protocol.model.datas.CheckWearData
import com.veepoo.protocol.model.datas.HRVOriginData
import com.veepoo.protocol.model.datas.OriginData
import com.veepoo.protocol.model.datas.OriginData3
import com.veepoo.protocol.model.datas.OriginHalfHourData
import com.veepoo.protocol.model.datas.SleepData
import com.veepoo.protocol.model.datas.Spo2hOriginData
import com.veepoo.protocol.model.datas.SportData
import com.veepoo.protocol.model.enums.EAllSetType
import com.veepoo.protocol.model.enums.EAutoMeasureType
import com.veepoo.protocol.model.enums.ECheckWear
import com.veepoo.protocol.model.settings.AllSetSetting
import com.veepoo.protocol.model.settings.CheckWearSetting
import com.veepoo.protocol.model.settings.ReadOriginSetting
import com.veepoo.protocol.model.settings.ReadSleepSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sequential Veepoo history / settings client. Firmware on VE30-class bands
 * drops overlapping Origin/sleep/HRV reads, so every call waits for complete
 * or a timeout and then retries — same pattern as the live SingleDay reads.
 */
class VeepooHistorySync(
    private val vpManager: VPOperateManager,
    private val mainHandler: Handler,
) {
    private val TAG = "VeepooHistorySync"

    @Volatile
    var cancelled: Boolean = false

    data class HandshakeExtras(
        val batteryPercent: Int? = null,
        val steps: Int? = null,
        val calories: Float? = null,
        val distanceMeters: Float? = null,
        val autoMeasure: List<AutoMeasureData> = emptyList(),
        val spo2Auto: AllSetData? = null,
        val wear: CheckWearData? = null,
    )

    data class HistoryPull(
        val samples: List<VeepooHistoryMapper.MappedSample>,
        val state: HistorySyncUiState,
    )

    suspend fun readHandshakeExtras(
        caps: DeviceCapabilities,
        wearEnabled: Boolean,
        onBattery: (Int) -> Unit = {},
    ): HandshakeExtras {
        val battery = retrying(times = 2, timeoutMs = SETTINGS_TIMEOUT_MS) { readBattery() }
        battery?.let(onBattery)
        val sport = retrying(times = 2, timeoutMs = SETTINGS_TIMEOUT_MS) { readSport() }
        val auto = if (caps.isSupportAutoMeasure) {
            retrying(times = 2, timeoutMs = SETTINGS_TIMEOUT_MS) { readAutoMeasure() } ?: emptyList()
        } else {
            emptyList()
        }
        val spo2Auto = if (caps.isSupportSpo2AutoDetect) {
            retrying(times = 2, timeoutMs = SETTINGS_TIMEOUT_MS) { readSpo2Auto() }
        } else {
            null
        }
        val wear = if (caps.isSupportWearDetect) {
            retrying(times = 2, timeoutMs = SETTINGS_TIMEOUT_MS) { applyWearDetect(wearEnabled) }
        } else {
            null
        }
        return HandshakeExtras(
            batteryPercent = battery,
            steps = sport?.step?.takeIf { it > 0 },
            calories = sport?.kcal?.toFloat()?.takeIf { it > 0f },
            distanceMeters = sport?.dis?.toFloat()?.takeIf { it > 0f },
            autoMeasure = auto,
            spo2Auto = spo2Auto,
            wear = wear,
        )
    }

    suspend fun pullHistory(
        caps: DeviceCapabilities,
        deviceId: String,
        deviceModel: String,
        onProgress: (HistorySyncUiState) -> Unit,
    ): HistoryPull {
        val collected = mutableListOf<VeepooHistoryMapper.MappedSample>()
        var state = HistorySyncUiState(isRunning = true, phase = "origin")
        onProgress(state)
        if (cancelled) return HistoryPull(emptyList(), state.copy(isRunning = false, lastError = "cancelado"))

        val watchDay = caps.historyDays.coerceIn(1, 7)

        if (caps.canReadMultiDayOrigin) {
            val origin = retrying(times = 3, timeoutMs = ORIGIN_TIMEOUT_MS) {
                readOrigin(caps, watchDay, deviceId, deviceModel) { progress ->
                    onProgress(state.copy(progress = progress, phase = "origin"))
                }
            }
            collected += origin.orEmpty()
            state = state.copy(
                originSamples = origin?.size ?: 0,
                progress = 0.25f,
                lastError = if (origin == null) "timeout Origin" else null,
            )
            onProgress(state)
        }

        if (!cancelled && caps.canReadSleepHistory) {
            state = state.copy(phase = "sleep")
            onProgress(state)
            val sleep = retrying(times = 3, timeoutMs = SLEEP_TIMEOUT_MS) {
                readSleep(watchDay, deviceId, deviceModel) { progress ->
                    onProgress(state.copy(progress = 0.25f + progress * 0.2f, phase = "sleep"))
                }
            }
            collected += sleep.orEmpty()
            state = state.copy(
                sleepDays = sleep?.size ?: 0,
                progress = 0.45f,
                lastError = state.lastError ?: if (sleep == null) "timeout sono" else null,
            )
            onProgress(state)
        }

        if (!cancelled && caps.canReadHrvOrigin) {
            state = state.copy(phase = "hrv")
            onProgress(state)
            val hrv = retrying(times = 3, timeoutMs = HRV_TIMEOUT_MS) {
                readHrv(watchDay, deviceId, deviceModel) { progress ->
                    onProgress(state.copy(progress = 0.45f + progress * 0.25f, phase = "hrv"))
                }
            }
            collected += hrv.orEmpty()
            state = state.copy(
                hrvSamples = hrv?.size ?: 0,
                progress = 0.7f,
                lastError = state.lastError ?: if (hrv == null) "timeout HRV" else null,
            )
            onProgress(state)
        }

        if (!cancelled && caps.canReadSpo2Origin) {
            state = state.copy(phase = "spo2")
            onProgress(state)
            val spo2 = retrying(times = 3, timeoutMs = SPO2_TIMEOUT_MS) {
                readSpo2Origin(watchDay, deviceId, deviceModel) { progress ->
                    onProgress(state.copy(progress = 0.7f + progress * 0.25f, phase = "spo2"))
                }
            }
            collected += spo2.orEmpty()
            state = state.copy(
                spo2Samples = spo2?.size ?: 0,
                progress = 1f,
                lastError = state.lastError ?: if (spo2 == null) "timeout SpO2 origin" else null,
            )
            onProgress(state)
        }

        val finished = state.copy(
            isRunning = false,
            phase = "done",
            progress = 1f,
            lastCompletedAtMs = System.currentTimeMillis(),
        )
        onProgress(finished)
        return HistoryPull(collected, finished)
    }

    suspend fun setAutoMeasureEnabled(
        current: List<AutoMeasureData>,
        enabled: Boolean,
    ): List<AutoMeasureData>? {
        if (current.isEmpty()) {
            val pulse = AutoMeasureData().apply {
                funType = EAutoMeasureType.PULSE_RATE
                isSwitchOpen = enabled
            }
            return writeAutoMeasure(pulse)
        }
        val updated = current.map { item ->
            if (item.funType == EAutoMeasureType.PULSE_RATE || item.funType == EAutoMeasureType.BLOOD_OXYGEN) {
                item.apply { isSwitchOpen = enabled }
            } else {
                item
            }
        }
        var last: List<AutoMeasureData>? = null
        for (item in updated) {
            last = writeAutoMeasure(item) ?: last
        }
        return last ?: updated
    }

    suspend fun setSpo2AutoEnabled(enabled: Boolean, existing: AllSetData?): AllSetData? {
        val startH = existing?.startHour ?: 22
        val startM = existing?.startMinute ?: 0
        val endH = existing?.endHour ?: 8
        val endM = existing?.endMinute ?: 0
        val setting = AllSetSetting(
            EAllSetType.SPO2H_NIGHT_AUTO_DETECT,
            startH,
            startM,
            endH,
            endM,
            1,
            if (enabled) 1 else 0,
        )
        return awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
            vpManager.settingSpo2hAutoDetect(
                ackLogger(),
                IAllSetDataListener { data ->
                    if (data == null) fail("empty") else done(data)
                },
                setting,
            )
        }
    }

    suspend fun applyWearDetect(enabled: Boolean): CheckWearData? {
        val setting = CheckWearSetting().apply { isOpen = enabled }
        return awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
            vpManager.setttingCheckWear(
                ackLogger(),
                ICheckWearDataListener { data ->
                    if (data == null) fail("empty") else done(data)
                },
                setting,
            )
        }
    }

    fun wearEnabledFrom(data: CheckWearData?, requested: Boolean): Boolean {
        return when (data?.checkWearState) {
            ECheckWear.OPEN_SUCCESS -> true
            ECheckWear.CLOSE_SUCCESS -> false
            ECheckWear.READ_SUCCESS -> requested
            else -> requested
        }
    }

    private suspend fun readBattery(): Int? = awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
        vpManager.readBattery(
            ackLogger(),
            IBatteryDataListener { data: BatteryData? ->
                val percent = VeepooBatteryMapper.fromSdk(data)
                if (percent == null) fail("empty battery") else done(percent)
            },
        )
    }

    private suspend fun readSport(): SportData? = awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
        vpManager.readSportStep(
            ackLogger(),
            ISportDataListener { data ->
                if (data == null) fail("empty sport") else done(data)
            },
        )
    }

    private suspend fun readAutoMeasure(): List<AutoMeasureData>? =
        awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
            vpManager.readAutoMeasureSettingData(
                ackLogger(),
                object : IAutoMeasureSettingDataListener {
                    override fun onSettingDataChange(list: MutableList<AutoMeasureData>?) {
                        done(list?.toList().orEmpty())
                    }

                    override fun onSettingDataChangeFail() {
                        fail("auto-measure fail")
                    }

                    override fun onSettingDataChangeSuccess() {
                        // write ACK only; read path uses onSettingDataChange
                    }
                },
            )
        }

    private suspend fun writeAutoMeasure(data: AutoMeasureData): List<AutoMeasureData>? =
        awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
            vpManager.setAutoMeasureSettingData(
                ackLogger(),
                data,
                object : IAutoMeasureSettingDataListener {
                    override fun onSettingDataChange(list: MutableList<AutoMeasureData>?) {
                        done(list?.toList() ?: listOf(data))
                    }

                    override fun onSettingDataChangeFail() {
                        fail("set auto-measure fail")
                    }

                    override fun onSettingDataChangeSuccess() {
                        done(listOf(data))
                    }
                },
            )
        }

    private suspend fun readSpo2Auto(): AllSetData? = awaitResult(SETTINGS_TIMEOUT_MS) { done, fail ->
        vpManager.readSpo2hAutoDetect(
            ackLogger(),
            IAllSetDataListener { data ->
                if (data == null) fail("empty spo2 auto") else done(data)
            },
        )
    }

    private suspend fun readOrigin(
        caps: DeviceCapabilities,
        watchDay: Int,
        deviceId: String,
        deviceModel: String,
        onProgress: (Float) -> Unit,
    ): List<VeepooHistoryMapper.MappedSample>? {
        val samples = mutableListOf<VeepooHistoryMapper.MappedSample>()
        val setting = ReadOriginSetting(watchDay, 0, false, 1)
        val completed = awaitComplete(ORIGIN_TIMEOUT_MS) { done, fail ->
            val listener = originListener(
                protocolVersion = caps.originProtocolVersion,
                deviceId = deviceId,
                deviceModel = deviceModel,
                sink = samples,
                onProgress = onProgress,
                onComplete = done,
                onFail = fail,
            )
            activeOriginListener = listener
            try {
                vpManager.readOriginDataBySetting(ackLogger(), listener, setting)
            } catch (e: Exception) {
                Log.w(TAG, "readOriginDataBySetting failed, falling back to FromDay: ${e.message}")
                try {
                    vpManager.readOriginDataFromDay(ackLogger(), listener, watchDay, 0, 1)
                } catch (inner: Exception) {
                    fail(inner.message ?: "origin start failed")
                }
            }
        }
        return if (completed) samples.toList() else null
    }

    private suspend fun readSleep(
        watchDay: Int,
        deviceId: String,
        deviceModel: String,
        onProgress: (Float) -> Unit,
    ): List<VeepooHistoryMapper.MappedSample>? {
        val samples = mutableListOf<VeepooHistoryMapper.MappedSample>()
        val completed = awaitComplete(SLEEP_TIMEOUT_MS) { done, fail ->
            val listener = object : ISleepDataListener {
                override fun onSleepDataChange(date: String?, sleepData: SleepData?) {
                    val mapped = sleepData?.let { VeepooHistoryMapper.fromSleep(it, deviceId, deviceModel) }
                    if (mapped != null) samples += mapped
                }

                override fun onSleepProgress(progress: Float) {
                    onProgress((progress / 100f).coerceIn(0f, 1f))
                }

                override fun onSleepProgressDetail(day: String?, progress: Int) = Unit

                override fun onReadSleepComplete() {
                    done()
                }
            }
            try {
                vpManager.readSleepDataFromDay(ackLogger(), listener, watchDay, 0)
            } catch (e: Exception) {
                Log.w(TAG, "readSleepDataFromDay failed, using BySetting: ${e.message}")
                try {
                    vpManager.readSleepDataBySetting(
                        ackLogger(),
                        listener,
                        ReadSleepSetting(watchDay, false, 0),
                    )
                } catch (inner: Exception) {
                    fail(inner.message ?: "sleep start failed")
                }
            }
        }
        return if (completed) samples.toList() else null
    }

    private suspend fun readHrv(
        watchDay: Int,
        deviceId: String,
        deviceModel: String,
        onProgress: (Float) -> Unit,
    ): List<VeepooHistoryMapper.MappedSample>? {
        val samples = mutableListOf<VeepooHistoryMapper.MappedSample>()
        val completed = awaitComplete(HRV_TIMEOUT_MS) { done, fail ->
            val listener = object : IHRVOriginDataListener {
                override fun onReadOriginProgress(progress: Float) {
                    onProgress((progress / 100f).coerceIn(0f, 1f))
                }

                override fun onReadOriginProgressDetail(day: Int, date: String?, all: Int, current: Int) = Unit

                override fun onHRVOriginListener(data: HRVOriginData?) {
                    val mapped = data?.let { VeepooHistoryMapper.fromHrv(it, deviceId, deviceModel) }
                    if (mapped != null) samples += mapped
                }

                override fun onDayHrvScore(day: Int, date: String?, score: Int) = Unit

                override fun onReadOriginComplete() {
                    done()
                }
            }
            try {
                vpManager.readHRVOriginBySetting(
                    ackLogger(),
                    listener,
                    ReadOriginSetting(watchDay, 0, false, 1),
                )
            } catch (e: Exception) {
                Log.w(TAG, "readHRVOriginBySetting failed, using readHRVOrigin: ${e.message}")
                try {
                    vpManager.readHRVOrigin(ackLogger(), listener, watchDay)
                } catch (inner: Exception) {
                    fail(inner.message ?: "hrv start failed")
                }
            }
        }
        return if (completed) samples.toList() else null
    }

    private suspend fun readSpo2Origin(
        watchDay: Int,
        deviceId: String,
        deviceModel: String,
        onProgress: (Float) -> Unit,
    ): List<VeepooHistoryMapper.MappedSample>? {
        val samples = mutableListOf<VeepooHistoryMapper.MappedSample>()
        val completed = awaitComplete(SPO2_TIMEOUT_MS) { done, fail ->
            val listener = object : ISpo2hOriginDataListener {
                override fun onReadOriginProgress(progress: Float) {
                    onProgress((progress / 100f).coerceIn(0f, 1f))
                }

                override fun onReadOriginProgressDetail(day: Int, date: String?, all: Int, current: Int) = Unit

                override fun onSpo2hOriginListener(data: Spo2hOriginData?) {
                    val mapped = data?.let { VeepooHistoryMapper.fromSpo2(it, deviceId, deviceModel) }
                    if (mapped != null) samples += mapped
                }

                override fun onReadOriginComplete() {
                    done()
                }
            }
            try {
                vpManager.readSpo2hOriginBySetting(
                    ackLogger(),
                    listener,
                    ReadOriginSetting(watchDay, 0, false, 1),
                )
            } catch (e: Exception) {
                Log.w(TAG, "readSpo2hOriginBySetting failed, using readSpo2hOrigin: ${e.message}")
                try {
                    vpManager.readSpo2hOrigin(ackLogger(), listener, watchDay)
                } catch (inner: Exception) {
                    fail(inner.message ?: "spo2 origin start failed")
                }
            }
        }
        return if (completed) samples.toList() else null
    }

    private fun originListener(
        protocolVersion: Int,
        deviceId: String,
        deviceModel: String,
        sink: MutableList<VeepooHistoryMapper.MappedSample>,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onFail: (String) -> Unit,
    ): IOriginProgressListener {
        fun addOrigin(origin: OriginData?) {
            val mapped = origin?.let { VeepooHistoryMapper.fromOrigin(it, deviceId, deviceModel) }
            if (mapped != null) sink += mapped
        }

        val progress = object : IOriginProgressListener {
            override fun onReadOriginProgressDetail(day: Int, date: String?, all: Int, current: Int) = Unit
            override fun onReadOriginProgress(progress: Float) {
                onProgress((progress / 100f).coerceIn(0f, 1f))
            }
            override fun onReadOriginComplete() {
                onComplete()
            }
            override fun onReadTimeout(code: Int) {
                onFail("origin timeout $code")
            }
        }

        val listener: IOriginProgressListener = if (protocolVersion >= 3) {
            object : IOriginData3Listener {
                override fun onOriginFiveMinuteListDataChange(list: MutableList<OriginData3>?) {
                    list?.forEach { addOrigin(it) }
                }

                override fun onOriginHalfHourDataChange(data: OriginHalfHourData?) {
                    data?.halfHourRateDatas?.forEach { rate ->
                        val origin = OriginData().apply {
                            date = data.date
                            rateValue = rate.rateValue
                            stepValue = 0
                            setmTime(rate.time)
                        }
                        addOrigin(origin)
                    }
                }

                override fun onOriginHRVOriginListDataChange(list: MutableList<HRVOriginData>?) {
                    list?.forEach { hrv ->
                        VeepooHistoryMapper.fromHrv(hrv, deviceId, deviceModel)?.let { sink += it }
                    }
                }

                override fun onOriginSpo2OriginListDataChange(list: MutableList<Spo2hOriginData>?) {
                    list?.forEach { spo2 ->
                        VeepooHistoryMapper.fromSpo2(spo2, deviceId, deviceModel)?.let { sink += it }
                    }
                }

                override fun onReadOriginProgressDetail(day: Int, date: String?, all: Int, current: Int) =
                    progress.onReadOriginProgressDetail(day, date, all, current)

                override fun onReadOriginProgress(value: Float) = progress.onReadOriginProgress(value)
                override fun onReadOriginComplete() = progress.onReadOriginComplete()
                override fun onReadTimeout(code: Int) = progress.onReadTimeout(code)
            }
        } else {
            object : IOriginDataListener {
                override fun onOringinFiveMinuteDataChange(data: OriginData?) = addOrigin(data)
                override fun onOringinHalfHourDataChange(data: OriginHalfHourData?) {
                    data?.halfHourRateDatas?.forEach { rate ->
                        val origin = OriginData().apply {
                            date = data.date
                            rateValue = rate.rateValue
                            setmTime(rate.time)
                        }
                        addOrigin(origin)
                    }
                }

                override fun onReadOriginProgressDetail(day: Int, date: String?, all: Int, current: Int) =
                    progress.onReadOriginProgressDetail(day, date, all, current)

                override fun onReadOriginProgress(value: Float) = progress.onReadOriginProgress(value)
                override fun onReadOriginComplete() = progress.onReadOriginComplete()
                override fun onReadTimeout(code: Int) = progress.onReadTimeout(code)
            }
        }

        return listener
    }

    private var activeOriginListener: IOriginProgressListener? = null

    private suspend fun <T> retrying(
        times: Int,
        timeoutMs: Long,
        block: suspend () -> T?,
    ): T? {
        repeat(times) { attempt ->
            if (cancelled) return null
            val result = try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "SDK call failed (attempt ${attempt + 1}): ${e.message}")
                null
            }
            if (result != null) return result
            delay(RETRY_GAP_MS * (attempt + 1))
        }
        return null
    }

    private suspend fun <T> awaitResult(
        timeoutMs: Long,
        start: (done: (T) -> Unit, fail: (String) -> Unit) -> Unit,
    ): T? = suspendCancellableCoroutine { cont ->
        if (cancelled) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val timeout = Runnable {
            if (cont.isActive) {
                Log.w(TAG, "SDK call timed out after ${timeoutMs}ms")
                cont.resume(null)
            }
        }
        mainHandler.postDelayed(timeout, timeoutMs)
        val finish: (T?) -> Unit = { value ->
            mainHandler.removeCallbacks(timeout)
            if (cont.isActive) cont.resume(value)
        }
        try {
            start({ finish(it) }, { finish(null) })
        } catch (e: Exception) {
            Log.w(TAG, "SDK start failed: ${e.message}")
            finish(null)
        }
        cont.invokeOnCancellation { mainHandler.removeCallbacks(timeout) }
    }

    private suspend fun awaitComplete(
        timeoutMs: Long,
        start: (done: () -> Unit, fail: (String) -> Unit) -> Unit,
    ): Boolean {
        val result = awaitResult<Boolean>(timeoutMs) { done, fail ->
            start({ done(true) }, { fail(it) })
        }
        return result == true
    }

    private fun ackLogger(): IBleWriteResponse = IBleWriteResponse { code ->
        if (code != Constants.REQUEST_SUCCESS) {
            Log.w(TAG, "Veepoo write ACK code=$code")
        }
    }

    companion object {
        private const val SETTINGS_TIMEOUT_MS = 15_000L
        private const val ORIGIN_TIMEOUT_MS = 120_000L
        private const val SLEEP_TIMEOUT_MS = 60_000L
        private const val HRV_TIMEOUT_MS = 90_000L
        private const val SPO2_TIMEOUT_MS = 90_000L
        private const val RETRY_GAP_MS = 800L
    }
}
