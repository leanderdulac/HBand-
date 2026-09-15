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
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.ingest.IngestPayloadMapper
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _latestTelemetry = MutableStateFlow<HBandTelemetry?>(null)
    val latestTelemetry: StateFlow<HBandTelemetry?> = _latestTelemetry.asStateFlow()

    // Real hardware connection state
    private val _isHardwareConnected = MutableStateFlow(false)
    val isHardwareConnected: StateFlow<Boolean> = _isHardwareConnected.asStateFlow()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hband_settings", Context.MODE_PRIVATE)

    var isAutoReconnectEnabled: Boolean = prefs.getBoolean(PREF_AUTO_RECONNECT, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(PREF_AUTO_RECONNECT, value).apply()
        }
    var currentPatientId: String = IngestPayloadMapper.DEFAULT_PATIENT_ID

    private val _sessionMessage = MutableStateFlow<String?>(null)
    val sessionMessage: StateFlow<String?> = _sessionMessage.asStateFlow()

    private val _capabilities = MutableStateFlow(DeviceCapabilities())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private val _autoMeasureState = MutableStateFlow(
        AutoMeasureUiState(
            heartRateEnabled = prefs.getBoolean(PREF_AUTO_MEASURE, true),
            spo2NightAutoEnabled = prefs.getBoolean(PREF_SPO2_AUTO, true),
        )
    )
    val autoMeasureState: StateFlow<AutoMeasureUiState> = _autoMeasureState.asStateFlow()

    private val _wearDetectState = MutableStateFlow(
        WearDetectUiState(enabled = prefs.getBoolean(PREF_WEAR_DETECT, true))
    )
    val wearDetectState: StateFlow<WearDetectUiState> = _wearDetectState.asStateFlow()

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

    private var userRequestedDisconnect = false
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
    private var activeScanCallback: ScanCallback? = null
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
    // (triggerSpotCheck / simulador) para a UI não rotular demo como "leitura ao vivo".
    private var hasReceivedRealSensorData = false

    // SDK oficial Veepoo/HBand (VPOperateManager) — usado para VE30/HBand reais, que exigem
    // o handshake proprietário (senha + syncPersonInfo) antes de liberar os sensores. GATT
    // genérico não decodifica o protocolo Veepoo (confirmado nos logs de campo: pacotes
    // reais chegam em características não documentadas e nunca mudam, pois o relógio nunca
    // recebeu o comando de start real).
    private val vpManager: VPOperateManager = VPOperateManager.getInstance()
    private var isVeepooConnection = false
    // Sem esta guarda, um duplo-toque (ou recomposição da UI) em "Conectar" chamava
    // connectDeviceViaVeepooSdk duas ou mais vezes antes do handshake anterior terminar,
    // registrando múltiplos connectDevice/notify em paralelo no mesmo MAC — confirmado em
    // campo: isso derruba a conexão BLE do VE30 (colisão de comandos no firmware).
    private var isConnectingVeepoo = false
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
    private var activeSpo2Listener: ISpo2hDataListener? = null
    private var activeHrvListener: IHrvDetectListener? = null
    private var activeTempListener: ITemptureDetectDataListener? = null
    private var liveLinkWatchdog: Runnable? = null
    private var lastFunctionSupport: FunctionDeviceSupportData? = null
    private var lastAutoMeasureSettings: List<AutoMeasureData> = emptyList()
    private var historySync: VeepooHistorySync? = null
    private val p1Controller = VeepooP1Controller(vpManager)
    private var postHandshakeJob: Job? = null
    private var lastKnownWorn: Boolean? = null
    private var advancedDetectActive = false

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
        const val PREF_SPO2_AUTO = "spo2_night_auto"
        const val PREF_WEAR_DETECT = "wear_detect_enabled"
        private const val MAX_RECONNECT_ATTEMPTS = 8

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
        private const val PPG_STAGE_GAP_MS = 400L
        // VE30 can fire STATUS_DISCONNECTED while HeartData still streams.
        // Keep the live latch until packets go quiet for this window.
        private const val LIVE_LINK_STALE_MS = 8_000L

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
        if (userRequestedDisconnect || _isHardwareConnected.value) return
        val mac = prefs.getString(PREF_LAST_MAC, null)?.trim().orEmpty()
        if (mac.isEmpty() || !BluetoothAdapter.checkBluetoothAddress(mac)) return
        val name = prefs.getString(PREF_LAST_NAME, null)?.takeIf { it.isNotBlank() } ?: "VE30"
        connectDevice(
            HBandDevice(
                deviceId = mac,
                name = name,
                macAddress = mac,
                batteryLevel = _connectedDevice.value?.batteryLevel ?: 0,
                rssi = _connectedDevice.value?.rssi ?: 0,
                isConnected = false,
                firmwareVersion = "Reconexão"
            )
        )
    }

    init {
        vpManager.init(context.applicationContext)
        vpManager.setAutoConnectBTBySdk(false)
        vpManager.registerBluetoothStateListener(object : IABluetoothStateListener() {
            override fun onBluetoothStateChanged(openOrClosed: Boolean) {
                Log.i(TAG, "Bluetooth do sistema (Veepoo SDK): ${if (openOrClosed) "ligado" else "desligado"}")
            }
        })
        checkBondedOrAutoConnect()
    }

    @SuppressLint("MissingPermission")
    fun checkBondedOrAutoConnect() {
        val adapter = bluetoothAdapter
        if (hasBlePermissions() && adapter != null && adapter.isEnabled) {
            try {
                val bonded = adapter.bondedDevices ?: emptySet()
                val bondedList = mutableListOf<HBandDevice>()

                for (dev in bonded) {
                    val name = dev.name ?: "Dispositivo Pareado"
                    val address = dev.address ?: continue
                    val hDev = HBandDevice(
                        deviceId = address,
                        name = name,
                        macAddress = address,
                        batteryLevel = 90,
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
                    Log.i(TAG, "Reconectando à última pulseira persistida...")
                    reconnectLastDevice()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking bonded devices: ${e.message}")
            }
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

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        discoveredMap.clear()

        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner

        if (hasBlePermissions() && adapter != null && adapter.isEnabled && scanner != null) {
            Log.i(TAG, "Starting REAL BLE hardware scan...")
            try {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.device?.let { device ->
                            val address = device.address ?: return@let
                            val rawName = device.name ?: result.scanRecord?.deviceName ?: ""
                            val displayName = if (rawName.isNotBlank()) rawName else "Dispositivo BLE ($address)"
                            val isCurrent = _connectedDevice.value?.macAddress.equals(address, ignoreCase = true) && _connectedDevice.value?.isConnected == true

                            val hbandDevice = HBandDevice(
                                deviceId = address,
                                name = displayName,
                                macAddress = address,
                                rssi = result.rssi,
                                isConnected = isCurrent,
                                batteryLevel = if (isCurrent) (_connectedDevice.value?.batteryLevel ?: 90) else 90,
                                firmwareVersion = "BLE Real"
                            )
                            discoveredMap[address] = hbandDevice
                            _scannedDevices.value = discoveredMap.values.toList()
                        }
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        results?.forEach { res ->
                            res.device?.let { dev ->
                                val address = dev.address ?: return@let
                                val rawName = dev.name ?: res.scanRecord?.deviceName ?: ""
                                val displayName = if (rawName.isNotBlank()) rawName else "Dispositivo BLE ($address)"
                                val isCurrent = _connectedDevice.value?.macAddress.equals(address, ignoreCase = true) && _connectedDevice.value?.isConnected == true

                                discoveredMap[address] = HBandDevice(
                                    deviceId = address,
                                    name = displayName,
                                    macAddress = address,
                                    rssi = res.rssi,
                                    isConnected = isCurrent,
                                    batteryLevel = 90,
                                    firmwareVersion = "BLE Real"
                                )
                            }
                        }
                        _scannedDevices.value = discoveredMap.values.toList()
                    }

                    override fun onScanFailed(errorCode: Int) {
                        Log.e(TAG, "BLE scan failed with error code: $errorCode")
                        _isScanning.value = false
                    }
                }

                activeScanCallback = callback
                scanner.startScan(null, scanSettings, callback)

                // Auto stop scan after 12 seconds
                mainHandler.postDelayed({
                    stopScanning()
                }, 12000)

            } catch (e: Exception) {
                Log.e(TAG, "Exception starting BLE scan: ${e.message}", e)
                _isScanning.value = false
            }
        } else {
            Log.w(TAG, "BLE hardware scanner unavailable")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!_isScanning.value) return
        _isScanning.value = false
        try {
            activeScanCallback?.let {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
        activeScanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: HBandDevice) {
        userRequestedDisconnect = false
        // Relógios Gear/WearOS/genéricos falam Bluetooth SIG padrão (Heart Rate Service etc.)
        // via GATT direto. VE30/HBand (e qualquer coisa não explicitamente "Gear") usam o
        // protocolo proprietário Veepoo, que exige o SDK oficial para o handshake de senha.
        val looksLikeGenericBleWatch = device.name.contains("Gear", ignoreCase = true) ||
            device.name.contains("WearOS", ignoreCase = true) ||
            device.name.contains("Galaxy Watch", ignoreCase = true)

        if (looksLikeGenericBleWatch) {
            connectDeviceViaRawGatt(device)
        } else {
            connectDeviceViaVeepooSdk(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDeviceViaRawGatt(device: HBandDevice) {
        isVeepooConnection = false
        stopScanning()
        disconnectGatt()

        // Clear previous cache to ensure NO fake data is presented
        resetBiometricsToZero()

        val adapter = bluetoothAdapter
        val isHardwareAvailable = hasBlePermissions() && adapter != null && adapter.isEnabled
        val isValidMac = try {
            BluetoothAdapter.checkBluetoothAddress(device.macAddress)
        } catch (e: Exception) {
            false
        }

        if (isHardwareAvailable && isValidMac) {
            Log.i(TAG, "Connecting REAL Bluetooth GATT to device: ${device.name} [${device.macAddress}]...")
            try {
                val remoteDevice = adapter!!.getRemoteDevice(device.macAddress)
                _connectedDevice.value = device.copy(
                    deviceId = remoteDevice.address,
                    macAddress = remoteDevice.address,
                    name = remoteDevice.name ?: device.name,
                    isConnected = true
                )
                _isHardwareConnected.value = true

                currentGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    remoteDevice.connectGatt(context, false, gattCallback)
                }

                persistLastDevice(_connectedDevice.value ?: device)
                HBandBleService.start(context)
                
                // Real initial empty telemetry snapshot (waiting for sensor)
                _latestTelemetry.value = createTelemetrySnapshot(_connectedDevice.value!!)
                startKeepAliveLoop(_connectedDevice.value!!)
                return
            } catch (e: Exception) {
                Log.e(TAG, "GATT connect failed: ${e.message}", e)
            }
        }

        Log.i(TAG, "Connecting device placeholder: ${device.name} [${device.macAddress}]")
        _connectedDevice.value = device.copy(isConnected = true)
        _isHardwareConnected.value = false
        _latestTelemetry.value = createTelemetrySnapshot(device)
    }

    /**
     * Conecta a um VE30/HBand real usando o SDK oficial Veepoo (VPOperateManager), que
     * implementa o handshake proprietário (senha + syncPersonInfo) exigido pelo firmware
     * antes de liberar qualquer sensor. GATT genérico não decodifica esse protocolo.
     */
    @SuppressLint("MissingPermission")
    private fun connectDeviceViaVeepooSdk(device: HBandDevice) {
        if (isConnectingVeepoo) {
            Log.d(TAG, "Conexão Veepoo já em andamento, ignorando toque duplicado em Conectar.")
            return
        }
        isConnectingVeepoo = true
        isVeepooConnection = true
        isSyncingPersonInfo = false
        hasStartedVeepooSensors = false
        advancedDetectActive = false
        p1Controller.stopAllDetect()
        cancelLiveLinkWatchdog()
        cancelHistorySync()
        _historySyncState.value = HistorySyncUiState()
        ppgStageGeneration++
        stopScanning()
        resetBiometricsToZero()

        val isValidMac = try {
            BluetoothAdapter.checkBluetoothAddress(device.macAddress)
        } catch (e: Exception) {
            false
        }

        if (!isValidMac) {
            Log.w(TAG, "MAC inválido para conexão Veepoo, usando placeholder: ${device.macAddress}")
            isConnectingVeepoo = false
            _connectedDevice.value = device.copy(isConnected = true)
            _isHardwareConnected.value = false
            _latestTelemetry.value = createTelemetrySnapshot(device)
            return
        }

        Log.i(TAG, "Conectando via SDK Veepoo/HBand ao VE30: ${device.name} [${device.macAddress}]...")
        _connectedDevice.value = device.copy(isConnected = false)
        persistLastDevice(device)
        HBandBleService.start(context)

        registerVeepooStatusListener(device.macAddress)

        vpManager.connectDevice(
            device.macAddress,
            device.name,
            IConnectResponse { code, _, _ ->
                if (code != Constants.REQUEST_SUCCESS) {
                    Log.e(TAG, "Falha ao conectar via Veepoo SDK (code=$code)")
                    isConnectingVeepoo = false
                    _sessionMessage.value = "Falha ao conectar ao VE30 (código $code)."
                    scheduleReconnect(device.macAddress, device.name)
                }
            },
            INotifyResponse { state ->
                if (state == Constants.REQUEST_SUCCESS) {
                    _isHardwareConnected.value = true
                    reconnectAttempt = 0
                    _connectedDevice.value = _connectedDevice.value?.copy(isConnected = true)
                    persistLastDevice(_connectedDevice.value ?: device)
                    confirmVeepooPassword()
                } else {
                    Log.e(TAG, "Falha ao ativar notificações Veepoo (state=$state)")
                    isConnectingVeepoo = false
                    _sessionMessage.value = "Falha ao ativar notificações do VE30."
                    scheduleReconnect(device.macAddress, device.name)
                }
            },
        )
    }

    private fun confirmVeepooPassword() {
        Log.i(TAG, "Autenticando senha padrão no VE30...")
        vpManager.confirmDevicePwd(
            IBleWriteResponse { code ->
                if (code != Constants.REQUEST_SUCCESS) {
                    Log.e(TAG, "Falha ao escrever comando de senha (code=$code)")
                }
            },
            object : IPwdDataListener {
                override fun onPwdDataChange(pwdData: PwdData) {
                    Log.i(TAG, "Senha confirmada no VE30. Nº ${pwdData.deviceNumber} v${pwdData.deviceVersion}")
                }

                override fun onConnectionConfirmTimeout() {
                    Log.e(TAG, "Timeout na confirmação de senha do VE30.")
                    isConnectingVeepoo = false
                    _isHardwareConnected.value = false
                    _sessionMessage.value = "Tempo esgotado ao confirmar a senha do VE30. Tente reconectar."
                    val device = _connectedDevice.value
                    if (device != null) {
                        scheduleReconnect(device.macAddress, device.name)
                    }
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
                Log.i(TAG, "Configurações do VE30 confirmadas: $customSettingData")
                syncVeepooPersonInfo()
            },
            DEFAULT_VEEPOO_PWD,
            true,
        )
    }

    private fun syncVeepooPersonInfo() {
        if (isSyncingPersonInfo) {
            Log.d(TAG, "syncPersonInfo já em andamento, ignorando chamada duplicada do firmware.")
            return
        }
        isSyncingPersonInfo = true
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
                    startPostHandshakeSync()
                } else {
                    Log.e(TAG, "Falha ao sincronizar perfil biométrico: $status")
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
        startTemperatureMonitoring()
        runHeartStage()
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
                if (generation != ppgStageGeneration) return@IHeartDataListener
                latchLiveHardwareLink()
                if (heart.data in 30..240) {
                    currentHeartRate = heart.data
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
            if (generation != ppgStageGeneration) return@ISpo2hDataListener
            latchLiveHardwareLink()
            if (data.value in 50..100) {
                currentSpO2 = data.value
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
        val mac = currentConnectedMac()
        val name = currentConnectedName()
        Log.i(TAG, "[PPG] Estágio PA...")
        vpManager.startDetectBP(
            IBleWriteResponse { code -> Log.i(TAG, "startDetectBP ACK code=$code") },
            IBPDetectDataListener { data ->
                Log.i(TAG, "onDataChange(BpData): ${data.highPressure}/${data.lowPressure} (progress=${data.progress})")
                if (generation != ppgStageGeneration) return@IBPDetectDataListener
                latchLiveHardwareLink()
                if (data.highPressure in 60..240 && data.lowPressure in 30..160) {
                    currentSystolic = data.highPressure
                    currentDiastolic = data.lowPressure
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
                if (generation != ppgStageGeneration) return
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
            // Reinicia o revezamento para manter FC/SpO2/PA atualizados continuamente.
            mainHandler.postDelayed({ runHeartStage() }, PPG_STAGE_GAP_MS)
        }, HRV_STAGE_TIMEOUT_MS)
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
        cancelScheduledReconnect()
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
            isSyncingPersonInfo = false
            advancedDetectActive = false
            ppgStageGeneration++
            _isHardwareConnected.value = false
            _connectedDevice.value = _connectedDevice.value?.copy(isConnected = false)
            scheduleReconnect(address, name)
        }
        liveLinkWatchdog = runnable
        mainHandler.postDelayed(runnable, LIVE_LINK_STALE_MS)
    }

    private fun persistLastDevice(device: HBandDevice) {
        val mac = device.macAddress.trim()
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) return
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
                    }
                    Constants.STATUS_DISCONNECTED -> {
                        Log.w(TAG, "Veepoo GATT desconectado: $address")
                        if (userRequestedDisconnect) {
                            isConnectingVeepoo = false
                            hasStartedVeepooSensors = false
                            isSyncingPersonInfo = false
                            cancelHistorySync()
                            p1Controller.stopAllDetect()
                            advancedDetectActive = false
                            ppgStageGeneration++
                            cancelLiveLinkWatchdog()
                            _isHardwareConnected.value = false
                            _connectedDevice.value = _connectedDevice.value?.copy(isConnected = false)
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
                        hasStartedVeepooSensors = false
                        isSyncingPersonInfo = false
                        cancelHistorySync()
                        p1Controller.stopAllDetect()
                        advancedDetectActive = false
                        ppgStageGeneration++
                        _isHardwareConnected.value = false
                        _connectedDevice.value = _connectedDevice.value?.copy(isConnected = false)
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

    private fun scheduleReconnect(address: String, name: String) {
        if (userRequestedDisconnect || !isAutoReconnectEnabled) return
        if (address.isBlank() || !BluetoothAdapter.checkBluetoothAddress(address)) return
        if (_isHardwareConnected.value) return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            _sessionMessage.value = "Não foi possível reconectar à pulseira após várias tentativas."
            return
        }
        cancelScheduledReconnect()
        reconnectAttempt++
        val notWorn = _wearDetectState.value.enabled && lastKnownWorn == false
        val shift = if (notWorn) 3 else 0
        val capMs = if (notWorn) 120_000L else 60_000L
        val delayMs = (2_000L * (1L shl (reconnectAttempt - 1 + shift).coerceAtMost(6))).coerceAtMost(capMs)
        Log.i(
            TAG,
            "Auto-reconnect attempt $reconnectAttempt in ${delayMs}ms to $address " +
                "(worn=$lastKnownWorn wearDetect=${_wearDetectState.value.enabled})",
        )
        val runnable = Runnable {
            if (userRequestedDisconnect || _isHardwareConnected.value) return@Runnable
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
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        userRequestedDisconnect = true
        cancelScheduledReconnect()
        cancelLiveLinkWatchdog()
        cancelHistorySync()
        p1Controller.stopAllDetect()
        advancedDetectActive = false
        ppgStageGeneration++
        stopTemperatureMonitoring()
        isConnectingVeepoo = false
        isSyncingPersonInfo = false
        hasStartedVeepooSensors = false
        if (isVeepooConnection) {
            vpManager.disconnectWatch(IBleWriteResponse {})
        } else {
            disconnectGatt()
        }
        keepAliveJob?.cancel()
        rssiPollJob?.cancel()
        _isHardwareConnected.value = false
        _connectedDevice.value = _connectedDevice.value?.copy(isConnected = false)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        try {
            clearGattQueue()
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT: ${e.message}")
        }
        currentGatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val dev = gatt?.device
            val address = dev?.address ?: _connectedDevice.value?.macAddress ?: ""
            val name = dev?.name ?: _connectedDevice.value?.name ?: "Gear S3 (9A7E) LE"

            Log.i(TAG, "onConnectionStateChange -> Device: $name [$address], status: $status, newState: $newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
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
                startKeepAliveLoop(devInfo)

                mainHandler.postDelayed({
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try { gatt?.requestMtu(512) } catch (_: Exception) {}
                    }
                    mainHandler.postDelayed({
                        gatt?.discoverServices()
                    }, 400)
                }, 200)

                startRssiMonitoring(gatt)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT disconnected from $address (status: $status)")
                rssiPollJob?.cancel()
                clearGattQueue()
                _isHardwareConnected.value = false
                _connectedDevice.value = _connectedDevice.value?.copy(isConnected = false)
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
        if (discoveredWriteCharacteristics.isEmpty()) return

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR) % 100
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)

        val commands = listOf(
            // HBand / Veepoo handshake and sensor triggers
            byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00),
            byteArrayOf(0x02, year.toByte(), month.toByte(), day.toByte(), hour.toByte(), min.toByte(), sec.toByte(), 0x00),
            byteArrayOf(0x04, 0x01, 0x00, 0x00, 0x00), // Realtime Heart Rate
            byteArrayOf(0x15, 0x01, 0x00, 0x00, 0x00), // PPG Stream
            byteArrayOf(0x08, 0x01, 0x00, 0x00, 0x00), // Step stream
            byteArrayOf(0x20, 0x01, 0x00, 0x00, 0x00), // SpO2
            byteArrayOf(0x05, 0x01, 0x00, 0x00, 0x00)  // Blood Pressure
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
        val realName = gatt?.device?.name ?: currentDev.name

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
                _connectedDevice.value = _connectedDevice.value?.copy(batteryLevel = battery)
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
                        if (b1 <= 5 && data.size >= 4) {
                            if (b2 in 60..220) currentSystolic = b2
                            if (b3 in 40..140) currentDiastolic = b3
                            if (b4 in 30..240) currentHeartRate = b4
                        }
                        emitRealTelemetry(realMac, realName)
                    }
                    0x20, 0x23 -> {
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

        _latestTelemetry.value = HBandTelemetry(
            deviceId = mac,
            deviceModel = model,
            timestamp = isoFormat.format(Date()),
            heartRate = currentHeartRate,
            bloodPressure = BloodPressure(currentSystolic, currentDiastolic),
            spO2 = currentSpO2,
            temperatureCelsius = currentTemp,
            steps = currentSteps,
            calories = currentCalories,
            distanceMeters = currentDistance,
            hrvScore = currentHrvScore,
            sleepSummary = SleepSummary(0, 0, 0),
            isRealSensorData = true
        )
    }

    @SuppressLint("MissingPermission")
    private fun startRssiMonitoring(gatt: BluetoothGatt?) {
        rssiPollJob?.cancel()
        rssiPollJob = scope.launch(Dispatchers.IO) {
            while (currentGatt != null && _connectedDevice.value?.isConnected == true) {
                delay(4000)
                try {
                    currentGatt?.readRemoteRssi()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading RSSI: ${e.message}")
                }
            }
        }
    }

    // Keep-alive loop that checks connection without generating fake data
    private fun startKeepAliveLoop(device: HBandDevice) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000)
                if (_connectedDevice.value?.isConnected == true && currentGatt != null) {
                    val dev = _connectedDevice.value ?: device
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
                    if (currentHeartRate > 0 || currentSteps > 0) {
                        _latestTelemetry.value = createTelemetrySnapshot(dev)
                    }
                }
            }
        }
    }

    fun setPatientId(id: String) {
        currentPatientId = id
    }

    fun setBatteryLevel(level: Int) {
        val clampedLevel = level.coerceIn(1, 100)
        _connectedDevice.value = _connectedDevice.value?.copy(batteryLevel = clampedLevel)
            ?: HBandDevice(batteryLevel = clampedLevel)
    }

    fun simulateLowBattery() {
        setBatteryLevel(14)
    }

    fun rechargeBattery() {
        setBatteryLevel(98)
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
    fun triggerSpotCheck(): HBandTelemetry {
        requestManualSensorRead()
        val dev = _connectedDevice.value ?: HBandDevice()
        val telemetry = createTelemetrySnapshot(dev)
        _latestTelemetry.value = telemetry
        return telemetry
    }

    fun generateCurrentTelemetry(device: HBandDevice = _connectedDevice.value ?: HBandDevice()): HBandTelemetry {
        return createTelemetrySnapshot(device)
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
        rrIntervals.clear()
        cumulativeRscSteps = 0
        lastRscCadence = 0
        lastKnownWorn = null
        _latestTelemetry.value = null
    }

    fun requestHistorySync() {
        if (!hasLiveHardwareSession() || !isVeepooConnection) {
            _sessionMessage.value = "Conecte uma pulseira Veepoo para sincronizar o histórico."
            return
        }
        ppgStageGeneration++
        hasStartedVeepooSensors = false
        startPostHandshakeSync(includeLiveSensors = true)
    }

    fun setAutoMeasureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_MEASURE, enabled).apply()
        _autoMeasureState.value = _autoMeasureState.value.copy(heartRateEnabled = enabled)
        if (!hasLiveHardwareSession() || !_capabilities.value.isSupportAutoMeasure) return
        scope.launch(Dispatchers.Main) {
            val sync = historyClient()
            val updated = sync.setAutoMeasureEnabled(lastAutoMeasureSettings, enabled)
            if (updated != null) {
                lastAutoMeasureSettings = updated
                applyAutoMeasureUi(updated, _autoMeasureState.value.spo2NightAutoEnabled)
            }
        }
    }

    fun setSpo2AutoDetectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SPO2_AUTO, enabled).apply()
        _autoMeasureState.value = _autoMeasureState.value.copy(spo2NightAutoEnabled = enabled)
        if (!hasLiveHardwareSession() || !_capabilities.value.isSupportSpo2AutoDetect) return
        scope.launch(Dispatchers.Main) {
            val sync = historyClient()
            val result = sync.setSpo2AutoEnabled(enabled, null)
            if (result != null) {
                _autoMeasureState.value = _autoMeasureState.value.copy(
                    spo2NightAutoEnabled = result.isOpen == 1 || result.openState == 1,
                    lastReadAtMs = System.currentTimeMillis(),
                )
            }
        }
    }

    fun setWearDetectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_WEAR_DETECT, enabled).apply()
        _wearDetectState.value = _wearDetectState.value.copy(enabled = enabled)
        if (!hasLiveHardwareSession() || !_capabilities.value.isSupportWearDetect) return
        scope.launch(Dispatchers.Main) {
            val sync = historyClient()
            val data = sync.applyWearDetect(enabled)
            _wearDetectState.value = _wearDetectState.value.copy(
                enabled = sync.wearEnabledFrom(data, enabled),
                lastResult = data?.checkWearState?.name.orEmpty(),
            )
        }
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
        ppgStageGeneration++
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
        postHandshakeJob?.cancel()
        historySync?.cancelled = true
        val sync = VeepooHistorySync(vpManager, mainHandler)
        historySync = sync
        postHandshakeJob = scope.launch(Dispatchers.Main) {
            try {
                if (includeLiveSensors) {
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

                val extras = sync.readHandshakeExtras(
                    caps = caps,
                    wearEnabled = _wearDetectState.value.enabled,
                )
                extras.batteryPercent?.let { setBatteryLevel(it) }
                extras.steps?.let { currentSteps = it }
                extras.calories?.let { currentCalories = it }
                extras.distanceMeters?.let { currentDistance = it }
                lastAutoMeasureSettings = extras.autoMeasure
                applyAutoMeasureUi(extras.autoMeasure, extras.spo2Auto?.let { it.isOpen == 1 || it.openState == 1 }
                    ?: _autoMeasureState.value.spo2NightAutoEnabled)
                extras.wear?.let { wear ->
                    _wearDetectState.value = _wearDetectState.value.copy(
                        supported = true,
                        enabled = sync.wearEnabledFrom(wear, _wearDetectState.value.enabled),
                        lastResult = wear.checkWearState?.name.orEmpty(),
                    )
                }
                readP1Settings(caps)
                if (currentSteps > 0) {
                    emitRealTelemetry(currentConnectedMac(), currentConnectedName())
                }

                if (sync.cancelled || userRequestedDisconnect) return@launch
                val pull = sync.pullHistory(
                    caps = caps,
                    deviceId = currentConnectedMac(),
                    deviceModel = currentConnectedName(),
                    onProgress = { _historySyncState.value = it },
                )
                _historySyncState.value = pull.state
                lastKnownWorn = pull.samples.mapNotNull { it.worn }.lastOrNull() ?: lastKnownWorn
                _wearDetectState.value = _wearDetectState.value.copy(lastWorn = lastKnownWorn)
                val telemetries = pull.samples.map { it.telemetry }
                if (telemetries.isNotEmpty()) {
                    onHistorySamples(telemetries)
                    applyLatestHistoryToLiveCache(pull.samples)
                }
                Log.i(TAG, historySummaryMessage(pull))
            } catch (e: Exception) {
                Log.e(TAG, "Falha no sync P0 pós-handshake: ${e.message}", e)
                _historySyncState.value = _historySyncState.value.copy(
                    isRunning = false,
                    lastError = e.message,
                    phase = "error",
                )
            } finally {
                if (includeLiveSensors && !userRequestedDisconnect && isConnectingVeepoo) {
                    startVeepooSensors()
                }
            }
        }
    }

    private fun applyLatestHistoryToLiveCache(samples: List<VeepooHistoryMapper.MappedSample>) {
        val latestOrigin = samples.filter { it.kind == VeepooHistoryMapper.MappedSample.Kind.ORIGIN }
            .maxByOrNull { it.epochMs }
        val latestHrv = samples.filter { it.kind == VeepooHistoryMapper.MappedSample.Kind.HRV }
            .maxByOrNull { it.epochMs }
        val latestSpo2 = samples.filter { it.kind == VeepooHistoryMapper.MappedSample.Kind.SPO2 }
            .maxByOrNull { it.epochMs }
        val latestSleep = samples.filter { it.kind == VeepooHistoryMapper.MappedSample.Kind.SLEEP }
            .maxByOrNull { it.epochMs }

        latestOrigin?.telemetry?.let { t ->
            if (t.heartRate in 30..240) currentHeartRate = t.heartRate
            if (t.bloodPressure.systolic in 60..240) currentSystolic = t.bloodPressure.systolic
            if (t.bloodPressure.diastolic in 30..160) currentDiastolic = t.bloodPressure.diastolic
            if (t.steps > 0) currentSteps = t.steps
            if (t.calories > 0f) currentCalories = t.calories
            if (t.distanceMeters > 0f) currentDistance = t.distanceMeters
            if (t.temperatureCelsius in 30f..43f) currentTemp = t.temperatureCelsius
        }
        latestSpo2?.telemetry?.let { t ->
            if (t.spO2 in 50..100) currentSpO2 = t.spO2
            if (t.heartRate in 30..240 && currentHeartRate == 0) currentHeartRate = t.heartRate
        }
        latestHrv?.telemetry?.let { t ->
            if (t.hrvScore > 0) currentHrvScore = t.hrvScore
        }
        if (currentHeartRate > 0 || currentSteps > 0 || currentSpO2 > 0 || latestSleep != null) {
            emitRealTelemetry(currentConnectedMac(), currentConnectedName())
            latestSleep?.telemetry?.sleepSummary?.let { sleep ->
                _latestTelemetry.value = _latestTelemetry.value?.copy(sleepSummary = sleep)
            }
        }
    }

    private fun applyAutoMeasureUi(items: List<AutoMeasureData>, spo2Night: Boolean) {
        val pulse = items.firstOrNull { it.funType == EAutoMeasureType.PULSE_RATE }
        _autoMeasureState.value = _autoMeasureState.value.copy(
            supported = _capabilities.value.isSupportAutoMeasure,
            spo2AutoSupported = _capabilities.value.isSupportSpo2AutoDetect,
            heartRateEnabled = pulse?.isSwitchOpen ?: _autoMeasureState.value.heartRateEnabled,
            spo2NightAutoEnabled = spo2Night,
            lastReadAtMs = System.currentTimeMillis(),
            summary = items.joinToString { "${it.funType}=${it.isSwitchOpen}" },
        )
        pulse?.let {
            prefs.edit().putBoolean(PREF_AUTO_MEASURE, it.isSwitchOpen).apply()
        }
    }

    private fun historySummaryMessage(pull: VeepooHistorySync.HistoryPull): String {
        val state = pull.state
        val parts = buildList {
            if (state.originSamples > 0) add("${state.originSamples} Origin")
            if (state.sleepDays > 0) add("${state.sleepDays} sono")
            if (state.hrvSamples > 0) add("${state.hrvSamples} HRV")
            if (state.spo2Samples > 0) add("${state.spo2Samples} SpO2")
        }
        return if (parts.isEmpty()) {
            "Histórico Veepoo lido — nenhum sample real nesta janela."
        } else {
            "Histórico Veepoo: ${parts.joinToString(", ")}."
        }
    }

    private fun historyClient(): VeepooHistorySync {
        val existing = historySync
        if (existing != null && !existing.cancelled) return existing
        return VeepooHistorySync(vpManager, mainHandler).also { historySync = it }
    }

    private fun cancelHistorySync() {
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

        return HBandTelemetry(
            deviceId = device.macAddress,
            deviceModel = device.name,
            timestamp = isoFormat.format(Date()),
            heartRate = currentHeartRate,
            bloodPressure = BloodPressure(currentSystolic, currentDiastolic),
            spO2 = currentSpO2,
            temperatureCelsius = currentTemp,
            steps = currentSteps,
            calories = currentCalories,
            distanceMeters = currentDistance,
            hrvScore = currentHrvScore,
            sleepSummary = SleepSummary(0, 0, 0),
            isRealSensorData = hasReceivedRealSensorData
        )
    }
}
