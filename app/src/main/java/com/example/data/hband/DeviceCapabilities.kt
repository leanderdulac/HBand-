package com.example.data.hband

/**
 * Capability snapshot after Veepoo handshake.
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
    val isSupportEcg: Boolean = false,
    val isSupportMultiLeadEcg: Boolean = false,
    val isSupportBloodGlucose: Boolean = false,
    val isSupportBloodGlucoseAdjusting: Boolean = false,
    val isSupportBloodComponent: Boolean = false,
    val isSupportBodyComponent: Boolean = false,
    val isSupportEmotion: Boolean = false,
    val isSupportFatigue: Boolean = false,
    val isSupportBreath: Boolean = false,
    val isSupportAlarm2: Boolean = false,
    val isSupportTextAlarm: Boolean = false,
    val isSupportHeartWarning: Boolean = false,
    val isSupportHealthRemind: Boolean = false,
    val isSupportLongSeat: Boolean = false,
    val isSupportNightTurnWrist: Boolean = false,
    val isSupportFindDevice: Boolean = false,
    val isSupportFindDeviceByPhone: Boolean = false,
    val probed: Boolean = false,
) {
    val canReadMultiDayOrigin: Boolean get() = probed && historyDays > 0
    val canReadSleepHistory: Boolean get() = probed
    val canReadHrvOrigin: Boolean get() = probed && isSupportHrv
    val canReadSpo2Origin: Boolean get() = probed && isSupportSpo2
    val hasAdvancedDetect: Boolean
        get() = isSupportEcg || isSupportBloodGlucose || isSupportBloodComponent ||
            isSupportBodyComponent || isSupportEmotion || isSupportFatigue || isSupportBreath
    val hasP1Settings: Boolean
        get() = isSupportAlarm2 || isSupportHeartWarning || isSupportHealthRemind ||
            isSupportLongSeat || isSupportNightTurnWrist || isSupportFindDevice ||
            isSupportFindDeviceByPhone
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

data class DetectSessionUiState(
    val supported: Boolean = false,
    val running: Boolean = false,
    val progress: Int = 0,
    val lastSummary: String = "",
    val lastError: String? = null,
    val sampleCount: Int = 0,
    val lastValue: Float? = null,
    val lastSecondary: Float? = null,
    val waveform: List<Int> = emptyList(),
)

data class AlarmUiState(
    val supported: Boolean = false,
    val alarm2: Boolean = false,
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val summary: String = "",
)

data class HeartWarningUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val high: Int = 120,
    val low: Int = 50,
    val summary: String = "",
)

data class LongSeatUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val thresholdMinutes: Int = 60,
    val summary: String = "",
)

data class NightTurnUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val summary: String = "",
)

data class FindDeviceUiState(
    val supported: Boolean = false,
    val findByPhoneSupported: Boolean = false,
    val enabled: Boolean = false,
    val finding: Boolean = false,
    val summary: String = "",
)

data class HealthRemindUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val summary: String = "",
)
