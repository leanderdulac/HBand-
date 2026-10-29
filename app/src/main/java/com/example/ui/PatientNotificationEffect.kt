package com.example.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun PatientNotificationEffect(
    notification: UiNotification?,
    snackbarHostState: SnackbarHostState,
    onDismiss: (UiNotification) -> Unit,
) {
    LaunchedEffect(notification) {
        notification?.let { displayed ->
            snackbarHostState.showSnackbar(
                message = displayed.message,
                actionLabel = "Fechar",
                duration = SnackbarDuration.Long,
            )
            onDismiss(displayed)
        }
    }
}
