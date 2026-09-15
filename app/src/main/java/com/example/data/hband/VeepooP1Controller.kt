package com.example.data.hband

import android.util.Log
import com.example.data.local.AdvancedMeasurementEntity
import com.inuker.bluetooth.library.connect.response.BleWriteResponse
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.IHealthRemindListener
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.data.IAlarm2DataListListener
import com.veepoo.protocol.listener.data.IAlarmDataListener
import com.veepoo.protocol.listener.data.IBloodComponentDetectListener
import com.veepoo.protocol.listener.data.IBloodGlucoseChangeListener
import com.veepoo.protocol.listener.data.IBodyComponentDetectListener
import com.veepoo.protocol.listener.data.IBreathDataListener
import com.veepoo.protocol.listener.data.IECGDetectListener
import com.veepoo.protocol.listener.data.IECGReadDataListener
import com.veepoo.protocol.listener.data.IECGReadIdListener
import com.veepoo.protocol.listener.data.IEmotionDetectListener
import com.veepoo.protocol.listener.data.IFatigueDataListener
import com.veepoo.protocol.listener.data.IFindDeviceDatalistener
import com.veepoo.protocol.listener.data.IFindDevicelistener
import com.veepoo.protocol.listener.data.IHeartWaringDataListener
import com.veepoo.protocol.listener.data.ILongSeatDataListener
import com.veepoo.protocol.listener.data.INightTurnWristeDataListener
import com.veepoo.protocol.model.datas.AlarmData
import com.veepoo.protocol.model.datas.AlarmData2
import com.veepoo.protocol.model.datas.BloodComponent
import com.veepoo.protocol.model.datas.BodyComponent
import com.veepoo.protocol.model.datas.BreathData
import com.veepoo.protocol.model.datas.EcgDetectInfo
import com.veepoo.protocol.model.datas.EcgDetectResult
import com.veepoo.protocol.model.datas.EcgDetectState
import com.veepoo.protocol.model.datas.EcgDiagnosis
import com.veepoo.protocol.model.datas.FatigueData
import com.veepoo.protocol.model.datas.FindDeviceData
import com.veepoo.protocol.model.datas.HealthRemind
import com.veepoo.protocol.model.datas.HeartWaringData
import com.veepoo.protocol.model.datas.LongSeatData
import com.veepoo.protocol.model.datas.MealInfo
import com.veepoo.protocol.model.datas.NightTurnWristeData
import com.veepoo.protocol.model.datas.TimeData
import com.veepoo.protocol.model.enums.EBloodComponentDetectState
import com.veepoo.protocol.model.enums.EBloodGlucoseRiskLevel
import com.veepoo.protocol.model.enums.EBloodGlucoseStatus
import com.veepoo.protocol.model.enums.EEcgDataType
import com.veepoo.protocol.model.enums.EFindDeviceStatus
import com.veepoo.protocol.model.enums.EHeartWaringStatus
import com.veepoo.protocol.model.enums.ELongSeatStatus
import com.veepoo.protocol.model.enums.EmotionDetectState
import com.veepoo.protocol.model.enums.DetectState
import com.veepoo.protocol.model.enums.HealthRemindType
import com.veepoo.protocol.model.settings.Alarm2Setting
import com.veepoo.protocol.model.settings.AlarmSetting
import com.veepoo.protocol.model.settings.HeartWaringSetting
import com.veepoo.protocol.model.settings.LongSeatSetting
import com.veepoo.protocol.model.settings.NightTurnWristSetting
import com.veepoo.protocol.multi_lead.data.MultiEcgDetectInfo
import com.veepoo.protocol.multi_lead.data.MultiEcgPreInfo
import com.veepoo.protocol.multi_lead.enums.ELeadFlag
import com.veepoo.protocol.multi_lead.listener.IMultiEcgDetectListener

/**
 * Official VPOperateManager wrappers for P1 detect + settings.
 * Callers must gate on [DeviceCapabilities]; this class never fabricates values.
 */
class VeepooP1Controller(
    private val vpManager: VPOperateManager,
) {
    private val TAG = "VeepooP1Controller"

    @Volatile
    var cancelled: Boolean = false

    private var ecgListener: IECGDetectListener? = null
    private var multiEcgListener: IMultiEcgDetectListener? = null
    private var glucoseListener: IBloodGlucoseChangeListener? = null
    private var bloodListener: IBloodComponentDetectListener? = null
    private var bodyListener: IBodyComponentDetectListener? = null
    private var emotionListener: IEmotionDetectListener? = null
    private var fatigueListener: IFatigueDataListener? = null
    private var breathListener: IBreathDataListener? = null
    private var findByPhoneListener: IFindDevicelistener? = null

    private val inukerWrite = BleWriteResponse { code -> Log.d(TAG, "inuker write code=$code") }
    private val protocolWrite = IBleWriteResponse { code -> Log.d(TAG, "protocol write code=$code") }

    fun stopAllDetect() {
        cancelled = true
        runCatching { vpManager.stopDetectECG(inukerWrite, true, ecgListener) }
        runCatching { vpManager.stopDetectMultiECG(inukerWrite) }
        glucoseListener?.let { runCatching { vpManager.stopBloodGlucoseDetect(protocolWrite, it) } }
        runCatching { vpManager.stopDetectBloodComponent(inukerWrite) }
        runCatching { vpManager.stopDetectBodyComponent(inukerWrite) }
        runCatching { vpManager.stopDetectEmotion(inukerWrite) }
        fatigueListener?.let { runCatching { vpManager.stopDetectFatigue(protocolWrite, it) } }
        breathListener?.let { runCatching { vpManager.stopDetectBreath(protocolWrite, it) } }
        findByPhoneListener?.let { runCatching { vpManager.stopFindDeviceByPhone(protocolWrite, it) } }
        clearListeners()
        cancelled = false
    }

    fun startEcg(
        deviceId: String,
        multiLead: Boolean,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val waveform = mutableListOf<Int>()
        if (multiLead) {
            val listener = object : IMultiEcgDetectListener {
                override fun onEcgDetectPreStart(info: MultiEcgPreInfo) {
                    onState(DetectSessionUiState(supported = true, running = true, lastSummary = "ECG multi-lead iniciando"))
                }

                override fun onEcgDeviceNotSupport() {
                    onState(DetectSessionUiState(supported = false, lastError = "ECG multi-lead não suportado"))
                }

                override fun onEcgDeviceNotInDetect() {
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = false,
                            lastError = "Pulseira não está em modo ECG",
                        ),
                    )
                }

                override fun onEcgDetectStateChange(info: MultiEcgDetectInfo) {
                    val hr = info.avgHeart
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = true,
                            lastValue = hr.takeIf { it in 30..240 }?.toFloat(),
                            sampleCount = waveform.size,
                            waveform = waveform.toList(),
                            lastSummary = if (hr in 30..240) "ECG multi-lead • $hr bpm" else "ECG multi-lead em andamento",
                        ),
                    )
                }

                override fun onEcgADCChange(lead: ELeadFlag?, adc: IntArray?, filter: Int, frequency: Int) {
                    val merged = VeepooAdvancedMapper.appendWaveform(waveform, adc)
                    waveform.clear()
                    waveform.addAll(merged)
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = true,
                            sampleCount = waveform.size,
                            waveform = waveform.toList(),
                            lastSummary = "ECG multi-lead • ${waveform.size} amostras ADC",
                        ),
                    )
                }
            }
            multiEcgListener = listener
            vpManager.startDetectMultiECG(inukerWrite, true, listener)
        } else {
            val listener = object : IECGDetectListener {
                override fun onEcgDetectInfoChange(info: EcgDetectInfo?) {
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = true,
                            lastSummary = "ECG ${info?.frequency ?: 0} Hz",
                        ),
                    )
                }

                override fun onEcgDetectStateChange(state: EcgDetectState?) {
                    val hr = state?.hr1?.takeIf { it in 30..240 } ?: 0
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = true,
                            progress = state?.progress ?: 0,
                            lastValue = hr.takeIf { it > 0 }?.toFloat(),
                            sampleCount = waveform.size,
                            waveform = waveform.toList(),
                            lastSummary = if (hr > 0) "ECG • $hr bpm" else "ECG em andamento",
                        ),
                    )
                }

                override fun onEcgDetectResultChange(result: EcgDetectResult?) {
                    VeepooAdvancedMapper.fromEcgResult(result, deviceId, waveform.toList())?.let(onSample)
                    val heart = result?.aveHeart?.takeIf { it in 30..240 }
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = false,
                            progress = 100,
                            lastValue = heart?.toFloat(),
                            sampleCount = waveform.size,
                            waveform = waveform.toList(),
                            lastSummary = if (result?.isSuccess == true && heart != null) {
                                "ECG ${heart} bpm • ${waveform.size} amostras"
                            } else if (waveform.isNotEmpty()) {
                                "ECG • ${waveform.size} amostras ADC reais"
                            } else {
                                "ECG sem amostras reais"
                            },
                            lastError = if (result != null && !result.isSuccess && waveform.isEmpty() && heart == null) {
                                "Firmware não devolveu ECG"
                            } else {
                                null
                            },
                        ),
                    )
                }

                override fun onEcgDetectDiagnosisChange(diagnosis: EcgDiagnosis?) {
                    Log.i(TAG, "ECG diagnosis: hr=${diagnosis?.heartRate} success=${diagnosis?.isSuccess}")
                }

                override fun onEcgADCChange(origin: IntArray?, filtered: IntArray?) {
                    val incoming = if (filtered != null && filtered.isNotEmpty()) filtered else origin
                    val merged = VeepooAdvancedMapper.appendWaveform(waveform, incoming)
                    waveform.clear()
                    waveform.addAll(merged)
                    onState(
                        DetectSessionUiState(
                            supported = true,
                            running = true,
                            sampleCount = waveform.size,
                            waveform = waveform.toList(),
                            lastSummary = "ECG • ${waveform.size} amostras ADC",
                        ),
                    )
                }
            }
            ecgListener = listener
            vpManager.startDetectECG(inukerWrite, true, listener)
        }
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando ECG…"))
    }

    fun stopEcg() {
        runCatching { vpManager.stopDetectECG(inukerWrite, true, ecgListener) }
        runCatching { vpManager.stopDetectMultiECG(inukerWrite) }
        ecgListener = null
        multiEcgListener = null
    }

    fun readStoredEcg(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val time = TimeData().apply { setCurrentTime() }
        vpManager.readECGId(
            inukerWrite,
            time,
            EEcgDataType.MANUALLY,
            IECGReadIdListener { ids ->
                if (ids == null || ids.isEmpty()) {
                    vpManager.readECGData(
                        inukerWrite,
                        time,
                        EEcgDataType.ALL,
                        ecgReadListener(deviceId, onState, onSample),
                    )
                } else {
                    vpManager.readECGManuallyData(
                        inukerWrite,
                        ids,
                        ecgReadListener(deviceId, onState, onSample),
                    )
                }
            },
        )
    }

    private fun ecgReadListener(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) = object : IECGReadDataListener {
        override fun readDataFinish(results: MutableList<EcgDetectResult>?) {
            val mapped = results.orEmpty().mapNotNull { VeepooAdvancedMapper.fromEcgResult(it, deviceId) }
            mapped.forEach(onSample)
            onState(
                DetectSessionUiState(
                    supported = true,
                    running = false,
                    lastSummary = if (mapped.isEmpty()) {
                        "Nenhum ECG gravado no firmware"
                    } else {
                        "Lidos ${mapped.size} ECG(s) gravados"
                    },
                    lastValue = mapped.lastOrNull()?.numericValue?.takeIf { it > 0f },
                    sampleCount = mapped.lastOrNull()?.sampleCount ?: 0,
                ),
            )
        }

        override fun readDiagnosisDataFinish(diagnosis: MutableList<EcgDiagnosis>?) {
            Log.i(TAG, "ECG diagnosis history size=${diagnosis?.size ?: 0}")
        }
    }

    fun startGlucose(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val listener = object : IBloodGlucoseChangeListener {
            override fun onDetectError(code: Int, status: EBloodGlucoseStatus?) {
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = false,
                        lastError = status?.name ?: "erro glicose $code",
                    ),
                )
            }

            override fun onBloodGlucoseDetect(progress: Int, value: Float, risk: EBloodGlucoseRiskLevel?) {
                VeepooAdvancedMapper.fromGlucose(value, progress, risk?.name, deviceId)?.let(onSample)
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = progress in 1..99,
                        progress = progress,
                        lastValue = value.takeIf { VeepooAdvancedMapper.isPlausibleGlucose(it) },
                        lastSummary = if (VeepooAdvancedMapper.isPlausibleGlucose(value)) {
                            "Glicose ${String.format(java.util.Locale.US, "%.1f", value)}"
                        } else {
                            "Medindo glicose… $progress%"
                        },
                    ),
                )
            }

            override fun onBloodGlucoseStopDetect() {
                onState(DetectSessionUiState(supported = true, running = false, lastSummary = "Glicose interrompida"))
            }

            override fun onBloodGlucoseAdjustingSettingSuccess(open: Boolean, value: Float) {
                Log.i(TAG, "glucose adjusting set open=$open value=$value")
            }

            override fun onBloodGlucoseAdjustingSettingFailed() {}
            override fun onBloodGlucoseAdjustingReadSuccess(open: Boolean, value: Float) {
                Log.i(TAG, "glucose adjusting read open=$open value=$value")
            }

            override fun onBloodGlucoseAdjustingReadFailed() {}
            override fun onBGMultipleAdjustingReadSuccess(p0: Boolean, p1: MealInfo?, p2: MealInfo?, p3: MealInfo?) {}
            override fun onBGMultipleAdjustingReadFailed() {}
            override fun onBGMultipleAdjustingSettingSuccess() {}
            override fun onBGMultipleAdjustingSettingFailed() {}
        }
        glucoseListener = listener
        vpManager.startBloodGlucoseDetect(protocolWrite, listener)
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando glicose…"))
    }

    fun stopGlucose() {
        glucoseListener?.let { runCatching { vpManager.stopBloodGlucoseDetect(protocolWrite, it) } }
        glucoseListener = null
    }

    fun startBloodComponent(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val listener = object : IBloodComponentDetectListener {
            override fun onDetectFailed(state: EBloodComponentDetectState) {
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = false,
                        lastError = state.name,
                    ),
                )
            }

            override fun onDetecting(progress: Int, component: BloodComponent) {
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = true,
                        progress = progress,
                        lastSummary = "Componentes sanguíneos $progress%",
                    ),
                )
            }

            override fun onDetectStop() {
                onState(DetectSessionUiState(supported = true, running = false, lastSummary = "Componentes sanguíneos interrompidos"))
            }

            override fun onDetectComplete(component: BloodComponent) {
                val mapped = VeepooAdvancedMapper.fromBloodComponent(component, deviceId)
                if (mapped != null) onSample(mapped)
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = false,
                        progress = 100,
                        lastSummary = mapped?.summary ?: "Firmware não devolveu componentes sanguíneos",
                        lastValue = mapped?.numericValue,
                        lastError = if (mapped == null) "sem valores reais" else null,
                    ),
                )
            }
        }
        bloodListener = listener
        vpManager.startDetectBloodComponent(inukerWrite, true, listener)
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando componentes sanguíneos…"))
    }

    fun stopBloodComponent() {
        runCatching { vpManager.stopDetectBloodComponent(inukerWrite) }
        bloodListener = null
    }

    fun startBodyComponent(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val listener = object : IBodyComponentDetectListener {
            override fun onDetecting(progress: Int, extra: Int) {
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = true,
                        progress = progress,
                        lastSummary = "Composição corporal $progress%",
                    ),
                )
            }

            override fun onDetectSuccess(component: BodyComponent) {
                val mapped = VeepooAdvancedMapper.fromBodyComponent(component, deviceId)
                if (mapped != null) onSample(mapped)
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = false,
                        progress = 100,
                        lastSummary = mapped?.summary ?: "Firmware não devolveu composição corporal",
                        lastValue = mapped?.numericValue,
                        lastError = if (mapped == null) "sem valores reais" else null,
                    ),
                )
            }

            override fun onDetectFailed(state: DetectState) {
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = false,
                        lastError = state.name,
                    ),
                )
            }

            override fun onDetectStop() {
                onState(DetectSessionUiState(supported = true, running = false, lastSummary = "Composição corporal interrompida"))
            }
        }
        bodyListener = listener
        vpManager.startDetectBodyComponent(inukerWrite, listener)
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando composição corporal…"))
    }

    fun stopBodyComponent() {
        runCatching { vpManager.stopDetectBodyComponent(inukerWrite) }
        bodyListener = null
    }

    fun startEmotion(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val listener = object : IEmotionDetectListener {
            override fun onEmotionDetect(progress: Int, value: Int) {
                VeepooAdvancedMapper.fromEmotion(progress, value, deviceId)?.let(onSample)
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = progress in 1..99,
                        progress = progress,
                        lastValue = value.takeIf { it > 0 }?.toFloat(),
                        lastSummary = if (value > 0) "Emoção $value" else "Medindo emoção… $progress%",
                    ),
                )
            }

            override fun onDetectFailed(state: EmotionDetectState) {
                onState(
                    DetectSessionUiState(
                        supported = true,
                        running = false,
                        lastError = state.name,
                    ),
                )
            }

            override fun onDetectStop() {
                onState(DetectSessionUiState(supported = true, running = false, lastSummary = "Emoção interrompida"))
            }
        }
        emotionListener = listener
        vpManager.startDetectEmotion(inukerWrite, listener)
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando emoção…"))
    }

    fun stopEmotion() {
        runCatching { vpManager.stopDetectEmotion(inukerWrite) }
        emotionListener = null
    }

    fun startFatigue(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val listener = IFatigueDataListener { data: FatigueData? ->
            VeepooAdvancedMapper.fromFatigue(data, deviceId)?.let(onSample)
            val running = (data?.progress ?: 0) in 1..99
            onState(
                DetectSessionUiState(
                    supported = true,
                    running = running,
                    progress = data?.progress ?: 0,
                    lastValue = data?.value?.takeIf { it > 0 }?.toFloat(),
                    lastSummary = if ((data?.value ?: 0) > 0) "Fadiga ${data?.value}" else "Medindo fadiga…",
                    lastError = if (data?.deviceState?.name == "UNPASS_WEAR") "fora do pulso" else null,
                ),
            )
        }
        fatigueListener = listener
        vpManager.startDetectFatigue(protocolWrite, listener)
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando fadiga…"))
    }

    fun stopFatigue() {
        fatigueListener?.let { runCatching { vpManager.stopDetectFatigue(protocolWrite, it) } }
        fatigueListener = null
    }

    fun startBreath(
        deviceId: String,
        onState: (DetectSessionUiState) -> Unit,
        onSample: (AdvancedMeasurementEntity) -> Unit,
    ) {
        val listener = IBreathDataListener { data: BreathData? ->
            VeepooAdvancedMapper.fromBreath(data, deviceId)?.let(onSample)
            val running = (data?.progressValue ?: 0) in 1..99
            onState(
                DetectSessionUiState(
                    supported = true,
                    running = running,
                    progress = data?.progressValue ?: 0,
                    lastValue = data?.value?.takeIf { it in 4..60 }?.toFloat(),
                    lastSummary = if ((data?.value ?: 0) in 4..60) {
                        "Respiração ${data?.value} rpm"
                    } else {
                        "Medindo respiração…"
                    },
                ),
            )
        }
        breathListener = listener
        vpManager.startDetectBreath(protocolWrite, listener)
        onState(DetectSessionUiState(supported = true, running = true, lastSummary = "Iniciando respiração…"))
    }

    fun stopBreath() {
        breathListener?.let { runCatching { vpManager.stopDetectBreath(protocolWrite, it) } }
        breathListener = null
    }

    fun readAlarms(caps: DeviceCapabilities, onState: (AlarmUiState) -> Unit) {
        if (caps.isSupportAlarm2) {
            vpManager.readAlarm2(
                protocolWrite,
                IAlarm2DataListListener { data: AlarmData2? ->
                    onState(alarm2ToUi(data, supported = true))
                },
            )
        } else {
            vpManager.readAlarm(
                protocolWrite,
                IAlarmDataListener { data: AlarmData? ->
                    val first = data?.alarmSettingList?.firstOrNull()
                    onState(
                        AlarmUiState(
                            supported = true,
                            alarm2 = false,
                            enabled = first?.isOpen == true,
                            hour = first?.hour ?: 8,
                            minute = first?.minute ?: 0,
                            summary = if (first == null) "Nenhum alarme" else formatClock(first.hour, first.minute),
                        ),
                    )
                },
            )
        }
    }

    fun setAlarmEnabled(caps: DeviceCapabilities, enabled: Boolean, hour: Int, minute: Int, onState: (AlarmUiState) -> Unit) {
        if (caps.isSupportAlarm2) {
            val setting = Alarm2Setting(0, hour, minute, EVERYDAY_REPEAT, 0, UNREPEAT_DATE, enabled)
            val listener = IAlarm2DataListListener { data: AlarmData2? ->
                onState(alarm2ToUi(data, supported = true))
            }
            if (enabled) {
                vpManager.addAlarm2(protocolWrite, listener, setting)
            } else {
                vpManager.modifyAlarm2(protocolWrite, listener, setting)
            }
        } else {
            val list = listOf(AlarmSetting(hour, minute, enabled))
            vpManager.settingAlarm(
                protocolWrite,
                IAlarmDataListener { data: AlarmData? ->
                    val first = data?.alarmSettingList?.firstOrNull()
                    onState(
                        AlarmUiState(
                            supported = true,
                            enabled = first?.isOpen ?: enabled,
                            hour = first?.hour ?: hour,
                            minute = first?.minute ?: minute,
                            summary = formatClock(first?.hour ?: hour, first?.minute ?: minute),
                        ),
                    )
                },
                list,
            )
        }
    }

    fun readHeartWarning(onState: (HeartWarningUiState) -> Unit) {
        vpManager.readHeartWarning(
            protocolWrite,
            IHeartWaringDataListener { data: HeartWaringData? ->
                onState(heartWarningToUi(data, supported = true))
            },
        )
    }

    fun setHeartWarning(high: Int, low: Int, enabled: Boolean, onState: (HeartWarningUiState) -> Unit) {
        vpManager.settingHeartWarning(
            protocolWrite,
            IHeartWaringDataListener { data: HeartWaringData? ->
                onState(heartWarningToUi(data, supported = true))
            },
            HeartWaringSetting(high, low, enabled),
        )
    }

    fun readLongSeat(onState: (LongSeatUiState) -> Unit) {
        vpManager.readLongSeat(
            protocolWrite,
            ILongSeatDataListener { data: LongSeatData? ->
                onState(longSeatToUi(data, supported = true))
            },
        )
    }

    fun setLongSeat(current: LongSeatUiState, enabled: Boolean, onState: (LongSeatUiState) -> Unit) {
        val setting = LongSeatSetting(
            current.startHour,
            current.startMinute,
            current.endHour,
            current.endMinute,
            current.thresholdMinutes,
            enabled,
        )
        vpManager.settingLongSeat(
            protocolWrite,
            setting,
            ILongSeatDataListener { data: LongSeatData? ->
                onState(longSeatToUi(data, supported = true))
            },
        )
    }

    fun readNightTurn(onState: (NightTurnUiState) -> Unit) {
        vpManager.readNightTurnWriste(
            protocolWrite,
            INightTurnWristeDataListener { data: NightTurnWristeData? ->
                onState(nightTurnToUi(data, supported = true))
            },
        )
    }

    fun setNightTurn(enabled: Boolean, onState: (NightTurnUiState) -> Unit) {
        vpManager.settingNightTurnWriste(
            protocolWrite,
            INightTurnWristeDataListener { data: NightTurnWristeData? ->
                onState(nightTurnToUi(data, supported = true))
            },
            NightTurnWristSetting(enabled, TimeData().apply { hour = 22; minute = 0 }, TimeData().apply { hour = 7; minute = 0 }, 0),
        )
    }

    fun readFindDevice(onState: (FindDeviceUiState) -> Unit) {
        vpManager.readFindDevice(
            protocolWrite,
            IFindDeviceDatalistener { data: FindDeviceData? ->
                onState(findDeviceToUi(data, previous = FindDeviceUiState(supported = true)))
            },
        )
    }

    fun setFindDevice(enabled: Boolean, previous: FindDeviceUiState, onState: (FindDeviceUiState) -> Unit) {
        vpManager.settingFindDevice(
            protocolWrite,
            IFindDeviceDatalistener { data: FindDeviceData? ->
                onState(findDeviceToUi(data, previous.copy(enabled = enabled)))
            },
            enabled,
        )
    }

    fun startFindByPhone(onState: (FindDeviceUiState) -> Unit) {
        val listener = object : IFindDevicelistener {
            override fun unSupportFindDeviceByPhone() {
                onState(FindDeviceUiState(findByPhoneSupported = false, summary = "Localizar pelo telefone não suportado"))
            }

            override fun findedDevice() {
                onState(FindDeviceUiState(supported = true, findByPhoneSupported = true, finding = false, summary = "Pulseira encontrada"))
            }

            override fun unFindDevice() {
                onState(FindDeviceUiState(supported = true, findByPhoneSupported = true, finding = false, summary = "Pulseira não encontrada"))
            }

            override fun findingDevice() {
                onState(FindDeviceUiState(supported = true, findByPhoneSupported = true, finding = true, summary = "Procurando pulseira…"))
            }
        }
        findByPhoneListener = listener
        vpManager.startFindDeviceByPhone(protocolWrite, listener)
    }

    fun stopFindByPhone() {
        findByPhoneListener?.let { runCatching { vpManager.stopFindDeviceByPhone(protocolWrite, it) } }
        findByPhoneListener = null
    }

    fun readHealthRemind(onState: (HealthRemindUiState) -> Unit) {
        vpManager.readHealthRemind(
            HealthRemindType.SEDENTARY,
            healthRemindListener(onState),
            protocolWrite,
        )
    }

    fun setHealthRemind(enabled: Boolean, onState: (HealthRemindUiState) -> Unit) {
        val remind = HealthRemind(
            HealthRemindType.SEDENTARY,
            TimeData().apply { hour = 9; minute = 0 },
            TimeData().apply { hour = 18; minute = 0 },
            60,
            enabled,
        )
        vpManager.settingHealthRemind(remind, healthRemindListener(onState), protocolWrite)
    }

    private fun healthRemindListener(onState: (HealthRemindUiState) -> Unit) = object : IHealthRemindListener {
        override fun functionNotSupport() {
            onState(HealthRemindUiState(supported = false, summary = "Lembrete de saúde não suportado"))
        }

        override fun onHealthRemindRead(remind: HealthRemind) {
            onState(healthRemindToUi(remind))
        }

        override fun onHealthRemindReadingComplete() {}
        override fun onHealthRemindReadFailed() {
            onState(HealthRemindUiState(supported = true, summary = "Falha ao ler lembrete"))
        }

        override fun onHealthRemindReport(remind: HealthRemind) {
            onState(healthRemindToUi(remind))
        }

        override fun onHealthRemindReportFailed() {}
        override fun onHealthRemindSettingSuccess(remind: HealthRemind) {
            onState(healthRemindToUi(remind))
        }

        override fun onHealthRemindSettingFailed(type: HealthRemindType) {
            onState(HealthRemindUiState(supported = true, summary = "Falha ao gravar lembrete"))
        }
    }

    private fun alarm2ToUi(data: AlarmData2?, supported: Boolean): AlarmUiState {
        val first = data?.alarm2SettingList?.firstOrNull()
        val count = data?.alarm2SettingList?.size ?: 0
        return AlarmUiState(
            supported = supported,
            alarm2 = true,
            enabled = first?.isOpen == true,
            hour = first?.alarmHour ?: 8,
            minute = first?.alarmMinute ?: 0,
            summary = when {
                first == null -> "Nenhum alarme no firmware"
                else -> "${formatClock(first.alarmHour, first.alarmMinute)} • $count alarme(s)"
            },
        )
    }

    private fun heartWarningToUi(data: HeartWaringData?, supported: Boolean): HeartWarningUiState {
        val high = data?.heartHigh?.takeIf { it in 80..220 } ?: 120
        val low = data?.heartLow?.takeIf { it in 30..80 } ?: 50
        val open = data?.isOpen == true
        return HeartWarningUiState(
            supported = supported,
            enabled = open,
            high = high,
            low = low,
            summary = when (data?.status) {
                EHeartWaringStatus.UNSUPPORT -> "Não suportado"
                else -> if (open) "Alerta $low–$high bpm" else "Alerta cardíaco desligado"
            },
        )
    }

    private fun longSeatToUi(data: LongSeatData?, supported: Boolean): LongSeatUiState {
        return LongSeatUiState(
            supported = supported && data?.status != ELongSeatStatus.UNSUPPORT,
            enabled = data?.isOpen == true,
            startHour = data?.startHour ?: 9,
            startMinute = data?.startMinute ?: 0,
            endHour = data?.endHour ?: 18,
            endMinute = data?.endMinute ?: 0,
            thresholdMinutes = data?.threshold ?: 60,
            summary = if (data?.isOpen == true) {
                "Sedentarismo ${formatClock(data.startHour, data.startMinute)}–${formatClock(data.endHour, data.endMinute)}"
            } else {
                "Lembrete de sedentarismo desligado"
            },
        )
    }

    private fun nightTurnToUi(data: NightTurnWristeData?, supported: Boolean): NightTurnUiState {
        val open = data?.isNightTureWirsteStatusOpen == true
        return NightTurnUiState(
            supported = supported,
            enabled = open,
            summary = if (open) "Virar pulso à noite ligado" else "Virar pulso à noite desligado",
        )
    }

    private fun findDeviceToUi(data: FindDeviceData?, previous: FindDeviceUiState): FindDeviceUiState {
        val enabled = when (data?.status) {
            EFindDeviceStatus.OPEN_SUCCESS -> true
            EFindDeviceStatus.CLOSE_SUCCESS -> false
            else -> previous.enabled
        }
        return previous.copy(
            supported = true,
            enabled = enabled,
            summary = data?.status?.name ?: previous.summary,
        )
    }

    private fun healthRemindToUi(remind: HealthRemind?): HealthRemindUiState {
        val open = remind?.status == true
        return HealthRemindUiState(
            supported = true,
            enabled = open,
            summary = if (open) {
                "Lembrete ${remind?.remindType?.name ?: "SEDENTARY"} a cada ${remind?.interval ?: 60} min"
            } else {
                "Lembrete de saúde desligado"
            },
        )
    }

    private fun formatClock(hour: Int, minute: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    private fun clearListeners() {
        ecgListener = null
        multiEcgListener = null
        glucoseListener = null
        bloodListener = null
        bodyListener = null
        emotionListener = null
        fatigueListener = null
        breathListener = null
        findByPhoneListener = null
    }

    companion object {
        private const val EVERYDAY_REPEAT = "1111111"
        private const val UNREPEAT_DATE = "0000-00-00"
    }
}
