import SwiftUI
import shared

struct HistoryScreen: View {
    @Environment(\.patientPalette) private var palette
    @State private var selected = ChartMetricType.sevenDaySummary
    @State private var period = ChartPeriod.d7
    @State private var showGraph = false
    @State private var menuOpen = false
    @State private var daysAgo = 0

    private var now: Int64 { PatientReadings.nowMillis }
    private var week: [SavedDay] { PatientReadings.savedWeek() }
    private static let metricTypes: [ChartMetricType] = [
        .sevenDaySummary, .heartRate, .bloodPressure, .spo2, .temperature, .activity,
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Histórico")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityAddTraits(.isHeader)
                    Text("Escolha o que deseja consultar.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                    metricMenu
                    if selected == .sevenDaySummary {
                        sevenDayCard
                    } else {
                        metricCard
                    }
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
        }
        .accessibilityIdentifier("recharts_sensor_dashboard")
    }

    private var metricMenu: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button("Ver: \(selected.label)") { menuOpen.toggle() }
                .buttonStyle(PatientPrimaryButton())
                .accessibilityIdentifier("history_metric_menu")
            if menuOpen {
                ForEach(Self.metricTypes, id: \.name) { type in
                    Button {
                        selected = type
                        menuOpen = false
                        showGraph = false
                    } label: {
                        Text(type.label)
                            .font(.system(size: 17))
                            .foregroundStyle(palette.onSurface)
                            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var sevenDayCard: some View {
        let selectedDay = week.indices.contains(6 - daysAgo) ? week[6 - daysAgo] : week.last
        return VStack(alignment: .leading, spacing: 16) {
            Text("Últimos 7 dias")
                .font(.system(size: 22, weight: .semibold))
            Text("Registros salvos neste aparelho. As datas seguem o horário do aparelho.")
                .font(.system(size: 17))
            if let selectedDay {
                Text(selectedDay.dateLabel)
                    .font(.system(size: 17, weight: .semibold))
                    .accessibilityIdentifier("history_selected_date")
                HStack(spacing: 12) {
                    Button("Dia anterior") { daysAgo = min(daysAgo + 1, 6) }
                        .buttonStyle(PatientPrimaryButton())
                        .disabled(daysAgo >= 6)
                        .accessibilityIdentifier("history_previous_day")
                    Button("Dia seguinte") { daysAgo = max(daysAgo - 1, 0) }
                        .buttonStyle(PatientPrimaryButton())
                        .disabled(daysAgo <= 0)
                        .accessibilityIdentifier("history_next_day")
                }
                if daysAgo > 0 {
                    Button("Voltar para hoje") { daysAgo = 0 }
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                        .accessibilityIdentifier("history_today")
                }
                SavedDayValues(day: selectedDay)
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("recharts_7day_summary_card")
    }

    private var metricCard: some View {
        let records = recentRecords
        return VStack(alignment: .leading, spacing: 16) {
            Text(selected == .heartRate ? "Batimentos por minuto" : selected.label)
                .font(.system(size: 22, weight: .semibold))
            if records.isEmpty {
                Text("Sem medições disponíveis")
                    .font(.system(size: 17, weight: .semibold))
                Text("Ainda não há registros com data e valor disponíveis para esta medição. Confira a conexão na aba Relógio.")
                    .font(.system(size: 17))
            } else {
                Text("Até 15 registros mais recentes neste celular. As datas seguem o horário do celular.")
                    .font(.system(size: 17))
                if selected == .heartRate || selected == .spo2 || selected == .temperature || selected == .bloodPressure {
                    ChartPeriodRow(selected: $period)
                    Button(showGraph ? "Fechar gráfico" : "Ver gráfico") { showGraph.toggle() }
                        .buttonStyle(PatientPrimaryButton())
                    if showGraph { plot }
                }
                ForEach(records, id: \.id) { row in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(row.when).font(.system(size: 17))
                        Text(row.value).font(.system(size: 20, weight: .semibold))
                        Divider()
                    }
                }
                Text("A ausência de um registro não significa que a medição foi normal.")
                    .font(.system(size: 15))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var plot: some View {
        let caption: String
        let color: Color
        let points: [ChartPoint]
        var secondary: [ChartPoint] = []
        switch selected {
        case .heartRate:
            caption = ExpectedRangeCopy.caption(for: "heart_rate")
            color = palette.heart
            points = PatientReadings.points(PatientCharts.shared.heartTrend(nowMillis: now, period: period))
        case .spo2:
            caption = ExpectedRangeCopy.caption(for: "spo2")
            color = palette.spo2
            points = PatientReadings.points(PatientCharts.shared.spo2Trend(nowMillis: now, period: period))
        case .temperature:
            caption = ExpectedRangeCopy.caption(for: "temperature")
            color = palette.temperature
            points = PatientReadings.points(PatientCharts.shared.temperatureTrend(nowMillis: now, period: period))
        case .bloodPressure:
            caption = ExpectedRangeCopy.caption(for: "bp_systolic")
            color = palette.pressure
            points = PatientReadings.points(PatientCharts.shared.pressureTrend(nowMillis: now, period: period))
            secondary = PatientReadings.points(PatientCharts.shared.diastolicTrend(nowMillis: now, period: period))
        default:
            caption = ExpectedRangeCopy.unconfigured
            color = palette.primary
            points = []
        }
        return TrendChart(
            points: points,
            caption: caption,
            color: color,
            secondary: secondary,
            chartDescription: "Gráfico de \(selected.label). Valores e horários disponíveis na lista abaixo."
        )
    }

    private var recentRecords: [HistoryRow] {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt-BR")
        formatter.dateFormat = "dd/MM/yyyy 'às' HH:mm"
        return PatientReadings.metrics()
            .filter { $0.timestampMillis > 0 && $0.timestampMillis <= now }
            .compactMap { metric -> HistoryRow? in
                guard let value = label(for: metric) else { return nil }
                let when = formatter.string(from: Date(timeIntervalSince1970: Double(metric.timestampMillis) / 1000))
                return HistoryRow(id: metric.id, when: when, value: value)
            }
            .sorted { $0.id > $1.id }
            .prefix(15)
            .map { $0 }
    }

    private func label(for metric: SensorMetric) -> String? {
        switch selected {
        case .sevenDaySummary: return nil
        case .heartRate: return metric.heartRate > 0 ? "\(metric.heartRate) bpm" : nil
        case .bloodPressure:
            return metric.systolicBp > 0 && metric.diastolicBp > 0
                ? "\(metric.systolicBp) / \(metric.diastolicBp) mmHg" : nil
        case .spo2: return metric.spO2 > 0 ? "\(metric.spO2)%" : nil
        case .temperature:
            return metric.temperatureCelsius > 0 ? String(format: "%.1f °C", metric.temperatureCelsius) : nil
        case .activity:
            var parts: [String] = []
            if metric.steps > 0 { parts.append("\(metric.steps) passos") }
            if metric.calories.isFinite && metric.calories > 0 {
                parts.append("\(Int(metric.calories)) kcal")
            }
            return parts.isEmpty ? nil : parts.joined(separator: " · ")
        }
    }
}

private struct HistoryRow {
    let id: Int64
    let when: String
    let value: String
}
