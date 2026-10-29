import SwiftUI
import shared

struct HomeScreen: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var session: VeepooSession
    var onShowHistory: () -> Void
    var onShowProfile: () -> Void
    var onConnectWatch: () -> Void

    @StateObject private var insight = GeminiInsightController()

    private var profileName: String {
        _ = session.profileRevision
        return PatientStore.shared.profile().fullName
    }
    private var hardwareReady: Bool { session.connected != nil && session.hardwareReady }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HomeWelcomeHeader(fullName: profileName, onEditProfile: onShowProfile)
                    watchStatusCard
                    if insight.highlightEnabled {
                        AiInsightHighlightCard(controller: insight)
                    }
                    ForEach(homeMetrics) { metric in
                        HomeHealthCard(metric: metric)
                    }
                    Button("Ver histórico", action: onShowHistory)
                        .buttonStyle(PatientPrimaryButton())
                        .accessibilityIdentifier("home_history_button")
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
            .onAppear { insight.appear() }
            .onChange(of: session.profileRevision) { _ in
                insight.profileDidChange()
            }
        }
    }

    private var watchStatusCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "applewatch")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(palette.primary)
                VStack(alignment: .leading, spacing: 4) {
                    Text(watchName)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityIdentifier("paired_watch_name")
                    Text(watchStatus)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityIdentifier("device_connection_status")
                }
                Spacer()
            }
            if !hardwareReady {
                Button(session.isScanning ? "Procurando…" : "Conectar meu relógio") {
                    onConnectWatch()
                }
                .buttonStyle(PatientPrimaryButton())
                .disabled(session.isScanning)
                .accessibilityIdentifier("connect_watch_button")
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityIdentifier("device_control_card")
    }

    private var watchName: String {
        let name = session.displayWatchName.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? "Relógio" : name
    }

    private var watchStatus: String {
        if hardwareReady { return "Conectado" }
        if session.isScanning { return "Procurando" }
        if session.connected != nil { return "Conectando" }
        return "Desconectado"
    }

    private var homeMetrics: [HomeMetricCard] {
        var cards: [HomeMetricCard] = [
            HomeMetricCard.heart(live: session.heartRate),
            HomeMetricCard.spo2(live: session.spo2),
            .hrv,
            .sleep,
            .steps,
        ]
        if PatientCharts.shared.hasPressureHistory() || HomeMetricCard.pressure.available {
            cards.append(.pressure)
        }
        return cards
    }
}

struct HomeMetricCard: Identifiable {
    let id: String
    let title: String
    let icon: String
    let value: String
    let time: String
    let hasValue: Bool

    var available: Bool { hasValue }

    static func heart(live: Int) -> HomeMetricCard {
        if live > 0 {
            return HomeMetricCard(id: "heart", title: "Batimentos", icon: "heart.fill",
                                  value: "\(live) bpm", time: "Agora", hasValue: true)
        }
        let row = PatientReadings.latest { $0.heartRate > 0 }
        if let row {
            return HomeMetricCard(id: "heart", title: "Batimentos", icon: "heart.fill",
                                  value: "\(row.heartRate) bpm",
                                  time: PatientReadings.shortTime(row.timestampMillis), hasValue: true)
        }
        return HomeMetricCard(id: "heart", title: "Batimentos", icon: "heart.fill",
                              value: "Sem dado", time: "Ainda não há medição", hasValue: false)
    }

    static func spo2(live: Int) -> HomeMetricCard {
        if live > 0 {
            return HomeMetricCard(id: "spo2", title: "Oxigênio", icon: "wind",
                                  value: "\(live)%", time: "Agora", hasValue: true)
        }
        let row = PatientReadings.latest { $0.spO2 > 0 }
        if let row {
            return HomeMetricCard(id: "spo2", title: "Oxigênio", icon: "wind",
                                  value: "\(row.spO2)%",
                                  time: PatientReadings.shortTime(row.timestampMillis), hasValue: true)
        }
        return HomeMetricCard(id: "spo2", title: "Oxigênio", icon: "wind",
                              value: "Sem dado", time: "Ainda não há medição", hasValue: false)
    }

    static var hrv: HomeMetricCard {
        let row = PatientReadings.latest { $0.hrvScore > 0 }
        if let row {
            return HomeMetricCard(id: "hrv", title: "Variação dos batimentos", icon: "waveform.path.ecg",
                                  value: "\(row.hrvScore)",
                                  time: PatientReadings.shortTime(row.timestampMillis), hasValue: true)
        }
        return HomeMetricCard(id: "hrv", title: "Variação dos batimentos", icon: "waveform.path.ecg",
                              value: "Sem dado", time: "Ainda não há medição", hasValue: false)
    }

    static var sleep: HomeMetricCard {
        let row = PatientReadings.latest { $0.deepSleepMinutes + $0.lightSleepMinutes > 0 }
        if let row {
            let total = Int(row.deepSleepMinutes + row.lightSleepMinutes)
            let hours = total / 60
            let minutes = total % 60
            let text = hours > 0 ? "\(hours) h \(minutes) min" : "\(minutes) min"
            return HomeMetricCard(id: "sleep", title: "Sono", icon: "moon.zzz.fill",
                                  value: text,
                                  time: PatientReadings.shortTime(row.timestampMillis), hasValue: true)
        }
        return HomeMetricCard(id: "sleep", title: "Sono", icon: "moon.zzz.fill",
                              value: "Sem dado", time: "Ainda não há medição", hasValue: false)
    }

    static var steps: HomeMetricCard {
        let row = PatientReadings.latest { $0.steps > 0 }
        if let row {
            return HomeMetricCard(id: "steps", title: "Passos", icon: "figure.walk",
                                  value: "\(row.steps)",
                                  time: PatientReadings.shortTime(row.timestampMillis), hasValue: true)
        }
        return HomeMetricCard(id: "steps", title: "Passos", icon: "figure.walk",
                              value: "Sem dado", time: "Ainda não há medição", hasValue: false)
    }

    static var pressure: HomeMetricCard {
        let row = PatientReadings.latest { $0.systolicBp > 0 && $0.diastolicBp > 0 }
        if let row {
            return HomeMetricCard(id: "pressure", title: "Pressão", icon: "drop.fill",
                                  value: "\(row.systolicBp)/\(row.diastolicBp)",
                                  time: PatientReadings.shortTime(row.timestampMillis), hasValue: true)
        }
        return HomeMetricCard(id: "pressure", title: "Pressão", icon: "drop.fill",
                              value: "Sem dado", time: "Ainda não há medição", hasValue: false)
    }
}

struct HomeHealthCard: View {
    @Environment(\.patientPalette) private var palette
    let metric: HomeMetricCard

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(palette.primaryContainer)
                    .frame(width: 52, height: 52)
                Image(systemName: metric.icon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(palette.primary)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(metric.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(palette.onSurfaceVariant)
                Text(metric.value)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(palette.onSurface)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                Text(metric.time)
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("home_metric_\(metric.id)")
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
