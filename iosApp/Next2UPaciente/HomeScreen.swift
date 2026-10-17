import SwiftUI
import shared

struct HomeScreen: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var session: VeepooSession
    var onShowHistory: () -> Void
    var onShowProfile: () -> Void
    var onConnectWatch: () -> Void

    @AppStorage("auto_ingest_live") private var autoIngest = true
    @StateObject private var insight = GeminiInsightController()
    @State private var showWatchDetails = false
    @State private var showOtherReadings = false
    @State private var renameDraft = ""

    private var profileName: String {
        _ = session.profileRevision
        return PatientStore.shared.profile().fullName
    }
    private var now: Int64 { PatientReadings.nowMillis }
    private var latest: SensorMetric? { PatientCharts.shared.latest() }
    private var connected: Bool { session.connected != nil && session.hardwareReady }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HomeWelcomeHeader(fullName: profileName, onEditProfile: onShowProfile)
                    deviceCard
                    if insight.highlightEnabled {
                        AiInsightHighlightCard(controller: insight)
                    }
                    vitalGrid
                    latestReadingCard
                    dailySummary
                    Button("Ver histórico", action: onShowHistory)
                        .buttonStyle(PatientPrimaryButton())
                        .accessibilityIdentifier("home_history_button")
                    syncCard
                    if !insight.highlightEnabled {
                        GeminiValidationCard()
                    }
                    AppVersionFooter()
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .patientChrome()
            .onAppear {
                renameDraft = session.displayWatchName
                insight.appear()
            }
            .onChange(of: session.profileRevision) { _ in
                insight.profileDidChange()
            }
            .onChange(of: session.watchAlias) { value in
                if renameDraft.isEmpty || renameDraft == session.connected?.name {
                    renameDraft = value
                }
            }
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
            if session.connected != nil || !session.displayWatchName.isEmpty {
                Text(session.displayWatchName)
                    .font(.system(size: 17))
                    .foregroundStyle(palette.onSurface)
                    .accessibilityIdentifier("paired_watch_name")
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
                        Text("Guardar novas leituras")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(palette.onSurface)
                        Text("Salvar neste aparelho as leituras recebidas do relógio.")
                            .font(.system(size: 15))
                            .foregroundStyle(palette.onSurfaceVariant)
                    }
                }
                .tint(palette.primary)
                .foregroundStyle(palette.onSurface)
                .accessibilityIdentifier("auto_ingest_switch")
                if connected {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Nome do relógio")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(palette.onSurface)
                        TextField("Nome do relógio", text: $renameDraft)
                            .font(.system(size: 17))
                            .patientFieldInk(palette)
                            .frame(minHeight: PatientTheme.minTouch)
                            .accessibilityIdentifier("input_watch_name")
                        Button("Renomear relógio") { session.renameWatch(renameDraft) }
                            .buttonStyle(PatientPrimaryButton())
                            .accessibilityIdentifier("rename_watch_button")
                        if !session.renameStatus.isEmpty {
                            Text(session.renameStatus)
                                .font(.system(size: 15))
                                .foregroundStyle(palette.onSurfaceVariant)
                        }
                    }
                    Button("Desconectar relógio") { session.disconnect() }
                        .buttonStyle(PatientPrimaryButton())
                        .accessibilityIdentifier("disconnect_watch_button")
                }
            }
        }
        .foregroundStyle(palette.onSurface)
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

/// Highlighted Android `AiInsightHighlightCard` — shown when `AI_INSIGHT_ENABLED` is true/YES.
struct AiInsightHighlightCard: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var controller: GeminiInsightController

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(palette.surface).frame(width: 44, height: 44)
                    Image(systemName: "sparkles")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(palette.primary)
                        .accessibilityIdentifier("ai_insight_icon")
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(GeminiInsight.shared.highlightTitle())
                        .font(.system(size: 22, weight: .bold))
                        .accessibilityAddTraits(.isHeader)
                    Text(generatedLabel)
                        .font(.system(size: 15))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .accessibilityIdentifier("ai_insight_generated_at")
                }
                Spacer()
            }
            insightBody
            Text(GeminiInsight.shared.reviewLabel())
                .font(.system(size: 13))
                .foregroundStyle(palette.onSurfaceVariant)
                .accessibilityIdentifier("ai_insight_review_label")
            Button {
                controller.refresh(force: true)
            } label: {
                HStack {
                    Image(systemName: "arrow.clockwise")
                    Text(GeminiInsight.shared.refreshLabel())
                }
            }
            .buttonStyle(PatientPrimaryButton())
            .disabled(controller.isLoading)
            .opacity(controller.isLoading ? 0.6 : 1)
            .accessibilityIdentifier("refresh_gemini_insight_button")
        }
        .foregroundStyle(palette.onPrimaryContainer)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [palette.primaryContainer, palette.primaryContainer.opacity(0.55), palette.ai],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(palette.primary.opacity(0.35), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityIdentifier("ai_insight_highlight_card")
    }

    @ViewBuilder
    private var insightBody: some View {
        if controller.isLoading {
            VStack(alignment: .leading, spacing: 10) {
                ProgressView().tint(palette.primary)
                InsightShimmerLines()
                Text(GeminiInsight.shared.loadingLabel()).font(.system(size: 17))
            }
            .accessibilityIdentifier("ai_insight_loading")
        } else if controller.failed {
            Text(GeminiInsight.shared.errorCard())
                .font(.system(size: 17))
                .accessibilityIdentifier("ai_insight_error")
        } else if controller.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            Text(GeminiInsight.shared.emptyCard())
                .font(.system(size: 17))
                .accessibilityIdentifier("ai_insight_empty")
        } else {
            Text(controller.text)
                .font(.system(size: 17))
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("gemini_insight_text")
        }
    }

    private var generatedLabel: String {
        guard let date = controller.generatedAt else { return GeminiInsight.shared.sevenDaysLabel() }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt-BR")
        formatter.dateFormat = "HH:mm"
        return "Gerado às \(formatter.string(from: date))"
    }
}

struct GeminiValidationCard: View {
    @Environment(\.patientPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(GeminiInsight.shared.validationTitle())
                .font(.system(size: 22, weight: .semibold))
            Text(GeminiInsight.shared.validationBody())
                .font(.system(size: 17))
            Text(GeminiInsight.shared.validationHint())
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.statusAttentionContainer)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("ai_insight_validation_card")
    }
}

struct InsightShimmerLines: View {
    @Environment(\.patientPalette) private var palette
    @State private var pulse = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach([1.0, 0.92, 0.6], id: \.self) { fraction in
                GeometryReader { geo in
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(palette.surface)
                        .frame(width: geo.size.width * fraction, height: 14)
                        .opacity(pulse ? 0.9 : 0.35)
                }
                .frame(height: 14)
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }
}

@MainActor
final class GeminiInsightController: ObservableObject {
    @Published var text = ""
    @Published var isLoading = false
    @Published var failed = false
    @Published var generatedAt: Date?

    private var lastRateLimit: Date?
    private var lastStarted: Date?
    private let cooldown: TimeInterval = 60
    private let minInterval: TimeInterval = 15 * 60

    var highlightEnabled: Bool {
        GeminiInsight.shared.isHighlightEnabled(
            flag: VeepooSession.bundledString("Next2UAiInsightEnabled") ?? "YES"
        )
    }

    func appear() {
        guard highlightEnabled, text.isEmpty, !isLoading else { return }
        refresh(force: true)
    }

    func profileDidChange() {
        guard highlightEnabled else { return }
        refresh(force: true)
    }

    func refresh(force: Bool) {
        guard !isLoading else { return }
        if !force, let lastStarted, Date().timeIntervalSince(lastStarted) < minInterval { return }
        isLoading = true
        failed = false
        lastStarted = Date()
        let now = PatientReadings.nowMillis
        Task { await generate(nowMillis: now) }
    }

    private func generate(nowMillis: Int64) async {
        let fallback = GeminiInsight.shared.fallbackFromStore(nowMillis: nowMillis)
        let prompt = GeminiInsight.shared.buildPromptFromStore(nowMillis: nowMillis)
        guard let prompt else {
            finish(text: fallback, failed: false)
            return
        }
        let key = VeepooSession.bundledString("Next2UGeminiApiKey") ?? ""
        if GeminiInsight.shared.isPlaceholderKey(key: key) {
            finish(text: fallback, failed: false)
            return
        }
        if let lastRateLimit, Date().timeIntervalSince(lastRateLimit) < cooldown {
            finish(text: fallback, failed: false)
            return
        }
        do {
            let url = URL(string: GeminiInsight.shared.generateUrl(apiKey: key))!
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.timeoutInterval = 30
            request.httpBody = try JSONSerialization.data(withJSONObject: [
                "contents": [["parts": [["text": prompt]]]],
            ])
            let (data, response) = try await URLSession.shared.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            if code == 429 { lastRateLimit = Date() }
            if (200...299).contains(code), let parsed = Self.parseText(data), !parsed.isEmpty {
                finish(text: parsed, failed: false)
                return
            }
            finish(text: fallback, failed: false)
        } catch {
            finish(text: fallback, failed: true)
        }
    }

    private func finish(text: String, failed: Bool) {
        self.text = text
        self.failed = failed
        self.generatedAt = Date()
        self.isLoading = false
    }

    private static func parseText(_ data: Data) -> String? {
        guard
            let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let candidates = root["candidates"] as? [[String: Any]],
            let content = candidates.first?["content"] as? [String: Any]
        else { return nil }
        let parts: [[String: Any]]
        if let typed = content["parts"] as? [[String: Any]] {
            parts = typed
        } else if let array = content["parts"] as? NSArray {
            parts = array.map { $0 }.compactMap { $0 as? [String: Any] }
        } else {
            return nil
        }
        let text = (parts.first?["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (text?.isEmpty == false) ? text : nil
    }
}
