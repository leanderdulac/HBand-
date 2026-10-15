import SwiftUI
import shared

struct HomeScreen: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var session: VeepooSession
    var onShowHistory: () -> Void
    var onShowProfile: () -> Void
    var onConnectWatch: () -> Void

    @AppStorage("auto_ingest_live") private var autoIngest = true
    @State private var showWatchDetails = false
    @State private var showOtherReadings = false

    private var profileName: String { PatientStore.shared.profile().fullName }
    private var now: Int64 { PatientReadings.nowMillis }
    private var latest: SensorMetric? { PatientCharts.shared.latest() }
    private var connected: Bool { session.connected != nil && session.hardwareReady }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HomeWelcomeHeader(fullName: profileName, onEditProfile: onShowProfile)
                    deviceCard
                    vitalGrid
                    latestReadingCard
                    dailySummary
                    Button("Ver histórico", action: onShowHistory)
                        .buttonStyle(PatientPrimaryButton())
                        .accessibilityIdentifier("home_history_button")
                    syncCard
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var deviceCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "applewatch")
                    .font(.system(size: 22))
                Text("Meu relógio")
                    .font(.system(size: 22, weight: .bold))
            }
            .foregroundStyle(palette.onSurface)
            Text(connected ? "Relógio conectado" : "Relógio desconectado")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(palette.onSurface)
                .accessibilityIdentifier("device_connection_status")
            if let watch = session.connected {
                Text(watch.name)
                    .font(.system(size: 17))
                    .foregroundStyle(palette.onSurface)
            }
            Button(connected ? "Salvar e enviar leitura" : "Conectar meu relógio") {
                if connected { session.flushNow() } else { onConnectWatch() }
            }
            .buttonStyle(PatientPrimaryButton())
            .accessibilityIdentifier(connected ? "spot_check_button" : "connect_watch_button")
            Text(
                connected
                    ? "Salva a leitura disponível e tenta enviar os registros pendentes. Se o acesso estiver pausado, os dados ficam salvos."
                    : "Deixe seu relógio perto deste aparelho. Toque em Conectar meu relógio para procurar e conectar."
            )
            .font(.system(size: 17))
            .foregroundStyle(palette.onSurface)
            HStack {
                Text(connected ? "Bateria: " : "Última carga: ")
                Text(batteryLabel)
                    .fontWeight(.bold)
                    .accessibilityIdentifier("device_battery_level")
            }
            .font(.system(size: 17))
            .foregroundStyle(palette.onSurface)
            if session.batteryPercent == nil {
                Text("A carga da bateria ainda não foi informada.")
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
            Button(showWatchDetails ? "Fechar opções do relógio" : "Opções do relógio") {
                showWatchDetails.toggle()
            }
            .font(.system(size: 17, weight: .semibold))
            .frame(maxWidth: .infinity, minHeight: PatientTheme.minTouch)
            .accessibilityIdentifier("watch_details_button")
            if showWatchDetails {
                Toggle(isOn: $autoIngest) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Guardar novas leituras").font(.system(size: 17, weight: .semibold))
                        Text("Salvar neste aparelho as leituras recebidas do relógio.")
                            .font(.system(size: 15))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                }
                .tint(palette.primary)
                .accessibilityIdentifier("auto_ingest_switch")
                if connected {
                    Button("Desconectar relógio") { session.disconnect() }
                        .buttonStyle(PatientPrimaryButton())
                        .accessibilityIdentifier("disconnect_watch_button")
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityIdentifier("device_control_card")
    }

    private var batteryLabel: String {
        if let percent = session.batteryPercent { return "\(percent)%" }
        return "—"
    }

    private var vitalGrid: some View {
        let kinds = visibleVitals
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            ForEach(kinds, id: \.rawValue) { kind in
                VitalCard(
                    kind: kind,
                    display: display(for: kind),
                    numeric: numeric(for: kind),
                    sparkline: sparkline(for: kind)
                )
            }
        }
        .accessibilityIdentifier("vital_card_grid")
    }

    /// P0 sensors we actually read (FC / SpO₂). PA and temp only if a real stored value exists.
    private var visibleVitals: [VitalKind] {
        var kinds: [VitalKind] = [.heart, .spo2]
        if PatientCharts.shared.hasPressureHistory() { kinds.append(.pressure) }
        if PatientCharts.shared.hasTemperatureHistory() { kinds.append(.temperature) }
        return kinds
    }

    private func display(for kind: VitalKind) -> String {
        let metric = latest
        switch kind {
        case .heart:
            let hr = session.heartRate > 0 ? session.heartRate : Int(metric?.heartRate ?? 0)
            return hr > 0 ? "\(hr) bpm" : "Sem dado"
        case .spo2:
            let o2 = session.spo2 > 0 ? session.spo2 : Int(metric?.spO2 ?? 0)
            return o2 > 0 ? "\(o2)%" : "Sem dado"
        case .pressure:
            if let metric, metric.systolicBp > 0, metric.diastolicBp > 0 {
                return "\(metric.systolicBp)/\(metric.diastolicBp)"
            }
            return "Sem dado"
        case .temperature:
            if let metric, metric.temperatureCelsius.isFinite, metric.temperatureCelsius > 0 {
                return String(format: "%.1f°C", metric.temperatureCelsius)
            }
            return "Sem dado"
        }
    }

    private func numeric(for kind: VitalKind) -> Double? {
        switch kind {
        case .heart:
            let hr = session.heartRate > 0 ? session.heartRate : Int(latest?.heartRate ?? 0)
            return hr > 0 ? Double(hr) : nil
        case .spo2:
            let o2 = session.spo2 > 0 ? session.spo2 : Int(latest?.spO2 ?? 0)
            return o2 > 0 ? Double(o2) : nil
        case .pressure:
            guard let metric = latest, metric.systolicBp > 0 else { return nil }
            return Double(metric.systolicBp)
        case .temperature:
            guard let metric = latest, metric.temperatureCelsius > 0 else { return nil }
            return Double(metric.temperatureCelsius)
        }
    }

    private func sparkline(for kind: VitalKind) -> [Double] {
        switch kind {
        case .heart: return PatientReadings.floats(PatientCharts.shared.heartSparkline(nowMillis: now))
        case .spo2: return PatientReadings.floats(PatientCharts.shared.spo2Sparkline(nowMillis: now))
        case .pressure: return PatientReadings.floats(PatientCharts.shared.pressureSparkline(nowMillis: now))
        case .temperature: return PatientReadings.floats(PatientCharts.shared.temperatureSparkline(nowMillis: now))
        }
    }

    private var latestReadingCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Última leitura disponível")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(palette.onSurface)
            if latest == nil && session.heartRate == 0 {
                Text("Ainda não há uma leitura disponível.")
                    .font(.system(size: 17))
                Text("Confira a conexão do relógio e tente ler os dados.")
                    .font(.system(size: 17))
            } else {
                Text(PatientReadings.formatRecorded(latest?.timestampMillis ?? now))
                    .font(.system(size: 17))
                Button(showOtherReadings ? "Fechar outras leituras" : "Ver outras leituras") {
                    showOtherReadings.toggle()
                }
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                .accessibilityIdentifier("telemetry_details_button")
                if showOtherReadings {
                    detailRow("Variação dos batimentos — índice do relógio", value(latest?.hrvScore, suffix: ""))
                    detailRow("Passos", value(latest?.steps, suffix: ""))
                    if let calories = latest?.calories, calories > 0 {
                        detailRow("Calorias", String(format: "%.0f kcal", calories))
                    } else {
                        detailRow("Calorias", nil)
                    }
                    if let meters = latest?.distanceMeters, meters > 0 {
                        detailRow("Distância", String(format: "%.2f km", meters / 1000))
                    } else {
                        detailRow("Distância", nil)
                    }
                }
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("latest_watch_reading")
    }

    private func value(_ number: Int32?, suffix: String) -> String? {
        guard let number, number > 0 else { return nil }
        return suffix.isEmpty ? "\(number)" : "\(number)\(suffix)"
    }

    @ViewBuilder
    private func detailRow(_ label: String, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Divider()
            Text(label).font(.system(size: 17))
            Text(value ?? "Sem medição disponível")
                .font(.system(size: 20, weight: .semibold))
        }
    }

    private var dailySummary: some View {
        let today = PatientReadings.savedWeek().last
        return VStack(alignment: .leading, spacing: 16) {
            Text("Registros de hoje")
                .font(.system(size: 22, weight: .semibold))
            if let today {
                Text(today.dateLabel).font(.system(size: 17))
                SavedDayValues(day: today)
            }
            Text("Tempo em atividade: indisponível. Os registros atuais não informam essa duração.")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("daily_health_summary_card")
    }

    private var syncCard: some View {
        let pending = session.pendingCount
        let title = pending > 0 ? "Há registros aguardando envio" : "Envio de dados"
        let body = pending > 0
            ? "Os registros continuam na fila do aplicativo. Você pode tentar enviá-los agora."
            : "A fila exibida não tem envios pendentes. Isso não confirma o recebimento pela equipe de saúde."
        return VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 22, weight: .semibold))
            Text(body)
                .font(.system(size: 17))
            if pending > 0 {
                Text("Na fila: \(pending)")
                    .font(.system(size: 17, weight: .semibold))
            }
            Button("Enviar agora") { session.flushNow() }
                .buttonStyle(PatientPrimaryButton())
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityIdentifier("workmanager_sync_status_card")
    }
}

struct SavedDayValues: View {
    @Environment(\.patientPalette) private var palette
    let day: SavedDay

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if day.recordCount == 0 {
                Text("Sem registros neste dia")
                    .font(.system(size: 17, weight: .semibold))
                Text("Para buscar dados do relógio, abra Relógio e confira a conexão.")
                    .font(.system(size: 17))
            } else {
                Text(day.recordCount == 1 ? "1 registro salvo" : "\(day.recordCount) registros salvos")
                    .font(.system(size: 17))
                labeled("Média dos batimentos por minuto", day.averageHeartRate.map { "\($0) bpm" })
                labeled("Passos — maior valor salvo", day.highestSteps.map { "\($0)" })
                labeled("Calorias — maior valor salvo", day.highestCalories.map { "\($0) kcal" })
                Text("Os registros podem estar incompletos. Não representam uma medição contínua de todo o dia.")
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .foregroundStyle(palette.onSurface)
    }

    private func labeled(_ label: String, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.system(size: 17))
            Text(value ?? "Sem medição disponível")
                .font(.system(size: 20, weight: .semibold))
        }
    }
}
