package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.StorageStartupState

@Composable
fun RequestPermissionsWhenStorageReady(state: StorageStartupState, requestPermissions: () -> Unit) {
    var requested by rememberSaveable { mutableStateOf(false) }
    val request by rememberUpdatedState(requestPermissions)
    LaunchedEffect(state) {
        if (state == StorageStartupState.READY && !requested) {
            requested = true
            request()
        }
    }
}

/** The operational content is never composed before storage and runtime initialization. */
@Composable
fun StorageStartupScreen(state: StorageStartupState, content: @Composable () -> Unit) {
    if (state == StorageStartupState.READY) {
        content()
        return
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("storage_startup_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state == StorageStartupState.OPENING) {
            Text("Abrindo seus registros", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall)
            CircularProgressIndicator()
            Text("Aguarde a verificação do armazenamento antes de usar o aplicativo.", modifier = Modifier.fillMaxWidth())
        } else {
            Text("Não foi possível abrir seus registros", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall)
            Text("A coleta pelo aplicativo e os envios não foram iniciados nesta abertura.", modifier = Modifier.fillMaxWidth())
            Text("Não limpe os dados, não desinstale o aplicativo e não tente substituir a chave do banco.", modifier = Modifier.fillMaxWidth())
            Text("Peça ajuda à equipe responsável pela instalação para verificar o armazenamento e recuperar o acesso.", modifier = Modifier.fillMaxWidth())
            Text("Esta tela não confirma a integridade dos registros nem recupera dados automaticamente.", modifier = Modifier.fillMaxWidth())
        }
    }
}
