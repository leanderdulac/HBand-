import SwiftUI
import UserNotifications
import UIKit
import shared

struct SettingsScreen: View {
    @Environment(\.patientPalette) private var palette
    @Environment(\.patientColorScheme) private var paletteScheme
    @Binding var themeStorage: String
    @ObservedObject var session: VeepooSession

    @AppStorage("auto_reconnect_ble") private var autoReconnect = true
    @AppStorage("hr_alerts_enabled") private var alertsEnabled = false
    @AppStorage("upper_hr_threshold") private var upperThreshold = 100
    @AppStorage("lower_hr_threshold") private var lowerThreshold = 50
    @State private var showProfile = false
    @State private var showContinuityHelp = false
    @State private var notifyOpenFailed = false
    #if DEBUG
    @State private var versionTaps = 0
    @State private var showDeveloper = false
    #endif

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Next2uBrandLogo()
                    Text("Ajustes")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityAddTraits(.isHeader)
                    profileCard
                    continuityCard
                    watchClockCard
                    reconnectCard
                    themeCard
                    hrAlertsCard
                    Text(AppVersion.label)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityIdentifier("settings_version_label")
                        #if DEBUG
                        .onTapGesture {
                            versionTaps += 1
                            if versionTaps >= 7 {
                                versionTaps = 0
                                showDeveloper = true
                            }
                        }
                        #endif
                }
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Ajustes")
            .patientChrome()
            .sheet(isPresented: $showProfile) {
                ProfileEditorSheet(onClose: {
                    showProfile = false
                    session.notifyProfileChanged()
                })
                .environment(\.patientPalette, palette)
                .environment(\.patientColorScheme, paletteScheme)
                .preferredColorScheme(paletteScheme)
            }
            .onChange(of: autoReconnect) { value in
                session.setAutoReconnect(value)
            }
            #if DEBUG
            .sheet(isPresented: $showDeveloper) {
                DeveloperScreen(session: session)
                    .environment(\.patientPalette, palette)
                    .environment(\.patientColorScheme, paletteScheme)
                    .preferredColorScheme(paletteScheme)
            }
            #endif
        }
        .accessibilityIdentifier("theme_selector")
    }

    private var watchClockCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Relógio").font(.system(size: 22, weight: .semibold))
                .accessibilityAddTraits(.isHeader)
            if !session.watchTimeStatus.isEmpty {
                Text(session.watchTimeStatus)
                    .font(.system(size: 17))
                    .accessibilityIdentifier("watch_clock_message")
            }
            Text(WatchClockText.lastSync(session.watchTimeLastSync))
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
                .accessibilityIdentifier("watch_clock_last_sync")
            Button(session.watchTimeSyncing ? "Sincronizando…" : "Sincronizar hora") {
                session.syncWatchTime(manual: true)
            }
            .buttonStyle(PatientPrimaryButton())
            .disabled(!session.hardwareReady || session.watchTimeSyncing)
            .accessibilityIdentifier("watch_clock_sync_button")
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var profileCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Meu perfil").font(.system(size: 22, weight: .semibold))
            Text(PatientStore.shared.profile().fullName)
                .font(.system(size: 17))
            Button("Ver e editar meu perfil") { showProfile = true }
                .buttonStyle(PatientPrimaryButton())
                .accessibilityIdentifier("btn_edit_profile_settings")
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("user_profile_settings_card")
    }

    /// Honest iOS continuity — written for the patient, without Android product names.
    private var continuityCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Envio contínuo neste iPhone")
                .font(.system(size: 22, weight: .semibold))
            Text("O Bluetooth em segundo plano é melhor esforço: o sistema pode pausar o app, e fechar o app pela lista de abertos não o relança.")
                .font(.system(size: 17))
            Text("Para melhorar a continuidade:")
                .font(.system(size: 17, weight: .semibold))
            Text("1. Ajustes → Geral → Atualização em Segundo Plano → next2u SAÚDE: ligado.")
                .font(.system(size: 17))
            Text("2. Deixe o Bluetooth ligado.")
                .font(.system(size: 17))
            Text("3. Não deslize o next2u SAÚDE para fechar. Fechar o app pela lista de abertos não restaura a sessão do relógio.")
                .font(.system(size: 17))
            Button("Abrir Ajustes deste iPhone") { openAppSettings() }
                .buttonStyle(PatientPrimaryButton())
                .accessibilityIdentifier("btn_ios_continuity_settings")
            Button(showContinuityHelp ? "Fechar ajuda" : "Como deixar o app ativo neste iPhone") {
                showContinuityHelp.toggle()
            }
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(palette.onSurface)
            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
            if showContinuityHelp {
                Text("Não há um botão para «liberar o app» neste iPhone. O relógio guarda as medidas; o iPhone sincroniza ao abrir, ao receber uma notificação ou quando o Bluetooth restabelece a sessão.")
                    .font(.system(size: 17))
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.statusAttentionContainer)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityIdentifier("ios_continuity_card")
    }

    private var reconnectCard: some View {
        Toggle(isOn: $autoReconnect) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Reconectar relógio")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(palette.onSurface)
                Text("Tentar conectar novamente quando o relógio perder a conexão.")
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .tint(palette.primary)
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityIdentifier("auto_reconnect_switch")
    }

    private var themeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Aparência").font(.system(size: 22, weight: .semibold))
            Text("O tema padrão é claro. Os temas escuros usam as paletas da proposta visual.")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
            ForEach(PatientThemeChoices.all) { option in
                Button { themeStorage = option.rawValue } label: {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(themeStorage == option.rawValue ? palette.primary : palette.outlineVariant)
                            .frame(width: 22, height: 22)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(option.label).font(.system(size: 17, weight: .semibold))
                            Text(option.hint).font(.system(size: 15)).foregroundStyle(palette.onSurfaceVariant)
                        }
                        Spacer()
                    }
                    .foregroundStyle(palette.onSurface)
                    .frame(minHeight: PatientTheme.primaryButton)
                    .padding(.horizontal, 12)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("theme_option_\(option.rawValue)")
                .accessibilityAddTraits(themeStorage == option.rawValue ? [.isSelected] : [])
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private var hrAlertsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Avisos de batimentos").font(.system(size: 22, weight: .semibold))
            Text("Avisos de batimentos neste aparelho")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
            Toggle("Ativar avisos neste aparelho", isOn: $alertsEnabled)
                .tint(palette.primary)
                .foregroundStyle(palette.onSurface)
                .frame(minHeight: PatientTheme.primaryButton)
                .accessibilityIdentifier("hr_alerts_toggle_switch")
                .onChange(of: alertsEnabled) { enabled in
                    if enabled { requestNotify() }
                }
            Text(alertsEnabled ? "Limites de aviso: \(lowerThreshold) – \(upperThreshold) bpm" : "Avisos desativados no aplicativo")
                .font(.system(size: 17, weight: .semibold))
            Text(
                alertsEnabled
                    ? "Os avisos dependem das leituras recebidas e da permissão de notificação deste aparelho."
                    : "Use a opção acima para ativar os avisos neste aparelho."
            )
            .font(.system(size: 15))
            if alertsEnabled {
                Button("Opções de notificação deste aparelho") { openAppSettings() }
                    .buttonStyle(PatientPrimaryButton())
                    .accessibilityIdentifier("open_notification_settings")
                if notifyOpenFailed {
                    Text("Não foi possível abrir as opções. Abra as configurações deste aparelho e procure next2u SAÚDE.")
                        .font(.system(size: 17))
                }
                Text("Avisar acima de").font(.system(size: 15, weight: .semibold))
                Text("\(upperThreshold) bpm")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(palette.statusAlertForeground)
                    .accessibilityIdentifier("upper_threshold_display")
                Slider(value: Binding(get: { Double(upperThreshold) }, set: { upperThreshold = Int($0) }), in: 80...180, step: 1)
                    .tint(palette.statusAlertForeground)
                    .accessibilityIdentifier("upper_threshold_slider")
                Text("Avisar abaixo de").font(.system(size: 15, weight: .semibold))
                Text("\(lowerThreshold) bpm")
                    .font(.system(size: 22, weight: .bold))
                    .accessibilityIdentifier("lower_threshold_display")
                Slider(value: Binding(get: { Double(lowerThreshold) }, set: { lowerThreshold = Int($0) }), in: 30...80, step: 1)
                    .tint(palette.primary)
                    .accessibilityIdentifier("lower_threshold_slider")
                Text(AlertCopy.shared.channelName())
                    .font(.system(size: 15, weight: .semibold))
                Text(AlertCopy.shared.body(currentHr: Int32(upperThreshold + 10), threshold: Int32(upperThreshold)))
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityIdentifier("hr_threshold_settings_card")
    }

    private func requestNotify() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    private func openAppSettings() {
        notifyOpenFailed = false
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            notifyOpenFailed = true
            return
        }
        UIApplication.shared.open(url) { ok in
            if !ok { notifyOpenFailed = true }
        }
    }
}

/// Android `UserProfileDialog` fields, labels and validation. No invented clinical fields.
struct ProfileEditorSheet: View {
    @Environment(\.patientPalette) private var palette
    @Environment(\.patientColorScheme) private var paletteScheme
    var onClose: () -> Void
    @State private var name = ""
    @State private var wristbandName = ""
    @State private var wristbandEdited = false
    /// 4 hex sorteados uma vez por formulário: a prévia e o valor gravado são iguais.
    @State private var patientIdHex = PatientIdentity.randomHex()
    @State private var age = ""
    @State private var gender = ""
    @State private var height = ""
    @State private var weight = ""
    @State private var steps = ""
    @State private var water = ""
    @State private var contact = ""
    @State private var notes = ""
    @State private var patientId = ""
    @State private var profileId = "CURRENT_USER"
    @State private var goalsExpanded = false
    @State private var confirmDiscard = false
    @State private var saveFailed = false

    private var parsedAge: Int32? { profileInt(age) }
    private var parsedHeight: Float? { profileDecimal(height) }
    private var parsedWeight: Float? { profileDecimal(weight) }
    private var parsedSteps: Int32? { profileInt(steps) }
    private var parsedWater: Int32? { profileInt(water) }
    private var nameValid: Bool { !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var valid: Bool {
        nameValid && parsedAge != nil && parsedHeight != nil && parsedWeight != nil &&
            parsedSteps != nil && parsedWater != nil
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Este é o perfil salvo neste aparelho. Confira seus dados antes de salvar.")
                        .font(.system(size: 17))
                    field("Nome completo", $name, tag: "input_profile_name", error: !nameValid, kind: .text)
                    VStack(alignment: .leading, spacing: 4) {
                        field("Nome da pulseira", wristbandBinding, tag: "input_wristband_name", error: false, kind: .text)
                        Text("Ajuda a equipe de saúde a reconhecer a sua pulseira. Até 64 letras.")
                            .font(.system(size: 15))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                    field("Idade em anos", $age, tag: "input_age", error: parsedAge == nil, kind: .number)
                    field("Gênero", $gender, tag: "input_gender", error: false, kind: .text)
                    field("Altura em centímetros", $height, tag: "input_height", error: parsedHeight == nil, kind: .decimal)
                    field("Peso em quilos", $weight, tag: "input_weight", error: parsedWeight == nil, kind: .decimal)
                    disclosure("Metas e contato", expanded: $goalsExpanded) {
                        Text("Metas cadastradas").font(.system(size: 17, weight: .semibold))
                        field("Meta de passos por dia", $steps, tag: "input_step_goal", error: parsedSteps == nil, kind: .number)
                        field("Meta de água em mL por dia", $water, tag: "input_water_goal", error: parsedWater == nil, kind: .number)
                        field("Contato de emergência", $contact, tag: "input_emergency_contact", error: false, kind: .phone)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Observações")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(palette.onSurfaceVariant)
                            TextField("Observações", text: $notes, axis: .vertical)
                                .font(.system(size: 17))
                                .patientFieldInk(palette)
                                .lineLimit(2...6)
                                .frame(minHeight: PatientTheme.minTouch)
                                .accessibilityIdentifier("input_medical_notes")
                        }
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Identificação do paciente")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(palette.onSurfaceVariant)
                        Text(previewPatientId.isEmpty || PatientIdentity.needsGeneration(previewPatientId)
                             ? "Será criada quando você salvar o perfil com o seu nome."
                             : previewPatientId)
                            .font(.system(size: 17))
                            .frame(maxWidth: .infinity, minHeight: PatientTheme.minTouch, alignment: .leading)
                            .accessibilityIdentifier("input_patient_id")
                        if PatientIdentity.needsGeneration(patientId), !PatientIdentity.needsGeneration(previewPatientId) {
                            Text("Esta identificação será gravada quando você salvar o perfil.")
                                .font(.system(size: 15))
                                .foregroundStyle(palette.onSurfaceVariant)
                                .accessibilityIdentifier("patient_id_preview_hint")
                        }
                        Text("Esta identificação liga as suas medições ao seu cadastro e não muda depois de criada. Se precisar corrigi-la, fale com a equipe responsável.")
                            .font(.system(size: 15))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                    if !valid {
                        Text("Confira os campos marcados antes de salvar.")
                            .font(.system(size: 17))
                            .foregroundStyle(palette.statusAlertForeground)
                    }
                    if saveFailed {
                        Text("Não foi possível gravar o perfil neste aparelho.")
                            .font(.system(size: 17))
                            .foregroundStyle(palette.statusAlertForeground)
                    }
                    Button("Salvar perfil") { save() }
                        .buttonStyle(PatientPrimaryButton())
                        .disabled(!valid)
                        .opacity(valid ? 1 : 0.5)
                        .accessibilityIdentifier("btn_save_profile")
                }
                .foregroundStyle(palette.onSurface)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Meu perfil")
            .navigationBarTitleDisplayMode(.large)
            .patientChrome()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar", action: requestClose)
                        .foregroundStyle(palette.primary)
                }
            }
            .onAppear { load() }
            .sheet(isPresented: $confirmDiscard) {
                discardSheet
                    .environment(\.patientPalette, palette)
                    .environment(\.patientColorScheme, paletteScheme)
                    .preferredColorScheme(paletteScheme)
            }
        }
        .accessibilityIdentifier("profile_editor")
    }

    private var discardSheet: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Sair sem salvar?")
                .font(.system(size: 22, weight: .semibold))
            Text("Você alterou seu perfil. Se sair agora, essas alterações serão perdidas.")
                .font(.system(size: 17))
            Button("Continuar editando") { confirmDiscard = false }
                .buttonStyle(PatientPrimaryButton())
                .accessibilityIdentifier("profile_continue_editing")
            Button("Sair sem salvar") { onClose() }
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                .accessibilityIdentifier("profile_discard_changes")
        }
        .foregroundStyle(palette.onSurface)
        .padding(PatientTheme.margin)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(palette.background)
        .presentationDetents([.medium])
        .accessibilityIdentifier("profile_discard_dialog")
    }

    private enum FieldKind { case text, number, decimal, phone }

    private func field(_ label: String, _ text: Binding<String>, tag: String, error: Bool, kind: FieldKind) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 15, weight: .semibold)).foregroundStyle(palette.onSurfaceVariant)
            TextField(label, text: text)
                .font(.system(size: 17))
                .keyboardType(keyboard(kind))
                .frame(minHeight: PatientTheme.minTouch)
                .patientFieldInk(palette)
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(error ? palette.statusAlertForeground : palette.outlineVariant)
                        .frame(height: 1)
                }
                .accessibilityIdentifier(tag)
            if error {
                Text(errorCopy(kind))
                    .font(.system(size: 15))
                    .foregroundStyle(palette.statusAlertForeground)
            }
        }
    }

    @ViewBuilder
    private func disclosure<Content: View>(_ title: String, expanded: Binding<Bool>, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                expanded.wrappedValue.toggle()
            } label: {
                HStack {
                    Text(title).font(.system(size: 17, weight: .semibold))
                    Spacer()
                    Image(systemName: expanded.wrappedValue ? "chevron.up" : "chevron.down")
                }
                .foregroundStyle(palette.onSurface)
                .frame(minHeight: PatientTheme.minTouch)
            }
            .buttonStyle(.plain)
            if expanded.wrappedValue { content() }
        }
    }

    private func keyboard(_ kind: FieldKind) -> UIKeyboardType {
        switch kind {
        case .text: return .default
        case .number: return .numberPad
        case .decimal: return .decimalPad
        case .phone: return .phonePad
        }
    }

    private func errorCopy(_ kind: FieldKind) -> String {
        switch kind {
        case .text: return "Preencha este campo."
        case .number: return "Digite só números, sem vírgula ou sinal de menos."
        case .decimal: return "Digite um número sem sinal de menos. Você pode usar vírgula."
        case .phone: return "Preencha este campo."
        }
    }

    private func profileInt(_ value: String) -> Int32? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let parsed = Int32(trimmed, radix: 10), parsed >= 0 else { return nil }
        return parsed
    }

    private func profileDecimal(_ value: String) -> Float? {
        let parsed = Float(value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: "."))
        guard let parsed, parsed.isFinite, parsed >= 0 else { return nil }
        return parsed
    }

    private func commaDecimal(_ value: Float) -> String {
        String(value).replacingOccurrences(of: ".", with: ",")
    }

    /// Identificação gravada ou, se ainda não existe, a prévia com o nome digitado.
    private var previewPatientId: String {
        PatientIdentity.resolved(current: patientId, fullName: name, hex: patientIdHex)
    }

    /// Enquanto o paciente não mexe no campo, a sugestão acompanha o nome completo.
    private var wristbandBinding: Binding<String> {
        Binding(
            get: {
                wristbandEdited ? wristbandName
                    : (UserDefaults.standard.string(forKey: VeepooSession.wristbandNameKey)
                        ?? VeepooSession.defaultWristbandName(profileName: name) ?? "")
            },
            set: { newValue in
                wristbandEdited = true
                wristbandName = String(newValue.prefix(64))
            }
        )
    }

    private func load() {
        VeepooSession.shared.ensurePatientId()
        let profile = PatientStore.shared.profile()
        profileId = profile.id
        patientId = profile.patientId
        name = profile.fullName
        wristbandEdited = false
        wristbandName = ""
        age = "\(profile.age)"
        gender = profile.gender
        height = commaDecimal(profile.heightCm)
        weight = commaDecimal(profile.weightKg)
        steps = "\(profile.dailyStepGoal)"
        water = "\(profile.targetWaterMl)"
        contact = profile.emergencyContact
        notes = profile.medicalNotes
    }

    private var hasChanges: Bool {
        let profile = PatientStore.shared.profile()
        return name != profile.fullName || wristbandEdited ||
            age != "\(profile.age)" ||
            gender != profile.gender ||
            height != commaDecimal(profile.heightCm) ||
            weight != commaDecimal(profile.weightKg) ||
            steps != "\(profile.dailyStepGoal)" ||
            water != "\(profile.targetWaterMl)" ||
            contact != profile.emergencyContact ||
            notes != profile.medicalNotes
    }

    private func requestClose() {
        if hasChanges { confirmDiscard = true } else { onClose() }
    }

    private func save() {
        guard valid, let parsedAge, let parsedHeight, let parsedWeight, let parsedSteps, let parsedWater else { return }
        saveFailed = false
        let finalPatientId = previewPatientId
        let saved = PatientStore.shared.saveProfile(
            profile: UserProfile(
                id: profileId,
                patientId: finalPatientId,
                fullName: name.trimmingCharacters(in: .whitespacesAndNewlines),
                age: parsedAge,
                gender: gender,
                heightCm: parsedHeight,
                weightKg: parsedWeight,
                dailyStepGoal: parsedSteps,
                targetWaterMl: parsedWater,
                emergencyContact: contact,
                medicalNotes: notes
            )
        )
        if saved {
            let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
            if wristbandEdited {
                VeepooSession.shared.saveWristbandName(wristbandName, profileName: trimmedName)
            }
            VeepooSession.shared.notifyProfileChanged()
            onClose()
        } else {
            saveFailed = true
        }
    }
}

/// Posts the frozen AlertCopy text. Limits are the ones cadastrados in Ajustes, not clinical ranges.
///
/// Edge-triggered: one notification when HR crosses the limit, then silence while it stays
/// out of range. Re-arms once HR is back inside by `hysteresis` bpm; if it stays out of range,
/// at most one reminder after `reminderAfter`. State lives in UserDefaults so a background
/// relaunch (BLE state restoration) does not fire again. Fixed identifiers replace, not stack.
enum HeartAlertDelivery {
    static let hysteresis = 5
    static let reminderAfter: TimeInterval = 30 * 60

    enum Kind: String { case high, low }

    static func evaluate(heartRate: Int, now: Date = Date()) {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: "hr_alerts_enabled"), heartRate > 0 else { return }
        let upper = defaults.object(forKey: "upper_hr_threshold") as? Int ?? 100
        let lower = defaults.object(forKey: "lower_hr_threshold") as? Int ?? 50
        let copy = AlertCopy.shared
        step(.high, outOfRange: heartRate > upper, backInside: heartRate <= upper - hysteresis, now: now) {
            post(.high, title: copy.titleHigh(), body: copy.body(currentHr: Int32(heartRate), threshold: Int32(upper)))
        }
        step(.low, outOfRange: heartRate < lower, backInside: heartRate >= lower + hysteresis, now: now) {
            post(.low, title: copy.titleLow(), body: copy.body(currentHr: Int32(heartRate), threshold: Int32(lower)))
        }
    }

    private static func step(_ kind: Kind, outOfRange: Bool, backInside: Bool, now: Date, fire: () -> Void) {
        let d = UserDefaults.standard
        let activeKey = "hr_alert_\(kind.rawValue)_active"
        let firedKey = "hr_alert_\(kind.rawValue)_fired_at"
        let remindedKey = "hr_alert_\(kind.rawValue)_reminded"
        if outOfRange {
            if !d.bool(forKey: activeKey) {
                d.set(true, forKey: activeKey)
                d.set(now.timeIntervalSince1970, forKey: firedKey)
                d.set(false, forKey: remindedKey)
                fire()
            } else if !d.bool(forKey: remindedKey),
                      now.timeIntervalSince1970 - d.double(forKey: firedKey) >= reminderAfter {
                d.set(true, forKey: remindedKey)
                fire()
            }
        } else if backInside, d.bool(forKey: activeKey) {
            d.set(false, forKey: activeKey)
            d.set(false, forKey: remindedKey)
        }
    }

    private static func post(_ kind: Kind, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.threadIdentifier = "hr-alert"
        let id = "hr-alert-\(kind.rawValue)"
        let center = UNUserNotificationCenter.current()
        center.removeDeliveredNotifications(withIdentifiers: [id])
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }
}

enum WatchClockText {
    private static func fmt(_ pattern: String, _ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "pt_BR")
        f.dateFormat = pattern
        return f.string(from: date)
    }
    static func success(_ date: Date) -> String { "Hora do relógio atualizada às \(fmt("HH:mm", date))" }
    static func lastSync(_ date: Date?) -> String {
        guard let date else { return "Última sincronização: nunca" }
        return "Última sincronização: \(fmt("dd/MM HH:mm", date))"
    }
}
