package com.example

import android.app.Application
import com.example.data.hband.HBandBleManager
import com.example.data.hband.HBandBleService
import com.example.data.local.AppDatabase
import com.example.data.remote.RetrofitClient
import com.example.worker.HBandWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HBandHealthSyncApp : Application() {
    val bleScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var bleManager: HBandBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Native SQLCipher MUST be loaded before any Room / SupportOpenHelperFactory open.
        AppDatabase.loadSqlCipherNativeLibrary()
        RetrofitClient.initialize(this)
        bleManager = HBandBleManager(this, bleScope)
        HBandWorkScheduler.schedulePeriodicIngest(this)
        HBandBleService.startIfPersistedSession(this)
    }
}
