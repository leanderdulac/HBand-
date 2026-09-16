package com.example

import android.app.Application
import android.util.Log
import com.example.data.hband.HBandBleManager
import com.example.data.hband.HBandBleService
import com.example.data.hband.VeepooEcgNative
import com.example.data.local.AppDatabase
import com.example.data.remote.RetrofitClient
import com.example.data.repository.WearableRepository
import com.example.worker.HBandWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HBandHealthSyncApp : Application() {
    val bleScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var bleManager: HBandBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Native SQLCipher MUST be loaded before any Room / SupportOpenHelperFactory open.
        AppDatabase.loadSqlCipherNativeLibrary()
        // ECG JNI (`JNIChange` → libnative-lib.so) is missing from vpprotocol AAR.
        VeepooEcgNative.loadOnce()
        RetrofitClient.initialize(this)
        val db = AppDatabase.getDatabase(this)
        val historyRepository = WearableRepository(
            queueDao = db.ingestQueueDao(),
            sensorMetricDao = db.sensorMetricDao(),
            apiService = RetrofitClient.apiService,
            advancedMeasurementDao = db.advancedMeasurementDao(),
        )
        bleManager = HBandBleManager(
            this,
            bleScope,
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
        )
        HBandWorkScheduler.schedulePeriodicIngest(this)
        HBandBleService.startIfPersistedSession(this)
    }
}
