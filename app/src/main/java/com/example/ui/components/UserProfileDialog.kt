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
import com.example.ui.ProfileSaveState
import com.example.ui.ProfileSaveStatus
import java.util.UUID

@Composable
fun UserProfileDialog(
    currentProfile: UserProfileEntity?,
    onDismissRequest: () -> Unit,
    onSaveProfile: (String, UserProfileEntity) -> Unit,
    saveState: ProfileSaveState? = null,
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
    var name by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.fullName) }
    var age by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.age.toString()) }
    var gender by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.gender) }
    var height by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.heightCm.toString().replace('.', ',')) }
    var weight by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.weightKg.toString().replace('.', ',')) }
    var steps by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.dailyStepGoal.toString()) }
    var water by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.targetWaterMl.toString()) }
    var contact by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.emergencyContact) }
    var notes by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(initial.medicalNotes) }
    var confirmDiscard by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(false) }
    var goalsExpanded by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(false) }
    var identityExpanded by rememberSaveable(initial.id, initial.patientId) { mutableStateOf(false) }
    val editorScroll = rememberSaveable(initial.id, initial.patientId, saver = ScrollState.Saver) { ScrollState(0) }
    var requestToken by rememberSaveable(initial.id, initial.patientId) { mutableStateOf<String?>(null) }
    // A live callback can be pending before collection observes the controller receipt.
    // After restoration, a missing receipt is uncertain, never replayed automatically.
    var submissionPending by remember(initial.id, initial.patientId) { mutableStateOf(false) }
    val receipt = saveState?.takeIf {
        it.token == requestToken && it.profileId == initial.id && it.patientId == initial.patientId
    }
    val anySaving = saveState?.status == ProfileSaveStatus.SAVING
    val pending = submissionPending || anySaving
    val editingEnabled = !pending && receipt?.status != ProfileSaveStatus.SAVED
    val uncertain = requestToken != null && !pending && receipt?.status != ProfileSaveStatus.SAVED
    LaunchedEffect(receipt) {
        if (receipt != null && receipt.status != ProfileSaveStatus.SAVING) {
            submissionPending = false
            if (receipt.status == ProfileSaveStatus.SAVED) onDismissRequest()
        }
    }
    LaunchedEffect(requestToken, receipt?.status) {
        if (requestToken != null) editorScroll.scrollTo(0)
    }
    val hasChanges = name != initial.fullName || age != initial.age.toString() ||
        gender != initial.gender || height != initial.heightCm.toString().replace('.', ',') ||
        weight != initial.weightKg.toString().replace('.', ',') ||
        steps != initial.dailyStepGoal.toString() || water != initial.targetWaterMl.toString() ||
        contact != initial.emergencyContact || notes != initial.medicalNotes
    val focusManager = LocalFocusManager.current
    val requestClose: () -> Unit = {
        if (pending || receipt?.status == ProfileSaveStatus.SAVED) {
            // Back/outside taps cannot discard a write whose result is still pending.
        } else if (hasChanges || requestToken != null) {
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
                if (requestToken != null) Text(
                    text = if (pending) "Salvando perfil neste celular…" else
                        "Gravação não confirmada. Suas alterações foram mantidas nesta tela. Confira os dados antes de salvar novamente; a tentativa anterior pode já ter sido gravada.",
                    modifier = Modifier.testTag("profile_save_feedback"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                PatientSummaryLayout(first = {
                    ProfileField(name, { name = it }, "Nome completo", "input_profile_name", error = name.isBlank(), enabled = editingEnabled)
                    ProfileField(age, { age = it }, "Idade em anos", "input_age", KeyboardType.Number, parsedAge == null, enabled = editingEnabled)
                    ProfileField(gender, { gender = it }, "Gênero", "input_gender", enabled = editingEnabled)
                    ProfileField(height, { height = it }, "Altura em centímetros", "input_height", KeyboardType.Decimal, parsedHeight == null, enabled = editingEnabled)
                    ProfileField(weight, { weight = it }, "Peso em quilos", "input_weight", KeyboardType.Decimal, parsedWeight == null, ImeAction.Done, enabled = editingEnabled)
                }, second = {
                    PatientSection("Metas e contato", goalsExpanded, { goalsExpanded = it }) {
                        Text("Metas cadastradas", style = MaterialTheme.typography.titleMedium)
                        ProfileField(steps, { steps = it }, "Meta de passos por dia", "input_step_goal", KeyboardType.Number, parsedSteps == null, enabled = editingEnabled)
                        ProfileField(water, { water = it }, "Meta de água em mL por dia", "input_water_goal", KeyboardType.Number, parsedWater == null, enabled = editingEnabled)
                        ProfileField(contact, { contact = it }, "Contato de emergência", "input_emergency_contact", KeyboardType.Phone, enabled = editingEnabled)
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it }, enabled = editingEnabled, label = { Text("Observações") },
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
                    if (valid && !submissionPending && !anySaving && receipt?.status != ProfileSaveStatus.SAVED) {
                        focusManager.clearFocus()
                        val token = UUID.randomUUID().toString()
                        requestToken = token
                        submissionPending = true
                        onSaveProfile(token, initial.copy(
                            fullName = name.trim(), age = parsedAge!!, gender = gender,
                            heightCm = parsedHeight!!, weightKg = parsedWeight!!,
                            dailyStepGoal = parsedSteps!!, targetWaterMl = parsedWater!!,
                            emergencyContact = contact, medicalNotes = notes,
                        ))
                    }
                },
                enabled = valid && editingEnabled,
                modifier = Modifier.heightIn(min = 56.dp).testTag("btn_save_profile"),
            ) { Text(if (pending) "Salvando…" else "Salvar perfil") }
        },
        dismissButton = {
            TextButton(onClick = requestClose, enabled = editingEnabled, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancelar") }
        },
    )

    if (confirmDiscard && editingEnabled) {
        ProfileDiscardConfirmation(
            onContinueEditing = { confirmDiscard = false },
            onDiscard = onDismissRequest,
            uncertainSave = uncertain,
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
internal fun ProfileDiscardConfirmation(onContinueEditing: () -> Unit, onDiscard: () -> Unit, uncertainSave: Boolean = false) {
    AlertDialog(
        onDismissRequest = onContinueEditing,
        modifier = Modifier.testTag("profile_discard_dialog"),
        title = { Text(if (uncertainSave) "Fechar sem salvar novamente?" else "Sair sem salvar?") },
        text = { Text(if (uncertainSave)
            "A gravação anterior não foi confirmada. Fechar descarta somente as alterações desta tela; um perfil já salvo no celular será mantido."
            else "Você alterou seu perfil. Se sair agora, essas alterações serão perdidas.") },
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
            ) { Text(if (uncertainSave) "Fechar edição" else "Sair sem salvar") }
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
    enabled: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange, enabled = enabled,
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
