import Foundation

/// Local-only patient consent for the "Resumo com IA" Gemini summary.
/// Default is not consented. Nothing is sent to Core; this never changes the ingest contract.
/// Copy matches Android `AiInsightConsentCopy` / `AiInsightConsentStore`.
enum AiInsightConsentCopy {
    static let title = "Resumo com IA"
    static let enableButton = "Ativar resumo com IA"
    static let authorize = "Autorizo"
    static let notNow = "Agora não"
    static let disabledBody =
        "O resumo com IA explica suas medições em linguagem simples. Ele é opcional: o aplicativo funciona normalmente sem ele."
    static let dialogIntro =
        "O Resumo com IA é um texto simples sobre as suas medições dos últimos 7 dias."
    static let dataSent = "Se você autorizar, o aplicativo envia para o Google Gemini:"
    static let dataItems = [
        "seu nome",
        "sua idade",
        "seu sexo",
        "sua altura",
        "seu peso",
        "suas observações",
        "as médias dos sinais vitais dos últimos 7 dias",
    ]
    static let purpose =
        "O Google Gemini usa esses dados só para escrever o resumo em linguagem fácil."
    static let optional =
        "Isso é opcional. O aplicativo continua funcionando normalmente sem o resumo."
    static let notDiagnosis =
        "O resumo não é um diagnóstico nem uma orientação médica."
    static let withdraw =
        "Você pode desligar esta opção a qualquer momento em Ajustes."
    static let privacyLabel = "Política de privacidade"
    static let deletionLabel = "Como apagar seus dados"
    static let privacyURL = "https://www.higianext2u.com.br/privacidade"
    static let deletionURL = "https://www.higianext2u.com.br/privacidade#exclusao"
    static let settingsOffHint =
        "O aplicativo envia seus dados ao Google Gemini só se você autorizar."
    static let settingsOnHint =
        "Autorizado. Você pode desligar a qualquer momento."
    static let grantedNotice =
        "Resumo com IA autorizado. Você pode desligar em Ajustes."
    static let withdrawnNotice =
        "Resumo com IA desligado. Nada mais é enviado para gerar o resumo."
    static let optionalLabel = "Opcional"
}

enum AiInsightConsentNotice {
    static let granted = Notification.Name("br.com.next2u.saude.paciente.aiInsightConsentGranted")
    static let withdrawn = Notification.Name("br.com.next2u.saude.paciente.aiInsightConsentWithdrawn")
}

struct AiInsightConsentSnapshot: Equatable {
    var consented: Bool = false
    var consentedAtMillis: Int64?
    var withdrawnAtMillis: Int64?
    var promptShown: Bool = false
}

enum AiInsightConsentPolicy {
    /// Gemini (and the automatic review) only run when the build flag and consent are both on.
    static func shouldGenerate(featureEnabled: Bool, consented: Bool) -> Bool {
        featureEnabled && consented
    }

    /// When the one-time first-connection consent dialog should appear.
    static func shouldPrompt(
        featureEnabled: Bool,
        consented: Bool,
        promptShown: Bool,
        hasPairedWatch: Bool,
        anotherPromptVisible: Bool = false
    ) -> Bool {
        featureEnabled && !consented && !promptShown && hasPairedWatch && !anotherPromptVisible
    }
}

/// Decides whether a Gemini HTTP request may be built. `urlForKey` is not called
/// unless the feature flag, explicit consent and a real key are all present.
enum AiInsightNetworkGate {
    static let placeholders: Set<String> = [
        "",
        "MY_GEMINI_API_KEY",
        "YOUR_GEMINI_API_KEY",
        "YOUR_GEMINI_API_KEY_HERE",
    ]

    static func isPlaceholderKey(_ key: String?) -> Bool {
        placeholders.contains(key?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "")
    }

    static func requestURL(
        featureEnabled: Bool,
        consented: Bool,
        apiKey: String,
        urlForKey: (String) -> String
    ) -> URL? {
        guard AiInsightConsentPolicy.shouldGenerate(featureEnabled: featureEnabled, consented: consented) else {
            return nil
        }
        let trimmed = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !isPlaceholderKey(trimmed) else { return nil }
        return URL(string: urlForKey(trimmed))
    }
}

/// Persists consent in UserDefaults. Missing keys mean not consented.
final class AiInsightConsentStore {
    static let keyConsented = "ai_insight_consented"
    static let keyConsentedAt = "ai_insight_consented_at_millis"
    static let keyWithdrawnAt = "ai_insight_withdrawn_at_millis"
    static let keyPromptShown = "ai_insight_consent_prompt_shown"

    static let shared = AiInsightConsentStore()

    private let defaults: UserDefaults
    private let nowMillis: () -> Int64

    init(defaults: UserDefaults = .standard, nowMillis: @escaping () -> Int64 = {
        Int64(Date().timeIntervalSince1970 * 1000)
    }) {
        self.defaults = defaults
        self.nowMillis = nowMillis
    }

    func snapshot() -> AiInsightConsentSnapshot {
        AiInsightConsentSnapshot(
            consented: defaults.bool(forKey: Self.keyConsented),
            consentedAtMillis: millis(Self.keyConsentedAt),
            withdrawnAtMillis: millis(Self.keyWithdrawnAt),
            promptShown: defaults.bool(forKey: Self.keyPromptShown)
        )
    }

    func isConsented() -> Bool { snapshot().consented }

    func grant() -> AiInsightConsentSnapshot {
        defaults.set(true, forKey: Self.keyConsented)
        defaults.set(Int(nowMillis()), forKey: Self.keyConsentedAt)
        defaults.removeObject(forKey: Self.keyWithdrawnAt)
        defaults.set(true, forKey: Self.keyPromptShown)
        let snap = snapshot()
        NotificationCenter.default.post(name: AiInsightConsentNotice.granted, object: nil)
        return snap
    }

    func withdraw() -> AiInsightConsentSnapshot {
        defaults.set(false, forKey: Self.keyConsented)
        defaults.set(Int(nowMillis()), forKey: Self.keyWithdrawnAt)
        let snap = snapshot()
        NotificationCenter.default.post(name: AiInsightConsentNotice.withdrawn, object: nil)
        return snap
    }

    func markPromptShown() -> AiInsightConsentSnapshot {
        defaults.set(true, forKey: Self.keyPromptShown)
        return snapshot()
    }

    private func millis(_ key: String) -> Int64? {
        guard defaults.object(forKey: key) != nil else { return nil }
        let value = defaults.integer(forKey: key)
        return value > 0 ? Int64(value) : nil
    }
}
