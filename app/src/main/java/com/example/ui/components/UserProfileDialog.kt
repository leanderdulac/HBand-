package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.UserProfileEntity

@Composable
fun UserProfileDialog(
    currentProfile: UserProfileEntity?,
    onDismissRequest: () -> Unit,
    onSaveProfile: (UserProfileEntity) -> Unit
) {
    val initial = currentProfile ?: UserProfileEntity()

    var fullName by remember { mutableStateOf(initial.fullName) }
    var patientId by remember { mutableStateOf(initial.patientId) }
    var ageStr by remember { mutableStateOf(initial.age.toString()) }
    var gender by remember { mutableStateOf(initial.gender) }
    var heightStr by remember { mutableStateOf(initial.heightCm.toInt().toString()) }
    var weightStr by remember { mutableStateOf(initial.weightKg.toInt().toString()) }
    var stepGoalStr by remember { mutableStateOf(initial.dailyStepGoal.toString()) }
    var waterGoalStr by remember { mutableStateOf(initial.targetWaterMl.toString()) }
    var emergencyContact by remember { mutableStateOf(initial.emergencyContact) }
    var medicalNotes by remember { mutableStateOf(initial.medicalNotes) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF00639B),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Perfil do Paciente / Usuário",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF001D31)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Configure seus dados biométricos e metas para sincronização HBand & GCP Cloud Run:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF44474E)
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_name")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = patientId,
                        onValueChange = { patientId = it },
                        label = { Text("ID Paciente GCP") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("input_patient_id")
                    )
                    OutlinedTextField(
                        value = ageStr,
                        onValueChange = { ageStr = it },
                        label = { Text("Idade") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("input_age")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gênero") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = { Text("Altura (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stepGoalStr,
                        onValueChange = { stepGoalStr = it },
                        label = { Text("Meta Passos/Dia") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waterGoalStr,
                        onValueChange = { waterGoalStr = it },
                        label = { Text("Meta Água (ml)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = emergencyContact,
                    onValueChange = { emergencyContact = it },
                    label = { Text("Contato de Emergência") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = medicalNotes,
                    onValueChange = { medicalNotes = it },
                    label = { Text("Notas Médicas / Observações") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = initial.copy(
                        fullName = fullName.ifBlank { "Alex Rivera" },
                        patientId = patientId.ifBlank { "PAT-HBAND-001" },
                        age = ageStr.toIntOrNull() ?: 32,
                        gender = gender.ifBlank { "Masculino" },
                        heightCm = heightStr.toFloatOrNull() ?: 178f,
                        weightKg = weightStr.toFloatOrNull() ?: 74f,
                        dailyStepGoal = stepGoalStr.toIntOrNull() ?: 8000,
                        targetWaterMl = waterGoalStr.toIntOrNull() ?: 2500,
                        emergencyContact = emergencyContact,
                        medicalNotes = medicalNotes
                    )
                    onSaveProfile(updated)
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00639B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("btn_save_profile")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salvar Perfil")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancelar", color = Color(0xFF44474E))
            }
        }
    )
}
