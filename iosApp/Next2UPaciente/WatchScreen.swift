import SwiftUI

struct WatchScreen: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var session: VeepooSession
    @State private var renameDraft = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PatientTheme.cardGap) {
                    Next2uBrandLogo()
                    Text("Relógio")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityAddTraits(.isHeader)

                    Text(patientStatusMessage)
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)

                    vitalsCard
                    actions
                    if session.connected != nil {
                        renameCard
                    }
                    deviceList
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .patientChrome()
        }
    }

    private var patientStatusMessage: String {
        guard session.connected != nil else {
            return session.isScanning ? "Procurando o relógio…" : "Toque em Buscar relógio para conectar."
        }
        guard session.hardwareReady else { return "Conectando ao relógio…" }
        let msg = session.sessionMessage
        let friendlyPrefixes = ["Batimentos:", "Medição concluída", "O relógio não está no pulso", "Ligue o Bluetooth"]
        if friendlyPrefixes.contains(where: { msg.hasPrefix($0) }) { return msg }
        return "Relógio conectado. As medições são feitas automaticamente."
    }

    private var vitalsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(session.connected?.name ?? "Nenhum relógio conectado")
                .font(.system(size: 17, weight: .semibold))
            Text("Batimentos: \(session.heartRate > 0 ? "\(session.heartRate) bpm" : "—")")
                .font(.system(size: 40, weight: .bold))
                .foregroundStyle(palette.heart)
                .accessibilityLabel("Batimentos \(session.heartRate > 0 ? "\(session.heartRate) por minuto" : "sem medição")")
            Text("Oxigênio: \(session.spo2 > 0 ? "\(session.spo2) %" : "—")")
                .font(.system(size: 22, weight: .semibold))
            if let battery = session.batteryPercent {
                Text("Bateria do relógio: \(battery)%")
                    .font(.system(size: 17))
            }
            Text(session.hardwareReady
                 ? "Relógio conectado."
                 : (session.connected == nil ? "Relógio desconectado." : "Conectando ao relógio…"))
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .foregroundStyle(palette.onSurface)
        .padding(PatientTheme.margin)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var renameCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Nome do relógio")
                .font(.system(size: 22, weight: .semibold))
            TextField("Nome do relógio", text: $renameDraft)
                .font(.system(size: 17))
                .patientFieldInk(palette)
                .frame(minHeight: PatientTheme.minTouch)
                .accessibilityIdentifier("watch_tab_input_name")
            Button("Renomear relógio") { session.renameWatch(renameDraft) }
                .buttonStyle(PatientPrimaryButton())
            if !session.renameStatus.isEmpty {
                Text(session.renameStatus)
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(PatientTheme.margin)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onAppear { renameDraft = session.displayWatchName }
    }

    private var actions: some View {
        VStack(spacing: 8) {
            Button(session.isScanning ? "Parar busca" : "Buscar relógio") {
                session.isScanning ? session.stopScan() : session.startScan()
            }
            .buttonStyle(PatientPrimaryButton())
            if session.connected != nil {
                Button("Desligar relógio") { session.disconnect() }
                    .buttonStyle(PatientPrimaryButton())
            }
        }
    }

    private var deviceList: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(session.devices) { watch in
                Button {
                    session.connect(watch)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(watch.name).font(.system(size: 20, weight: .semibold))
                            Text(watch.isOsConnected ? "Já neste iPhone — toque para usar" : "Toque para conectar")
                                .font(.system(size: 15))
                        }
                        Spacer()
                    }
                    .foregroundStyle(palette.onSurface)
                    .frame(minHeight: PatientTheme.primaryButton)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct PatientPrimaryButton: ButtonStyle {
    @Environment(\.patientPalette) private var palette

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold))
            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
            .foregroundStyle(palette.onPrimary)
            .background(palette.primary.opacity(configuration.isPressed ? 0.7 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Telas técnicas (URL/chave do Core, IDs, HTTP, diagnóstico BLE)
/// só existem em builds Debug, e só no ecrã escondido de desenvolvimento.
enum DeveloperFlags {
    static var showTechnicalUI: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }
}
