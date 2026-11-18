import Foundation

@main
struct MeasurementCyclePolicyTests {
    static func main() {
        var failures = 0
        func expect(_ cond: @autoclosure () -> Bool, _ msg: String) {
            if !cond() {
                fputs("FAIL: \(msg)\n", stderr)
                failures += 1
            }
        }

        expect(MeasurementCyclePolicy.cycleIdleSeconds == 5 * 60, "idle de sucesso é 5 min")
        expect(MeasurementCyclePolicy.bloodCadence == 30 * 60, "cadência de PA é 30 min")
        expect(MeasurementCyclePolicy.idleSeconds(afterEmptyCycles: 0) == 5 * 60, "ciclo com leitura: 5 min")
        expect(MeasurementCyclePolicy.idleSeconds(afterEmptyCycles: 1) == 2 * 60, "1º vazio: 2 min")
        expect(MeasurementCyclePolicy.idleSeconds(afterEmptyCycles: 2) == 4 * 60, "2º vazio: 4 min")
        expect(MeasurementCyclePolicy.idleSeconds(afterEmptyCycles: 3) == 8 * 60, "3º vazio: 8 min")
        expect(MeasurementCyclePolicy.idleSeconds(afterEmptyCycles: 5) == 30 * 60, "teto 30 min")
        expect(MeasurementCyclePolicy.idleSeconds(afterEmptyCycles: 99) == 30 * 60, "teto estável")

        expect(MeasurementCyclePolicy.failureBackoff(afterConsecutiveFailures: 0) == 0, "sem falha: 0")
        expect(MeasurementCyclePolicy.failureBackoff(afterConsecutiveFailures: 1) == 30, "1ª falha: 30 s")
        expect(MeasurementCyclePolicy.failureBackoff(afterConsecutiveFailures: 2) == 60, "2ª falha: 60 s")
        expect(MeasurementCyclePolicy.failureBackoff(afterConsecutiveFailures: 3) == 120, "3ª falha: 120 s")
        expect(MeasurementCyclePolicy.failureBackoff(afterConsecutiveFailures: 9) == 10 * 60, "teto 10 min")
        expect(MeasurementCyclePolicy.failureBackoff(afterConsecutiveFailures: 99) == 10 * 60, "teto estável")

        let now = Date(timeIntervalSince1970: 1_000_000)
        expect(MeasurementCyclePolicy.bloodIsDue(unsupported: true, lastSuccessAt: nil, now: now) == false, "PA indisponível nunca é devida")
        expect(MeasurementCyclePolicy.bloodIsDue(unsupported: false, lastSuccessAt: nil, now: now) == true, "PA nunca medida é devida")
        expect(MeasurementCyclePolicy.bloodIsDue(unsupported: false, lastSuccessAt: now.addingTimeInterval(-29 * 60), now: now) == false, "PA com 29 min ainda não é devida")
        expect(MeasurementCyclePolicy.bloodIsDue(unsupported: false, lastSuccessAt: now.addingTimeInterval(-30 * 60), now: now) == true, "PA com 30 min é devida")

        expect(
            MeasurementCyclePolicy.shouldStartCycle(hardwareReady: false, sensorBusy: false, now: now, nextCycleAt: now, force: true) == false,
            "sem hardware não inicia, mesmo com force"
        )
        expect(
            MeasurementCyclePolicy.shouldStartCycle(hardwareReady: true, sensorBusy: true, now: now, nextCycleAt: now, force: true) == false,
            "sensor ocupado: single-flight, nem com force"
        )
        expect(
            MeasurementCyclePolicy.shouldStartCycle(hardwareReady: true, sensorBusy: false, now: now, nextCycleAt: now.addingTimeInterval(60), force: false) == false,
            "ainda na pausa: não inicia"
        )
        expect(
            MeasurementCyclePolicy.shouldStartCycle(hardwareReady: true, sensorBusy: false, now: now, nextCycleAt: now.addingTimeInterval(60), force: true) == true,
            "force ignora a pausa se o sensor está livre"
        )
        expect(
            MeasurementCyclePolicy.shouldStartCycle(hardwareReady: true, sensorBusy: false, now: now, nextCycleAt: now, force: false) == true,
            "pausa vencida inicia"
        )

        expect(MeasurementCyclePolicy.restartsHeartImmediatelyAfterCycle == false, "fim de ciclo não religa a FC na hora")
        expect(MeasurementCyclePolicy.restartsHeartImmediatelyAfterStageEnd == false, "fim de estágio não religa a FC na hora")

        // WatchBatteryPolicy (build 19)
        typealias P = WatchBatteryPolicy
        let hr = P.Slot(kind: P.Kind.heartRate.rawValue, on: false, intervalMinutes: 5, minStep: 5)
        expect(P.desired(hr) == P.Slot(kind: 0, on: true, intervalMinutes: 30, minStep: 5), "FC automática a cada 30 min")
        expect(P.desired(P.Slot(kind: 0, on: true, intervalMinutes: 30, minStep: 5)) == nil, "FC já conforme não reescreve")
        expect(P.desired(P.Slot(kind: 0, on: true, intervalMinutes: 60, minStep: 5)) == nil, "não encurta intervalo maior")
        expect(P.desired(P.Slot(kind: 0, on: true, intervalMinutes: 10, minStep: 7))?.intervalMinutes == 35, "arredonda ao passo")
        expect(P.desired(P.Slot(kind: P.Kind.bloodPressure.rawValue, on: true, intervalMinutes: 30, minStep: 30))?.intervalMinutes == 60, "PA a cada 60 min")
        for kind in [P.Kind.bloodOxygen, .hrv, .stress, .bodyTemperature, .bloodGlucose, .bloodComponents, .lorentz] {
            expect(P.desired(P.Slot(kind: kind.rawValue, on: true, intervalMinutes: 10, minStep: 5))?.on == false, "\(kind) desligado")
        }
        expect(P.desired(P.Slot(kind: P.Kind.fallReminder.rawValue, on: true, intervalMinutes: 0, minStep: 1)) == nil, "lembrete de queda intocado")
        expect(P.manualAllowed(lastManualAt: nil, now: now, foreground: false) == false, "manual só em primeiro plano")
        expect(P.manualAllowed(lastManualAt: nil, now: now, foreground: true), "manual liberado")
        expect(P.manualAllowed(lastManualAt: now.addingTimeInterval(-30), now: now, foreground: true) == false, "manual no máximo 1/min")
        expect(P.historyPollDue(lastPullAt: now.addingTimeInterval(-29 * 60), now: now) == false, "histórico: ainda não")
        expect(P.historyPollDue(lastPullAt: now.addingTimeInterval(-30 * 60), now: now), "histórico a cada 30 min")
        expect(P.automaticLiveLoop == false, "sem ciclo ao vivo automático")
        expect(P.stepPullSeconds >= 10 * 60, "passos no máximo a cada 10 min")
        expect(P.acceptLiveSample(manualCycleActive: false) == false, "amostra ao vivo fora do ciclo manual é descartada")
        expect(P.acceptLiveSample(manualCycleActive: true), "amostra ao vivo no ciclo manual entra")
        expect(P.shouldSendStop(force: true, locallyRunning: false), "parar mesmo sem flag local")
        expect(P.shouldSendStop(force: false, locallyRunning: true), "parar o teste que o app abriu")
        expect(P.shouldSendStop(force: false, locallyRunning: false) == false, "sem teste local não reenvia parada")
        let closed = Set(P.legacySwitchesToClose.map(\.rawValue))
        expect(closed.contains(17), "PPG contínuo entra na lista de fechar")
        expect(closed.contains(1000), "SpO₂ noturno entra na lista de fechar")
        expect(closed.contains(24) && closed.contains(26), "temperatura e ECG sempre aberto são fechados")
        expect(!closed.contains(5) && !closed.contains(6), "FC/PA automáticas ficam no intervalo, não no interruptor legado")

        if failures > 0 {
            fputs("\(failures) teste(s) falharam.\n", stderr)
            exit(1)
        }
        print("MeasurementCyclePolicy: todos os testes passaram.")
    }
}
