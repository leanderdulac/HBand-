package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.StorageStartupState
import com.example.ui.HomeScreen
import com.example.ui.MainViewModel
import com.example.ui.components.BleLabFrame
import com.example.ui.components.RequestPermissionsWhenStorageReady
import com.example.ui.components.StorageStartupScreen
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = allRequestedPermissionsGranted(permissions)
        if (allGranted && (application as? HBandHealthSyncApp)?.storageStartup?.isReady == true) {
            mainViewModel.showNotification("Permissões solicitadas concedidas.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val startup = (application as HBandHealthSyncApp).storageStartup
            val state by startup.state.collectAsStateWithLifecycle()
            RequestPermissionsWhenStorageReady(state, onRequestFailure = {
                mainViewModel.showNotification(
                    "Não foi possível solicitar permissões. Tente conectar pela aba Relógio; notificações ficam em Ajustes.",
                    isError = true,
                )
            }) { checkAndRequestBlePermissions() }
            PatientStartupApp(
                state = state,
                openingTheme = remember { AppThemeOption.storedIn(this@MainActivity) },
                bleLab = BuildConfig.BLE_LAB,
            ) {
                ReadyPatientApp(viewModel = mainViewModel)
            }
        }
    }

    private fun checkAndRequestBlePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

internal fun allRequestedPermissionsGranted(permissions: Map<String, Boolean>): Boolean =
    permissions.isNotEmpty() && permissions.entries.all { it.value }

/** Operational UI (and therefore MainViewModel) is composed only after storage runtime is READY. */
@Composable
internal fun PatientStartupApp(
    state: StorageStartupState,
    openingTheme: AppThemeOption,
    bleLab: Boolean,
    readyContent: @Composable () -> Unit,
) {
    if (state == StorageStartupState.READY) {
        readyContent()
        return
    }
    MyApplicationTheme(themeOption = openingTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BleLabFrame(enabled = bleLab) {
                StorageStartupScreen(state) {}
            }
        }
    }
}

@Composable
private fun ReadyPatientApp(viewModel: MainViewModel) {
    val themeOption by viewModel.themeOption.collectAsStateWithLifecycle()
    MyApplicationTheme(themeOption = themeOption) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BleLabFrame(enabled = BuildConfig.BLE_LAB) {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
