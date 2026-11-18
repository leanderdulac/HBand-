import Foundation

/// Regras de intervalo e retomada do ciclo de medição ao vivo no VE30.
/// Extraídas de `VeepooSession` para poder testar sem o SDK e para
/// impedir que um teste óptico fique ligado em loop até acabar a bateria.
enum MeasurementCyclePolicy {
    /// Pausa mínima entre ciclos com leitura (FC/SpO₂).
    static let cycleIdleSeconds: TimeInterval = 5 * 60
    static let maxIdleSeconds: TimeInterval = 30 * 60
    /// Primeira retomada após ciclo vazio (fora do pulso / sem amostra).
    static let emptyRetrySeconds: TimeInterval = 2 * 60
    /// Pressão ao vivo no máximo a cada 30 min após sucesso.
    static let bloodCadence: TimeInterval = 30 * 60
    /// Backoff após falha/timeout de um estágio (igual à ideia do Android 1.7.8).
    static let failureInitialBackoff: TimeInterval = 30
    static let failureMaxBackoff: TimeInterval = 10 * 60

    static func idleSeconds(afterEmptyCycles empty: Int) -> TimeInterval {
        guard empty > 0 else { return cycleIdleSeconds }
        return min(maxIdleSeconds, emptyRetrySeconds * pow(2, Double(min(empty - 1, 4))))
    }

    static func bloodIsDue(unsupported: Bool, lastSuccessAt: Date?, now: Date) -> Bool {
        guard !unsupported else { return false }
        guard let at = lastSuccessAt else { return true }
        return now.timeIntervalSince(at) >= bloodCadence
    }

    /// `consecutiveFailures` começa em 1 após a primeira falha.
    static func failureBackoff(afterConsecutiveFailures n: Int) -> TimeInterval {
        guard n > 0 else { return 0 }
        let shift = min(n - 1, 8)
        return min(failureMaxBackoff, failureInitialBackoff * pow(2, Double(shift)))
    }

    static func shouldStartCycle(
        hardwareReady: Bool,
        sensorBusy: Bool,
        now: Date,
        nextCycleAt: Date,
        force: Bool
    ) -> Bool {
        guard hardwareReady, !sensorBusy else { return false }
        if force { return true }
        return now >= nextCycleAt
    }

    /// Em segundo plano também: nunca religar a FC no mesmo instante em que o ciclo termina.
    static var restartsHeartImmediatelyAfterCycle: Bool { false }

    static var restartsHeartImmediatelyAfterStageEnd: Bool { false }
}

/// Política de bateria do VE30 (iOS build 19 / Android 1.8.0).
///
/// Beta testers relataram o relógio esgotando a bateria com leituras demais. Mesmo
/// com a pausa de 5 min do build 18, o app ligava o PPG ~1,5–3 min a cada ~6–7 min
/// e, a cada reconexão, começava um ciclo forçado (sem pausa). Agora:
/// - quem mede é o próprio relógio (medição automática do firmware): FC a cada
///   30 min, PA a cada 60 min; SpO₂/HRV/estresse/temperatura/glicose desligados;
/// - o app só lê o histórico gravado no relógio a cada 30 min (sem sensor ligado);
/// - teste ao vivo (FC → SpO₂ → PA) só quando o paciente toca em "Medir agora",
///   em primeiro plano, no máximo um por minuto.
enum WatchBatteryPolicy {
    static let heartRateAutoIntervalMinutes = 30
    static let bloodPressureAutoIntervalMinutes = 60
    static let historyPollSeconds: TimeInterval = 30 * 60
    /// Passos do dia (leitura BLE simples, sem sensor óptico). Antes: 60 s.
    static let stepPullSeconds: TimeInterval = 10 * 60
    static let manualMinGapSeconds: TimeInterval = 60
    /// O ciclo ao vivo nunca começa sozinho (pausa concluída / reconexão / segundo plano).
    static let automaticLiveLoop = false

    /// Valores brutos de `VPAutoMonitTestType` (VPAutoMonitTestModel.h, SDK 2.2.110.15).
    enum Kind: Int {
        case heartRate = 0, bloodPressure, bloodGlucose, stress, bloodOxygen,
             bodyTemperature, lorentz, hrv, bloodComponents, fallReminder
    }

    static let offKinds: Set<Kind> = [
        .bloodGlucose, .stress, .bloodOxygen, .bodyTemperature, .lorentz, .hrv, .bloodComponents,
    ]

    struct Slot: Equatable {
        var kind: Int
        var on: Bool
        var intervalMinutes: Int
        var minStep: Int
    }

    /// Estado desejado, ou nil se o item já está conforme (não escreve à toa).
    static func desired(_ slot: Slot, heartRateUserOff: Bool = false) -> Slot? {
        var target = slot
        switch Kind(rawValue: slot.kind) {
        case .heartRate?:
            target.on = !heartRateUserOff
            if target.on { target.intervalMinutes = interval(for: slot, wanted: heartRateAutoIntervalMinutes) }
        case .bloodPressure?:
            target.on = true
            target.intervalMinutes = interval(for: slot, wanted: bloodPressureAutoIntervalMinutes)
        case let kind? where offKinds.contains(kind):
            target.on = false
        default:
            return nil
        }
        return target == slot ? nil : target
    }

    /// Arredonda para cima ao passo do firmware; nunca encurta um intervalo mais longo.
    static func interval(for slot: Slot, wanted: Int) -> Int {
        let step = max(1, slot.minStep)
        let rounded = ((wanted + step - 1) / step) * step
        return max(rounded, slot.intervalMinutes)
    }

    static func manualAllowed(lastManualAt: Date?, now: Date, foreground: Bool) -> Bool {
        guard foreground else { return false }
        guard let last = lastManualAt else { return true }
        return now.timeIntervalSince(last) >= manualMinGapSeconds
    }

    static func historyPollDue(lastPullAt: Date, now: Date) -> Bool {
        now.timeIntervalSince(lastPullAt) >= historyPollSeconds
    }
}
