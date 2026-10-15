import SwiftUI
import UserNotifications
import UIKit
import shared

struct SettingsScreen: View {
    @Environment(\.patientPalette) private var palette
    @Binding var themeStorage: String
    @ObservedObject var session: VeepooSession

    @AppStorage("auto_reconnect_ble") private var autoReconnect = true
    @AppStorage("hr_alerts_enabled") private var alertsEnabled = false
    @AppStorage("upper_hr_threshold") private var upperThreshold = 100
    @AppStorage("lower_hr_threshold") private var lowerThreshold = 50
    @State private var showProfile = false
    @State private var showContinuityHelp = false
    @State private var notifyOpenFailed = false

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
                    reconnectCard
                    themeCard
                    hrAlertsCard
                    Text("Assinatura do piloto")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    Text(SigningPlaceholders.summary)
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Ajustes")
            .sheet(isPresented: $showProfile) {
                ProfileEditorSheet(onClose: { showProfile = false })
            }
            .onChange(of: autoReconnect) { value in
                session.setAutoReconnect(value)
            }
        }
        .accessibilityIdentifier("theme_selector")
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

    /// Honest iOS continuity — not the Android battery-exemption card.
    private var continuityCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Envio contínuo neste iPhone")
                .font(.system(size: 22, weight: .semibold))
            Text("O iPhone não tem o pedido de isenção de bateria do Android. O Bluetooth em segundo plano é melhor esforço: o sistema pode pausar o app, e fechar o app pela lista de abertos não o relança.")
                .font(.system(size: 17))
            Text("Para melhorar a continuidade:")
                .font(.system(size: 17, weight: .semibold))
            Text("1. Ajustes → Geral → Atualização em Segundo Plano → next2u SAÚDE: ligado.")
                .font(.system(size: 17))
            Text("2. Deixe o Bluetooth ligado.")
                .font(.system(size: 17))
            Text("3. Não deslize o next2u SAÚDE para fechar. Force-quit no iOS não restaura a sessão do relógio.")
                .font(.system(size: 17))
            Button("Abrir Ajustes deste iPhone") { openAppSettings() }
                .buttonStyle(PatientPrimaryButton())
                .accessibilityIdentifier("btn_ios_continuity_settings")
            Button(showContinuityHelp ? "Fechar ajuda" : "Como deixar o app ativo neste iPhone") {
                showContinuityHelp.toggle()
            }
            .font(.system(size: 17, weight: .semibold))
            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
            if showContinuityHelp {
                Text("Não existe «Liberar o app» no iOS. Samsung e Multilaser não se aplicam a este iPhone. O relógio guarda medidas; o iPhone sincroniza ao abrir, ao receber notify ou no restore do Bluetooth.")
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
                Text("Tentar conectar novamente quando o relógio perder a conexão.")
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .tint(palette.primary)
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

struct ProfileEditorSheet: View {
    @Environment(\.patientPalette) private var palette
    var onClose: () -> Void
    @State private var name = ""
    @State private var age = ""
    @State private var gender = ""
    @State private var height = ""
    @State private var weight = ""
    @State private var steps = ""
    @State private var water = ""
    @State private var contact = ""
    @State private var notes = ""
    @State private var patientId = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    field("Nome", $name)
                    field("Idade", $age)
                    field("Sexo", $gender)
                    field("Altura (cm)", $height)
                    field("Peso (kg)", $weight)
                    field("Meta de passos", $steps)
                    field("Meta de água (ml)", $water)
                    field("Contato de emergência", $contact)
                    field("Notas", $notes)
                    field("patient_id", $patientId)
                    Button("Salvar perfil") { save() }
                        .buttonStyle(PatientPrimaryButton())
                }
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Meu perfil")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fechar", action: onClose) } }
            .onAppear { load() }
        }
    }

    private func field(_ label: String, _ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 15, weight: .semibold)).foregroundStyle(palette.onSurfaceVariant)
            TextField(label, text: text)
                .font(.system(size: 17))
                .frame(minHeight: PatientTheme.minTouch)
                .foregroundStyle(palette.onSurface)
        }
    }

    private func load() {
        let p = PatientStore.shared.profile()
        name = p.fullName
        age = "\(p.age)"
        gender = p.gender
        height = String(format: "%.0f", p.heightCm)
        weight = String(format: "%.0f", p.weightKg)
        steps = "\(p.dailyStepGoal)"
        water = "\(p.targetWaterMl)"
        contact = p.emergencyContact
        notes = p.medicalNotes
        patientId = p.patientId
    }

    private func save() {
        let current = PatientStore.shared.profile()
        _ = PatientStore.shared.saveProfile(
            profile: UserProfile(
                id: current.id,
                patientId: patientId.isEmpty ? current.patientId : patientId,
                fullName: name,
                age: Int32(age, radix: 10) ?? current.age,
                gender: gender,
                heightCm: Float(height.replacingOccurrences(of: ",", with: ".")) ?? current.heightCm,
                weightKg: Float(weight.replacingOccurrences(of: ",", with: ".")) ?? current.weightKg,
                dailyStepGoal: Int32(steps, radix: 10) ?? current.dailyStepGoal,
                targetWaterMl: Int32(water, radix: 10) ?? current.targetWaterMl,
                emergencyContact: contact,
                medicalNotes: notes
            )
        )
        onClose()
    }
}

/// Posts the frozen AlertCopy text. Limits are the ones cadastrados in Ajustes, not clinical ranges.
enum HeartAlertDelivery {
    static func evaluate(heartRate: Int) {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: "hr_alerts_enabled"), heartRate > 0 else { return }
        let upper = defaults.object(forKey: "upper_hr_threshold") as? Int ?? 100
        let lower = defaults.object(forKey: "lower_hr_threshold") as? Int ?? 50
        let copy = AlertCopy.shared
        if heartRate > upper {
            post(title: copy.titleHigh(), body: copy.body(currentHr: Int32(heartRate), threshold: Int32(upper)))
        } else if heartRate < lower {
            post(title: copy.titleLow(), body: copy.body(currentHr: Int32(heartRate), threshold: Int32(lower)))
        }
    }

    private static func post(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: "hr-alert-\(Int(Date().timeIntervalSince1970))",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }
}
