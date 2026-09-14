package com.example.data.hband

/**
 * P0 capability snapshot after Veepoo handshake.
 *
 * Flags are derived from [com.veepoo.protocol.model.datas.FunctionDeviceSupportData]
 * plus [com.veepoo.protocol.util.FunctionCheckUtil] — never assumed true.
 */
data class DeviceCapabilities(
    val historyDays: Int = 1,
    val originProtocolVersion: Int = 0,
    val isSupportAutoMeasure: Boolean = false,
    val isSupportPreciseSleep: Boolean = false,
    val isSupportWearDetect: Boolean = false,
    val isSupportSpo2: Boolean = false,
    val isSupportSpo2AutoDetect: Boolean = false,
    val isSupportHrv: Boolean = false,
    val isSupportAllDayHrv: Boolean = false,
    val isSupportHeart: Boolean = false,
    val isSupportBp: Boolean = false,
    val isSupportTemperature: Boolean = false,
    val probed: Boolean = false,
) {
    val canReadMultiDayOrigin: Boolean get() = probed && historyDays > 0
    val canReadSleepHistory: Boolean get() = probed
    val canReadHrvOrigin: Boolean get() = probed && isSupportHrv
    val canReadSpo2Origin: Boolean get() = probed && isSupportSpo2
}

data class AutoMeasureUiState(
    val supported: Boolean = false,
    val heartRateEnabled: Boolean = false,
    val spo2NightAutoEnabled: Boolean = false,
    val spo2AutoSupported: Boolean = false,
    val lastReadAtMs: Long? = null,
    val summary: String = "",
)

data class WearDetectUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val lastResult: String = "",
    val lastWorn: Boolean? = null,
)

data class HistorySyncUiState(
    val isRunning: Boolean = false,
    val phase: String = "idle",
    val progress: Float = 0f,
    val originSamples: Int = 0,
    val sleepDays: Int = 0,
    val hrvSamples: Int = 0,
    val spo2Samples: Int = 0,
    val lastError: String? = null,
    val lastCompletedAtMs: Long? = null,
) {
    val totalSamples: Int get() = originSamples + sleepDays + hrvSamples + spo2Samples
}
