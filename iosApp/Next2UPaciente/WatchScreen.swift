import SwiftUI

struct WatchScreen: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var session: VeepooSession

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PatientTheme.cardGap) {
                    Text("Relógio")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityAddTraits(.isHeader)

                    Text(session.sessionMessage)
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)

                    vitalsCard
                    actions
                    deviceList
                    ingestCard
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var vitalsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(session.connected.map { "\($0.name) · \($0.address)" } ?? "Nenhum relógio ligado")
                .font(.system(size: 17, weight: .semibold))
            Text("Batimentos: \(session.heartRate > 0 ? "\(session.heartRate) bpm" : "—")")
                .font(.system(size: 40, weight: .bold))
                .foregroundStyle(Color(hex: 0xC2334A))
                .accessibilityLabel("Batimentos \(session.heartRate > 0 ? "\(session.heartRate) por minuto" : "sem medição")")
            Text("Oxigênio: \(session.spo2 > 0 ? "\(session.spo2) %" : "—")")
                .font(.system(size: 22, weight: .semibold))
            if let battery = session.batteryPercent {
                Text("Bateria do relógio: \(battery)%")
                    .font(.system(size: 17))
            }
            Text(session.hardwareReady ? "Sessão Veepoo pronta (senha 0000)." : "Aguardando ligação e senha.")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .padding(PatientTheme.margin)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
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
                        VStack(alignment: .leading) {
                            Text(watch.name).font(.system(size: 17, weight: .semibold))
                            Text(watch.address).font(.system(size: 15))
                        }
                        Spacer()
                        Text("\(watch.rssi) dBm").font(.system(size: 15))
                    }
                    .foregroundStyle(palette.onSurface)
                    .frame(minHeight: PatientTheme.primaryButton)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var ingestCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Envio ao Core")
                .font(.system(size: 22, weight: .semibold))
            Text("A fila usa o módulo Kotlin shared (client_reading_id + lote). A chave não vai para o git.")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
            TextField("URL HTTPS do Core", text: $session.ingestUrl)
                .textInputAutocapitalization(.never)
                .font(.system(size: 17))
                .frame(minHeight: PatientTheme.minTouch)
            SecureField("Chave X-API-Key (override)", text: $session.ingestKey)
                .font(.system(size: 17))
                .frame(minHeight: PatientTheme.minTouch)
            Button("Guardar chave e URL") { session.configureIngest() }
                .buttonStyle(PatientPrimaryButton())
            Text("Último client_reading_id: \(session.lastClientReadingId ?? "—")")
                .font(.system(size: 15))
            Text("HTTP do último flush: \(session.lastHttpCode == 0 ? "—" : "\(session.lastHttpCode)")")
                .font(.system(size: 17, weight: .semibold))
            Text(session.lastFlushMessage)
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
            Text("Na fila: \(session.pendingCount)")
                .font(.system(size: 15))
        }
        .padding(PatientTheme.margin)
        .background(palette.ai)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct PatientPrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .semibold))
            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
            .foregroundStyle(.white)
            .background(Color(hex: 0x1450A3).opacity(configuration.isPressed ? 0.7 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
