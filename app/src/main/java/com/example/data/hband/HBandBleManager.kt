package com.example.data.hband

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.ingest.IngestPayloadMapper
import com.example.data.ingest.LastValidBloodPressure
import com.example.data.model.BloodPressure
import com.example.data.model.HBandDevice
import com.example.data.model.HBandTelemetry
import com.example.data.model.SleepSummary
import com.inuker.bluetooth.library.Constants
import com.inuker.bluetooth.library.connect.response.BleWriteResponse
import com.inuker.bluetooth.library.search.SearchResult
import com.inuker.bluetooth.library.search.response.SearchResponse
import com.veepoo.protocol.VPOperateManager
import com.veepoo.protocol.listener.base.IABleConnectStatusListener
import com.veepoo.protocol.listener.base.IABluetoothStateListener
import com.veepoo.protocol.listener.base.IBleWriteResponse
import com.veepoo.protocol.listener.base.IConnectResponse
import com.veepoo.protocol.listener.base.INotifyResponse
import com.veepoo.protocol.listener.data.IBPDetectDataListener
import com.veepoo.protocol.listener.data.IBatteryDataListener
import com.veepoo.protocol.listener.data.ICustomSettingDataListener
import com.veepoo.protocol.listener.data.IDeviceFuctionDataListener
import com.veepoo.protocol.listener.data.IHeartDataListener
import com.veepoo.protocol.listener.data.IHrvDetectListener
import com.veepoo.protocol.listener.data.ILightDataCallBack
import com.veepoo.protocol.listener.data.IPersonInfoDataListener
import com.veepoo.protocol.listener.data.IPwdDataListener
import com.veepoo.protocol.listener.data.ISocialMsgDataListener
import com.veepoo.protocol.listener.data.ISpo2hDataListener
import com.veepoo.protocol.listener.data.ITemptureDetectDataListener
import com.veepoo.protocol.model.datas.DeviceFunctionPackage1
import com.veepoo.protocol.model.datas.DeviceFunctionPackage2
import com.veepoo.protocol.model.datas.DeviceFunctionPackage3
import com.veepoo.protocol.model.datas.DeviceFunctionPackage4
import com.veepoo.protocol.model.datas.DeviceFunctionPackage5
import com.veepoo.protocol.model.datas.AutoMeasureData
import com.veepoo.protocol.model.datas.FunctionDeviceSupportData
import com.veepoo.protocol.model.datas.FunctionSocailMsgData
import com.veepoo.protocol.model.datas.PersonInfoData
import com.veepoo.protocol.model.datas.PwdData
import com.veepoo.protocol.model.enums.EAutoMeasureType
import com.veepoo.protocol.model.enums.EBPDetectModel
import com.veepoo.protocol.model.enums.EOprateStauts
import com.veepoo.protocol.model.enums.ESex
import com.veepoo.protocol.model.enums.HrvDetectState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class HBandBleManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onHistorySamples: (List<HBandTelemetry>) -> Unit = {},
    private val onAdvancedSample: (com.example.data.local.AdvancedMeasurementEntity) -> Unit = {},
    private val onSportReading: (String, VeepooSportReading) -> Unit = { _, _ -> },
    initiallyDisconnectedByUser: Boolean = false,
) {
    private val TAG = "HBandBleManager"

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _scannedDevices = MutableStateFlow<List<HBandDevice>>(emptyList())
    val scannedDevices: StateFlow<List<HBandDevice>> = _scannedDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<HBandDevice?>(null)
    val connectedDevice: StateFlow<HBandDevice?> = _connectedDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _scanFailure = MutableStateFlow<BleScanFailure?>(null)
    val scanFailure: StateFlow<BleScanFailure?> = _scanFailure.asStateFlow()

    private val _latestTelemetry = MutableStateFlow<HBandTelemetry?>(null)
    val latestTelemetry: StateFlow<HBandTelemetry?> = _latestTelemetry.asStateFlow()

    // Real hardware connection state
    private val _isHardwareConnected = MutableStateFlow(false)
    val isHardwareConnected: StateFlow<Boolean> = _isHardwareConnected.asStateFlow()

    private val _sessionPhase = MutableStateFlow(BleSessionPhase.DISCONNECTED)
    val sessionPhase: StateFlow<BleSessionPhase> = _sessionPhase.asStateFlow()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)

    var isAutoReconnectEnabled: Boolean = prefs.getBoolean(PREF_AUTO_RECONNECT, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(PREF_AUTO_RECONNECT, value).apply()
            if (!value) cancelScheduledReconnect()
            publishSessionPhase()
        }
    var currentPatientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID

    private val _sessionMessage = MutableStateFlow<String?>(null)
    val sessionMessage: StateFlow<String?> = _sessionMessage.asStateFlow()

    private val advertisedServices = ConcurrentHashMap<String, Set<UUID>>()
    /** MACs whose advertisement carried the Veepoo manufacturer ID (f8f8 / f8f9). */
    private val veepooManufacturerMacs = ConcurrentHashMap.newKeySet<String>()
    private val _incompatibleWatch = MutableStateFlow<HBandDevice?>(null)
    val incompatibleWatch: StateFlow<HBandDevice?> = _incompatibleWatch.asStateFlow()
    private val veepooWriteBackoff = VeepooWriteBackoff()
    private val lastValidBp = LastValidBloodPressure()
    private val bpDetectRetry = BpDetectRetry()
    private var incompatibleCardMac: String? = null

    private val _capabilities = MutableStateFlow(DeviceCapabilities())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private val _autoMeasureState = MutableStateFlow(
        AutoMeasureUiState(
            heartRateEnabled = prefs.getBoolean(PREF_AUTO_MEASURE, true),
            spo2NightAutoEnabled = prefs.getBoolean(PREF_SPO2_AUTO, false),
        )
    )
    val autoMeasureState: StateFlow<AutoMeasureUiState> = _autoMeasureState.asStateFlow()

    private val _wearDetectState = MutableStateFlow(
        WearDetectUiState(enabled = prefs.getBoolean(PREF_WEAR_DETECT, true))
    )
    val wearDetectState: StateFlow<WearDetectUiState> = _wearDetectState.asStateFlow()

    private val _watchClockState = MutableStateFlow(
        WatchClockUiState(lastSyncEpochMs = prefs.getLong(PREF_WATCH_TIME_SYNC_AT, 0L))
    )
    val watchClockState: StateFlow<WatchClockUiState> = _watchClockState.asStateFlow()

    private val _watchAutoMeasureState = MutableStateFlow(WatchAutoMeasureState())
    val watchAutoMeasureState: StateFlow<WatchAutoMeasureState> = _watchAutoMeasureState.asStateFlow()

    private val _historySyncState = MutableStateFlow(HistorySyncUiState())
    val historySyncState: StateFlow<HistorySyncUiState> = _historySyncState.asStateFlow()

    private val _ecgState = MutableStateFlow(DetectSessionUiState())
    val ecgState: StateFlow<DetectSessionUiState> = _ecgState.asStateFlow()
    private val _glucoseState = MutableStateFlow(DetectSessionUiState())
    val glucoseState: StateFlow<DetectSessionUiState> = _glucoseState.asStateFlow()
    private val _bloodComponentState = MutableStateFlow(DetectSessionUiState())
    val bloodComponentState: StateFlow<DetectSessionUiState> = _bloodComponentState.asStateFlow()
    private val _bodyComponentState = MutableStateFlow(DetectSessionUiState())
    val bodyComponentState: StateFlow<DetectSessionUiState> = _bodyComponentState.asStateFlow()
    private val _emotionState = MutableStateFlow(DetectSessionUiState())
    val emotionState: StateFlow<DetectSessionUiState> = _emotionState.asStateFlow()
    private val _fatigueState = MutableStateFlow(DetectSessionUiState())
    val fatigueState: StateFlow<DetectSessionUiState> = _fatigueState.asStateFlow()
    private val _breathDetectState = MutableStateFlow(DetectSessionUiState())
    val breathDetectState: StateFlow<DetectSessionUiState> = _breathDetectState.asStateFlow()
    private val _alarmState = MutableStateFlow(AlarmUiState())
    val alarmState: StateFlow<AlarmUiState> = _alarmState.asStateFlow()
    private val _heartWarningState = MutableStateFlow(HeartWarningUiState())
    val heartWarningState: StateFlow<HeartWarningUiState> = _heartWarningState.asStateFlow()
    private val _longSeatState = MutableStateFlow(LongSeatUiState())
    val longSeatState: StateFlow<LongSeatUiState> = _longSeatState.asStateFlow()
    private val _nightTurnState = MutableStateFlow(NightTurnUiState())
    val nightTurnState: StateFlow<NightTurnUiState> = _nightTurnState.asStateFlow()
    private val _findDeviceState = MutableStateFlow(FindDeviceUiState())
    val findDeviceState: StateFlow<FindDeviceUiState> = _findDeviceState.asStateFlow()
    private val _healthRemindState = MutableStateFlow(HealthRemindUiState())
    val healthRemindState: StateFlow<HealthRemindUiState> = _healthRemindState.asStateFlow()

    private var userRequestedDisconnect = initiallyDisconnectedByUser
    private var veepooStatusListener: IABleConnectStatusListener? = null
    private var veepooStatusListenerMac: String? = null
    private var reconnectRunnable: Runnable? = null

    // Perfil biométrico real do usuário (sincronizado pelo ViewModel a partir do
    // UserProfileEntity). O VE30 usa altura/peso/idade/sexo para calibrar seus algoritmos
    // de estimativa (PA e HRV via análise de onda de pulso) — enviar valores diferentes do
    // perfil real do usuário faz o relógio calcular com uma calibração errada, produzindo
    // leituras que não batem com o que o próprio visor do relógio mostra.
    private var profileHeightCm: Int = 175
    private var profileWeightKg: Int = 72
    private var profileAge: Int = 32
    private var profileIsMale: Boolean = true
    private var profileStepGoal: Int = 8000

    fun updateBiometricProfile(heightCm: Int, weightKg: Int, age: Int, isMale: Boolean, stepGoal: Int) {
        profileHeightCm = heightCm
        profileWeightKg = weightKg
        profileAge = age
        profileIsMale = isMale
        profileStepGoal = stepGoal
    }

    private var currentGatt: BluetoothGatt? = null
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private val discoveredMap = ConcurrentHashMap<String, HBandDevice>()
    private val discoveredWriteCharacteristics = mutableListOf<BluetoothGattCharacteristic>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var keepAliveJob: Job? = null
    private var rssiPollJob: Job? = null
    private var reconnectAttempt = 0

    // Biometric cache - ONLY real values from hardware sensors
    private var currentHeartRate = 0
    private var currentSystolic = 0
    private var currentDiastolic = 0
    private var currentSpO2 = 0
    private var currentTemp = 0.0f
    private var currentSteps = 0
    private var currentCalories = 0.0f
    private var currentDistance = 0.0f
    private var currentHrvScore = 0
    private var isWristContactDetected = true
    private var lastHardwareReadTime: Long = 0
    // true assim que QUALQUER característica GATT real (HR, RSC, BP, SpO2, temperatura,
    // HBand/Veepoo) devolve um pacote reconhecido. Distingue dado real de fallback sintético
    // (simulador) para a UI não rotular demo como "leitura ao vivo".
    private var hasReceivedRealSensorData = false

    // SDK oficial Veepoo/HBand (VPOperateManager) — usado para VE30/HBand reais, que exigem
    // o handshake proprietário (senha + syncPersonInfo) antes de liberar os sensores. GATT
    // genérico não decodifica o protocolo Veepoo (confirmado nos logs de campo: pacotes
    // reais chegam em características não documentadas e nunca mudam, pois o relógio nunca
    // recebeu o comando de start real).
    private val vpManager: VPOperateManager = VPOperateManager.getInstance()
    private var isVeepooConnection = false
    val isVeepooSession: Boolean get() = isVeepooConnection
    // Sem esta guarda, um duplo-toque (ou recomposição da UI) em "Conectar" chamava
    // connectDeviceViaVeepooSdk duas ou mais vezes antes do handshake anterior terminar,
    // registrando múltiplos connectDevice/notify em paralelo no mesmo MAC — confirmado em
    // campo: isso derruba a conexão BLE do VE30 (colisão de comandos no firmware).
    private var isConnectingVeepoo = false
    private var connectAttemptStartedAt = 0L
    private var connectAttemptWatchdog: Runnable? = null
    private var veepooConnectGeneration = 0
    private var processCreatedAtElapsed = SystemClock.elapsedRealtime()
    private var startGraceReconnectPosted = false
    private var bondNoneCycles = 0
    private var suppressDisconnectReconnect = false
    // O firmware do VE30 dispara onFunctionSupportDataChange/OnPersoninfoDataChange mais de
    // uma vez por handshake (confirmado em campo: 4x em ~1s). Sem guarda, isso reenviava os
    // 5 comandos de start de sensor repetidamente e em rajada, o que pode confundir o
    // firmware e impedir que ele nunca comece a notificar dados reais.
    private var isSyncingPersonInfo = false
    private var hasStartedVeepooSensors = false
    // FC, SpO2 e PA compartilham o MESMO sensor óptico PPG no VE30 — iniciar mais de uma
    // dessas detecções ao mesmo tempo faz o firmware trocar de modo e cancelar
    // silenciosamente a anterior (confirmado em campo: com os 3 comandos disparados em
    // rajada de 600ms, só a PA — iniciada por último — chegava a completar; FC e SpO2 nunca
    // reportavam nenhum valor real). Por isso elas rodam em sequência, revezando em loop
    // contínuo, nunca em paralelo. `ppgStageGeneration` invalida callbacks/timeouts de um
    // estágio já abandonado (nova conexão ou avanço de estágio).
    private var ppgStageGeneration = 0
    private var ppgCycleRunning = false
    private var ppgCycleGotReading = false
    private var ppgEmptyCycles = 0
    private var ppgConsecutiveFailures = 0
    private var ppgNextCycleAtElapsedMs = 0L
    private var lastManualMeasureElapsedMs: Long? = null
    private var historyPollRunnable: Runnable? = null
    private var consecutiveReconnectFailures = 0
    private var ppgNextCycleRunnable: Runnable? = null
    private var lastBpSuccessElapsedMs: Long? = null
    private var bpUnsupported = false
    private var activeSpo2Listener: ISpo2hDataListener? = null
    private var activeHrvListener: IHrvDetectListener? = null
    private var activeTempListener: ITemptureDetectDataListener? = null
    private var liveLinkWatchdog: Runnable? = null
    private var lastFunctionSupport: FunctionDeviceSupportData? = null
    private var lastAutoMeasureSettings: List<AutoMeasureData> = emptyList()
    private var lastCustomSettingData: com.veepoo.protocol.model.settings.CustomSettingData? = null
    /** Primeiro plano até a activity ir para o fundo. Medição ao vivo só com isso true. */
    @Volatile private var appInForeground: Boolean = true
    /** Testes encurtam o keep-alive; produção usa [WatchBatteryPolicy.GATT_KEEPALIVE_MS]. */
    @Volatile private var keepAliveDelayMs: Long = WatchBatteryPolicy.GATT_KEEPALIVE_MS
    private var lastRealtimeStopElapsedMs = 0L
    private val watchSettingsMutex = Mutex()

    /** Fuso/hora do telefone mudou: reacerta o relógio se houver sessão Veepoo ativa. */
    private val clockChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: android.content.Intent) {
            if (!isVeepooConnection || !hasLiveHardwareSession()) return
            Log.i(TAG, "VE30 hora: ${intent.action} — reacertando")
            scope.launch(Dispatchers.Main) {
                watchSettingsMutex.withLock {
                    if (isVeepooConnection && hasLiveHardwareSession()) writeWatchTime(manual = false)
                }
            }
        }
    }

    init {
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext,
                clockChangeReceiver,
                android.content.IntentFilter().apply {
                    addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(android.content.Intent.ACTION_TIME_CHANGED)
                },
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "Receiver de fuso não registrado: ${it.message}") }
    }
    private var autoMeasureRevision = 0L
    private var spo2SettingRevision = 0L
    private var wearSettingRevision = 0L
    private var historySync: VeepooHistorySync? = null
    private val p1Controller = VeepooP1Controller(vpManager)
    private var postHandshakeJob: Job? = null
    private var batteryPollJob: Job? = null
    private var lastKnownWorn: Boolean? = null
    private var advancedDetectActive = false
    private var pwdConfirmAttempt = 0
    private var pwdConfirmGeneration = 0
    private var passwordHandshakeSucceeded = false
    private var passwordConfirmInFlight = false
    private var pendingPwdTimeout: Runnable? = null
    private var personInfoFallback: Runnable? = null

    // RR intervals cache for real HRV calculation (RMSSD)
    private val rrIntervals = LinkedList<Int>()
    private var cumulativeRscSteps = 0
    private var lastRscCadence = 0

    // GATT Sequential Execution Queue to prevent GATT_BUSY / 133 collisions
    private sealed class GattOp {
        data class WriteDesc(val gatt: BluetoothGatt, val desc: BluetoothGattDescriptor, val value: ByteArray) : GattOp()
        data class ReadChar(val gatt: BluetoothGatt, val char: BluetoothGattCharacteristic) : GattOp()
        data class WriteChar(val gatt: BluetoothGatt, val char: BluetoothGattCharacteristic, val value: ByteArray) : GattOp()
    }

    private val gattOpQueue: Queue<GattOp> = LinkedList()
    private var isGattOpInProgress = false
    private var gattTimeoutRunnable: Runnable? = null

    companion object {
        const val DEFAULT_VEEPOO_PWD = "0000"
        const val PREF_AUTO_RECONNECT = "auto_reconnect_ble"
        const val PREF_LAST_MAC = "last_ble_mac"
        const val PREF_LAST_NAME = "last_ble_name"
        const val PREF_AUTO_MEASURE = "auto_measure_hr"
        const val PREF_AUTO_MEASURE_USER_OFF = "auto_measure_hr_user_off"
        const val PREF_SPO2_AUTO = "spo2_night_auto"
        const val PREF_WEAR_DETECT = "wear_detect_enabled"
        const val PREF_WATCH_TIME_SYNC_AT = "watch_time_sync_at"
        const val WATCH_TIME_TIMEOUT_MS = 8_000L

        // Duração de cada estágio do revezamento de sensores PPG (FC/SpO2/PA) antes de
        // avançar para o próximo, mesmo sem uma leitura válida ainda. Observado em campo: o
        // VE30 reporta 0 continuamente enquanto ainda está calculando e só entrega o valor
        // real no(s) último(s) pacote(s) do ciclo. Confirmado com SpO2 (0% até ~14s, depois
        // 96-98% real) e PA (0/0 até progress=96, resolve perto de progress=100 em ~26-30s).
        // FC parece ser ainda mais lento/sensível a movimento — em dois testes de 12s e 30s
        // nunca chegou a resolver, por isso tem a janela mais generosa das quatro.
        private const val HEART_STAGE_TIMEOUT_MS = 60_000L
        private const val SPO2_STAGE_TIMEOUT_MS = 30_000L
        private const val BP_STAGE_TIMEOUT_MS = 40_000L
        private const val HRV_STAGE_TIMEOUT_MS = 15_000L
        private const val PPG_STAGE_GAP_MS = PpgCyclePolicy.STAGE_GAP_MS
        // VE30 can fire STATUS_DISCONNECTED while HeartData still streams.
        // Keep the live latch until packets go quiet for this window.
        private const val LIVE_LINK_STALE_MS = 8_000L
        private const val SDK_BATTERY_POLL_MS = 5 * 60 * 1000L

        // Bluetooth SIG Standard Services & Characteristics (Samsung Gear S3, WearOS, Garmin, etc.)
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BODY_SENSOR_LOCATION_UUID: UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_CONTROL_POINT_UUID: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

        val RUNNING_SPEED_AND_CADENCE_UUID: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
        val RSC_MEASUREMENT_UUID: UUID = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")

        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        val DEVICE_INFORMATION_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_UUID: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

        val HEALTH_THERMOMETER_SERVICE_UUID: UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        val TEMPERATURE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb")

        val BLOOD_PRESSURE_SERVICE_UUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
        val BLOOD_PRESSURE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")

        val PULSE_OXIMETER_SERVICE_UUID: UUID = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb")
        val PLX_CONTINUOUS_MEASUREMENT_UUID: UUID = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb")
        val PLX_SPOT_CHECK_UUID: UUID = UUID.fromString("00002a5e-0000-1000-8000-00805f9b34fb")

        // HBand & Veepoo Proprietary UUIDs
        val HBAND_PRIMARY_SERVICE_UUID: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
        val HBAND_WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("000036f5-0000-1000-8000-00805f9b34fb")
        val HBAND_NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("000036f6-0000-1000-8000-00805f9b34fb")

        val VEEPOO_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val VEEPOO_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val VEEPOO_TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun telemetryToJson(telemetry: HBandTelemetry, patientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID): String {
            return IngestPayloadMapper.telemetryToJson(telemetry, patientId)
        }
    }

    fun consumeSessionMessage() {
        _sessionMessage.value = null
    }

    fun hasPersistedSession(): Boolean {
        val mac = prefs.getString(PREF_LAST_MAC, null)?.trim().orEmpty()
        return mac.isNotEmpty() && BluetoothAdapter.checkBluetoothAddress(mac)
    }

    fun reconnectLastDevice() {
        if (!isAutoReconnectEnabled || userRequestedDisconnect || _isHardwareConnected.value) return
        val wait = VeepooReconnectPolicy.processStartDelayMs(
            processCreatedAtElapsed,
            SystemClock.elapsedRealtime(),
        )
        if (wait > 0L) {
            if (!startGraceReconnectPosted) {
                startGraceReconnectPosted = true
                Log.i(TAG, "Aguardando ${wait}ms após o início do processo antes de reconectar")
                mainHandler.postDelayed({
                    startGraceReconnectPosted = false
                    reconnectLastDevice()
                }, wait)
            }
            return
        }
        if (isConnectAttemptStale()) abortHungVeepooConnect(scheduleRetry = false)
        val mac = prefs.getString(PREF_LAST_MAC, null)?.trim().orEmpty()
        if (mac.isEmpty() || !BluetoothAdapter.checkBluetoothAddress(mac)) return
        val name = prefs.getString(PREF_LAST_NAME, null)?.takeIf { it.isNotBlank() } ?: "Relógio"
        connectDevice(
            HBandDevice(
                deviceId = mac,
                name = name,
                macAddress = mac,
                rssi = _connectedDevice.value?.rssi ?: 0,
                isConnected = false,
                firmwareVersion = "Reconexão"
            ).withUnknownBattery(connected = false)
        )
    }

    /** Doze-proof backup: clear a hung connecting latch so the next reconnect can run. */
    fun releaseStaleConnectAttempt() {
        if (isConnectAttemptStale()) abortHungVeepooConnect(scheduleRetry = false)
    }

    @SuppressLint("MissingPermission")
    fun checkBondedOrAutoConnect() {
        try {
            val adapter = bluetoothAdapter
            if (hasBlePermissions() && adapter != null && adapter.isEnabled) {
                val bonded = adapter.bondedDevices ?: emptySet()
                val bondedList = mutableListOf<HBandDevice>()

                val known = knownWatchAddresses()
                for (dev in bonded) {
                    val address = dev.address ?: continue
                    val rawName = bluetoothDeviceNameOrFallback(dev, "")
                    if (!VeepooDeviceSupport.shouldListForPatient(
                            name = rawName,
                            serviceUuids = advertisedServices[address].orEmpty(),
                            hasVeepooManufacturer = veepooManufacturerMacs.contains(address),
                            address = address,
                            knownAddresses = known,
                        )
                    ) continue
                    val savedName = if (matchesKnownWatch(address)) {
                        prefs.getString(PREF_LAST_NAME, null).orEmpty()
                    } else {
                        ""
                    }
                    val hDev = HBandDevice(
                        deviceId = address,
                        name = VeepooDeviceSupport.patientDisplayName(rawName.ifBlank { savedName }),
                        macAddress = address,
                        rssi = -60,
                        isConnected = false,
                        firmwareVersion = "Bluetooth Pareado"
                    )
                    bondedList.add(hDev)
                }

                if (bondedList.isNotEmpty()) {
                    _scannedDevices.value = bondedList
                }

                // Prefer the last successful MAC. Auto-connecting the first bonded
                // "Watch/Band/Fit" device used to grab the wrong peripheral.
                if (isAutoReconnectEnabled && !_isHardwareConnected.value && hasPersistedSession()) {
                    val mac = prefs.getString(PREF_LAST_MAC, null)?.trim().orEmpty()
                    val name = prefs.getString(PREF_LAST_NAME, null)?.takeIf { it.isNotBlank() } ?: "Relógio"
                    if (_connectedDevice.value == null && BluetoothAdapter.checkBluetoothAddress(mac)) {
                        _connectedDevice.value = HBandDevice(
                            deviceId = mac,
                            name = name,
                            macAddress = mac,
                            isConnected = false,
                            firmwareVersion = "Reconexão",
                        ).withUnknownBattery(connected = false)
                        publishSessionPhase()
                    }
                    Log.i(TAG, "Reconectando à última pulseira persistida...")
                    reconnectLastDevice()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bonded devices: ${e.message}")
        }
    }

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scanSession by lazy {
        BleScanSession(
            handler = mainHandler,
            canScan = ::hasBlePermissions,
            startScan = ::startHardwareScan,
            stopScan = ::stopHardwareScan,
            onResults = ::acceptScanResults,
            onScanningChanged = { scanning ->
                if (scanning) {
                    _scanFailure.value = null
                    discoveredMap.clear()
                    _scannedDevices.value = emptyList()
                }
                _isScanning.value = scanning
            },
            onFailure = { failure ->
                _scanFailure.value = failure
                Log.w(TAG, "BLE scan failure: $failure")
            },
        )
    }

    fun startScanning() = scanSession.start()

    fun stopScanning() = scanSession.stop()

    @SuppressLint("MissingPermission")
    private fun startHardwareScan(callback: ScanCallback) {
        val adapter = bluetoothAdapter ?: throw BleScanUnavailableException(BleScanFailure.SCANNER_UNAVAILABLE)
        if (!adapter.isEnabled) throw BleScanUnavailableException(BleScanFailure.BLUETOOTH_OFF)
        val scanner = adapter.bluetoothLeScanner ?: throw BleScanUnavailableException(BleScanFailure.SCANNER_UNAVAILABLE)
        activeScanner = scanner
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // Wide scan on purpose: a hardware ScanFilter would drop a VE30 that only
        // puts its name in the advertisement. The patient list is filtered in
        // acceptScanResults with the same Veepoo signals the SDK expects.
        scanner.startScan(null, settings, callback)
    }

    @SuppressLint("MissingPermission")
    private fun stopHardwareScan(callback: ScanCallback) {
        val scanner = activeScanner
        activeScanner = null
        scanner?.stopScan(callback)
    }

    private fun acceptScanResults(results: List<ScanResult>) {
        val known = knownWatchAddresses()
        for (result in results) {
            val device = result.device ?: continue
            val address = device.address ?: continue
            val rawName = bluetoothDeviceNameOrFallback(device, result.scanRecord?.deviceName ?: "")
            val scannedUuids = buildSet {
                result.scanRecord?.serviceUuids?.forEach { add(it.uuid) }
                result.scanRecord?.serviceData?.keys?.forEach { add(it.uuid) }
            }
            if (scannedUuids.isNotEmpty()) {
                advertisedServices[address] = advertisedServices[address].orEmpty() + scannedUuids
            }
            if (hasVeepooManufacturer(result)) veepooManufacturerMacs.add(address)
            val previous = discoveredMap[address]
            val effectiveName = rawName.trim().ifBlank { previous?.name.orEmpty() }
            if (!VeepooDeviceSupport.shouldListForPatient(
                    name = effectiveName,
                    serviceUuids = advertisedServices[address].orEmpty(),
                    hasVeepooManufacturer = veepooManufacturerMacs.contains(address),
                    address = address,
                    knownAddresses = known,
                )
            ) continue
            val isCurrent = _connectedDevice.value?.macAddress.equals(address, ignoreCase = true) &&
                _connectedDevice.value?.isConnected == true
            discoveredMap[address] = HBandDevice(
                deviceId = address,
                name = VeepooDeviceSupport.patientDisplayName(effectiveName),
                macAddress = address,
                rssi = result.rssi,
                isConnected = isCurrent,
                batteryLevel = liveScanBatteryLevel(isCurrent),
                batteryIsSimulated = false,
                firmwareVersion = "BLE Real",
            )
        }
        _scannedDevices.value = discoveredMap.values.toList()
    }

    private fun knownWatchAddresses(): Set<String> = buildSet {
        prefs.getString(PREF_LAST_MAC, null)?.let { add(it) }
        _connectedDevice.value?.macAddress?.let { add(it) }
    }

    private fun matchesKnownWatch(address: String): Boolean =
        VeepooDeviceSupport.matchesKnownAddress(address, knownWatchAddresses())

    private fun hasVeepooManufacturer(result: ScanResult): Boolean {
        val sparse = result.scanRecord?.manufacturerSpecificData ?: return false
        for (index in 0 until sparse.size()) {
            val payload = sparse.valueAt(index)
            if (VeepooDeviceSupport.isVeepooManufacturer(sparse.keyAt(index), payload?.size ?: 0)) {
                return true
            }
        }
        return false
    }

    private fun hasVeepooManufacturer(mac: String?): Boolean =
        mac != null && veepooManufacturerMacs.contains(mac)

    @SuppressLint("MissingPermission")
    fun connectDevice(device: HBandDevice): Boolean {
        // Reject before resetting the current session or persisting another device.
        val failure = try {
            when {
                !BluetoothAdapter.checkBluetoothAddress(device.macAddress) -> "O endereço do relógio é inválido. Confira o código informado."
                !hasBlePermissions() -> "A conexão não começou. Confira as permissões de dispositivos próximos do aplicativo."
                bluetoothAdapter == null -> "O Bluetooth não está disponível neste aparelho."
                bluetoothAdapter?.isEnabled != true -> "A conexão não começou porque o Bluetooth estava desligado. Ligue-o e tente novamente."
                else -> null
            }
        } catch (_: SecurityException) {
            "A conexão não começou. Confira as permissões de dispositivos próximos do aplicativo."
        }
        if (failure != null) {
            _sessionMessage.value = failure
            return false
        }
        userRequestedDisconnect = false
        val advertised = advertisedServices[device.macAddress].orEmpty()
        return when (VeepooDeviceSupport.classify(device.name, advertised, hasVeepooManufacturer(device.macAddress))) {
            VeepooDeviceSupport.Kind.UNSUPPORTED -> {
                markIncompatible(device, persist = false)
                false
            }
            VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO -> connectDeviceViaVeepooSdk(device)
            VeepooDeviceSupport.Kind.UNKNOWN -> connectDeviceViaRawGatt(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDeviceViaRawGatt(device: HBandDevice): Boolean {
        isVeepooConnection = false
        stopScanning()
        disconnectGatt()
        keepAliveJob?.cancel()
        rssiPollJob?.cancel()
        cancelSdkBatteryPolling()

        // Clear previous cache to ensure NO fake data is presented
        resetBiometricsToZero()
        _isHardwareConnected.value = false
        _connectedDevice.value = device.withUnknownBattery(connected = false)
        try {
            val adapter = bluetoothAdapter ?: error("Bluetooth adapter unavailable")
            if (!adapter.isEnabled) error("Bluetooth disabled during connection")
            val remoteDevice = adapter.getRemoteDevice(device.macAddress)
            _connectedDevice.value = device.copy(
                deviceId = remoteDevice.address,
                macAddress = remoteDevice.address,
                name = bluetoothDeviceNameOrFallback(remoteDevice, device.name),
            ).withUnknownBattery(connected = false)

            currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remoteDevice.connectGatt(context, false, gattCallback)
            } ?: error("Android did not create a GATT connection")
            // Only the successful connection callback may mark connected, persist,
            // start the service or publish telemetry.
            return true
        } catch (e: Exception) {
            Log.e(TAG, "GATT connect failed: ${e.message}", e)
            disconnectGatt()
            markDeviceDisconnected()
            _sessionMessage.value = "Não foi possível iniciar a conexão. Confira o Bluetooth e as permissões e tente novamente."
            return false
        }
    }

    /**
     * Conecta a um VE30/HBand real usando o SDK oficial Veepoo (VPOperateManager), que
     * implementa o handshake proprietário (senha + syncPersonInfo) exigido pelo firmware
     * antes de liberar qualquer sensor. GATT genérico não decodifica esse protocolo.
     */
    @SuppressLint("MissingPermission")
    private fun connectDeviceViaVeepooSdk(device: HBandDevice): Boolean {
        val liveSession = hasLiveHardwareSession()
        val connectingStale = isConnectAttemptStale()
        // Skip only a fresh in-flight connect or a current GATT notify session.
        // A hung connecting=true with no live session must retry.
        if (
            VeepooPasswordHandshake.shouldSkipDuplicateConnect(
                connecting = isConnectingVeepoo,
                liveSession = liveSession,
                notifyUp = _isHardwareConnected.value,
                confirmInFlight = passwordConfirmInFlight,
                connectingStale = connectingStale,
            )
        ) {
            Log.i(
                TAG,
                "VE30 senha: connect ignorado (connecting=$isConnectingVeepoo live=$liveSession " +
                    "notify=${_isHardwareConnected.value} confirmInFlight=$passwordConfirmInFlight " +
                    "stale=$connectingStale)",
            )
            cancelScheduledReconnect()
            if (!passwordHandshakeSucceeded && _isHardwareConnected.value && !passwordConfirmInFlight) {
                confirmVeepooPassword(isRetry = false)
            }
            return false
        }
        if (connectingStale) abortHungVeepooConnect(scheduleRetry = false)
        resetLeftoverSdkConnect()
        val hadLiveVeepoo = passwordHandshakeSucceeded && isVeepooConnection
        val generation = ++veepooConnectGeneration
        isConnectingVeepoo = true
        isVeepooConnection = true
        isSyncingPersonInfo = false
        hasStartedVeepooSensors = false
        advancedDetectActive = false
        passwordHandshakeSucceeded = false
        passwordConfirmInFlight = false
        pwdConfirmAttempt = 0
        veepooWriteBackoff.reset()
        cancelPendingPwdTimeout()
        cancelPersonInfoFallback()
        keepAliveJob?.cancel()
        keepAliveJob = null
        // Para um teste que a sessão anterior ou o GATT cru deixou ligado, e solta o GATT
        // cru antes do SDK assumir o rádio. Sem isso o onServicesDiscovered caía no
        // broadcast que liga FC/PPG/SpO₂/PA.
        stopLiveMeasurement()
        disconnectGatt()
        if (hadLiveVeepoo) p1Controller.stopAllDetect() else p1Controller.cancelled = true
        cancelLiveLinkWatchdog()
        cancelHistorySync()
        cancelSdkBatteryPolling()
        _historySyncState.value = HistorySyncUiState()
        stopScanning()
        resetBiometricsToZero()
        runCatching {
            vpManager.setConnectionConfirmTimeout(VeepooPasswordHandshake.SDK_CONFIRM_TIMEOUT_SEC)
        }

        Log.i(TAG, "Conectando via SDK Veepoo/HBand ao VE30: ${device.name} [${device.macAddress}]...")
        _connectedDevice.value = device.withUnknownBattery(connected = false)
        persistLastDevice(device)
        HBandBleService.start(context)
        armConnectAttemptWatchdog(device.macAddress, device.name)

        registerVeepooStatusListener(device.macAddress)

        vpManager.connectDevice(
            device.macAddress,
            device.name,
            IConnectResponse { code, _, _ ->
                if (generation != veepooConnectGeneration) return@IConnectResponse
                if (code != Constants.REQUEST_SUCCESS) {
                    Log.e(TAG, "VE30 senha: connect GATT falhou (code=$code)")
                    cancelConnectAttemptWatchdog()
                    isConnectingVeepoo = false
                    connectAttemptStartedAt = 0L
                    passwordConfirmInFlight = false
                    _sessionMessage.value = "Falha ao conectar ao VE30 (código $code)."
                    publishSessionPhase()
                    scheduleReconnect(device.macAddress, device.name)
                }
            },
            INotifyResponse { state ->
                if (generation != veepooConnectGeneration) return@INotifyResponse
                if (state == Constants.REQUEST_SUCCESS) {
                    Log.i(TAG, "VE30 senha: notify GATT OK — enviando confirmDevicePwd")
                    cancelConnectAttemptWatchdog()
                    connectAttemptStartedAt = 0L
                    bondNoneCycles = 0
                    _isHardwareConnected.value = true
                    reconnectAttempt = 0
                    cancelScheduledReconnect()
                    _connectedDevice.value = _connectedDevice.value?.copy(isConnected = true)
                    persistLastDevice(_connectedDevice.value ?: device)
                    publishSessionPhase()
                    confirmVeepooPassword()
                } else {
                    Log.e(TAG, "VE30 senha: notify GATT falhou (state=$state)")
                    cancelConnectAttemptWatchdog()
                    isConnectingVeepoo = false
                    connectAttemptStartedAt = 0L
                    passwordConfirmInFlight = false
                    _sessionMessage.value = "Falha ao ativar notificações do VE30."
                    publishSessionPhase()
                    scheduleReconnect(device.macAddress, device.name)
                }
            },
        )
        return true
    }

    private fun confirmVeepooPassword(isRetry: Boolean = false) {
        if (userRequestedDisconnect || passwordHandshakeSucceeded) return
        if (!isRetry) {
            pwdConfirmAttempt = 0
        }
        if (pwdConfirmAttempt >= VeepooPasswordHandshake.MAX_CONFIRM_ATTEMPTS) {
            Log.w(TAG, "VE30 senha: confirmDevicePwd não relançado (tentativas esgotadas)")
            return
        }
        pwdConfirmAttempt++
        val generation = ++pwdConfirmGeneration
        passwordConfirmInFlight = true
        Log.i(
            TAG,
            "VE30 senha: enviando confirmDevicePwd attempt=$pwdConfirmAttempt/" +
                "${VeepooPasswordHandshake.MAX_CONFIRM_ATTEMPTS} gen=$generation",
        )
        runCatching {
            vpManager.setConnectionConfirmTimeout(VeepooPasswordHandshake.SDK_CONFIRM_TIMEOUT_SEC)
        }
        vpManager.confirmDevicePwd(
            IBleWriteResponse { code ->
                if (generation != pwdConfirmGeneration) return@IBleWriteResponse
                if (code != Constants.REQUEST_SUCCESS) {
                    Log.e(TAG, "VE30 senha: WRITE_FAIL code=$code attempt=$pwdConfirmAttempt gen=$generation")
                    when (veepooWriteBackoff.onWriteResult(code, Constants.REQUEST_SUCCESS)) {
                        VeepooWriteBackoff.Decision.ABORT_UNSUPPORTED -> {
                            markIncompatible(_connectedDevice.value, persist = false)
                            return@IBleWriteResponse
                        }
                        VeepooWriteBackoff.Decision.BACKOFF -> {
                            _sessionMessage.value = VeepooPasswordHandshake.MSG_WRITE_FAIL
                            if (!passwordHandshakeSucceeded &&
                                pwdConfirmAttempt < VeepooPasswordHandshake.MAX_CONFIRM_ATTEMPTS
                            ) {
                                mainHandler.postDelayed(
                                    { confirmVeepooPassword(isRetry = true) },
                                    VeepooPasswordHandshake.LATE_CALLBACK_GRACE_MS,
                                )
                            }
                        }
                        VeepooWriteBackoff.Decision.CONTINUE -> Unit
                    }
                } else {
                    Log.i(TAG, "VE30 senha: escrita GATT OK attempt=$pwdConfirmAttempt gen=$generation")
                }
            },
            object : IPwdDataListener {
                override fun onPwdDataChange(pwdData: PwdData) {
                    if (generation != pwdConfirmGeneration) {
                        Log.d(TAG, "VE30 senha: onPwdDataChange ignorado (gen antiga $generation)")
                        return
                    }
                    val status = pwdData.getmStatus()
                    Log.i(
                        TAG,
                        "VE30 senha: resposta status=$status nº ${pwdData.deviceNumber} " +
                            "v${pwdData.deviceVersion} attempt=$pwdConfirmAttempt",
                    )
                    if (VeepooPasswordHandshake.isPwdAccepted(status?.name)) {
                        onPasswordConfirmed(pwdData)
                    } else {
                        Log.e(TAG, "VE30 senha: CHECK_FAIL status=$status — senha padrão recusada")
                    }
                }

                override fun onConnectionConfirmTimeout() {
                    Log.w(
                        TAG,
                        "VE30 senha: CONFIRM_TIMEOUT gen=$generation attempt=$pwdConfirmAttempt " +
                            "handshake=$passwordHandshakeSucceeded live=${hasLiveHardwareSession()}",
                    )
                    val runnable = Runnable { handlePasswordConfirmTimeout(generation) }
                    cancelPendingPwdTimeout()
                    pendingPwdTimeout = runnable
                    mainHandler.postDelayed(runnable, VeepooPasswordHandshake.LATE_CALLBACK_GRACE_MS)
                }
            },
            object : IDeviceFuctionDataListener {
                override fun onFunctionSupportDataChange(functionSupport: FunctionDeviceSupportData) {
                    lastFunctionSupport = functionSupport
                    val probed = VeepooCapabilityProbe.fromManager(vpManager, functionSupport)
                    _capabilities.value = probed
                    applyP1CapabilityFlags(probed)
                    Log.i(
                        TAG,
                        "Funções suportadas pelo VE30: days=${probed.historyDays} " +
                            "autoMeasure=${probed.isSupportAutoMeasure} preciseSleep=${probed.isSupportPreciseSleep} " +
                            "wear=${probed.isSupportWearDetect} originV=${probed.originProtocolVersion}",
                    )
                }

                override fun onDeviceFunctionPackage1Report(functionPackage1: DeviceFunctionPackage1) {}
                override fun onDeviceFunctionPackage2Report(functionPackage2: DeviceFunctionPackage2) {}
                override fun onDeviceFunctionPackage3Report(functionPackage3: DeviceFunctionPackage3) {}
                override fun onDeviceFunctionPackage4Report(functionPackage4: DeviceFunctionPackage4) {}
                override fun onDeviceFunctionPackage5Report(functionPackage5: DeviceFunctionPackage5) {}
            },
            object : ISocialMsgDataListener {
                override fun onSocialMsgSupportDataChange(socailMsgData: FunctionSocailMsgData) {}
                override fun onSocialMsgSupportDataChange2(socailMsgData: FunctionSocailMsgData) {}
            },
            ICustomSettingDataListener { customSettingData ->
                // O app demo oficial só chama syncPersonInfo depois deste callback — usar o
                // overload de 6 args (sem esse listener) deixava o handshake incompleto no
                // firmware real, mesmo com onPwdDataChange/onFunctionSupportDataChange OK.
                Log.i(TAG, "VE30 senha: custom settings OK ($customSettingData) — syncPersonInfo")
                lastCustomSettingData = customSettingData
                recordAutoMeasure(customSettingData)
                markPasswordHandshakeSucceeded("custom-settings")
                syncVeepooPersonInfo()
            },
            DEFAULT_VEEPOO_PWD,
            true,
        )
    }

    private fun onPasswordConfirmed(pwdData: PwdData) {
        markPasswordHandshakeSucceeded("pwd-data nº ${pwdData.deviceNumber}")
        _sessionMessage.value = null
        schedulePersonInfoFallback()
    }

    private fun markPasswordHandshakeSucceeded(reason: String) {
        if (userRequestedDisconnect) return
        passwordHandshakeSucceeded = true
        passwordConfirmInFlight = false
        veepooWriteBackoff.reset()
        _incompatibleWatch.value = null
        incompatibleCardMac = null
        cancelPendingPwdTimeout()
        cancelScheduledReconnect()
        cancelLiveLinkWatchdog()
        cancelConnectAttemptWatchdog()
        connectAttemptStartedAt = 0L
        runCatching { vpManager.removeConnectionConfirmationTask() }
        if (!_isHardwareConnected.value) {
            Log.i(TAG, "VE30 senha: latch hardwareConnected após $reason")
        }
        _isHardwareConnected.value = true
        isConnectingVeepoo = true
        reconnectAttempt = 0
        publishSessionPhase()
        val current = _connectedDevice.value
        if (current != null && !current.isConnected) {
            _connectedDevice.value = current.copy(isConnected = true)
        }
        Log.i(TAG, "VE30 senha: SUCCESS ($reason)")
        refreshSdkBattery()
        startSdkBatteryPolling()
    }

    private fun handlePasswordConfirmTimeout(generation: Int) {
        if (generation != pwdConfirmGeneration) {
            Log.i(TAG, "VE30 senha: timeout ignorado (geração antiga $generation≠$pwdConfirmGeneration)")
            return
        }
        if (userRequestedDisconnect) return
        val liveSession = hasLiveHardwareSession() ||
            (hasStartedVeepooSensors && lastHardwareReadTime > 0L &&
                System.currentTimeMillis() - lastHardwareReadTime < LIVE_LINK_STALE_MS)
        val notifyUp = _isHardwareConnected.value
        val decision = VeepooPasswordHandshake.onConfirmTimeout(
            attemptIndex = pwdConfirmAttempt,
            handshakeSucceeded = passwordHandshakeSucceeded,
            liveSession = liveSession,
            notifyUp = notifyUp,
        )
        Log.w(
            TAG,
            "VE30 senha: timeout decision=${decision.logReason} action=${decision.action} " +
                "keep=${decision.keepLiveSession} live=$liveSession notify=$notifyUp",
        )
        when (decision.action) {
            VeepooPasswordHandshake.Action.IGNORE -> return
            VeepooPasswordHandshake.Action.RETRY_CONFIRM -> {
                decision.sessionMessage?.let { _sessionMessage.value = it }
                confirmVeepooPassword(isRetry = true)
            }
            VeepooPasswordHandshake.Action.KEEP_SESSION -> {
                passwordConfirmInFlight = false
                decision.sessionMessage?.let { _sessionMessage.value = it }
                cancelScheduledReconnect()
                cancelConnectAttemptWatchdog()
                connectAttemptStartedAt = 0L
                isConnectingVeepoo = true
                if (decision.keepLiveSession) {
                    _isHardwareConnected.value = true
                }
                publishSessionPhase()
                if (!isSyncingPersonInfo && !hasStartedVeepooSensors) {
                    Log.i(TAG, "VE30 senha: timeout esgotado — syncPersonInfo na sessão GATT viva")
                    syncVeepooPersonInfo()
                }
            }
            VeepooPasswordHandshake.Action.RECONNECT -> {
                passwordConfirmInFlight = false
                isConnectingVeepoo = false
                connectAttemptStartedAt = 0L
                cancelConnectAttemptWatchdog()
                _isHardwareConnected.value = false
                _sessionMessage.value = decision.sessionMessage
                publishSessionPhase()
                val device = _connectedDevice.value
                if (device != null) {
                    scheduleReconnect(device.macAddress, device.name)
                }
            }
        }
    }

    private fun cancelPendingPwdTimeout() {
        pendingPwdTimeout?.let { mainHandler.removeCallbacks(it) }
        pendingPwdTimeout = null
    }

    private fun cancelPersonInfoFallback() {
        personInfoFallback?.let { mainHandler.removeCallbacks(it) }
        personInfoFallback = null
    }

    private fun schedulePersonInfoFallback() {
        cancelPersonInfoFallback()
        val runnable = Runnable {
            if (userRequestedDisconnect || isSyncingPersonInfo) return@Runnable
            if (!passwordHandshakeSucceeded) return@Runnable
            Log.i(TAG, "VE30 senha: fallback syncPersonInfo (custom settings atrasado)")
            syncVeepooPersonInfo()
        }
        personInfoFallback = runnable
        mainHandler.postDelayed(runnable, VeepooPasswordHandshake.PERSON_INFO_FALLBACK_MS)
    }

    private fun syncVeepooPersonInfo() {
        if (isSyncingPersonInfo) {
            Log.d(TAG, "syncPersonInfo já em andamento, ignorando chamada duplicada do firmware.")
            return
        }
        isSyncingPersonInfo = true
        cancelPersonInfoFallback()
        Log.i(TAG, "Sincronizando perfil biométrico com o VE30...")
        vpManager.syncPersonInfo(
            IBleWriteResponse { code ->
                if (code != Constants.REQUEST_SUCCESS) {
                    Log.e(TAG, "Falha ao escrever perfil biométrico (code=$code)")
                }
            },
            IPersonInfoDataListener { status ->
                if (status == EOprateStauts.OPRATE_SUCCESS) {
                    Log.i(TAG, "VE30 handshake OK — probe + histórico P0, depois sensores ao vivo.")
                    markPasswordHandshakeSucceeded("syncPersonInfo")
                    startPostHandshakeSync()
                } else {
                    Log.e(TAG, "Falha ao sincronizar perfil biométrico: $status")
                    isSyncingPersonInfo = false
                }
            },
            PersonInfoData(
                if (profileIsMale) ESex.MAN else ESex.WOMEN,
                profileHeightCm,
                profileWeightKg,
                profileAge,
                profileStepGoal,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun startVeepooSensors() {
        if (hasStartedVeepooSensors) {
            Log.d(TAG, "Sensores já iniciados nesta sessão, ignorando novo start.")
            return
        }
        hasStartedVeepooSensors = true
        // Política de bateria: o relógio mede sozinho (medição automática do firmware) e o
        // app só lê o histórico. Nenhum teste óptico/temperatura começa aqui; só no toque
        // do paciente em "medir agora" (requestManualMeasurement).
        if (WatchBatteryPolicy.CONTINUOUS_TEMPERATURE) startTemperatureMonitoring()
        Log.i(TAG, "Sessão Veepoo pronta: sem teste ao vivo automático (política de bateria)")
        scheduleHistoryPoll()
    }

    /**
     * Toque do paciente em medir (app em primeiro plano): um único ciclo FC → SpO2 → PA
     * (se devida) → HRV, sem reagendar. No máximo um a cada [WatchBatteryPolicy.MANUAL_MIN_GAP_MS].
     */
    fun requestManualMeasurement(): Boolean {
        if (!isVeepooConnection || userRequestedDisconnect) return false
        val now = SystemClock.elapsedRealtime()
        if (!WatchBatteryPolicy.manualAllowed(lastManualMeasureElapsedMs, now, foreground = appInForeground)) {
            _sessionMessage.value = "Medição recente. Aguarde um minuto para medir de novo."
            return false
        }
        if (!isConnectingVeepoo || advancedDetectActive || ppgCycleRunning) {
            _sessionMessage.value = "Relógio ocupado ou desconectado. Tente de novo em instantes."
            return false
        }
        lastManualMeasureElapsedMs = now
        _sessionMessage.value = "Medindo no relógio… mantenha a pulseira no pulso."
        startPpgCycle(reason = "medição manual", force = true)
        return true
    }

    private fun scheduleHistoryPoll() {
        cancelHistoryPoll()
        val runnable = Runnable {
            historyPollRunnable = null
            if (userRequestedDisconnect || !isVeepooConnection || !hasLiveHardwareSession()) return@Runnable
            if (advancedDetectActive || ppgCycleRunning || _historySyncState.value.isRunning) {
                scheduleHistoryPoll()
                return@Runnable
            }
            pollWatchHistory()
        }
        historyPollRunnable = runnable
        mainHandler.postDelayed(runnable, WatchBatteryPolicy.HISTORY_POLL_MS)
    }

    private fun cancelHistoryPoll() {
        historyPollRunnable?.let { mainHandler.removeCallbacks(it) }
        historyPollRunnable = null
    }

    /** Lê só o dia de hoje do histórico gravado pelo relógio (sem sensor ligado) e passos. */
    private fun pollWatchHistory() {
        val sync = historyClient()
        val deviceId = currentConnectedMac()
        val deviceModel = currentConnectedName()
        scope.launch(Dispatchers.Main) {
            try {
                val caps = _capabilities.value.copy(historyDays = 1)
                val sport = watchSettingsMutex.withLock {
                    if (!currentSettingsClient(sync)) return@launch
                    sync.readSportOnce()
                }
                sport?.let { reading ->
                    currentSteps = reading.steps ?: currentSteps
                    currentCalories = reading.calories ?: currentCalories
                    currentDistance = reading.distanceMeters ?: currentDistance
                    onSportReading(deviceId, reading)
                }
                val pull = watchSettingsMutex.withLock {
                    if (!currentSettingsClient(sync)) return@launch
                    sync.pullHistoryWithoutSleep(caps, deviceId, deviceModel) {
                        if (historySync === sync && !sync.cancelled) _historySyncState.value = it
                    }
                }
                if (!currentSettingsClient(sync)) return@launch
                _historySyncState.value = pull.state
                val telemetries = pull.samples.map { it.telemetry }
                if (telemetries.isNotEmpty()) {
                    onHistorySamples(telemetries)
                    lastValidBp.rememberFromHistory(telemetries)
                }
                Log.i(TAG, "Histórico periódico (hoje): ${historyReadStatusText(pull.state)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Histórico periódico falhou: ${e.message}")
            } finally {
                if (!userRequestedDisconnect && isVeepooConnection && hasLiveHardwareSession()) scheduleHistoryPoll()
            }
        }
    }

    /**
     * SpO₂ noturno, SpO₂ de 24 h e interruptores contínuos (PPG, temperatura, ECG
     * sempre aberto, etc.) ficam na memória do relógio. Um app antigo pode tê-los
     * deixado ligados; cada conexão escreve o desligado, mesmo se o histórico falhar.
     */
    private suspend fun enforceContinuousMonitoringOff(sync: VeepooHistorySync, caps: DeviceCapabilities) {
        if (caps.isSupportSpo2AutoDetect) {
            val closed = sync.confirmedSpo2Enabled(sync.setSpo2AutoEnabled(false, null))
            if (currentSettingsClient(sync) && closed == false) {
                prefs.edit().putBoolean(PREF_SPO2_AUTO, false).apply()
                _autoMeasureState.value = _autoMeasureState.value.copy(
                    spo2NightAutoEnabled = false,
                    spo2Confirmation = WatchSettingConfirmation.CONFIRMED,
                )
            }
        }
        if (sync.closeContinuousCustomSwitches(lastCustomSettingData) && currentSettingsClient(sync)) {
            recordAutoMeasure(lastCustomSettingData)
        }
        if (caps.isSupportSpo2) sync.ensureAllDaySpo2Off()
    }

    /** Mantém no relógio a medição automática econômica (ver [WatchBatteryPolicy]). */
    private suspend fun applyBatterySafeAutoMeasure(sync: VeepooHistorySync) {
        if (!_capabilities.value.isSupportAutoMeasure) return
        val current = lastAutoMeasureSettings
        if (current.isEmpty()) return
        val userOff = prefs.getBoolean(PREF_AUTO_MEASURE_USER_OFF, false)
        val updated = sync.applyBatteryPolicy(current, heartRateUserOff = userOff) ?: return
        if (currentSettingsClient(sync)) {
            lastAutoMeasureSettings = updated
            applyAutoMeasureUi(updated, null, updateReadTime = false)
            Log.i(TAG, "VE30 medição automática ajustada: ${updated.joinToString { "${it.funType}=${it.isSwitchOpen}/${it.measureInterval}min" }}")
        }
    }

    /** Temperatura usa um sensor térmico independente — não compete pelo PPG, roda à parte. */
    @SuppressLint("MissingPermission")
    private fun startTemperatureMonitoring() {
        val mac = currentConnectedMac()
        val name = currentConnectedName()
        Log.i(TAG, "Enviando startDetectTempture...")
        val listener = ITemptureDetectDataListener { data ->
            Log.i(TAG, "onDataChange(TemptureDetectData): ${data.tempture}°C (progress=${data.progress})")
            latchLiveHardwareLink()
            if (data.tempture in 30f..42f) {
                currentTemp = data.tempture
                emitRealTelemetry(mac, name)
            }
        }
        activeTempListener = listener
        vpManager.startDetectTempture(
            IBleWriteResponse { code -> Log.i(TAG, "startDetectTempture ACK code=$code") },
            listener,
        )
    }

    private fun stopTemperatureMonitoring() {
        activeTempListener?.let { listener ->
            runCatching { vpManager.stopDetectTempture(IBleWriteResponse {}, listener) }
        }
        activeTempListener = null
    }

    @SuppressLint("MissingPermission")
    private fun runHeartStage() {
        if (!isConnectingVeepoo || advancedDetectActive) return
        val generation = ++ppgStageGeneration
        val mac = currentConnectedMac()
        val name = currentConnectedName()
        Log.i(TAG, "[PPG] Estágio FC...")
        vpManager.startDetectHeart(
            IBleWriteResponse { code -> Log.i(TAG, "startDetectHeart ACK code=$code") },
            IHeartDataListener { heart ->
                Log.i(TAG, "onDataChange(HeartData): ${heart.data} bpm")
                if (!acceptStageSample(generation)) return@IHeartDataListener
                latchLiveHardwareLink()
                if (heart.data in 30..240) {
                    currentHeartRate = heart.data
                    ppgCycleGotReading = true
                    emitRealTelemetry(mac, name)
                }
            },
        )
        mainHandler.postDelayed({
            if (generation != ppgStageGeneration) return@postDelayed
            vpManager.stopDetectHeart(IBleWriteResponse {})
            mainHandler.postDelayed({ runSpo2Stage() }, PPG_STAGE_GAP_MS)
        }, HEART_STAGE_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun runSpo2Stage() {
        if (!isConnectingVeepoo || advancedDetectActive) return
        val generation = ++ppgStageGeneration
        val mac = currentConnectedMac()
        val name = currentConnectedName()
        Log.i(TAG, "[PPG] Estágio SpO2...")
        val listener = ISpo2hDataListener { data ->
            Log.i(TAG, "onSpO2HADataChange: ${data.value}%")
            if (!acceptStageSample(generation)) return@ISpo2hDataListener
            latchLiveHardwareLink()
            if (data.value in 50..100) {
                currentSpO2 = data.value
                ppgCycleGotReading = true
                emitRealTelemetry(mac, name)
            }
        }
        activeSpo2Listener = listener
        vpManager.startDetectSPO2H(
            IBleWriteResponse { code -> Log.i(TAG, "startDetectSPO2H ACK code=$code") },
            listener,
            ILightDataCallBack {},
        )
        mainHandler.postDelayed({
            if (generation != ppgStageGeneration) return@postDelayed
            activeSpo2Listener?.let { vpManager.stopDetectSPO2H(IBleWriteResponse {}, it) }
            mainHandler.postDelayed({ runBpStage() }, PPG_STAGE_GAP_MS)
        }, SPO2_STAGE_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun runBpStage() {
        if (!isConnectingVeepoo || advancedDetectActive) return
        val generation = ++ppgStageGeneration
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!PpgCyclePolicy.bloodIsDue(bpUnsupported, lastBpSuccessElapsedMs, nowElapsed)) {
            Log.i(TAG, "Estágio PA adiado: última medição ainda dentro de 30 min")
            mainHandler.postDelayed({
                if (generation != ppgStageGeneration) return@postDelayed
                mainHandler.postDelayed({ runHrvStage() }, PPG_STAGE_GAP_MS)
            }, PPG_STAGE_GAP_MS)
            return
        }
        if (!bpDetectRetry.shouldAttempt(nowElapsed)) {
            Log.w(
                TAG,
                "startDetectBP adiado após falha anterior (backoff ${bpDetectRetry.waitMs(nowElapsed)} ms)",
            )
            mainHandler.postDelayed({
                if (generation != ppgStageGeneration) return@postDelayed
                mainHandler.postDelayed({ runHrvStage() }, PPG_STAGE_GAP_MS)
            }, PPG_STAGE_GAP_MS)
            return
        }
        val mac = currentConnectedMac()
        val name = currentConnectedName()
        Log.i(TAG, "[PPG] Estágio PA...")
        vpManager.startDetectBP(
            IBleWriteResponse { code ->
                Log.i(TAG, "startDetectBP ACK code=$code")
                if (generation != ppgStageGeneration) return@IBleWriteResponse
                if (code != Constants.REQUEST_SUCCESS) {
                    Log.e(
                        TAG,
                        "startDetectBP falhou no VE30 (code=$code); nova tentativa no próximo ciclo com backoff",
                    )
                    bpDetectRetry.onAck(success = false, nowElapsedMs = SystemClock.elapsedRealtime())
                    ppgStageGeneration++
                    runCatching {
                        vpManager.stopDetectBP(IBleWriteResponse {}, EBPDetectModel.DETECT_MODEL_PUBLIC)
                    }
                    mainHandler.postDelayed({ runHrvStage() }, PPG_STAGE_GAP_MS)
                } else {
                    bpDetectRetry.onAck(success = true, nowElapsedMs = SystemClock.elapsedRealtime())
                }
            },
            IBPDetectDataListener { data ->
                Log.i(TAG, "onDataChange(BpData): ${data.highPressure}/${data.lowPressure} (progress=${data.progress})")
                if (!acceptStageSample(generation)) return@IBPDetectDataListener
                latchLiveHardwareLink()
                if (data.highPressure in 60..240 && data.lowPressure in 30..160) {
                    currentSystolic = data.highPressure
                    currentDiastolic = data.lowPressure
                    lastValidBp.remember(data.highPressure, data.lowPressure, System.currentTimeMillis())
                    lastBpSuccessElapsedMs = SystemClock.elapsedRealtime()
                    ppgCycleGotReading = true
                    emitRealTelemetry(mac, name)
                }
            },
            EBPDetectModel.DETECT_MODEL_PUBLIC,
        )
        mainHandler.postDelayed({
            if (generation != ppgStageGeneration) return@postDelayed
            vpManager.stopDetectBP(IBleWriteResponse {}, EBPDetectModel.DETECT_MODEL_PUBLIC)
            mainHandler.postDelayed({ runHrvStage() }, PPG_STAGE_GAP_MS)
        }, BP_STAGE_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun runHrvStage() {
        if (!isConnectingVeepoo || advancedDetectActive) return
        val generation = ++ppgStageGeneration
        val mac = currentConnectedMac()
        val name = currentConnectedName()
        Log.i(TAG, "[PPG] Estágio HRV...")
        val listener = object : IHrvDetectListener {
            override fun onHrvDetect(hrv: Int) {
                Log.i(TAG, "onHrvDetect: $hrv")
                if (!acceptStageSample(generation)) return
                latchLiveHardwareLink()
                currentHrvScore = hrv
                emitRealTelemetry(mac, name)
            }

            override fun onDetectFailed(detectState: HrvDetectState) {
                Log.w(TAG, "Falha na detecção de HRV do VE30: $detectState")
            }

            override fun onDetectStop() {}
        }
        activeHrvListener = listener
        vpManager.startDetectHrv(
            BleWriteResponse { code -> Log.i(TAG, "startDetectHrv ACK code=$code") },
            listener,
        )
        mainHandler.postDelayed({
            if (generation != ppgStageGeneration) return@postDelayed
            activeHrvListener?.let { vpManager.stopDetectHrv(BleWriteResponse {}, it) }
            finishPpgCycle(failed = false)
        }, HRV_STAGE_TIMEOUT_MS)
    }

    private fun startPpgCycle(reason: String, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!PpgCyclePolicy.shouldStartCycle(
                hardwareReady = isConnectingVeepoo && !advancedDetectActive,
                cycleRunning = ppgCycleRunning,
                nowElapsedMs = now,
                nextCycleAtElapsedMs = ppgNextCycleAtElapsedMs,
                force = force,
            )
        ) {
            Log.d(TAG, "Ciclo PPG adiado ($reason); próxima em ${ (ppgNextCycleAtElapsedMs - now).coerceAtLeast(0L) } ms")
            return
        }
        cancelPpgNextCycle()
        ppgCycleRunning = true
        ppgCycleGotReading = false
        Log.i(TAG, "Ciclo PPG: início ($reason)")
        runHeartStage()
    }

    private fun finishPpgCycle(failed: Boolean) {
        stopPpgSensors()
        ppgCycleRunning = false
        if (failed) {
            ppgConsecutiveFailures++
        } else if (ppgCycleGotReading) {
            ppgConsecutiveFailures = 0
        }
        ppgEmptyCycles = if (ppgCycleGotReading) 0 else ppgEmptyCycles + 1
        val rest = PpgCyclePolicy.restAfterCycle(
            emptyCycles = ppgEmptyCycles,
            consecutiveFailures = ppgConsecutiveFailures,
            failed = failed,
        )
        ppgNextCycleAtElapsedMs = SystemClock.elapsedRealtime() + rest
        Log.i(
            TAG,
            "Ciclo PPG: fim; leitura=${if (ppgCycleGotReading) "sim" else "não"}; próxima em ${rest / 1000}s",
        )
        if (WatchBatteryPolicy.AUTOMATIC_LIVE_LOOP) schedulePpgNextCycle(rest)
    }

    private fun schedulePpgNextCycle(delayMs: Long) {
        cancelPpgNextCycle()
        val generation = ppgStageGeneration
        val runnable = Runnable {
            if (generation != ppgStageGeneration) return@Runnable
            if (userRequestedDisconnect || !isConnectingVeepoo || advancedDetectActive) return@Runnable
            startPpgCycle(reason = "pausa concluída", force = false)
        }
        ppgNextCycleRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPpgNextCycle() {
        ppgNextCycleRunnable?.let { mainHandler.removeCallbacks(it) }
        ppgNextCycleRunnable = null
    }

    /** Amostra de um estágio manual. Fora do ciclo, o pacote não vira telemetria e o teste é parado. */
    private fun acceptStageSample(generation: Int): Boolean {
        if (generation != ppgStageGeneration) return false
        if (WatchBatteryPolicy.acceptLiveSample(ppgCycleRunning)) return true
        stopPpgSensors()
        return false
    }

    /**
     * Para teste ao vivo no SDK e no GATT cru. Não desconecta.
     * Chamado na conexão, no disconnect, ao ir para segundo plano e ao esquecer o relógio.
     */
    private fun stopLiveMeasurement() {
        cancelPpgNextCycle()
        stopPpgSensors()
        stopTemperatureMonitoring()
        stopGattRealtimeSensors(currentGatt)
        if (passwordHandshakeSucceeded) runCatching { p1Controller.stopAllDetect() }
        advancedDetectActive = false
        _ecgState.value = _ecgState.value.copy(running = false)
        _glucoseState.value = _glucoseState.value.copy(running = false)
        _bloodComponentState.value = _bloodComponentState.value.copy(running = false)
        _bodyComponentState.value = _bodyComponentState.value.copy(running = false)
        _emotionState.value = _emotionState.value.copy(running = false)
        _fatigueState.value = _fatigueState.value.copy(running = false)
        _breathDetectState.value = _breathDetectState.value.copy(running = false)
    }

    fun onHostForegrounded() {
        appInForeground = true
    }

    /** A activity saiu da tela: o sensor óptico não pode continuar medindo. */
    fun onHostBackgrounded() {
        appInForeground = false
        if (!isVeepooConnection && currentGatt == null) return
        Log.i(TAG, "App em segundo plano: parando medição ao vivo")
        stopLiveMeasurement()
    }

    /** Escreve 0x00 nos opcodes de teste ao vivo. No máximo um lote a cada 30 s. */
    private fun stopGattRealtimeSensors(gatt: BluetoothGatt?) {
        val active = gatt ?: currentGatt ?: return
        if (discoveredWriteCharacteristics.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRealtimeStopElapsedMs < 30_000L) return
        lastRealtimeStopElapsedMs = now
        for (target in discoveredWriteCharacteristics) {
            for (cmd in WatchBatteryPolicy.connectStopCommands()) {
                enqueueGattOp(GattOp.WriteChar(active, target, cmd))
            }
        }
    }

    private fun acceptProprietaryLiveSample(gatt: BluetoothGatt?): Boolean {
        if (WatchBatteryPolicy.acceptLiveSample(ppgCycleRunning)) return true
        stopGattRealtimeSensors(gatt)
        return false
    }

    /** Para todos os testes PPG/HRV e invalida callbacks pendentes. */
    private fun stopPpgSensors() {
        ppgStageGeneration++
        ppgCycleRunning = false
        runCatching { vpManager.stopDetectHeart(IBleWriteResponse {}) }
        activeSpo2Listener?.let { runCatching { vpManager.stopDetectSPO2H(IBleWriteResponse {}, it) } }
        activeSpo2Listener = null
        runCatching { vpManager.stopDetectBP(IBleWriteResponse {}, EBPDetectModel.DETECT_MODEL_PUBLIC) }
        activeHrvListener?.let { runCatching { vpManager.stopDetectHrv(BleWriteResponse {}, it) } }
        activeHrvListener = null
    }

    private fun currentConnectedMac(): String = _connectedDevice.value?.macAddress ?: ""
    private fun currentConnectedName(): String = _connectedDevice.value?.name ?: "VE30"

    /**
     * HeartData (and other live detects) prove the VE30 session is up even if
     * the SDK fired a spurious GATT disconnect. Keep P1 buttons enabled.
     */
    private fun latchLiveHardwareLink() {
        if (userRequestedDisconnect) return
        lastHardwareReadTime = System.currentTimeMillis()
        cancelLiveLinkWatchdog()
        if (!_isHardwareConnected.value) {
            Log.i(TAG, "Latching hardwareConnected from live detect callback")
        }
        _isHardwareConnected.value = true
        if (isVeepooConnection) {
            isConnectingVeepoo = true
        }
        reconnectAttempt = 0
        cancelConnectAttemptWatchdog()
        connectAttemptStartedAt = 0L
        cancelScheduledReconnect()
        publishSessionPhase()
        val current = _connectedDevice.value
        if (current != null && !current.isConnected) {
            _connectedDevice.value = current.copy(isConnected = true)
        }
    }

    private fun hasLiveHardwareSession(): Boolean {
        if (userRequestedDisconnect) return false
        return VeepooSessionGate.actionsEnabled(
            hardwareConnected = _isHardwareConnected.value,
            connectedMac = currentConnectedMac(),
            telemetry = _latestTelemetry.value,
        )
    }

    private fun cancelLiveLinkWatchdog() {
        liveLinkWatchdog?.let { mainHandler.removeCallbacks(it) }
        liveLinkWatchdog = null
    }

    private fun armLiveLinkWatchdog(address: String, name: String) {
        cancelLiveLinkWatchdog()
        val runnable = Runnable {
            if (userRequestedDisconnect) return@Runnable
            val age = System.currentTimeMillis() - lastHardwareReadTime
            val stale = lastHardwareReadTime == 0L || age > LIVE_LINK_STALE_MS
            if (!stale) {
                Log.i(TAG, "GATT disconnect ignored — live detect still ${age}ms ago")
                return@Runnable
            }
            Log.w(TAG, "Live detect went quiet after GATT disconnect — clearing hardware latch")
            hasStartedVeepooSensors = false
            isConnectingVeepoo = false
            connectAttemptStartedAt = 0L
            cancelConnectAttemptWatchdog()
            isSyncingPersonInfo = false
            advancedDetectActive = false
            cancelPpgNextCycle()
            stopPpgSensors()
            _isHardwareConnected.value = false
            markDeviceDisconnected()
            publishSessionPhase()
            scheduleReconnect(address, name)
        }
        liveLinkWatchdog = runnable
        mainHandler.postDelayed(runnable, LIVE_LINK_STALE_MS)
    }

    private fun persistLastDevice(device: HBandDevice) {
        val mac = device.macAddress.trim()
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) return
        if (VeepooDeviceSupport.classify(
                device.name,
                advertisedServices[mac].orEmpty(),
                hasVeepooManufacturer(mac),
            ) == VeepooDeviceSupport.Kind.UNSUPPORTED
        ) return
        prefs.edit()
            .putString(PREF_LAST_MAC, mac)
            .putString(PREF_LAST_NAME, device.name)
            .apply()
    }

    private fun registerVeepooStatusListener(mac: String) {
        if (veepooStatusListenerMac == mac && veepooStatusListener != null) return
        veepooStatusListenerMac?.let { previous ->
            veepooStatusListener?.let { listener ->
                try {
                    vpManager.unregisterConnectStatusListener(previous, listener)
                } catch (e: Exception) {
                    Log.w(TAG, "Não foi possível remover o listener Veepoo anterior: ${e.message}")
                }
            }
        }
        val listener = object : IABleConnectStatusListener() {
            override fun onConnectStatusChanged(address: String, status: Int) {
                when (status) {
                    Constants.STATUS_CONNECTED -> {
                        Log.i(TAG, "Veepoo GATT conectado: $address")
                        reconnectAttempt = 0
                        consecutiveReconnectFailures = 0
                    }
                    Constants.STATUS_DISCONNECTED -> {
                        Log.w(TAG, "Veepoo GATT desconectado: $address")
                        if (suppressDisconnectReconnect) {
                            suppressDisconnectReconnect = false
                            return
                        }
                        if (userRequestedDisconnect) {
                            isConnectingVeepoo = false
                            connectAttemptStartedAt = 0L
                            cancelConnectAttemptWatchdog()
                            hasStartedVeepooSensors = false
                            isSyncingPersonInfo = false
                            cancelHistorySync()
                            if (passwordHandshakeSucceeded) p1Controller.stopAllDetect() else p1Controller.cancelled = true
                            advancedDetectActive = false
                            cancelPpgNextCycle()
                            stopPpgSensors()
                            cancelLiveLinkWatchdog()
                            _isHardwareConnected.value = false
                            markDeviceDisconnected()
                            publishSessionPhase()
                            return
                        }
                        val liveAge = System.currentTimeMillis() - lastHardwareReadTime
                        val sensorsLive = (hasStartedVeepooSensors || advancedDetectActive) &&
                            lastHardwareReadTime > 0L &&
                            liveAge < LIVE_LINK_STALE_MS
                        if (sensorsLive || hasStartedVeepooSensors) {
                            Log.w(
                                TAG,
                                "Ignorando STATUS_DISCONNECTED enquanto sensores ainda rodam " +
                                    "(lastDetect=${liveAge}ms, sensors=$hasStartedVeepooSensors)",
                            )
                            armLiveLinkWatchdog(address, _connectedDevice.value?.name ?: "VE30")
                            return
                        }
                        isConnectingVeepoo = false
                        connectAttemptStartedAt = 0L
                        cancelConnectAttemptWatchdog()
                        hasStartedVeepooSensors = false
                        isSyncingPersonInfo = false
                        cancelHistorySync()
                        if (passwordHandshakeSucceeded) p1Controller.stopAllDetect() else p1Controller.cancelled = true
                        advancedDetectActive = false
                        stopPpgSensors()
                        stopTemperatureMonitoring()
                        _isHardwareConnected.value = false
                        markDeviceDisconnected()
                        publishSessionPhase()
                        val name = _connectedDevice.value?.name ?: "VE30"
                        scheduleReconnect(address, name)
                    }
                }
            }
        }
        veepooStatusListener = listener
        veepooStatusListenerMac = mac
        vpManager.registerConnectStatusListener(mac, listener)
    }

    private fun cancelScheduledReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun isConnectAttemptStale(): Boolean = VeepooReconnectPolicy.isConnectingStale(
        connecting = isConnectingVeepoo,
        startedAtElapsedMs = connectAttemptStartedAt,
        nowElapsedMs = SystemClock.elapsedRealtime(),
        notifyUp = _isHardwareConnected.value,
        liveSession = hasLiveHardwareSession(),
    )

    private fun publishSessionPhase() {
        _sessionPhase.value = VeepooReconnectPolicy.phase(
            hardwareConnected = _isHardwareConnected.value,
            deviceConnected = _connectedDevice.value?.isConnected == true,
            autoReconnect = isAutoReconnectEnabled,
            hasPersistedSession = hasPersistedSession(),
            userStopped = userRequestedDisconnect,
            connecting = isConnectingVeepoo,
            connectAttemptStale = isConnectAttemptStale(),
            failedAttempts = reconnectAttempt,
        )
    }

    private fun armConnectAttemptWatchdog(address: String, name: String) {
        cancelConnectAttemptWatchdog()
        connectAttemptStartedAt = SystemClock.elapsedRealtime()
        val generation = veepooConnectGeneration
        val runnable = Runnable {
            if (generation != veepooConnectGeneration) return@Runnable
            if (userRequestedDisconnect) return@Runnable
            if (!isConnectAttemptStale()) return@Runnable
            Log.w(TAG, "VE30: watchdog de connect — tentativa sem sessão ao vivo ($address/$name)")
            abortHungVeepooConnect(scheduleRetry = true)
        }
        connectAttemptWatchdog = runnable
        mainHandler.postDelayed(runnable, VeepooReconnectPolicy.CONNECT_ATTEMPT_TIMEOUT_MS)
        publishSessionPhase()
    }

    private fun cancelConnectAttemptWatchdog() {
        connectAttemptWatchdog?.let { mainHandler.removeCallbacks(it) }
        connectAttemptWatchdog = null
    }

    private fun resetLeftoverSdkConnect() {
        runCatching {
            if (vpManager.isCurrentDeviceConnected) {
                Log.w(TAG, "VE30: sessão SDK residual — desconectando antes de nova tentativa")
                suppressDisconnectReconnect = true
                vpManager.disconnectWatch(IBleWriteResponse {})
            }
        }
    }

    private fun abortHungVeepooConnect(scheduleRetry: Boolean) {
        val device = _connectedDevice.value
        val address = device?.macAddress.orEmpty()
        val name = device?.name ?: "VE30"
        Log.w(
            TAG,
            "VE30: abortando connect preso (connecting=$isConnectingVeepoo " +
                "notify=${_isHardwareConnected.value} retry=$scheduleRetry)",
        )
        cancelConnectAttemptWatchdog()
        veepooConnectGeneration++
        isConnectingVeepoo = false
        passwordConfirmInFlight = false
        passwordHandshakeSucceeded = false
        connectAttemptStartedAt = 0L
        bondNoneCycles = 0
        hasStartedVeepooSensors = false
        isSyncingPersonInfo = false
        cancelPendingPwdTimeout()
        cancelPersonInfoFallback()
        suppressDisconnectReconnect = true
        runCatching { vpManager.disconnectWatch(IBleWriteResponse {}) }
        _isHardwareConnected.value = false
        markDeviceDisconnected()
        publishSessionPhase()
        if (scheduleRetry && address.isNotBlank()) {
            scheduleReconnect(address, name)
        }
    }

    private fun scheduleReconnect(address: String, name: String) {
        if (userRequestedDisconnect || !isAutoReconnectEnabled) return
        if (address.isBlank() || !BluetoothAdapter.checkBluetoothAddress(address)) return
        if (_isHardwareConnected.value) return
        cancelScheduledReconnect()
        val next = VeepooReconnectPolicy.nextAttemptIndex(reconnectAttempt)
        if (next == VeepooReconnectPolicy.MAX_TRACKED_ATTEMPTS && reconnectAttempt < next) {
            _sessionMessage.value = "Ainda tentando reconectar. Aproxime a pulseira deste celular."
        }
        reconnectAttempt = next
        val notWorn = _wearDetectState.value.enabled && lastKnownWorn == false
        val delayMs = WatchBatteryPolicy.reconnectDelayMs(
            VeepooReconnectPolicy.delayMs(reconnectAttempt, notWorn),
            consecutiveReconnectFailures++,
        )
        Log.i(
            TAG,
            "Auto-reconnect attempt $reconnectAttempt in ${delayMs}ms to $address " +
                "(worn=$lastKnownWorn wearDetect=${_wearDetectState.value.enabled})",
        )
        val runnable = Runnable {
            if (!isAutoReconnectEnabled || userRequestedDisconnect || _isHardwareConnected.value) return@Runnable
            if (isConnectAttemptStale()) abortHungVeepooConnect(scheduleRetry = false)
            connectDevice(
                HBandDevice(
                    deviceId = address,
                    name = name,
                    macAddress = address,
                    isConnected = false,
                    firmwareVersion = "Reconexão"
                )
            )
        }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
        publishSessionPhase()
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        userRequestedDisconnect = true
        cancelScheduledReconnect()
        cancelLiveLinkWatchdog()
        cancelConnectAttemptWatchdog()
        connectAttemptStartedAt = 0L
        startGraceReconnectPosted = false
        cancelPendingPwdTimeout()
        cancelPersonInfoFallback()
        cancelHistorySync()
        val hadLiveVeepoo = passwordHandshakeSucceeded && isVeepooConnection
        stopLiveMeasurement()
        if (hadLiveVeepoo) p1Controller.stopAllDetect() else p1Controller.cancelled = true
        advancedDetectActive = false
        isConnectingVeepoo = false
        isSyncingPersonInfo = false
        hasStartedVeepooSensors = false
        passwordHandshakeSucceeded = false
        passwordConfirmInFlight = false
        pwdConfirmAttempt = 0
        lastHardwareReadTime = 0L
        resetBiometricsToZero()
        if (isVeepooConnection) {
            vpManager.disconnectWatch(IBleWriteResponse {})
        } else {
            disconnectGatt()
        }
        keepAliveJob?.cancel()
        rssiPollJob?.cancel()
        cancelSdkBatteryPolling()
        _isHardwareConnected.value = false
        markDeviceDisconnected()
        publishSessionPhase()
    }

    fun forgetDevice() {
        val target = _incompatibleWatch.value ?: _connectedDevice.value
        userRequestedDisconnect = true
        stopLiveMeasurement()
        cancelScheduledReconnect()
        cancelConnectAttemptWatchdog()
        connectAttemptStartedAt = 0L
        p1Controller.cancelled = true
        isConnectingVeepoo = false
        isVeepooConnection = false
        passwordHandshakeSucceeded = false
        passwordConfirmInFlight = false
        veepooWriteBackoff.reset()
        disconnectGatt()
        _isHardwareConnected.value = false
        markDeviceDisconnected()
        _connectedDevice.value = null
        _incompatibleWatch.value = null
        incompatibleCardMac = null
        val mac = target?.macAddress?.trim().orEmpty()
        if (mac.isNotEmpty()) {
            advertisedServices.remove(mac)
            unbindBond(mac)
        }
        prefs.edit().remove(PREF_LAST_MAC).remove(PREF_LAST_NAME).apply()
        publishSessionPhase()
        startScanning()
    }

    private fun markIncompatible(device: HBandDevice?, persist: Boolean) {
        val current = device ?: return
        userRequestedDisconnect = true
        cancelScheduledReconnect()
        cancelPendingPwdTimeout()
        cancelPersonInfoFallback()
        cancelHistorySync()
        cancelLiveLinkWatchdog()
        cancelConnectAttemptWatchdog()
        connectAttemptStartedAt = 0L
        p1Controller.cancelled = true
        isConnectingVeepoo = false
        isVeepooConnection = false
        passwordHandshakeSucceeded = false
        passwordConfirmInFlight = false
        _isHardwareConnected.value = false
        disconnectGatt()
        markDeviceDisconnected()
        val shown = current.copy(isConnected = false)
        if (incompatibleCardMac != shown.macAddress) {
            incompatibleCardMac = shown.macAddress
            _incompatibleWatch.value = shown
        } else if (_incompatibleWatch.value == null) {
            _incompatibleWatch.value = shown
        }
        _connectedDevice.value = shown
        if (!persist) {
            prefs.edit().remove(PREF_LAST_MAC).remove(PREF_LAST_NAME).apply()
        }
        Log.w(TAG, "Relógio incompatível (${shown.name} ${shown.macAddress}); Veepoo parado.")
        publishSessionPhase()
    }

    @SuppressLint("MissingPermission")
    private fun unbindBond(mac: String) {
        if (!BluetoothAdapter.checkBluetoothAddress(mac) || !hasBlePermissions()) return
        runCatching {
            val remote = bluetoothAdapter?.getRemoteDevice(mac) ?: return
            remote.javaClass.getMethod("removeBond").invoke(remote)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        val previous = currentGatt
        currentGatt = null // Closing may deliver a callback from the abandoned attempt.
        try {
            clearGattQueue()
            previous?.disconnect()
            previous?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            // A platform callback can arrive before connectGatt returns its handle.
            // Queue it after the current main-thread request, then check ownership.
            mainHandler.post { handleConnectionState(gatt, status, newState) }
        }

        @SuppressLint("MissingPermission")
        private fun handleConnectionState(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (gatt == null || gatt !== currentGatt || isVeepooConnection || userRequestedDisconnect) return
            val dev = gatt?.device
            val address = dev?.address ?: _connectedDevice.value?.macAddress ?: ""
            val name = bluetoothDeviceNameOrFallback(dev, _connectedDevice.value?.name ?: "Relógio")

            Log.i(TAG, "onConnectionStateChange -> Device: $name [$address], status: $status, newState: $newState")

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                reconnectAttempt = 0
                _isHardwareConnected.value = true
                val devInfo = (_connectedDevice.value ?: HBandDevice()).copy(
                    deviceId = address,
                    macAddress = address,
                    name = name,
                    isConnected = true
                )
                _connectedDevice.value = devInfo
                persistLastDevice(devInfo)
                HBandBleService.start(context)
                startKeepAliveLoop()

                mainHandler.postDelayed({
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try { gatt?.requestMtu(512) } catch (_: Exception) {}
                    }
                    mainHandler.postDelayed({
                        gatt?.discoverServices()
                    }, 400)
                }, 200)

                startRssiMonitoring(gatt)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT disconnected from $address (status: $status)")
                rssiPollJob?.cancel()
                disconnectGatt()
                _isHardwareConnected.value = false
                markDeviceDisconnected()
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _sessionMessage.value = "Não foi possível conectar ao relógio. Aproxime-o deste aparelho e tente novamente."
                }
                scheduleReconnect(address, name)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            mainHandler.postDelayed({
                gatt?.discoverServices()
            }, 200)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                Log.i(TAG, "GATT Services Discovered on ${gatt.device.address}: ${gatt.services.size} services")
                val discovered = gatt.services.map { it.uuid }.toSet()
                if (discovered.isNotEmpty()) advertisedServices[gatt.device.address] = discovered
                val name = bluetoothDeviceNameOrFallback(gatt.device, _connectedDevice.value?.name.orEmpty())
                when (VeepooDeviceSupport.classify(name, discovered, hasVeepooManufacturer(gatt.device.address))) {
                    VeepooDeviceSupport.Kind.UNSUPPORTED -> {
                        markIncompatible(
                            (_connectedDevice.value ?: HBandDevice()).copy(
                                name = name,
                                macAddress = gatt.device.address,
                                deviceId = gatt.device.address,
                            ),
                            persist = false,
                        )
                        return
                    }
                    VeepooDeviceSupport.Kind.SUPPORTED_VEEPOO -> {
                        // Não cai no broadcast que ligava FC/PPG/SpO₂/PA. Manda o 0x00
                        // e só então solta o GATT, para o firmware sair do modo de teste.
                        val writes = gatt.services.flatMap { service -> service.characteristics }
                            .filter { char ->
                                val props = char.properties
                                (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                            }
                        discoveredWriteCharacteristics.clear()
                        discoveredWriteCharacteristics.addAll(writes)
                        lastRealtimeStopElapsedMs = 0L
                        stopGattRealtimeSensors(gatt)
                        val device = (_connectedDevice.value ?: HBandDevice()).copy(
                            name = name,
                            macAddress = gatt.device.address,
                            deviceId = gatt.device.address,
                        )
                        val startSdk = !isVeepooConnection
                        val flushMs = if (writes.isEmpty()) 0L else 3_200L
                        mainHandler.postDelayed({
                            if (currentGatt === gatt) disconnectGatt()
                            if (startSdk && !isVeepooConnection) connectDeviceViaVeepooSdk(device)
                        }, flushMs)
                        return
                    }
                    VeepooDeviceSupport.Kind.UNKNOWN -> Unit
                }
                discoveredWriteCharacteristics.clear()
                clearGattQueue()

                for (service in gatt.services) {
                    Log.i(TAG, "Service: ${service.uuid} with ${service.characteristics.size} characteristics")
                    for (characteristic in service.characteristics) {
                        val props = characteristic.properties
                        val hasNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        val hasIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        val hasWrite = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        val hasRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0

                        if (hasWrite) {
                            discoveredWriteCharacteristics.add(characteristic)
                        }

                        // Enable notification or indication on EVERY capable characteristic
                        if (hasNotify || hasIndicate) {
                            val isIndicate = hasIndicate && !hasNotify
                            try {
                                gatt.setCharacteristicNotification(characteristic, true)
                                val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
                                if (descriptor != null) {
                                    val descriptorValue = if (isIndicate) {
                                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                    } else {
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    }
                                    enqueueGattOp(GattOp.WriteDesc(gatt, descriptor, descriptorValue))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error registering notify descriptor on ${characteristic.uuid}: ${e.message}")
                            }
                        }

                        // Read device info and battery directly
                        if (hasRead) {
                            when (characteristic.uuid) {
                                BATTERY_LEVEL_CHARACTERISTIC_UUID,
                                MANUFACTURER_NAME_UUID,
                                MODEL_NUMBER_UUID,
                                FIRMWARE_REVISION_UUID,
                                BODY_SENSOR_LOCATION_UUID -> {
                                    enqueueGattOp(GattOp.ReadChar(gatt, characteristic))
                                }
                            }
                        }
                    }
                }

                // If Heart Rate Control Point exists (e.g. Gear S3), start HR sensor
                val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                val hrControlPoint = hrService?.getCharacteristic(HEART_RATE_CONTROL_POINT_UUID)
                if (hrControlPoint != null) {
                    enqueueGattOp(GattOp.WriteChar(gatt, hrControlPoint, byteArrayOf(0x01)))
                }

                // Broadcast HBand/VE30 wake commands if it's a Veepoo device
                broadcastSensorCommands(gatt)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic != null) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: ByteArray(0)
                parseIncomingGattData(gatt, characteristic, data)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseIncomingGattData(gatt, characteristic, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            onGattOpFinished()
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: ByteArray(0)
                parseIncomingGattData(gatt, characteristic, data)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            onGattOpFinished()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                parseIncomingGattData(gatt, characteristic, value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            onGattOpFinished()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.i(TAG, "onDescriptorWrite for ${descriptor?.characteristic?.uuid}, status: $status")
            onGattOpFinished()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectedDevice.value = _connectedDevice.value?.copy(rssi = rssi)
            }
        }
    }

    // Sequential Queue Runner
    @Synchronized
    private fun enqueueGattOp(op: GattOp) {
        gattOpQueue.offer(op)
        if (!isGattOpInProgress) {
            processNextGattOp()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun processNextGattOp() {
        val op = gattOpQueue.poll()
        if (op == null) {
            isGattOpInProgress = false
            return
        }

        isGattOpInProgress = true

        // Timeout safety (700ms)
        gattTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        gattTimeoutRunnable = Runnable {
            Log.w(TAG, "GATT op timed out, proceeding to next")
            onGattOpFinished()
        }
        mainHandler.postDelayed(gattTimeoutRunnable!!, 700)

        mainHandler.post {
            try {
                when (op) {
                    is GattOp.WriteDesc -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            op.gatt.writeDescriptor(op.desc, op.value)
                        } else {
                            @Suppress("DEPRECATION")
                            op.desc.value = op.value
                            @Suppress("DEPRECATION")
                            op.gatt.writeDescriptor(op.desc)
                        }
                    }
                    is GattOp.ReadChar -> {
                        op.gatt.readCharacteristic(op.char)
                    }
                    is GattOp.WriteChar -> {
                        val hasNoResp = (op.char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                        val writeType = if (hasNoResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            op.gatt.writeCharacteristic(op.char, op.value, writeType)
                        } else {
                            @Suppress("DEPRECATION")
                            op.char.value = op.value
                            op.char.writeType = writeType
                            @Suppress("DEPRECATION")
                            op.gatt.writeCharacteristic(op.char)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing GATT op: ${e.message}")
                onGattOpFinished()
            }
        }
    }

    @Synchronized
    private fun onGattOpFinished() {
        gattTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        gattTimeoutRunnable = null
        isGattOpInProgress = false
        processNextGattOp()
    }

    @Synchronized
    private fun clearGattQueue() {
        gattTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        gattTimeoutRunnable = null
        gattOpQueue.clear()
        isGattOpInProgress = false
    }

    @SuppressLint("MissingPermission")
    fun broadcastSensorCommands(gatt: BluetoothGatt?) {
        val activeGatt = gatt ?: currentGatt ?: return
        val mac = activeGatt.device?.address ?: _connectedDevice.value?.macAddress
        if (!VeepooDeviceSupport.maySendVeepooCommand(
                _connectedDevice.value?.name,
                advertisedServices[mac].orEmpty(),
                hasVeepooManufacturer(mac),
            )
        ) return
        if (discoveredWriteCharacteristics.isEmpty()) return

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR) % 100
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)

        // Nunca manda 0x04/0x15/0x20/0x05 com 0x01 aqui: isso liga o sensor e o
        // firmware passa a notificar um pacote a cada ~5 s até receber 0x00.
        val commands = WatchBatteryPolicy.connectStopCommands() + listOf(
            byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00),
            byteArrayOf(0x02, year.toByte(), month.toByte(), day.toByte(), hour.toByte(), min.toByte(), sec.toByte(), 0x00),
        )

        for (target in discoveredWriteCharacteristics) {
            for (cmd in commands) {
                enqueueGattOp(GattOp.WriteChar(activeGatt, target, cmd))
            }
        }
    }

    private fun parseIncomingGattData(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        if (data.isEmpty()) return
        val currentDev = _connectedDevice.value ?: HBandDevice()
        val realMac = gatt?.device?.address ?: currentDev.macAddress
        val realName = bluetoothDeviceNameOrFallback(gatt?.device, currentDev.name)

        val hexString = data.joinToString(" ") { "%02X".format(it) }
        Log.i(TAG, "GATT Packet RX on ${characteristic.uuid} ($realMac): $hexString (len=${data.size})")

        lastHardwareReadTime = System.currentTimeMillis()

        when (characteristic.uuid) {
            // 1. STANDARD BLUETOOTH SIG HEART RATE MEASUREMENT (00002a37) - Samsung Gear S3, WearOS, Garmin, Apple Watch, etc.
            HEART_RATE_MEASUREMENT_UUID -> {
                val flags = data[0].toInt() and 0xFF
                val is16Bit = (flags and 0x01) != 0
                val sensorContactBit = (flags shr 1) and 0x03
                isWristContactDetected = (sensorContactBit != 2) // 2 = no contact
                val hasEnergyExpended = (flags and 0x08) != 0
                val hasRrIntervals = (flags and 0x10) != 0

                var offset = 1
                val hr = if (is16Bit && data.size >= offset + 2) {
                    val v = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
                    offset += 2
                    v
                } else if (data.size >= offset + 1) {
                    val v = data[offset].toInt() and 0xFF
                    offset += 1
                    v
                } else {
                    currentHeartRate
                }

                if (hasEnergyExpended && data.size >= offset + 2) {
                    val energyKcal = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
                    offset += 2
                    if (energyKcal > 0) {
                        currentCalories = energyKcal.toFloat()
                    }
                }

                // Extract real RR-intervals and compute real RMSSD HRV score
                if (hasRrIntervals) {
                    while (offset + 1 < data.size) {
                        val rr = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
                        // RR interval is in 1/1024 seconds -> convert to ms
                        val rrMs = (rr * 1000) / 1024
                        if (rrMs in 300..2000) {
                            rrIntervals.addLast(rrMs)
                            if (rrIntervals.size > 30) rrIntervals.removeFirst()
                        }
                        offset += 2
                    }

                    if (rrIntervals.size >= 5) {
                        // Calculate RMSSD
                        var sumSqDiff = 0.0
                        for (i in 1 until rrIntervals.size) {
                            val diff = (rrIntervals[i] - rrIntervals[i - 1]).toDouble()
                            sumSqDiff += diff * diff
                        }
                        val rmssd = sqrt(sumSqDiff / (rrIntervals.size - 1))
                        // Convert RMSSD ms (typically 20..120ms) to 0..100 HRV score
                        currentHrvScore = (rmssd * 1.2).toInt().coerceIn(40, 99)
                    }
                }

                if (hr in 30..240) {
                    currentHeartRate = hr
                    emitRealTelemetry(realMac, realName)
                    Log.i(TAG, "Parsed REAL Gear S3 / BLE Heart Rate: HR=$hr bpm, WristContact=$isWristContactDetected, HRV=$currentHrvScore")
                }
            }

            // 2. STANDARD RUNNING SPEED AND CADENCE (00002a53) - Gear S3 Realtime Pedometer / Steps
            RSC_MEASUREMENT_UUID -> {
                val flags = data[0].toInt() and 0xFF
                val hasStrideLength = (flags and 0x01) != 0
                val hasTotalDistance = (flags and 0x02) != 0
                val isRunning = (flags and 0x04) != 0

                var offset = 1
                if (data.size >= offset + 2) {
                    // Instantaneous speed (1/256 m/s)
                    val rawSpeed = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
                    offset += 2
                }

                if (data.size >= offset + 1) {
                    // Instantaneous cadence (RPM / steps per minute)
                    val cadence = data[offset].toInt() and 0xFF
                    offset += 1
                    lastRscCadence = cadence
                    if (cadence > 0) {
                        cumulativeRscSteps += (cadence / 30).coerceAtLeast(1)
                        currentSteps = cumulativeRscSteps
                        currentCalories = currentSteps * 0.042f
                        currentDistance = currentSteps * 0.72f
                    }
                }

                if (hasStrideLength && data.size >= offset + 2) {
                    offset += 2
                }

                if (hasTotalDistance && data.size >= offset + 4) {
                    val d0 = data[offset].toLong() and 0xFF
                    val d1 = data[offset + 1].toLong() and 0xFF
                    val d2 = data[offset + 2].toLong() and 0xFF
                    val d3 = data[offset + 3].toLong() and 0xFF
                    val decimeters = (d3 shl 24) or (d2 shl 16) or (d1 shl 8) or d0
                    currentDistance = (decimeters / 10f)
                }

                emitRealTelemetry(realMac, realName)
                Log.i(TAG, "Parsed REAL Gear S3 Cadence & Activity: Cadence=$lastRscCadence RPM, Steps=$currentSteps")
            }

            // 3. STANDARD BATTERY LEVEL (00002a19)
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> {
                val battery = (data[0].toInt() and 0xFF).coerceIn(0, 100)
                setBatteryLevel(battery)
                Log.i(TAG, "Parsed REAL Battery Level: $battery%")
            }

            // 4. DEVICE INFORMATION (00002a24 / 00002a29 / 00002a26)
            MODEL_NUMBER_UUID -> {
                val modelName = String(data).trim()
                if (modelName.isNotBlank()) {
                    _connectedDevice.value = _connectedDevice.value?.copy(name = modelName)
                    Log.i(TAG, "Parsed REAL Device Model: $modelName")
                }
            }
            MANUFACTURER_NAME_UUID -> {
                val mfg = String(data).trim()
                Log.i(TAG, "Parsed REAL Device Manufacturer: $mfg")
            }
            FIRMWARE_REVISION_UUID -> {
                val fw = String(data).trim()
                _connectedDevice.value = _connectedDevice.value?.copy(firmwareVersion = fw)
            }

            // 5. STANDARD HEALTH THERMOMETER (00002a1c)
            TEMPERATURE_MEASUREMENT_UUID -> {
                if (data.size >= 5) {
                    val mantissa = ((data[3].toInt() and 0xFF) shl 16) or ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val rawTemp = mantissa / 100f
                    if (rawTemp in 32.0f..43.0f) {
                        currentTemp = rawTemp
                        emitRealTelemetry(realMac, realName)
                    }
                }
            }

            // 6. STANDARD BLOOD PRESSURE (00002a35)
            BLOOD_PRESSURE_MEASUREMENT_UUID -> {
                if (data.size >= 5) {
                    val sys = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val dia = ((data[4].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                    if (sys in 60..240 && dia in 40..150) {
                        currentSystolic = sys
                        currentDiastolic = dia
                        emitRealTelemetry(realMac, realName)
                    }
                }
            }

            // 7. STANDARD PULSE OXIMETER (00002a5f / 00002a5e)
            PLX_CONTINUOUS_MEASUREMENT_UUID, PLX_SPOT_CHECK_UUID -> {
                if (data.size >= 3) {
                    val spo2 = data[1].toInt() and 0xFF
                    val hr = data[2].toInt() and 0xFF
                    if (spo2 in 70..100) currentSpO2 = spo2
                    if (hr in 30..240) currentHeartRate = hr
                    emitRealTelemetry(realMac, realName)
                }
            }

            // 8. HBAND & VEEPOO PROPRIETARY PROTOCOLS
            HBAND_NOTIFY_CHARACTERISTIC_UUID, VEEPOO_TX_CHAR_UUID -> {
                val header = data[0].toInt() and 0xFF
                val b1 = if (data.size > 1) data[1].toInt() and 0xFF else 0
                val b2 = if (data.size > 2) data[2].toInt() and 0xFF else 0
                val b3 = if (data.size > 3) data[3].toInt() and 0xFF else 0
                val b4 = if (data.size > 4) data[4].toInt() and 0xFF else 0
                val b5 = if (data.size > 5) data[5].toInt() and 0xFF else 0

                when (header) {
                    0x04, 0x15 -> {
                        if (!acceptProprietaryLiveSample(gatt)) return
                        val resolvedHr = when {
                            b1 <= 5 && b2 in 30..240 -> b2
                            b1 in 30..240 && (b2 == 0 || b2 > 65 || data.size == 2) -> b1
                            b2 in 30..240 -> b2
                            b1 in 30..240 -> b1
                            b3 in 30..240 -> b3
                            else -> null
                        }
                        if (resolvedHr != null && resolvedHr > 0) {
                            currentHeartRate = resolvedHr
                        }
                        if (b1 > 5 && b2 in 60..220 && b3 in 40..140) {
                            currentSystolic = b2
                            currentDiastolic = b3
                        }
                        if (data.size > 5 && b5 in 80..100) {
                            currentSpO2 = b5
                        }
                        emitRealTelemetry(realMac, realName)
                    }
                    0x05 -> {
                        if (!acceptProprietaryLiveSample(gatt)) return
                        if (b1 <= 5 && data.size >= 4) {
                            if (b2 in 60..220) currentSystolic = b2
                            if (b3 in 40..140) currentDiastolic = b3
                            if (b4 in 30..240) currentHeartRate = b4
                        }
                        emitRealTelemetry(realMac, realName)
                    }
                    0x20, 0x23 -> {
                        if (!acceptProprietaryLiveSample(gatt)) return
                        if (b2 in 70..100) currentSpO2 = b2
                        if (b3 in 30..240) currentHeartRate = b3
                        emitRealTelemetry(realMac, realName)
                    }
                    0x08, 0x09, 0x40 -> {
                        if (data.size >= 5) {
                            val s1 = data[1].toLong() and 0xFF
                            val s2 = data[2].toLong() and 0xFF
                            val s3 = data[3].toLong() and 0xFF
                            val s4 = data[4].toLong() and 0xFF
                            val steps = ((s1 shl 24) or (s2 shl 16) or (s3 shl 8) or s4).toInt().coerceIn(0, 100000)
                            if (steps > 0) {
                                currentSteps = steps
                                currentCalories = steps * 0.042f
                                currentDistance = steps * 0.72f
                                emitRealTelemetry(realMac, realName)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun emitRealTelemetry(mac: String, model: String) {
        hasReceivedRealSensorData = true
        lastHardwareReadTime = System.currentTimeMillis()
        if (!userRequestedDisconnect) {
            latchLiveHardwareLink()
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        _latestTelemetry.value = liveSnapshot(mac, model, isoFormat.format(Date()), realSensorData = true)
    }

    private fun liveSnapshot(
        mac: String,
        model: String,
        timestamp: String,
        realSensorData: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): HBandTelemetry = HBandTelemetry(
        deviceId = mac,
        deviceModel = model,
        timestamp = timestamp,
        heartRate = currentHeartRate,
        bloodPressure = lastValidBp.resolve(currentSystolic, currentDiastolic, nowMs),
        spO2 = currentSpO2,
        temperatureCelsius = currentTemp,
        steps = currentSteps,
        calories = currentCalories,
        distanceMeters = currentDistance,
        hrvScore = currentHrvScore,
        sleepSummary = SleepSummary(0, 0, 0),
        isRealSensorData = realSensorData,
    )

    @SuppressLint("MissingPermission")
    private fun startRssiMonitoring(gatt: BluetoothGatt?) {
        rssiPollJob?.cancel()
        rssiPollJob = scope.launch(Dispatchers.IO) {
            while (currentGatt != null && _connectedDevice.value?.isConnected == true) {
                delay(WatchBatteryPolicy.RSSI_POLL_MS)
                try {
                    currentGatt?.readRemoteRssi()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading RSSI: ${e.message}")
                }
            }
        }
    }

    // A battery poll is not a sensor measurement; never republish cached vitals with a new time.
    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(keepAliveDelayMs)
                if (_connectedDevice.value?.isConnected == true && currentGatt != null) {
                    // Keep GATT connection active
                    currentGatt?.let { gatt ->
                        val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                        val hrChar = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                        val battService = gatt.getService(BATTERY_SERVICE_UUID)
                        val battChar = battService?.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID)
                        
                        if (battChar != null) {
                            enqueueGattOp(GattOp.ReadChar(gatt, battChar))
                        }
                    }
                }
            }
        }
    }

    fun setPatientId(id: String) {
        currentPatientId = id
    }

    fun setBatteryLevel(level: Int?) {
        val current = _connectedDevice.value ?: return
        val clamped = level?.takeIf { it in 0..100 }
        _connectedDevice.value = current.copy(
            batteryLevel = clamped,
            batteryIsSimulated = false,
        )
    }

    @SuppressLint("MissingPermission")
    fun requestManualSensorRead() {
        currentGatt?.let { gatt ->
            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val hasRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                    if (hasRead) {
                        when (characteristic.uuid) {
                            HEART_RATE_MEASUREMENT_UUID,
                            BATTERY_LEVEL_CHARACTERISTIC_UUID,
                            RSC_MEASUREMENT_UUID,
                            TEMPERATURE_MEASUREMENT_UUID,
                            BLOOD_PRESSURE_MEASUREMENT_UUID,
                            PLX_CONTINUOUS_MEASUREMENT_UUID,
                            PLX_SPOT_CHECK_UUID,
                            MANUFACTURER_NAME_UUID,
                            MODEL_NUMBER_UUID -> {
                                enqueueGattOp(GattOp.ReadChar(gatt, characteristic))
                            }
                        }
                    }
                }
            }
            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            val hrControlPoint = hrService?.getCharacteristic(HEART_RATE_CONTROL_POINT_UUID)
            if (hrControlPoint != null) {
                enqueueGattOp(GattOp.WriteChar(gatt, hrControlPoint, byteArrayOf(0x01)))
            }
            broadcastSensorCommands(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    fun triggerSpotCheck(): HBandTelemetry? {
        if (isVeepooConnection) {
            requestManualMeasurement()
            return _latestTelemetry.value
        }
        requestManualSensorRead()
        // The request is asynchronous. Only a received sample supplies measurement time;
        // preserve the last published sample (or its absence) until a callback replaces it.
        return _latestTelemetry.value
    }

    fun resetBiometricsToZero() {
        hasReceivedRealSensorData = false
        currentHeartRate = 0
        currentSystolic = 0
        currentDiastolic = 0
        currentSpO2 = 0
        currentTemp = 0.0f
        currentSteps = 0
        currentCalories = 0.0f
        currentDistance = 0.0f
        currentHrvScore = 0
        lastValidBp.clear()
        bpDetectRetry.reset()
        rrIntervals.clear()
        cumulativeRscSteps = 0
        lastRscCadence = 0
        lastKnownWorn = null
        _latestTelemetry.value = null
    }

    /** Botão "Sincronizar hora" em Ajustes > Relógio. */
    fun requestWatchTimeSync() {
        if (_watchClockState.value.syncing) return
        if (!hasLiveHardwareSession() || !isVeepooConnection) {
            _watchClockState.value = _watchClockState.value.copy(
                lastResultOk = false, message = "Conecte o relógio para sincronizar a hora.",
            )
            return
        }
        scope.launch(Dispatchers.Main) {
            watchSettingsMutex.withLock {
                if (hasLiveHardwareSession()) writeWatchTime(manual = true)
            }
        }
    }

    /** Chamar com watchSettingsMutex adquirido: não interrompe medições, só enfileira a escrita. */
    private suspend fun writeWatchTime(manual: Boolean) {
        val now = System.currentTimeMillis()
        val f = WatchClock.fields(now, android.text.format.DateFormat.is24HourFormat(context))
        _watchClockState.value = _watchClockState.value.copy(syncing = true, message = null)
        Log.i(TAG, "VE30 hora: enviando settingTime $f (manual=$manual)")
        val code = withTimeoutOrNull(WATCH_TIME_TIMEOUT_MS) {
            suspendCancellableCoroutine<Int> { cont ->
                try {
                    vpManager.settingTime(
                        IBleWriteResponse { w ->
                            if (w != Constants.REQUEST_SUCCESS && cont.isActive) cont.resume(w)
                        },
                        com.veepoo.protocol.listener.data.IResponseListener { r ->
                            if (cont.isActive) cont.resume(r)
                        },
                        com.veepoo.protocol.model.settings.DeviceTimeSetting(
                            f.year, f.month, f.day, f.hour, f.minute, f.second,
                            if (f.is24h) com.veepoo.protocol.model.enums.ETimeMode.MODE_24
                            else com.veepoo.protocol.model.enums.ETimeMode.MODE_12,
                        ),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "VE30 hora: settingTime falhou ao iniciar: ${e.message}")
                    if (cont.isActive) cont.resume(-1)
                }
            }
        }
        // Falhas de escrita GATT chegam como códigos negativos; timeout como null.
        val ok = code != null && code >= 0
        if (ok) {
            val at = now
            prefs.edit().putLong(PREF_WATCH_TIME_SYNC_AT, at).apply()
            _watchClockState.value = WatchClockUiState(
                syncing = false, lastSyncEpochMs = at, lastResultOk = true,
                message = WatchClock.successMessage(now),
            )
            Log.i(TAG, "VE30 hora: sincronizada (resposta=$code)")
        } else {
            _watchClockState.value = _watchClockState.value.copy(
                syncing = false, lastResultOk = false,
                message = "Não foi possível acertar a hora do relógio.",
            )
            Log.w(TAG, "VE30 hora: falhou (resposta=$code)")
        }
    }

    private fun recordAutoMeasure(data: com.veepoo.protocol.model.settings.CustomSettingData?) {
        if (data == null) return
        val state = runCatching {
            WatchAutoMeasureState(
                known = true,
                heartRateAuto = WatchFace.statusToBool(data.autoHeartDetect?.name),
                hrvAuto = WatchFace.statusToBool(data.autoHrv?.name),
            )
        }.getOrNull() ?: return
        _watchAutoMeasureState.value = state
        Log.i(TAG, "VE30 medição automática: FC=${state.heartRateAuto} HRV=${state.hrvAuto}")
    }

    /** Relê os ajustes do relógio (inclui medição automática de FC). Chamar com watchSettingsMutex. */
    private suspend fun readWatchAutoMeasure() {
        val data = withTimeoutOrNull(WATCH_TIME_TIMEOUT_MS) {
            suspendCancellableCoroutine<com.veepoo.protocol.model.settings.CustomSettingData?> { cont ->
                try {
                    vpManager.readCustomSetting(
                        IBleWriteResponse { w -> if (w != Constants.REQUEST_SUCCESS && cont.isActive) cont.resume(null) },
                        ICustomSettingDataListener { d -> if (cont.isActive) cont.resume(d) },
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "VE30 readCustomSetting falhou: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        recordAutoMeasure(data)
    }

    fun requestHistorySync() {
        if (!hasLiveHardwareSession() || !isVeepooConnection) {
            _sessionMessage.value = "Conecte uma pulseira Veepoo para sincronizar o histórico."
            return
        }
        stopPpgSensors()
        stopTemperatureMonitoring()
        hasStartedVeepooSensors = false
        startPostHandshakeSync(includeLiveSensors = true)
    }

    fun setAutoMeasureEnabled(enabled: Boolean) {
        if (_autoMeasureState.value.heartRateConfirmation == WatchSettingConfirmation.PENDING) return
        autoMeasureRevision++
        prefs.edit().putBoolean(PREF_AUTO_MEASURE, enabled)
            .putBoolean(PREF_AUTO_MEASURE_USER_OFF, !enabled).apply()
        _autoMeasureState.value = _autoMeasureState.value.copy(heartRateConfirmation = WatchSettingConfirmation.UNCONFIRMED)
        if (!hasLiveHardwareSession() || !_capabilities.value.isSupportAutoMeasure) return
        val sync = historyClient()
        _autoMeasureState.value = _autoMeasureState.value.copy(heartRateConfirmation = WatchSettingConfirmation.PENDING)
        runWatchSetting(sync, onUnconfirmed = {
            if (_autoMeasureState.value.heartRateConfirmation == WatchSettingConfirmation.PENDING)
                _autoMeasureState.value = _autoMeasureState.value.copy(heartRateConfirmation = WatchSettingConfirmation.UNCONFIRMED)
        }) {
            val updated = sync.setAutoMeasureEnabled(lastAutoMeasureSettings, enabled)
            if (currentSettingsClient(sync) && updated != null) {
                lastAutoMeasureSettings = updated
                applyAutoMeasureUi(updated, null, updateReadTime = false)
            }
        }
    }

    fun setSpo2AutoDetectEnabled(enabled: Boolean) {
        if (_autoMeasureState.value.spo2Confirmation == WatchSettingConfirmation.PENDING) return
        spo2SettingRevision++
        prefs.edit().putBoolean(PREF_SPO2_AUTO, enabled).apply()
        _autoMeasureState.value = _autoMeasureState.value.copy(spo2Confirmation = WatchSettingConfirmation.UNCONFIRMED)
        if (!hasLiveHardwareSession() || !_capabilities.value.isSupportSpo2AutoDetect) return
        val sync = historyClient()
        _autoMeasureState.value = _autoMeasureState.value.copy(spo2Confirmation = WatchSettingConfirmation.PENDING)
        runWatchSetting(sync, onUnconfirmed = {
            if (_autoMeasureState.value.spo2Confirmation == WatchSettingConfirmation.PENDING)
                _autoMeasureState.value = _autoMeasureState.value.copy(spo2Confirmation = WatchSettingConfirmation.UNCONFIRMED)
        }) {
            val result = sync.confirmedSpo2Enabled(sync.setSpo2AutoEnabled(enabled, null))
            if (currentSettingsClient(sync) && result != null) {
                _autoMeasureState.value = _autoMeasureState.value.copy(
                    spo2NightAutoEnabled = result, spo2Confirmation = WatchSettingConfirmation.CONFIRMED,
                )
            }
        }
    }

    fun setWearDetectEnabled(enabled: Boolean) {
        if (_wearDetectState.value.confirmation == WatchSettingConfirmation.PENDING) return
        wearSettingRevision++
        prefs.edit().putBoolean(PREF_WEAR_DETECT, enabled).apply()
        _wearDetectState.value = _wearDetectState.value.copy(confirmation = WatchSettingConfirmation.UNCONFIRMED)
        if (!hasLiveHardwareSession() || !_capabilities.value.isSupportWearDetect) return
        val sync = historyClient()
        _wearDetectState.value = _wearDetectState.value.copy(confirmation = WatchSettingConfirmation.PENDING)
        runWatchSetting(sync, onUnconfirmed = {
            if (_wearDetectState.value.confirmation == WatchSettingConfirmation.PENDING)
                _wearDetectState.value = _wearDetectState.value.copy(confirmation = WatchSettingConfirmation.UNCONFIRMED)
        }) {
            val data = sync.applyWearDetect(enabled)
            val result = sync.confirmedWearEnabled(data)
            if (currentSettingsClient(sync) && result != null) {
                _wearDetectState.value = _wearDetectState.value.copy(
                    enabled = result, lastResult = data!!.checkWearState.name,
                    confirmation = WatchSettingConfirmation.CONFIRMED,
                )
            }
        }
    }

    private fun currentSettingsClient(sync: VeepooHistorySync): Boolean =
        historySync === sync && !sync.cancelled && !userRequestedDisconnect && hasLiveHardwareSession()

    private fun runWatchSetting(sync: VeepooHistorySync, onUnconfirmed: () -> Unit, action: suspend () -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                watchSettingsMutex.withLock {
                    if (currentSettingsClient(sync)) action()
                }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Completion marks the request unconfirmed, without implying rollback. */ }
        }.invokeOnCompletion {
            // Also runs if the scope was cancelled before the coroutine started.
            if (historySync === sync) onUnconfirmed()
        }
    }

    private fun invalidateSettingConfirmations() {
        fun invalidated(status: WatchSettingConfirmation) = if (status == WatchSettingConfirmation.PENDING)
            WatchSettingConfirmation.UNCONFIRMED else WatchSettingConfirmation.UNKNOWN
        _autoMeasureState.value = _autoMeasureState.value.copy(
            heartRateConfirmation = invalidated(_autoMeasureState.value.heartRateConfirmation),
            spo2Confirmation = invalidated(_autoMeasureState.value.spo2Confirmation),
        )
        _wearDetectState.value = _wearDetectState.value.copy(confirmation = invalidated(_wearDetectState.value.confirmation))
    }

    fun startEcgDetect() = startGatedDetect(_capabilities.value.isSupportEcg, _ecgState) {
        val started = p1Controller.startEcg(
            currentConnectedMac(),
            _capabilities.value.isSupportMultiLeadEcg,
            onDetectState(_ecgState),
            ::publishAdvancedSample,
        )
        if (!started) {
            mainHandler.post { endAdvancedDetect() }
        }
    }

    fun stopEcgDetect() {
        p1Controller.stopEcg()
        _ecgState.value = _ecgState.value.copy(running = false, lastSummary = "ECG parado")
        endAdvancedDetect()
    }

    fun readStoredEcg() {
        if (!_capabilities.value.isSupportEcg || !hasLiveHardwareSession()) return
        p1Controller.readStoredEcg(
            currentConnectedMac(),
            onDetectState(_ecgState),
            ::publishAdvancedSample,
        )
    }

    fun startGlucoseDetect() = startGatedDetect(_capabilities.value.isSupportBloodGlucose, _glucoseState) {
        p1Controller.startGlucose(currentConnectedMac(), onDetectState(_glucoseState), ::publishAdvancedSample)
    }

    fun stopGlucoseDetect() {
        p1Controller.stopGlucose()
        _glucoseState.value = _glucoseState.value.copy(running = false, lastSummary = "Glicose interrompida")
        endAdvancedDetect()
    }

    fun startBloodComponentDetect() = startGatedDetect(_capabilities.value.isSupportBloodComponent, _bloodComponentState) {
        p1Controller.startBloodComponent(currentConnectedMac(), onDetectState(_bloodComponentState), ::publishAdvancedSample)
    }

    fun stopBloodComponentDetect() {
        p1Controller.stopBloodComponent()
        _bloodComponentState.value = _bloodComponentState.value.copy(running = false)
        endAdvancedDetect()
    }

    fun startBodyComponentDetect() = startGatedDetect(_capabilities.value.isSupportBodyComponent, _bodyComponentState) {
        p1Controller.startBodyComponent(currentConnectedMac(), onDetectState(_bodyComponentState), ::publishAdvancedSample)
    }

    fun stopBodyComponentDetect() {
        p1Controller.stopBodyComponent()
        _bodyComponentState.value = _bodyComponentState.value.copy(running = false)
        endAdvancedDetect()
    }

    fun startEmotionDetect() = startGatedDetect(_capabilities.value.isSupportEmotion, _emotionState) {
        p1Controller.startEmotion(currentConnectedMac(), onDetectState(_emotionState), ::publishAdvancedSample)
    }

    fun stopEmotionDetect() {
        p1Controller.stopEmotion()
        _emotionState.value = _emotionState.value.copy(running = false)
        endAdvancedDetect()
    }

    fun startFatigueDetect() = startGatedDetect(_capabilities.value.isSupportFatigue, _fatigueState) {
        p1Controller.startFatigue(currentConnectedMac(), onDetectState(_fatigueState), ::publishAdvancedSample)
    }

    fun stopFatigueDetect() {
        p1Controller.stopFatigue()
        _fatigueState.value = _fatigueState.value.copy(running = false)
        endAdvancedDetect()
    }

    fun startBreathDetect() = startGatedDetect(_capabilities.value.isSupportBreath, _breathDetectState) {
        p1Controller.startBreath(currentConnectedMac(), onDetectState(_breathDetectState), ::publishAdvancedSample)
    }

    fun stopBreathDetect() {
        p1Controller.stopBreath()
        _breathDetectState.value = _breathDetectState.value.copy(running = false)
        endAdvancedDetect()
    }

    fun setBandAlarmEnabled(enabled: Boolean) {
        if (!_capabilities.value.isSupportAlarm2 && !_capabilities.value.probed) return
        if (!hasLiveHardwareSession()) return
        p1Controller.setAlarmEnabled(
            _capabilities.value,
            enabled,
            _alarmState.value.hour,
            _alarmState.value.minute,
        ) { _alarmState.value = it.copy(supported = true) }
    }

    fun setHeartWarningEnabled(enabled: Boolean) {
        if (!_capabilities.value.isSupportHeartWarning || !hasLiveHardwareSession()) return
        p1Controller.setHeartWarning(
            _heartWarningState.value.high,
            _heartWarningState.value.low,
            enabled,
        ) { _heartWarningState.value = it.copy(supported = true) }
    }

    fun setLongSeatEnabled(enabled: Boolean) {
        if (!_capabilities.value.isSupportLongSeat || !hasLiveHardwareSession()) return
        p1Controller.setLongSeat(_longSeatState.value, enabled) { _longSeatState.value = it }
    }

    fun setNightTurnEnabled(enabled: Boolean) {
        if (!_capabilities.value.isSupportNightTurnWrist || !hasLiveHardwareSession()) return
        p1Controller.setNightTurn(enabled) { _nightTurnState.value = it.copy(supported = true) }
    }

    fun setFindDeviceEnabled(enabled: Boolean) {
        if (!_capabilities.value.isSupportFindDevice || !hasLiveHardwareSession()) return
        p1Controller.setFindDevice(enabled, _findDeviceState.value) { _findDeviceState.value = it }
    }

    fun startFindDeviceByPhone() {
        if (!_capabilities.value.isSupportFindDeviceByPhone || !hasLiveHardwareSession()) return
        p1Controller.startFindByPhone { _findDeviceState.value = it.copy(findByPhoneSupported = true) }
    }

    fun stopFindDeviceByPhone() {
        p1Controller.stopFindByPhone()
        _findDeviceState.value = _findDeviceState.value.copy(finding = false, summary = "Busca encerrada")
    }

    fun setHealthRemindEnabled(enabled: Boolean) {
        if (!_capabilities.value.isSupportHealthRemind || !hasLiveHardwareSession()) return
        p1Controller.setHealthRemind(enabled) { _healthRemindState.value = it.copy(supported = true) }
    }

    private fun onDetectState(
        target: MutableStateFlow<DetectSessionUiState>,
    ): (DetectSessionUiState) -> Unit = { next ->
        target.value = next.copy(supported = true)
        if (!next.running) {
            mainHandler.post { endAdvancedDetect() }
        }
    }

    private fun startGatedDetect(
        supported: Boolean,
        state: MutableStateFlow<DetectSessionUiState>,
        block: () -> Unit,
    ) {
        if (!beginAdvancedDetect(supported)) {
            state.value = state.value.copy(supported = false, lastError = "Não suportado ou desconectado")
            return
        }
        block()
    }

    private fun beginAdvancedDetect(supported: Boolean): Boolean {
        if (!supported || !hasLiveHardwareSession() || !isVeepooConnection) return false
        advancedDetectActive = true
        cancelPpgNextCycle()
        stopPpgSensors()
        ppgCycleRunning = false
        stopTemperatureMonitoring()
        runCatching { vpManager.stopDetectHeart(IBleWriteResponse {}) }
        activeSpo2Listener?.let { runCatching { vpManager.stopDetectSPO2H(IBleWriteResponse {}, it) } }
        runCatching { vpManager.stopDetectBP(IBleWriteResponse {}, EBPDetectModel.DETECT_MODEL_PUBLIC) }
        activeHrvListener?.let { runCatching { vpManager.stopDetectHrv(BleWriteResponse {}, it) } }
        return true
    }

    private fun endAdvancedDetect() {
        val stillRunning = _ecgState.value.running || _glucoseState.value.running ||
            _bloodComponentState.value.running || _bodyComponentState.value.running ||
            _emotionState.value.running || _fatigueState.value.running || _breathDetectState.value.running
        if (stillRunning) return
        advancedDetectActive = false
        if (!userRequestedDisconnect && isConnectingVeepoo) {
            hasStartedVeepooSensors = false
            startVeepooSensors()
        }
    }

    private fun publishAdvancedSample(entity: com.example.data.local.AdvancedMeasurementEntity) {
        onAdvancedSample(entity)
        if (entity.kind == com.example.data.local.AdvancedMeasurementKind.ECG && entity.numericValue.toInt() in 30..240) {
            currentHeartRate = entity.numericValue.toInt()
            if (entity.secondaryValue > 0f) currentHrvScore = entity.secondaryValue.toInt()
            emitRealTelemetry(currentConnectedMac(), currentConnectedName())
        }
    }

    private fun applyP1CapabilityFlags(caps: DeviceCapabilities) {
        _ecgState.value = _ecgState.value.copy(supported = caps.isSupportEcg)
        _glucoseState.value = _glucoseState.value.copy(supported = caps.isSupportBloodGlucose)
        _bloodComponentState.value = _bloodComponentState.value.copy(supported = caps.isSupportBloodComponent)
        _bodyComponentState.value = _bodyComponentState.value.copy(supported = caps.isSupportBodyComponent)
        _emotionState.value = _emotionState.value.copy(supported = caps.isSupportEmotion)
        _fatigueState.value = _fatigueState.value.copy(supported = caps.isSupportFatigue)
        _breathDetectState.value = _breathDetectState.value.copy(supported = caps.isSupportBreath)
        _alarmState.value = _alarmState.value.copy(supported = caps.isSupportAlarm2, alarm2 = caps.isSupportAlarm2)
        _heartWarningState.value = _heartWarningState.value.copy(supported = caps.isSupportHeartWarning)
        _longSeatState.value = _longSeatState.value.copy(supported = caps.isSupportLongSeat)
        _nightTurnState.value = _nightTurnState.value.copy(supported = caps.isSupportNightTurnWrist)
        _findDeviceState.value = _findDeviceState.value.copy(
            supported = caps.isSupportFindDevice,
            findByPhoneSupported = caps.isSupportFindDeviceByPhone,
        )
        _healthRemindState.value = _healthRemindState.value.copy(supported = caps.isSupportHealthRemind)
    }

    private fun readP1Settings(caps: DeviceCapabilities) {
        if (caps.isSupportAlarm2) p1Controller.readAlarms(caps) { _alarmState.value = it }
        if (caps.isSupportHeartWarning) p1Controller.readHeartWarning { _heartWarningState.value = it.copy(supported = true) }
        if (caps.isSupportLongSeat) p1Controller.readLongSeat { _longSeatState.value = it }
        if (caps.isSupportNightTurnWrist) p1Controller.readNightTurn { _nightTurnState.value = it.copy(supported = true) }
        if (caps.isSupportFindDevice) p1Controller.readFindDevice { _findDeviceState.value = it.copy(findByPhoneSupported = caps.isSupportFindDeviceByPhone) }
        if (caps.isSupportHealthRemind) p1Controller.readHealthRemind { _healthRemindState.value = it.copy(supported = true) }
    }

    private fun startPostHandshakeSync(includeLiveSensors: Boolean = true) {
        invalidateSettingConfirmations()
        postHandshakeJob?.cancel()
        historySync?.cancelled = true
        val sync = VeepooHistorySync(vpManager, mainHandler)
        historySync = sync
        val initialAutoRevision = autoMeasureRevision
        val initialSpo2Revision = spo2SettingRevision
        val initialWearRevision = wearSettingRevision
        postHandshakeJob = scope.launch(Dispatchers.Main) {
            val queryContext = currentCoroutineContext()
            val queryDeviceId = currentConnectedMac()
            val queryDeviceModel = currentConnectedName()
            try {
                if (includeLiveSensors && WatchBatteryPolicy.CONTINUOUS_TEMPERATURE) {
                    startTemperatureMonitoring()
                }
                var caps = _capabilities.value
                if (!caps.probed) {
                    caps = VeepooCapabilityProbe.fromManager(vpManager, lastFunctionSupport)
                    _capabilities.value = caps
                }
                _autoMeasureState.value = _autoMeasureState.value.copy(
                    supported = caps.isSupportAutoMeasure,
                    spo2AutoSupported = caps.isSupportSpo2AutoDetect,
                )
                _wearDetectState.value = _wearDetectState.value.copy(supported = caps.isSupportWearDetect)
                applyP1CapabilityFlags(caps)

                val extras = watchSettingsMutex.withLock {
                    if (historySync !== sync || sync.cancelled || userRequestedDisconnect) return@launch
                    sync.readHandshakeExtras(
                        caps = caps,
                        wearEnabled = prefs.getBoolean(PREF_WEAR_DETECT, true),
                        onBattery = { setBatteryLevel(it) },
                    )
                }
                if (historySync !== sync || sync.cancelled || !queryContext.isActive || userRequestedDisconnect) return@launch
                extras.batteryPercent?.let { setBatteryLevel(it) }
                extras.sport?.let { reading ->
                    currentSteps = reading.steps ?: 0
                    currentCalories = reading.calories ?: 0f
                    currentDistance = reading.distanceMeters ?: 0f
                    onSportReading(queryDeviceId, reading)
                    // Core requires HR. When a live HR already exists, enqueue the
                    // updated steps with the current HR/SpO2 instead of Room-only counters.
                    if (IngestPayloadMapper.isIngestibleHeartRate(currentHeartRate)) {
                        emitRealTelemetry(queryDeviceId, queryDeviceModel)
                    }
                }
                // An older handshake snapshot cannot overwrite a later explicit request, even
                // after that request has completed and is no longer pending.
                val auto = if (initialAutoRevision == autoMeasureRevision && _autoMeasureState.value.heartRateConfirmation != WatchSettingConfirmation.PENDING) extras.autoMeasure else emptyList()
                if (auto.isNotEmpty()) lastAutoMeasureSettings = auto
                val spo2 = if (initialSpo2Revision == spo2SettingRevision && _autoMeasureState.value.spo2Confirmation != WatchSettingConfirmation.PENDING) sync.confirmedSpo2Enabled(extras.spo2Auto) else null
                applyAutoMeasureUi(auto, spo2)
                val wearEnabled = if (initialWearRevision == wearSettingRevision && _wearDetectState.value.confirmation != WatchSettingConfirmation.PENDING) sync.confirmedWearEnabled(extras.wear) else null
                if (wearEnabled != null) {
                    _wearDetectState.value = _wearDetectState.value.copy(
                        supported = true, enabled = wearEnabled,
                        lastResult = extras.wear!!.checkWearState.name,
                        confirmation = WatchSettingConfirmation.CONFIRMED,
                    )
                }
                readP1Settings(caps)
                if (sync.cancelled || userRequestedDisconnect) return@launch
                watchSettingsMutex.withLock {
                    if (historySync === sync && !sync.cancelled && !userRequestedDisconnect && isVeepooConnection) {
                        applyBatterySafeAutoMeasure(sync)
                        enforceContinuousMonitoringOff(sync, caps)
                    }
                }
                if (sync.cancelled || userRequestedDisconnect) return@launch
                val pull = sync.pullHistory(
                    caps = caps,
                    deviceId = queryDeviceId,
                    deviceModel = queryDeviceModel,
                    onProgress = {
                        if (historySync === sync && !sync.cancelled && queryContext.isActive) {
                            _historySyncState.value = it
                        }
                    },
                )
                if (historySync !== sync || sync.cancelled || !queryContext.isActive || userRequestedDisconnect) return@launch
                _historySyncState.value = pull.state
                lastKnownWorn = pull.samples.mapNotNull { it.worn }.lastOrNull() ?: lastKnownWorn
                _wearDetectState.value = _wearDetectState.value.copy(lastWorn = lastKnownWorn)
                val telemetries = pull.samples.map { it.telemetry }
                if (telemetries.isNotEmpty()) {
                    onHistorySamples(telemetries)
                    lastValidBp.rememberFromHistory(telemetries)
                    // History retains its original measurement times in the repository.
                    // Never re-emit interval counters or old vitals as live measurements.
                    // Last valid BP may still ride along on later HR-only live snapshots.
                }
                Log.i(TAG, "Histórico Veepoo: ${historyReadStatusText(pull.state)}")
                // Acerta a hora do VE30 a cada conexão: depois do histórico (não atrasa a leitura)
                // e antes dos sensores ao vivo, serializado com as demais escritas de ajustes.
                watchSettingsMutex.withLock {
                    if (historySync === sync && !sync.cancelled && !userRequestedDisconnect && isVeepooConnection) {
                        writeWatchTime(manual = false)
                        readWatchAutoMeasure()
                        applyBatterySafeAutoMeasure(sync)
                        enforceContinuousMonitoringOff(sync, caps)
                    }
                }
            } catch (e: CancellationException) {
                // An abandoned query must not overwrite a newer session or restart its sensors.
                if (historySync === sync && _historySyncState.value.isRunning) {
                    _historySyncState.value = _historySyncState.value.copy(isRunning = false, phase = "cancelado")
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Falha no sync P0 pós-handshake: ${e.message}", e)
                _historySyncState.value = _historySyncState.value.copy(
                    isRunning = false,
                    lastError = e.message,
                    phase = "error",
                )
            } finally {
                if (includeLiveSensors && historySync === sync && !sync.cancelled &&
                    currentCoroutineContext().isActive && !userRequestedDisconnect && isConnectingVeepoo) {
                    startVeepooSensors()
                }
            }
        }
    }

    private fun applyAutoMeasureUi(items: List<AutoMeasureData>, spo2Night: Boolean?, updateReadTime: Boolean = true) {
        val pulse = items.firstOrNull { it.funType == EAutoMeasureType.PULSE_RATE }
        val before = _autoMeasureState.value
        _autoMeasureState.value = before.copy(
            supported = _capabilities.value.isSupportAutoMeasure,
            spo2AutoSupported = _capabilities.value.isSupportSpo2AutoDetect,
            heartRateEnabled = pulse?.isSwitchOpen ?: before.heartRateEnabled,
            spo2NightAutoEnabled = spo2Night ?: before.spo2NightAutoEnabled,
            lastReadAtMs = if (updateReadTime && (pulse != null || spo2Night != null)) System.currentTimeMillis() else before.lastReadAtMs,
            summary = if (items.isNotEmpty()) items.joinToString { "${it.funType}=${it.isSwitchOpen}" } else before.summary,
            heartRateConfirmation = if (pulse != null) WatchSettingConfirmation.CONFIRMED else before.heartRateConfirmation,
            spo2Confirmation = if (spo2Night != null) WatchSettingConfirmation.CONFIRMED else before.spo2Confirmation,
        )
        pulse?.let { prefs.edit().putBoolean(PREF_AUTO_MEASURE, it.isSwitchOpen).apply() }
    }

    private fun liveScanBatteryLevel(isCurrent: Boolean): Int? {
        val current = _connectedDevice.value
        if (!isCurrent || current == null || current.batteryIsSimulated) return null
        return current.batteryLevel
    }

    private fun markDeviceDisconnected() {
        cancelSdkBatteryPolling()
        _connectedDevice.value = _connectedDevice.value?.withUnknownBattery(connected = false)
    }

    private fun cancelSdkBatteryPolling() {
        batteryPollJob?.cancel()
        batteryPollJob = null
    }

    private fun startSdkBatteryPolling() {
        if (!isVeepooConnection || userRequestedDisconnect) return
        batteryPollJob?.cancel()
        batteryPollJob = scope.launch(Dispatchers.Main) {
            while (true) {
                delay(SDK_BATTERY_POLL_MS)
                if (userRequestedDisconnect || !_isHardwareConnected.value) return@launch
                refreshSdkBattery()
            }
        }
    }

    private fun refreshSdkBattery() {
        if (!isVeepooConnection || userRequestedDisconnect) return
        vpManager.readBattery(
            IBleWriteResponse { },
            IBatteryDataListener { data ->
                val percent = VeepooBatteryMapper.fromSdk(data)
                if (percent != null) {
                    setBatteryLevel(percent)
                    Log.i(TAG, "SDK battery: $percent%")
                } else {
                    Log.w(TAG, "SDK battery unmapped: $data")
                }
            },
        )
    }

    private fun historyClient(): VeepooHistorySync {
        val existing = historySync
        if (existing != null && !existing.cancelled) return existing
        return VeepooHistorySync(vpManager, mainHandler).also { historySync = it }
    }

    private fun cancelHistorySync() {
        cancelHistoryPoll()
        invalidateSettingConfirmations()
        historySync?.cancelled = true
        postHandshakeJob?.cancel()
        postHandshakeJob = null
        if (_historySyncState.value.isRunning) {
            _historySyncState.value = _historySyncState.value.copy(isRunning = false, phase = "cancelado")
        }
    }

    fun createTelemetrySnapshot(device: HBandDevice): HBandTelemetry {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return liveSnapshot(
            mac = device.macAddress,
            model = device.name,
            timestamp = isoFormat.format(Date()),
            realSensorData = hasReceivedRealSensorData,
        )
    }

    private val bondStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: android.content.Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            if (!hasBlePermissions()) return
            val remote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val target = _connectedDevice.value?.macAddress ?: return
            if (!remote.address.equals(target, ignoreCase = true)) return
            val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
            val next = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            handleBondState(previous, next)
        }
    }

    // Kotlin initializes properties in declaration order. Restoring a saved watch
    // calls stopScanning and uses gattCallback, so their fields must exist first.
    init {
        VeepooSdkToasts.suppressFunctionNotSupported()
        vpManager.init(context.applicationContext)
        vpManager.setAutoConnectBTBySdk(false)
        resetLeftoverSdkConnect()
        vpManager.registerBluetoothStateListener(object : IABluetoothStateListener() {
            override fun onBluetoothStateChanged(openOrClosed: Boolean) {
                Log.i(TAG, "Bluetooth do sistema (Veepoo SDK): ${if (openOrClosed) "ligado" else "desligado"}")
                if (!openOrClosed && isConnectingVeepoo && !_isHardwareConnected.value) {
                    abortHungVeepooConnect(scheduleRetry = isAutoReconnectEnabled)
                }
            }
        })
        registerBondStateReceiver()
        checkBondedOrAutoConnect()
        publishSessionPhase()
    }

    private fun registerBondStateReceiver() {
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext,
                bondStateReceiver,
                android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "Receiver de emparelhamento não registrado: ${it.message}") }
    }

    private fun handleBondState(previous: Int, next: Int) {
        val decision = VeepooBondPolicy.onBondState(previous, next, bondNoneCycles)
        bondNoneCycles = decision.noneCycles
        Log.i(TAG, "VE30 bond: $previous->$next cycles=$bondNoneCycles action=${decision.action}")
        if (decision.action != VeepooBondPolicy.Action.ABORT_CONNECT) return
        if (userRequestedDisconnect || _isHardwareConnected.value) return
        _sessionMessage.value = "Não foi possível emparelhar a pulseira. Nova tentativa em instantes."
        abortHungVeepooConnect(scheduleRetry = false)
        val device = _connectedDevice.value ?: return
        cancelScheduledReconnect()
        reconnectAttempt = VeepooReconnectPolicy.nextAttemptIndex(reconnectAttempt)
        val runnable = Runnable {
            if (!isAutoReconnectEnabled || userRequestedDisconnect || _isHardwareConnected.value) return@Runnable
            connectDevice(device.copy(isConnected = false, firmwareVersion = "Reconexão"))
        }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, VeepooReconnectPolicy.BOND_ABORT_BACKOFF_MS)
        publishSessionPhase()
    }
}
