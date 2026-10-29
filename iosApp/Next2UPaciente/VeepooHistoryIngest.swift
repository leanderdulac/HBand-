import Foundation
import VeepooBleSDK
import shared

/// Maps Veepoo SDK database history (filled by `veepooSdkStartReadDeviceAllData`)
/// into the shared ingest queue. IDs are device + metric + record time so the
/// Core treats reconnect/replay as the same reading.
enum VeepooHistoryIngest {
    static let maxDays = 7

    struct Sample {
        let metricType: String
        let timestamp: Date
        let heartRate: Int
        let spo2: Int
        let systolic: Int
        let diastolic: Int
        let temperatureCelsius: Float
        let steps: Int
        let calories: Float
        let distanceMeters: Float
        let hrvScore: Int
        let deepSleepMinutes: Int
        let lightSleepMinutes: Int
        let awakeMinutes: Int
    }

    static func samples(deviceId: String, days: Int) -> [Sample] {
        // VPDataBaseOperation: table ID is the watch MAC (`andTableID`).
        let tableId = deviceId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !tableId.isEmpty else { return [] }
        let dayCount = min(max(days, 1), maxDays)
        var collected: [Sample] = []
        for day in dayStrings(count: dayCount) {
            collected += originSamples(day: day, tableId: tableId)
            collected += oxygenSamples(day: day, tableId: tableId)
            collected += hrvSamples(day: day, tableId: tableId)
            collected += sleepSamples(day: day, tableId: tableId)
        }
        return collected
    }

    static func enqueue(
        samples: [Sample],
        deviceId: String,
        deviceModel: String,
        patientId: String
    ) async -> Int {
        var queued = 0
        for sample in samples {
            let millis = Int64((sample.timestamp.timeIntervalSince1970 * 1000).rounded())
            guard millis > 0 else { continue }
            guard let recorded = try? await IngestRuntime.shared.recordHistorySample(
                deviceId: deviceId,
                deviceModel: deviceModel,
                timestampIso: isoUtc(sample.timestamp),
                timestampMillis: millis,
                metricType: sample.metricType,
                heartRate: Int32(sample.heartRate),
                spo2: Int32(sample.spo2),
                systolic: Int32(sample.systolic),
                diastolic: Int32(sample.diastolic),
                temperatureCelsius: sample.temperatureCelsius,
                steps: Int32(sample.steps),
                calories: sample.calories,
                distanceMeters: sample.distanceMeters,
                hrvScore: Int32(sample.hrvScore),
                deepSleepMinutes: Int32(sample.deepSleepMinutes),
                lightSleepMinutes: Int32(sample.lightSleepMinutes),
                awakeMinutes: Int32(sample.awakeMinutes),
                patientId: patientId
            ) else { continue }
            if recorded.queued { queued += 1 }
        }
        return queued
    }

    private static func originSamples(day: String, tableId: String) -> [Sample] {
        let raw = VPDataBaseOperation.veepooSDKGetOriginalData(withDate: day, andTableID: tableId)
        guard let dict = raw as? [String: Any] else { return [] }
        return dict.compactMap { timeKey, value in
            guard let row = value as? [String: Any] else { return nil }
            guard let date = combine(day: day, time: timeKey) else { return nil }
            let heart = heartValue(in: row)
            let sys = int(row["systolic"])
            let dia = int(row["diastolic"])
            let steps = max(0, int(row["stepValue"]))
            let calories = float(row["calValue"])
            let distanceKm = float(row["disValue"])
            let temp = float(row["temperature"])
            if heart == 0, sys == 0, dia == 0, steps == 0, temp < 30 { return nil }
            return Sample(
                metricType: "origin",
                timestamp: date,
                heartRate: heart,
                spo2: 0,
                systolic: sys >= 60 && sys <= 240 ? sys : 0,
                diastolic: dia >= 30 && dia <= 160 ? dia : 0,
                temperatureCelsius: temp >= 30 && temp <= 43 ? temp : 0,
                steps: steps,
                calories: max(0, calories),
                distanceMeters: max(0, distanceKm) * 1000,
                hrvScore: 0,
                deepSleepMinutes: 0,
                lightSleepMinutes: 0,
                awakeMinutes: 0
            )
        }
    }

    private static func oxygenSamples(day: String, tableId: String) -> [Sample] {
        let raw = VPDataBaseOperation.veepooSDKGetDeviceOxygenData(withDate: day, andTableID: tableId)
        guard let rows = raw as? [Any] else { return [] }
        return rows.compactMap { item in
            guard let row = item as? [String: Any] else { return nil }
            let time = string(row["Time"]) ?? string(row["time"]) ?? ""
            guard let date = combine(day: day, time: time) else { return nil }
            let oxygen = int(row["OxygenValue"]) != 0 ? int(row["OxygenValue"]) : int(row["oxygenValue"])
            let heart = int(row["HeartValue"]) != 0 ? int(row["HeartValue"]) : int(row["heartValue"])
            let validOxygen = (50...100).contains(oxygen) ? oxygen : 0
            let validHeart = (30...240).contains(heart) ? heart : 0
            if validOxygen == 0, validHeart == 0 { return nil }
            return Sample(
                metricType: "spo2",
                timestamp: date,
                heartRate: validHeart,
                spo2: validOxygen,
                systolic: 0,
                diastolic: 0,
                temperatureCelsius: 0,
                steps: max(0, int(row["StepValue"]) != 0 ? int(row["StepValue"]) : int(row["stepValue"])),
                calories: 0,
                distanceMeters: 0,
                hrvScore: max(0, int(row["HRV"]) != 0 ? int(row["HRV"]) : int(row["hrvValue"])),
                deepSleepMinutes: 0,
                lightSleepMinutes: 0,
                awakeMinutes: 0
            )
        }
    }

    private static func hrvSamples(day: String, tableId: String) -> [Sample] {
        let raw = VPDataBaseOperation.veepooSDKGetDeviceHrvData(withDate: day, andTableID: tableId)
        guard let rows = raw as? [Any] else { return [] }
        return rows.compactMap { item in
            guard let row = item as? [String: Any] else { return nil }
            let time = string(row["time"]) ?? string(row["Time"]) ?? ""
            guard let date = combine(day: day, time: time) else { return nil }
            let score = int(row["hrvValue"])
            if score <= 0 { return nil }
            let heart = int(row["heartValue"])
            return Sample(
                metricType: "hrv",
                timestamp: date,
                heartRate: (30...240).contains(heart) ? heart : 0,
                spo2: 0,
                systolic: 0,
                diastolic: 0,
                temperatureCelsius: 0,
                steps: 0,
                calories: 0,
                distanceMeters: 0,
                hrvScore: score,
                deepSleepMinutes: 0,
                lightSleepMinutes: 0,
                awakeMinutes: 0
            )
        }
    }

    private static func sleepSamples(day: String, tableId: String) -> [Sample] {
        let raw = VPDataBaseOperation.veepooSDKGetSleepData(withDate: day, andTableID: tableId)
        guard let rows = raw as? [Any] else { return [] }
        return rows.compactMap { item in
            guard let row = item as? [String: Any] else { return nil }
            let start = string(row["SLEEP_TIME"]) ?? ""
            guard let date = parseSleepStart(start) ?? combine(day: day, time: "00:00") else { return nil }
            let deepHours = float(row["DEEP_HOUR"])
            let lightHours = float(row["LIGHT_HOUR"])
            let deep = Int((deepHours * 60).rounded())
            let light = Int((lightHours * 60).rounded())
            let totalH = int(row["SLE_HOUR"])
            let totalM = int(row["SLE_MINUTE"])
            let all = max(0, totalH * 60 + totalM)
            let awake = max(0, all - deep - light)
            if deep == 0, light == 0, all == 0 { return nil }
            return Sample(
                metricType: "sleep",
                timestamp: date,
                heartRate: 0,
                spo2: 0,
                systolic: 0,
                diastolic: 0,
                temperatureCelsius: 0,
                steps: 0,
                calories: 0,
                distanceMeters: 0,
                hrvScore: 0,
                deepSleepMinutes: max(0, deep),
                lightSleepMinutes: max(0, light),
                awakeMinutes: awake
            )
        }
    }

    private static func heartValue(in row: [String: Any]) -> Int {
        let direct = int(row["heartValue"])
        if (30...240).contains(direct) { return direct }
        for key in ["ppgs", "ecgs"] {
            let values: [Any]
            if let arr = row[key] as? [Any] {
                values = arr
            } else if let arr = row[key] as? NSArray {
                values = arr.map { $0 }
            } else {
                continue
            }
            for item in values.reversed() {
                let v = int(item)
                if (30...240).contains(v) { return v }
            }
        }
        return 0
    }

    private static func dayStrings(count: Int, now: Date = Date()) -> [String] {
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return (0..<count).compactMap { offset in
            calendar.date(byAdding: .day, value: -offset, to: now).map(formatter.string)
        }
    }

    private static func combine(day: String, time: String) -> Date? {
        let trimmed = time.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = Calendar.current.timeZone
        for pattern in ["yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss"] {
            formatter.dateFormat = pattern
            if let date = formatter.date(from: "\(day) \(trimmed)") { return date }
        }
        return nil
    }

    private static func parseSleepStart(_ raw: String) -> Date? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = Calendar.current.timeZone
        for pattern in ["yyyy/MM/dd HH:mm", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss"] {
            formatter.dateFormat = pattern
            if let date = formatter.date(from: trimmed) { return date }
        }
        return nil
    }

    private static func isoUtc(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        return formatter.string(from: date)
    }

    private static func string(_ value: Any?) -> String? {
        if let text = value as? String {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        if let number = value as? NSNumber { return number.stringValue }
        return nil
    }

    private static func int(_ value: Any?) -> Int {
        if let number = value as? NSNumber { return number.intValue }
        if let text = value as? String { return Int(text.trimmingCharacters(in: .whitespaces)) ?? 0 }
        return 0
    }

    private static func float(_ value: Any?) -> Float {
        if let number = value as? NSNumber { return number.floatValue }
        if let text = value as? String { return Float(text.trimmingCharacters(in: .whitespaces)) ?? 0 }
        return 0
    }
}
