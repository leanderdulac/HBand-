package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.UserProfileEntity

@Composable
fun UserProfileDialog(
    currentProfile: UserProfileEntity?,
    onDismissRequest: () -> Unit,
    onSaveProfile: (UserProfileEntity) -> Unit,
) {
    if (currentProfile == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Meu perfil") },
            text = { Text("Não foi possível mostrar seu perfil. Se ele continuar indisponível, peça ajuda à equipe responsável pelo seu cadastro.") },
            confirmButton = { TextButton(onClick = onDismissRequest, modifier = Modifier.heightIn(min = 56.dp)) { Text("Fechar") } },
        )
        return
    }
    val initial = currentProfile
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.fullName) }
    var age by rememberSaveable(initial.id) { mutableStateOf(initial.age.toString()) }
    var gender by rememberSaveable(initial.id) { mutableStateOf(initial.gender) }
    var height by rememberSaveable(initial.id) { mutableStateOf(initial.heightCm.toString().replace('.', ',')) }
    var weight by rememberSaveable(initial.id) { mutableStateOf(initial.weightKg.toString().replace('.', ',')) }
    var steps by rememberSaveable(initial.id) { mutableStateOf(initial.dailyStepGoal.toString()) }
    var water by rememberSaveable(initial.id) { mutableStateOf(initial.targetWaterMl.toString()) }
    var contact by rememberSaveable(initial.id) { mutableStateOf(initial.emergencyContact) }
    var notes by rememberSaveable(initial.id) { mutableStateOf(initial.medicalNotes) }
    var confirmDiscard by rememberSaveable(initial.id) { mutableStateOf(false) }
    var goalsExpanded by rememberSaveable(initial.id) { mutableStateOf(false) }
    var identityExpanded by rememberSaveable(initial.id) { mutableStateOf(false) }
    val editorScroll = rememberSaveable(initial.id, saver = ScrollState.Saver) { ScrollState(0) }
    val hasChanges = name != initial.fullName || age != initial.age.toString() ||
        gender != initial.gender || height != initial.heightCm.toString().replace('.', ',') ||
        weight != initial.weightKg.toString().replace('.', ',') ||
        steps != initial.dailyStepGoal.toString() || water != initial.targetWaterMl.toString() ||
        contact != initial.emergencyContact || notes != initial.medicalNotes
    val focusManager = LocalFocusManager.current
    val requestClose: () -> Unit = {
        if (hasChanges) {
            focusManager.clearFocus()
            confirmDiscard = true
        } else {
            onDismissRequest()
        }
    }
    val parsedAge = age.trim().toIntOrNull()?.takeIf { it >= 0 }
    val parsedHeight = profileDecimal(height)
    val parsedWeight = profileDecimal(weight)
    val parsedSteps = steps.trim().toIntOrNull()?.takeIf { it >= 0 }
    val parsedWater = water.trim().toIntOrNull()?.takeIf { it >= 0 }
    val valid = name.isNotBlank() && parsedAge != null && parsedHeight != null &&
        parsedWeight != null && parsedSteps != null && parsedWater != null

    ProfileEditorDialog(
        onDismissRequest = requestClose,
        scrollState = editorScroll,
        content = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Este é o perfil salvo neste aparelho. Confira seus dados antes de salvar.", style = MaterialTheme.typography.bodyLarge)
                PatientSummaryLayout(first = {
                    ProfileField(name, { name = it }, "Nome completo", "input_profile_name", error = name.isBlank())
                    ProfileField(age, { age = it }, "Idade em anos", "input_age", KeyboardType.Number, parsedAge == null)
                    ProfileField(gender, { gender = it }, "Gênero", "input_gender")
                    ProfileField(height, { height = it }, "Altura em centímetros", "input_height", KeyboardType.Decimal, parsedHeight == null)
                    ProfileField(weight, { weight = it }, "Peso em quilos", "input_weight", KeyboardType.Decimal, parsedWeight == null, ImeAction.Done)
                }, second = {
                    PatientSection("Metas e contato", goalsExpanded, { goalsExpanded = it }) {
                        Text("Metas cadastradas", style = MaterialTheme.typography.titleMedium)
                        ProfileField(steps, { steps = it }, "Meta de passos por dia", "input_step_goal", KeyboardType.Number, parsedSteps == null)
                        ProfileField(water, { water = it }, "Meta de água em mL por dia", "input_water_goal", KeyboardType.Number, parsedWater == null)
                        ProfileField(contact, { contact = it }, "Contato de emergência", "input_emergency_contact", KeyboardType.Phone)
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it }, label = { Text("Observações") },
                            modifier = Modifier.fillMaxWidth().testTag("input_medical_notes"), minLines = 2,
                        )
                    }
                    PatientSection("Identificação do cadastro", identityExpanded, { identityExpanded = it }) {
                        OutlinedTextField(
                            value = initial.patientId, onValueChange = {}, readOnly = true,
                            label = { Text("Identificação do paciente") },
                            modifier = Modifier.fillMaxWidth().testTag("input_patient_id"),
                        )
                        Text("Esta identificação vincula os registros ao cadastro. Se precisar corrigi-la, fale com a equipe responsável.", style = MaterialTheme.typography.bodyMedium)
                    }
                })
                if (!valid) Text("Confira os campos marcados antes de salvar.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (valid) {
                        onSaveProfile(initial.copy(
                            fullName = name.trim(), age = parsedAge!!, gender = gender,
                            heightCm = parsedHeight!!, weightKg = parsedWeight!!,
                            dailyStepGoal = parsedSteps!!, targetWaterMl = parsedWater!!,
                            emergencyContact = contact, medicalNotes = notes,
                        ))
                        onDismissRequest()
                    }
                },
                enabled = valid,
                modifier = Modifier.heightIn(min = 56.dp).testTag("btn_save_profile"),
            ) { Text("Salvar perfil") }
        },
        dismissButton = {
            TextButton(onClick = requestClose, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") }
        },
    )

    if (confirmDiscard) {
        ProfileDiscardConfirmation(
            onContinueEditing = { confirmDiscard = false },
            onDiscard = onDismissRequest,
        )
    }
}

/** Keep the form scrollable while both exit choices remain in the dialog footer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorDialog(
    onDismissRequest: () -> Unit,
    scrollState: ScrollState,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(16.dp).testTag("profile_editor"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Meu perfil", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Column(Modifier.weight(1f, fill = false).verticalScroll(scrollState).testTag("profile_editor_scroll")) { content() }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
internal fun ProfileDiscardConfirmation(onContinueEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinueEditing,
        modifier = Modifier.testTag("profile_discard_dialog"),
        title = { Text("Sair sem salvar?") },
        text = { Text("Você alterou seu perfil. Se sair agora, essas alterações serão perdidas.") },
        confirmButton = {
            Button(
                onClick = onContinueEditing,
                modifier = Modifier.heightIn(min = 56.dp).testTag("profile_continue_editing"),
            ) { Text("Continuar editando") }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.heightIn(min = 56.dp).testTag("profile_discard_changes"),
            ) { Text("Sair sem salvar") }
        },
    )
}

internal fun profileDecimal(value: String): Float? =
    value.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        isError = error,
        supportingText = if (error) ({ Text(when (keyboardType) {
            KeyboardType.Text -> "Preencha este campo."
            KeyboardType.Number -> "Digite só números, sem vírgula ou sinal de menos."
            else -> "Digite um número sem sinal de menos. Você pode usar vírgula."
        }) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus() },
        ),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
