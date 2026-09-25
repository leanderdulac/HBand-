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

    lateinit var bleManager: HBandBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        bleScope.launch {
            var opened: AppDatabase? = null
            storageStartup.initialize(
                openStorage = { opened = withContext(Dispatchers.IO) { AppDatabase.openVerified(this@HBandHealthSyncApp) } },
                startRuntime = { startDataRuntime(checkNotNull(opened)) },
            )
            if (storageStartup.isReady) {
                HBandWorkScheduler.schedulePeriodicIngest(this@HBandHealthSyncApp)
                HBandBleService.startIfPersistedSession(this@HBandHealthSyncApp)
            }
        }
    }

    fun readyBleManagerOrNull(): HBandBleManager? =
        if (storageStartup.isReady && ::bleManager.isInitialized) bleManager else null

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
        bleManager = HBandBleManager(
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
        )
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
}
