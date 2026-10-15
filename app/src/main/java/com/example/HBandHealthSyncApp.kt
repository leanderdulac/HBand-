package com.example

import android.app.Application
import android.util.Log
import com.example.data.hband.HBandBleManager
import com.example.data.hband.HBandBleService
import com.example.data.hband.VeepooEcgNative
import com.example.data.ingest.recordLiveReadingsWithSync
import com.example.data.local.AppDatabase
import com.example.data.local.localWriteTransaction
import com.example.data.remote.RetrofitClient
import com.example.data.repository.WearableRepository
import com.example.worker.HBandWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.local.StorageStartupGate

class HBandHealthSyncApp : Application() {
    val bleScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val storageStartup = StorageStartupGate()
    var startupStopRequested: Boolean = false
        private set

    @Volatile
    private var runtimeBleManager: HBandBleManager? = null

    /**
     * Same contract as the previous `lateinit` getter: throws until [startDataRuntime]
     * installs the manager. UI/receivers must use [readyBleManagerOrNull] or
     * [resolveRuntimeBleManager] so a cold start cannot crash-loop.
     */
    val bleManager: HBandBleManager
        get() = runtimeBleManager
            ?: throw UninitializedPropertyAccessException(
                "lateinit property bleManager has not been initialized"
            )

    override fun onCreate() {
        super.onCreate()
        bleScope.launch {
            var opened: AppDatabase? = null
            try {
                storageStartup.initialize(
                    openStorage = { opened = withContext(Dispatchers.IO) { AppDatabase.openVerified(this@HBandHealthSyncApp) } },
                    startRuntime = { startDataRuntime(checkNotNull(opened)) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Inicialização do runtime interrompida.", error)
            }
            if (storageStartup.isReady) {
                runCatching { HBandWorkScheduler.schedulePeriodicIngest(this@HBandHealthSyncApp) }
                    .onFailure { Log.e(TAG, "Falha ao agendar envio periódico.", it) }
                runCatching { HBandBleService.startIfPersistedSession(this@HBandHealthSyncApp) }
                    .onFailure { Log.e(TAG, "Falha ao retomar sessão BLE.", it) }
            }
        }
    }

    fun readyBleManagerOrNull(): HBandBleManager? =
        runtimeBleManager?.takeIf { storageStartup.isReady }

    internal fun installRuntimeBleManager(manager: HBandBleManager) {
        runtimeBleManager = manager
    }

    fun requestBleSessionStop() {
        val manager = readyBleManagerOrNull()
        if (manager == null) startupStopRequested = true
        manager?.disconnectDevice()
    }

    private fun startDataRuntime(db: AppDatabase) {
        // ECG JNI (`JNIChange` → libnative-lib.so) is missing from vpprotocol AAR.
        VeepooEcgNative.loadOnce()
        RetrofitClient.initialize(this)
        val historyRepository = WearableRepository(
            queueDao = db.ingestQueueDao(),
            sensorMetricDao = db.sensorMetricDao(),
            apiService = RetrofitClient.apiService,
            ingestTransportProvider = { RetrofitClient.captureIngestTransport() },
            localWriteTransaction = db.localWriteTransaction(),
            advancedMeasurementDao = db.advancedMeasurementDao(),
        )
        installRuntimeBleManager(HBandBleManager(
            this,
            bleScope,
            initiallyDisconnectedByUser = startupStopRequested,
            onHistorySamples = { samples ->
                bleScope.launch(Dispatchers.IO) {
                    try {
                        historyRepository.persistHistorySamples(samples, bleManager.currentPatientId)
                    } catch (e: Exception) {
                        Log.e("HBandHealthSyncApp", "Falha ao persistir histórico Veepoo: ${e.message}", e)
                    }
                }
            },
            onAdvancedSample = { sample ->
                bleScope.launch(Dispatchers.IO) {
                    try {
                        historyRepository.persistAdvancedSample(sample, bleManager.currentPatientId)
                    } catch (e: Exception) {
                        Log.e("HBandHealthSyncApp", "Falha ao persistir medição P1: ${e.message}", e)
                    }
                }
            },
            onSportReading = { deviceId, reading ->
                bleScope.launch(Dispatchers.IO) {
                    try {
                        historyRepository.persistSportReading(deviceId, reading)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e("HBandHealthSyncApp", "Falha ao salvar contadores do relógio.", error)
                    }
                }
            },
        ))
        val prefs = getSharedPreferences("hband_settings", MODE_PRIVATE)
        bleScope.recordLiveReadingsWithSync(
            readings = bleManager.latestTelemetry,
            savingEnabled = { prefs.getBoolean("auto_ingest_live", true) },
            save = { telemetry ->
                val patientId = db.userProfileDao().getUserProfile()?.patientId ?: bleManager.currentPatientId
                historyRepository.persistTelemetry(telemetry, patientId)
            },
            sync = { historyRepository.processQueueDetailed() },
            onFailure = { error ->
                Log.e("HBandHealthSyncApp", "Falha ao salvar leitura ao vivo; a coleta de novas leituras continua.", error)
            },
            onSyncFailure = {
                Log.e("HBandHealthSyncApp", "Falha ao processar envios; confira a fila preservada no aparelho.")
            },
        )
    }

    private companion object {
        const val TAG = "HBandHealthSyncApp"
    }
}

/**
 * MainViewModel and other early consumers must not read [HBandHealthSyncApp.bleManager]
 * (the old lateinit getter). That throws [UninitializedPropertyAccessException] on
 * every cold start until storage/runtime finish, which Android then restart-loops.
 */
fun resolveRuntimeBleManager(
    application: Application,
    fallback: () -> HBandBleManager,
): HBandBleManager = (application as? HBandHealthSyncApp)?.readyBleManagerOrNull() ?: fallback()
