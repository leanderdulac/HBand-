package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.data.model.HBandDevice
import com.example.data.hband.BleScanFailure

@Composable
fun PatientWatchScreen(
    scannedDevices: List<HBandDevice>,
    connectedDevice: HBandDevice?,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onConnectDevice: (HBandDevice) -> Unit,
    onConnectByMac: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    scanFailure: BleScanFailure? = null,
    onStopScan: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPermissions by remember { mutableStateOf(emptyList<String>()) }
    fun isGranted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pendingAction
        val requested = pendingPermissions
        pendingAction = null
        pendingPermissions = emptyList()
        if (action == null || requested.isEmpty()) {
            // The result can outlive the screen that requested it. Without the
            // original action, ask for another explicit tap instead of guessing
            // which watch/action to resume or calling the result a denial.
            permissionMessage = "Não foi possível retomar a ação. Toque novamente na opção de busca ou conexão."
        } else if (requested.all { result[it] == true || isGranted(it) }) {
            permissionMessage = null
            action()
        } else {
            permissionMessage = "A permissão não foi concedida. A busca ou conexão não foi iniciada. Você pode tentar novamente ou abrir as permissões do aplicativo."
        }
    }
    fun withPermissions(permissions: List<String>, action: () -> Unit) {
        if (pendingPermissions.isNotEmpty()) {
            permissionMessage = "Conclua a solicitação de permissão antes de iniciar outra ação."
            return
        }
        val missing = permissions.filterNot(::isGranted)
        if (missing.isEmpty()) {
            permissionMessage = null
            action()
        } else {
            val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Manifest.permission.ACCESS_FINE_LOCATION in missing)
                (missing + Manifest.permission.ACCESS_COARSE_LOCATION).distinct() else missing
            pendingPermissions = requested
            pendingAction = action
            try {
                launcher.launch(requested.toTypedArray())
            } catch (_: Exception) {
                pendingAction = null
                pendingPermissions = emptyList()
                permissionMessage = "Não foi possível abrir a solicitação de permissão. Toque novamente na opção de busca ou conexão para tentar outra vez."
            }
        }
    }
    val connectPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()
    val scanPermissions = buildList {
        addAll(connectPermissions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            // Both are already declared by this app. Android 12+ requires requesting
            // coarse alongside fine; do not change the SDK's location requirement here.
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    PatientWatchContent(
        scannedDevices = scannedDevices,
        connectedDevice = connectedDevice,
        isScanning = isScanning,
        permissionMessage = permissionMessage,
        scanFailure = scanFailure,
        // Stopping releases local scan state even if permission was revoked meanwhile.
        onStopScan = onStopScan,
        onStartScan = { withPermissions(scanPermissions, onStartScan) },
        onConnectDevice = { device -> withPermissions(connectPermissions) { onConnectDevice(device) } },
        onConnectByMac = { mac -> withPermissions(connectPermissions) { onConnectByMac(mac) } },
        onDisconnectDevice = { withPermissions(connectPermissions, onDisconnectDevice) },
        onOpenPermissions = {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            } catch (_: Exception) {
                permissionMessage = "Não foi possível abrir as permissões. Tente novamente ou procure este aplicativo nas configurações do Android."
            }
        },
    )
}

@Composable
internal fun PatientWatchContent(
    scannedDevices: List<HBandDevice>,
    connectedDevice: HBandDevice?,
    isScanning: Boolean,
    permissionMessage: String?,
    onStartScan: () -> Unit,
    onConnectDevice: (HBandDevice) -> Unit,
    onConnectByMac: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onOpenPermissions: () -> Unit,
    scanFailure: BleScanFailure? = null,
    onStopScan: (() -> Unit)? = null,
) {
    var showSupport by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val connected = connectedDevice?.isConnected == true
    val feedback = permissionMessage ?: if (isScanning) null else scanFailure?.patientMessage()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("patient_watch_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Meu relógio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        PatientSummaryLayout(first = {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (connected) "Relógio conectado" else "Relógio desconectado", style = MaterialTheme.typography.titleLarge)
                    if (connected) {
                        Text(connectedDevice!!.name, style = MaterialTheme.typography.bodyLarge)
                        Text("Você pode voltar ao Início para consultar as leituras.", style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = onDisconnectDevice, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Desconectar relógio")
                        }
                    }
                    Button(
                        onClick = onStartScan,
                        enabled = !isScanning,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("scan_ble_button"),
                    ) { Text(if (isScanning) "Buscando relógio…" else "Buscar meu relógio") }
                    if (isScanning && onStopScan != null) {
                        OutlinedButton(
                            onClick = onStopScan,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("stop_ble_scan_button"),
                        ) { Text("Parar busca") }
                    }
                    if (isScanning) Text("Aguarde. A lista será atualizada com os relógios encontrados.", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    feedback?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("watch_connection_feedback").semantics { liveRegion = LiveRegionMode.Polite })
                        if (permissionMessage != null || scanFailure == BleScanFailure.PERMISSION_REQUIRED) {
                            OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text("Abrir permissões do aplicativo")
                            }
                        }
                    }
                    if (!connected) {
                        Text("Deixe o relógio perto deste aparelho e confira se o Bluetooth está ligado.", style = MaterialTheme.typography.bodyLarge)
                        Text("Ao buscar, este aparelho pode pedir acesso a dispositivos próximos e à localização. Essas permissões são necessárias para a busca nesta versão do aplicativo.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }, second = {
            Text("Relógios encontrados", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("watch_results_heading").semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = when (scannedDevices.size) {
                        0 -> "Nenhum relógio na lista"
                        1 -> "1 relógio na lista"
                        else -> "${scannedDevices.size} relógios na lista"
                    }
                })
            if (scannedDevices.isEmpty()) {
                Text(
                    if (isScanning) "Ainda não apareceu nenhum relógio." else "Nenhum relógio na lista. Toque em Buscar meu relógio para procurar novamente.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            scannedDevices.forEach { device ->
                val isCurrent = connected && connectedDevice?.deviceId == device.deviceId
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(device.name.ifBlank { "Relógio sem nome" }, style = MaterialTheme.typography.titleLarge)
                        // Keep the actual identifier available to distinguish two watches with the same name.
                        Text("Identificação: ${device.macAddress}", style = MaterialTheme.typography.bodyMedium)
                        if (isCurrent) Text("Conectado", style = MaterialTheme.typography.bodyLarge)
                        else Button(
                            onClick = { onConnectDevice(device) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connect_watch_${device.deviceId}"),
                        ) { Text("Conectar este relógio") }
                    }
                }
            }
        })
        OutlinedButton(
            onClick = { showSupport = !showSupport },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("watch_support_button"),
        ) { Text(if (showSupport) "Fechar ajuda de conexão" else "Ajuda para conectar") }
        if (showSupport) {
            Text("Confira se o relógio está carregado e perto deste aparelho. Se houver mais de um, confirme a identificação antes de conectar.", style = MaterialTheme.typography.bodyLarge)
            Text("Conexão por código", style = MaterialTheme.typography.titleMedium)
            Text("Use esta opção se o suporte informou o endereço do seu relógio.", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Endereço do relógio (MAC)") },
                isError = address.isNotBlank() && !isValidWatchAddress(address),
                supportingText = {
                    Text(if (address.isNotBlank() && !isValidWatchAddress(address))
                        "Confira o código recebido do suporte. Use 6 pares com números ou letras de A a F, separados por dois-pontos."
                    else "Use o endereço informado pelo suporte: 6 pares separados por dois-pontos.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth().testTag("custom_mac_input"),
            )
            Button(
                onClick = { onConnectByMac(address.trim()) },
                enabled = isValidWatchAddress(address),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connect_mac_button"),
            ) { Text("Conectar com este código") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

internal fun isValidWatchAddress(value: String): Boolean =
    Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$").matches(value.trim())

private fun BleScanFailure.patientMessage(): String = when (this) {
    BleScanFailure.PERMISSION_REQUIRED -> "A busca foi interrompida por falta de permissão. Confira as permissões do aplicativo e tente novamente."
    BleScanFailure.BLUETOOTH_OFF -> "A busca não começou porque o Bluetooth estava desligado. Confira se está ligado e tente novamente."
    BleScanFailure.SCANNER_UNAVAILABLE -> "A busca Bluetooth não está disponível no momento. Confira o Bluetooth deste aparelho e tente novamente."
    BleScanFailure.SCAN_FAILED -> "Não foi possível concluir a busca. Aguarde um pouco e tente novamente."
    BleScanFailure.STOP_FAILED -> "Não foi possível confirmar o encerramento da busca Bluetooth. Confira o Bluetooth deste aparelho antes de tentar novamente."
}

internal fun patientWatchConnectionHint(actionsEnabled: Boolean): String =
    if (actionsEnabled) "Não foi possível confirmar a conexão. Se a ação não funcionar, conecte o relógio novamente na aba Relógio."
    else "Conecte o relógio na aba Relógio para continuar."
