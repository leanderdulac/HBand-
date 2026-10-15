import Foundation
import shared

enum PatientReadings {
    static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    static func metrics() -> [SensorMetric] {
        nsArray(PatientCharts.shared.allMetrics()).compactMap { $0 as? SensorMetric }
    }

    static func queueItems(_ values: Any?) -> [StoredQueueItem] {
        nsArray(values).compactMap { $0 as? StoredQueueItem }
    }

    static func floats(_ values: Any?) -> [Double] {
        nsArray(values).compactMap { item in
            if let number = item as? NSNumber { return number.doubleValue }
            return nil
        }
    }

    static func points(_ values: Any?) -> [ChartPoint] {
        nsArray(values).compactMap { item in
            guard let point = item as? TrendPoint else { return nil }
            return ChartPoint(timestampMillis: point.timestampMillis, value: Double(point.value))
        }
    }

    static func formatRecorded(_ millis: Int64) -> String {
        guard millis > 0 else { return "Horário do registro indisponível" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt-BR")
        formatter.dateFormat = "dd/MM/yyyy 'às' HH:mm"
        return "Registro de " + formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    static func weekdayLabel(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt-BR")
        formatter.dateFormat = "EEEE, dd/MM/yyyy"
        return formatter.string(from: date)
    }

    static func savedWeek(now: Date = Date()) -> [SavedDay] {
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: metrics()) { metric -> String in
            let date = Date(timeIntervalSince1970: Double(metric.timestampMillis) / 1000)
            let comps = calendar.dateComponents([.year, .month, .day], from: date)
            return "\(comps.year ?? 0)-\(comps.month ?? 0)-\(comps.day ?? 0)"
        }
        return (0...6).reversed().map { daysAgo in
            let day = calendar.date(byAdding: .day, value: -daysAgo, to: now) ?? now
            let comps = calendar.dateComponents([.year, .month, .day], from: day)
            let key = "\(comps.year ?? 0)-\(comps.month ?? 0)-\(comps.day ?? 0)"
            let records = grouped[key] ?? []
            let heart = records.map { Int($0.heartRate) }.filter { $0 > 0 }
            let steps = records.map { Int($0.steps) }.filter { $0 > 0 }
            let calories = records.map { $0.calories }.filter { $0.isFinite && $0 > 0 }
            return SavedDay(
                dateLabel: weekdayLabel(day),
                recordCount: records.count,
                averageHeartRate: heart.isEmpty ? nil : heart.reduce(0, +) / heart.count,
                highestSteps: steps.max(),
                highestCalories: calories.max().map { Int($0) }
            )
        }
    }

    private static func nsArray(_ value: Any?) -> [Any] {
        if let array = value as? [Any] { return array }
        if let array = value as? NSArray { return array.map { $0 } }
        return []
    }
}

struct ChartPoint {
    let timestampMillis: Int64
    let value: Double
}

struct SavedDay {
    let dateLabel: String
    let recordCount: Int
    let averageHeartRate: Int?
    let highestSteps: Int?
    let highestCalories: Int?
}

enum ExpectedRangeCopy {
    static let unconfigured = "Faixa esperada: não configurada (placeholder R2/R3) — não é limiar clínico"

    static func caption(for key: String) -> String {
        switch key {
        case "heart_rate": return ExpectedRangePlaceholders.shared.heartRate.caption
        case "spo2": return ExpectedRangePlaceholders.shared.spo2.caption
        case "bp_systolic": return ExpectedRangePlaceholders.shared.systolic.caption
        case "temperature": return ExpectedRangePlaceholders.shared.temperature.caption
        default: return unconfigured
        }
    }
}
