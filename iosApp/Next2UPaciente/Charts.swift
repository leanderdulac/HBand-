import SwiftUI

struct Sparkline: View {
    let values: [Double]
    let color: Color
    var description: String = "Tendência dos últimos 7 dias"

    var body: some View {
        Canvas { context, size in
            let stroke = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
            if values.count < 2 {
                var path = Path()
                path.move(to: CGPoint(x: 0, y: size.height / 2))
                path.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                context.stroke(path, with: .color(color.opacity(0.35)), style: stroke)
                return
            }
            let minV = values.min() ?? 0
            let maxV = values.max() ?? 1
            let range = max(maxV - minV, 1)
            var path = Path()
            for (index, value) in values.enumerated() {
                let x = size.width * CGFloat(index) / CGFloat(max(values.count - 1, 1))
                let y = size.height - CGFloat((value - minV) / range) * size.height
                if index == 0 { path.move(to: CGPoint(x: x, y: y)) }
                else { path.addLine(to: CGPoint(x: x, y: y)) }
            }
            context.stroke(path, with: .color(color), style: stroke)
        }
        .frame(maxWidth: .infinity, minHeight: 36, maxHeight: 36)
        .accessibilityLabel(description)
        .accessibilityIdentifier("vital_sparkline")
    }
}

struct TrendChart: View {
    @Environment(\.patientPalette) private var palette
    let points: [ChartPoint]
    let caption: String
    let color: Color
    var secondary: [ChartPoint] = []
    var secondaryColor: Color? = nil
    var chartDescription: String = "Gráfico. Valores e horários disponíveis na lista abaixo."

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Cada ponto é um registro real deste aparelho. Veja os valores e horários na lista abaixo.")
                .font(.system(size: 17))
                .foregroundStyle(palette.onSurface)
            Text(caption)
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
                .accessibilityIdentifier("expected_range_caption")
            if points.isEmpty {
                Text("Sem medições neste período.")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(palette.onSurface)
            } else {
                Canvas { context, size in
                    let inset: CGFloat = 12
                    let width = max(size.width - 2 * inset, 1)
                    let height = max(size.height - 2 * inset, 1)
                    let values = points.map(\.value) + secondary.map(\.value)
                    let low = values.min() ?? 0
                    let high = values.max() ?? 1
                    let span = max(high - low, 1)
                    let start = points.map(\.timestampMillis).min() ?? 0
                    let end = points.map(\.timestampMillis).max() ?? start
                    func xOf(_ ts: Int64) -> CGFloat {
                        if end == start { return inset + width / 2 }
                        return inset + width * CGFloat(Double(ts - start) / Double(end - start))
                    }
                    func yOf(_ value: Double) -> CGFloat {
                        inset + height * (1 - (value - low) / span)
                    }
                    var axis = Path()
                    axis.move(to: CGPoint(x: inset, y: inset + height))
                    axis.addLine(to: CGPoint(x: inset + width, y: inset + height))
                    context.stroke(axis, with: .color(palette.outline), lineWidth: 1)
                    drawSeries(context, points, color, xOf, yOf)
                    if !secondary.isEmpty {
                        drawSeries(context, secondary, secondaryColor ?? color.opacity(0.55), xOf, yOf)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 180, maxHeight: 180)
                .accessibilityLabel(chartDescription)
                .accessibilityIdentifier("trend_chart")
            }
        }
    }

    private func drawSeries(
        _ context: GraphicsContext,
        _ series: [ChartPoint],
        _ color: Color,
        _ xOf: (Int64) -> CGFloat,
        _ yOf: (Double) -> CGFloat
    ) {
        guard !series.isEmpty else { return }
        var path = Path()
        for (index, point) in series.enumerated() {
            let p = CGPoint(x: xOf(point.timestampMillis), y: yOf(point.value))
            if index == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        for point in series {
            let rect = CGRect(x: xOf(point.timestampMillis) - 4, y: yOf(point.value) - 4, width: 8, height: 8)
            context.fill(Path(ellipseIn: rect), with: .color(color))
        }
    }
}

enum VitalKind: String, CaseIterable {
    case heart, spo2, pressure, temperature

    var title: String {
        switch self {
        case .heart: return "Batimentos"
        case .spo2: return "Oxigênio"
        case .pressure: return "Pressão"
        case .temperature: return "Temperatura"
        }
    }

    var icon: String {
        switch self {
        case .heart: return "heart.fill"
        case .spo2: return "wind"
        case .pressure: return "waveform.path.ecg"
        case .temperature: return "thermometer.medium"
        }
    }
}

struct StatusChip: View {
    @Environment(\.patientPalette) private var palette
    let hasValue: Bool

    var body: some View {
        let label = hasValue ? "Sem faixa" : "Sem dado"
        let container = palette.statusNoDataContainer
        let foreground = palette.statusNoDataForeground
        HStack(spacing: 6) {
            Image(systemName: "minus")
                .font(.system(size: 12, weight: .semibold))
            Text(label)
                .font(.system(size: 13, weight: .semibold))
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .frame(minHeight: 32)
        .background(container)
        .clipShape(Capsule())
        .accessibilityLabel("Situação: \(label)")
        .accessibilityIdentifier("status_chip")
    }
}

struct VitalCard: View {
    @Environment(\.patientPalette) private var palette
    let kind: VitalKind
    let display: String
    let numeric: Double?
    let sparkline: [Double]

    private var color: Color {
        switch kind {
        case .heart: return palette.heart
        case .spo2: return palette.spo2
        case .pressure: return palette.pressure
        case .temperature: return palette.temperature
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(color.opacity(0.12))
                        .frame(width: 36, height: 36)
                    Image(systemName: kind.icon)
                        .foregroundStyle(color)
                }
                Text(kind.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
            Text(display)
                .font(.system(size: 32, weight: .bold))
                .foregroundStyle(palette.onSurface)
                .frame(minHeight: 48, alignment: .leading)
                .accessibilityIdentifier(valueTag)
            Sparkline(
                values: sparkline,
                color: color,
                description: "Tendência de 7 dias de \(kind.title). \(sparkline.count) registros reais."
            )
            StatusChip(hasValue: numeric != nil)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("vital_card_\(kind.rawValue)")
        .accessibilityElement(children: .combine)
    }

    private var valueTag: String {
        switch kind {
        case .heart: return "gauge_heart_rate"
        case .spo2: return "gauge_spo2"
        case .pressure: return "gauge_blood_pressure"
        case .temperature: return "gauge_temp"
        }
    }
}

/// Swift-side period chips. Kotlin Native `ChartPeriod` is a class and is not
/// visible here without `import shared`; Histórico talks to `:shared` with `days`.
enum HistoryPeriod: String, CaseIterable, Identifiable {
    case d7, d30, m3

    var id: String { rawValue }

    var days: Int {
        switch self {
        case .d7: return 7
        case .d30: return 30
        case .m3: return 90
        }
    }

    var label: String {
        switch self {
        case .d7: return "7 dias"
        case .d30: return "30 dias"
        case .m3: return "3 meses"
        }
    }
}

/// Swift-side metric menu. Same labels as Kotlin `ChartMetricType`.
enum HistoryMetric: String, CaseIterable, Identifiable {
    case sevenDaySummary
    case heartRate
    case bloodPressure
    case spo2
    case temperature
    case activity

    var id: String { rawValue }

    var label: String {
        switch self {
        case .sevenDaySummary: return "Resumo de 7 dias"
        case .heartRate: return "Batimentos"
        case .bloodPressure: return "Pressão arterial"
        case .spo2: return "Oxigênio no sangue"
        case .temperature: return "Temperatura"
        case .activity: return "Passos e calorias"
        }
    }

    var showsTrend: Bool {
        switch self {
        case .heartRate, .spo2, .temperature, .bloodPressure: return true
        case .sevenDaySummary, .activity: return false
        }
    }
}

struct ChartPeriodRow: View {
    @Environment(\.patientPalette) private var palette
    @Binding var selected: HistoryPeriod

    var body: some View {
        HStack(spacing: 8) {
            ForEach(HistoryPeriod.allCases) { period in
                Button {
                    selected = period
                } label: {
                    Text(period.label)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(selected == period ? palette.onPrimary : palette.onSurfaceVariant)
                        .frame(minHeight: PatientTheme.minTouch)
                        .padding(.horizontal, 12)
                        .background(selected == period ? palette.primary : palette.surface)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(palette.outlineVariant, lineWidth: selected == period ? 0 : 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("chart_period_\(period.rawValue)")
            }
        }
        .accessibilityIdentifier("chart_period_row")
    }
}
