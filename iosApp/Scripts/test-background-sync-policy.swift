import Foundation

@main
struct BackgroundSyncPolicyTests {
    static func main() {
        var failures = 0
        func expect(_ cond: @autoclosure () -> Bool, _ msg: String) {
            if !cond() {
                fputs("FAIL: \(msg)\n", stderr)
                failures += 1
            }
        }

        typealias P = BackgroundSyncPolicy
        let idle = P.HandshakeAction(resetClock: false, retryPassword: false, resetLink: false)
        let retry = P.HandshakeAction(resetClock: false, retryPassword: true, resetLink: false)
        let reset = P.HandshakeAction(resetClock: false, retryPassword: false, resetLink: true)
        let suspended = P.HandshakeAction(resetClock: true, retryPassword: false, resetLink: false)

        expect(P.backgroundPolicyWait < P.backgroundExecutionBudget / 2, "histórico começa bem antes dos 25s")
        expect(P.historyFallbackDelay(inBackground: true) == 3, "fallback de política em fundo é 3s")
        expect(P.historyFallbackDelay(inBackground: false) == 25, "primeiro plano mantém o fallback de 25s")
        expect(P.backgroundConnectWait > P.handshakeTimeout, "a espera de ligação cobre uma senha lenta")
        expect(P.handshakeTimeout > 8, "a senha não usa mais o timeout de 8s")

        expect(
            P.handshakeAction(attemptAge: 19, retriesSoFar: 0, sinceLastCheck: 3, inBackground: false) == idle,
            "antes de 20s não mexe na senha"
        )
        expect(
            P.handshakeAction(attemptAge: 20, retriesSoFar: 0, sinceLastCheck: 3, inBackground: false) == retry,
            "primeira tentativa só reenvia a senha"
        )
        expect(
            P.handshakeAction(attemptAge: 20, retriesSoFar: 1, sinceLastCheck: 3, inBackground: false) == retry,
            "segunda tentativa ainda não derruba o link"
        )
        expect(
            P.handshakeAction(attemptAge: 20, retriesSoFar: 2, sinceLastCheck: 3, inBackground: false) == reset,
            "primeiro plano: a terceira janela religa o link"
        )
        expect(
            P.handshakeAction(attemptAge: 600, retriesSoFar: 9, sinceLastCheck: 3, inBackground: true) == retry,
            "fundo: senha lenta não chama cancelPeripheralConnection"
        )
        expect(
            P.handshakeAction(attemptAge: 3_600, retriesSoFar: 0, sinceLastCheck: 8, inBackground: false) == suspended,
            "tempo suspenso não conta como handshake parado"
        )
        expect(
            P.handshakeAction(attemptAge: 3_600, retriesSoFar: 4, sinceLastCheck: 40, inBackground: true) == suspended,
            "acordar depois de horas não derruba o link na hora"
        )

        expect(P.classifyKeychain(0) == .success, "errSecSuccess")
        expect(P.classifyKeychain(-25300) == .notFound, "errSecItemNotFound")
        expect(P.classifyKeychain(-25308) == .interactionNotAllowed, "errSecInteractionNotAllowed")
        expect(P.classifyKeychain(-25291) == .other, "outro status não é item ausente")
        expect(P.mayCreateKey(.notFound), "chave nova só se o item não existe")
        expect(P.mayCreateKey(.interactionNotAllowed) == false, "Keychain bloqueado não gera chave nova")
        expect(P.mayCreateKey(.other) == false, "erro de Keychain não gera chave nova")
        expect(P.mayCreateKey(.success) == false, "chave existente não é recriada")
        expect(
            P.transientStoreDetail == "Armazenamento temporariamente indisponível. Tente de novo sem apagar os registros.",
            "detalhe transitório igual ao PatientStore"
        )

        let now = Date(timeIntervalSince1970: 1_000_000)
        let fresh = P.onEnteredBackground(lastHistoryPullAt: now.addingTimeInterval(-30), now: now)
        expect(fresh.stopLiveSensors, "fundo continua a parar o PPG")
        expect(fresh.pullHistory == false, "leitura recente não repete o histórico")
        let due = P.onEnteredBackground(lastHistoryPullAt: now.addingTimeInterval(-3 * 60), now: now)
        expect(due.stopLiveSensors && due.pullHistory, "fundo lê o histórico nesta janela")

        expect(
            P.shouldSkipBatteryRewrite(inBackground: true, policyCoversThisWatch: true),
            "fundo não reescreve a política já gravada"
        )
        expect(
            P.shouldSkipBatteryRewrite(inBackground: true, policyCoversThisWatch: false) == false,
            "fundo ainda grava a política na primeira vez"
        )
        expect(
            P.shouldSkipBatteryRewrite(inBackground: false, policyCoversThisWatch: true) == false,
            "primeiro plano continua a rever a política"
        )
        expect(P.batteryPolicyCovers(storedUuid: "ABC", currentUuid: "ABC"), "mesmo relógio")
        expect(P.batteryPolicyCovers(storedUuid: "ABC", currentUuid: "DEF") == false, "outro relógio")
        expect(P.batteryPolicyCovers(storedUuid: "  ", currentUuid: "ABC") == false, "uuid vazio não conta")

        expect(
            P.shouldStartBackgroundServiceScan(inBackground: true, hardwareReady: false, canRetrievePeripheral: false),
            "sem UUID recuperável: scan por serviço"
        )
        expect(
            P.shouldStartBackgroundServiceScan(inBackground: true, hardwareReady: false, canRetrievePeripheral: true) == false,
            "pending connect dispensa o scan"
        )
        expect(
            P.shouldStartBackgroundServiceScan(inBackground: false, hardwareReady: false, canRetrievePeripheral: false) == false,
            "scan de serviço não substitui o scan do usuário"
        )
        expect(
            P.shouldStartBackgroundServiceScan(inBackground: true, hardwareReady: true, canRetrievePeripheral: false) == false,
            "já ligado: não escaneia"
        )
        expect(
            P.matchesKnownWatch(uuid: "U1", address: "", knownUuids: ["u1"], knownAddresses: []),
            "UUID conhecido, sem diferenciar maiúsculas"
        )
        expect(
            P.matchesKnownWatch(uuid: "", address: "AA:BB", knownUuids: [], knownAddresses: ["aa:bb"]),
            "MAC conhecido"
        )
        expect(
            P.matchesKnownWatch(uuid: "X", address: "Y", knownUuids: ["U1"], knownAddresses: ["AA"]) == false,
            "desconhecido não entra no scan de fundo"
        )

        if failures > 0 {
            fputs("\(failures) teste(s) falharam.\n", stderr)
            exit(1)
        }
        print("BackgroundSyncPolicy: todos os testes passaram.")
    }
}
