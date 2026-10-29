package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.remote.FirestoreBackupManager

@Composable
fun CloudBackupUnavailableCard() {
    Card(Modifier.fillMaxWidth().testTag("cloud_backup_unavailable")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recuperação em nuvem indisponível", modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium)
            Text(FirestoreBackupManager.UNAVAILABLE_MESSAGE, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}
