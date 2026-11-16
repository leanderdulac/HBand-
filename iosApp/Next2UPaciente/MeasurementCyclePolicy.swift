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
