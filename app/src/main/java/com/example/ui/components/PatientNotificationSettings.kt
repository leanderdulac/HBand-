package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Reads the app-wide setting only; channel delivery and clinical monitoring are not inferred. */
@Composable
internal fun PatientNotificationSettings() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun readAllowed(): Boolean? = try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (_: Exception) { null }
    var allowed by remember(context) { mutableStateOf(readAllowed()) }
    var couldNotOpen by remember { mutableStateOf(false) }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = readAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowed == false) Text(
            "As notificações deste aplicativo estão desativadas neste aparelho.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("app_notifications_disabled"),
        )
        if (allowed == null) Text(
            "Não foi possível consultar a permissão. Confira as opções de notificação deste aparelho.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = {
                couldNotOpen = false
                try {
                    context.startActivity(appNotificationSettingsIntent(context))
                } catch (_: Exception) {
                    try {
                        context.startActivity(appDetailsIntent(context))
                    } catch (_: Exception) { couldNotOpen = true }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("open_notification_settings"),
        ) { Text("Opções de notificação deste aparelho") }
        if (couldNotOpen) Text(
            "Não foi possível abrir as opções. Abra as configurações deste aparelho e procure next2u SAÚDE.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun appNotificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else appDetailsIntent(context)

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
