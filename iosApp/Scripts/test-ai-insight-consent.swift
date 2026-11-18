import Foundation
#if canImport(Darwin)
import Darwin
#elseif canImport(Glibc)
import Glibc
#endif

@main
struct AiInsightConsentTests {
    static func main() throws {
        var failures = 0
        func expect(_ cond: @autoclosure () -> Bool, _ msg: String) {
            if !cond() {
                fputs("FAIL: \(msg)\n", stderr)
                failures += 1
            }
        }

        expect(AiInsightConsentCopy.title == "Resumo com IA", "título")
        expect(AiInsightConsentCopy.enableButton == "Ativar resumo com IA", "botão ativar")
        expect(AiInsightConsentCopy.authorize == "Autorizo", "autorizo")
        expect(AiInsightConsentCopy.notNow == "Agora não", "agora não")
        expect(
            AiInsightConsentCopy.disabledBody ==
                "O resumo com IA explica suas medições em linguagem simples. Ele é opcional: o aplicativo funciona normalmente sem ele.",
            "corpo desligado"
        )
        expect(
            AiInsightConsentCopy.dialogIntro ==
                "O Resumo com IA é um texto simples sobre as suas medições dos últimos 7 dias.",
            "intro"
        )
        expect(
            AiInsightConsentCopy.dataSent == "Se você autorizar, o aplicativo envia para o Google Gemini:",
            "dados enviados"
        )
        expect(
            AiInsightConsentCopy.dataItems == [
                "seu nome", "sua idade", "seu sexo", "sua altura", "seu peso",
                "suas observações", "as médias dos sinais vitais dos últimos 7 dias",
            ],
            "itens"
        )
        expect(
            AiInsightConsentCopy.purpose ==
                "O Google Gemini usa esses dados só para escrever o resumo em linguagem fácil.",
            "finalidade"
        )
        expect(
            AiInsightConsentCopy.optional ==
                "Isso é opcional. O aplicativo continua funcionando normalmente sem o resumo.",
            "opcional"
        )
        expect(
            AiInsightConsentCopy.notDiagnosis ==
                "O resumo não é um diagnóstico nem uma orientação médica.",
            "não é diagnóstico"
        )
        expect(
            AiInsightConsentCopy.withdraw ==
                "Você pode desligar esta opção a qualquer momento em Ajustes.",
            "revogação"
        )
        expect(AiInsightConsentCopy.privacyLabel == "Política de privacidade", "política")
        expect(AiInsightConsentCopy.deletionLabel == "Como apagar seus dados", "exclusão")
        expect(AiInsightConsentCopy.privacyURL == "https://www.higianext2u.com.br/privacidade", "url política")
        expect(
            AiInsightConsentCopy.deletionURL == "https://www.higianext2u.com.br/privacidade#exclusao",
            "url exclusão"
        )
        expect(
            AiInsightConsentCopy.settingsOffHint ==
                "O aplicativo envia seus dados ao Google Gemini só se você autorizar.",
            "dica desligada"
        )
        expect(
            AiInsightConsentCopy.settingsOnHint == "Autorizado. Você pode desligar a qualquer momento.",
            "dica ligada"
        )

        let suite = "ai-insight-consent-test"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        var now: Int64 = 1_000
        let store = AiInsightConsentStore(defaults: defaults, nowMillis: { now })

        let initial = store.snapshot()
        expect(initial.consented == false, "padrão desligado")
        expect(initial.consentedAtMillis == nil, "sem data de autorização")
        expect(initial.withdrawnAtMillis == nil, "sem data de revogação")
        expect(initial.promptShown == false, "prompt ainda não mostrado")
        expect(store.isConsented() == false, "isConsented padrão")

        now = 5_000
        let granted = store.grant()
        expect(granted.consented, "autorizado")
        expect(granted.consentedAtMillis == 5_000, "data da autorização")
        expect(granted.withdrawnAtMillis == nil, "revogação limpa ao autorizar")
        expect(granted.promptShown, "prompt marcado ao autorizar")

        now = 9_000
        let withdrawn = store.withdraw()
        expect(withdrawn.consented == false, "revogado")
        expect(withdrawn.consentedAtMillis == 5_000, "data da autorização permanece")
        expect(withdrawn.withdrawnAtMillis == 9_000, "data da revogação")
        expect(withdrawn.promptShown, "prompt continua marcado")

        let reloaded = AiInsightConsentStore(defaults: defaults, nowMillis: { 99 }).snapshot()
        expect(reloaded.consented == false, "revogação persiste")
        expect(reloaded.withdrawnAtMillis == 9_000, "data persiste")
        expect(reloaded.consentedAtMillis == 5_000, "autorização anterior persiste")

        now = 11_000
        let again = store.grant()
        expect(again.consented && again.consentedAtMillis == 11_000 && again.withdrawnAtMillis == nil, "nova autorização")
        let persisted = AiInsightConsentStore(defaults: defaults, nowMillis: { 1 }).snapshot()
        expect(persisted.consented && persisted.consentedAtMillis == 11_000, "nova instância lê o mesmo estado")

        defaults.removePersistentDomain(forName: suite)
        let fresh = AiInsightConsentStore(defaults: defaults, nowMillis: { 1 })
        expect(fresh.markPromptShown().promptShown && fresh.isConsented() == false, "agora não não autoriza")

        expect(AiInsightConsentPolicy.shouldGenerate(featureEnabled: false, consented: false) == false, "flag off")
        expect(AiInsightConsentPolicy.shouldGenerate(featureEnabled: true, consented: false) == false, "sem consentimento")
        expect(AiInsightConsentPolicy.shouldGenerate(featureEnabled: false, consented: true) == false, "consentimento com flag off")
        expect(AiInsightConsentPolicy.shouldGenerate(featureEnabled: true, consented: true), "flag e consentimento")

        expect(
            AiInsightConsentPolicy.shouldPrompt(
                featureEnabled: true, consented: false, promptShown: false, hasPairedWatch: true
            ),
            "prompt após parear"
        )
        expect(
            AiInsightConsentPolicy.shouldPrompt(
                featureEnabled: true, consented: false, promptShown: false, hasPairedWatch: false
            ) == false,
            "sem relógio não pergunta"
        )
        expect(
            AiInsightConsentPolicy.shouldPrompt(
                featureEnabled: true, consented: true, promptShown: false, hasPairedWatch: true
            ) == false,
            "já autorizado não pergunta"
        )
        expect(
            AiInsightConsentPolicy.shouldPrompt(
                featureEnabled: true, consented: false, promptShown: true, hasPairedWatch: true
            ) == false,
            "prompt só uma vez"
        )
        expect(
            AiInsightConsentPolicy.shouldPrompt(
                featureEnabled: false, consented: false, promptShown: false, hasPairedWatch: true
            ) == false,
            "flag off não pergunta"
        )
        expect(
            AiInsightConsentPolicy.shouldPrompt(
                featureEnabled: true, consented: false, promptShown: false, hasPairedWatch: true,
                anotherPromptVisible: true
            ) == false,
            "não empilha outro aviso"
        )

        func forbidURL(_ key: String) -> String {
            fputs("LEAK: urlForKey chamado com \(key)\n", stderr)
            failures += 1
            return "https://generativelanguage.googleapis.com/should-not-run"
        }
        expect(
            AiInsightNetworkGate.requestURL(
                featureEnabled: true, consented: false, apiKey: "real-key", urlForKey: forbidURL
            ) == nil,
            "sem consentimento não monta URL"
        )
        expect(
            AiInsightNetworkGate.requestURL(
                featureEnabled: false, consented: true, apiKey: "real-key", urlForKey: forbidURL
            ) == nil,
            "flag off não monta URL"
        )
        for placeholder in ["", "  ", "YOUR_GEMINI_API_KEY", "MY_GEMINI_API_KEY", "YOUR_GEMINI_API_KEY_HERE"] {
            expect(
                AiInsightNetworkGate.requestURL(
                    featureEnabled: true, consented: true, apiKey: placeholder, urlForKey: forbidURL
                ) == nil,
                "placeholder não monta URL (\(placeholder))"
            )
        }
        var built = 0
        let url = AiInsightNetworkGate.requestURL(
            featureEnabled: true, consented: true, apiKey: " real-key ",
            urlForKey: { key in
                built += 1
                expect(key == "real-key", "chave aparada")
                return "https://example.test/gemini?key=\(key)"
            }
        )
        expect(built == 1, "urlForKey só com consentimento e chave real")
        expect(url?.absoluteString == "https://example.test/gemini?key=real-key", "URL só depois do portão")

        let config = try String(contentsOfFile: "iosApp/Config/Next2UPaciente.xcconfig", encoding: .utf8)
        let lines = config.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let assignments = lines.filter {
            $0.trimmingCharacters(in: .whitespaces).hasPrefix("NEXT2U_GEMINI_API_KEY")
        }
        expect(assignments.count == 1, "uma atribuição da chave Gemini")
        expect(
            assignments.first?.trimmingCharacters(in: .whitespaces) == "NEXT2U_GEMINI_API_KEY =",
            "xcconfig commitado deixa a chave vazia"
        )
        let keyIndex = lines.firstIndex {
            $0.trimmingCharacters(in: .whitespaces).hasPrefix("NEXT2U_GEMINI_API_KEY")
        } ?? -1
        let includeIndex = lines.firstIndex { $0.contains("#include? \"Secrets.local.xcconfig\"") } ?? -1
        expect(keyIndex >= 0 && includeIndex > keyIndex, "Secrets.local.xcconfig pode preencher a chave depois")
        expect(!config.contains("AIza"), "sem chave real no xcconfig")
        expect(config.contains("CURRENT_PROJECT_VERSION = 23"), "build 23")
        expect(!config.contains("YOUR_GEMINI_API_KEY"), "placeholder não entra no Release")

        let plist = try String(contentsOfFile: "iosApp/Next2UPaciente/Info.plist", encoding: .utf8)
        expect(plist.contains("<string>$(NEXT2U_GEMINI_API_KEY)</string>"), "plist usa a variável")
        expect(!plist.contains("AIza"), "plist sem chave literal")

        let ignore = try String(contentsOfFile: ".gitignore", encoding: .utf8)
        expect(ignore.contains("iosApp/Config/Secrets.local.xcconfig"), "secrets no gitignore")

        let project = try String(contentsOfFile: "iosApp/Next2UPaciente.xcodeproj/project.pbxproj", encoding: .utf8)
        expect(project.contains("AiInsightConsent.swift"), "fonte de consentimento no projeto")
        expect(!project.contains("NEXT2U_GEMINI_API_KEY = "), "pbxproj não define a chave")

        if failures > 0 {
            fputs("\(failures) falha(s)\n", stderr)
            exit(1)
        }
        print("ok")
    }
}
