import SwiftUI
import UIKit
import shared

/// Build flag from Info.plist (`NEXT2U_AI_INSIGHT_ENABLED`). Pilot builds are YES;
/// consent is still required before any Gemini request.
enum AiInsightFeatureFlag {
    static var enabled: Bool {
        GeminiInsight.shared.isHighlightEnabled(
            flag: VeepooSession.bundledString("Next2UAiInsightEnabled") ?? "YES"
        )
    }
}

enum AiInsightConsentPrompt: String, Identifiable {
    case firstConnection
    case manual
    var id: String { rawValue }
}

@MainActor
final class AiInsightConsentModel: ObservableObject {
    static let shared = AiInsightConsentModel()

    @Published private(set) var snapshot: AiInsightConsentSnapshot
    private let store: AiInsightConsentStore

    private init(store: AiInsightConsentStore = .shared) {
        self.store = store
        self.snapshot = store.snapshot()
    }

    func grant() {
        snapshot = store.grant()
        UIAccessibility.post(notification: .announcement, argument: AiInsightConsentCopy.grantedNotice)
    }

    func withdraw() {
        snapshot = store.withdraw()
        UIAccessibility.post(notification: .announcement, argument: AiInsightConsentCopy.withdrawnNotice)
    }

    func markPromptShown() {
        snapshot = store.markPromptShown()
    }
}

/// Same disclosure as Android `AiInsightConsentDialog`: Autorizo / Agora não.
struct AiInsightConsentSheet: View {
    @Environment(\.patientPalette) private var palette
    var onAuthorize: () -> Void
    var onNotNow: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(AiInsightConsentCopy.dialogIntro)
                    Text(AiInsightConsentCopy.dataSent)
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(AiInsightConsentCopy.dataItems, id: \.self) { item in
                            Text("• \(item)")
                        }
                    }
                    Text(AiInsightConsentCopy.purpose)
                    Text(AiInsightConsentCopy.optional)
                    Text(AiInsightConsentCopy.notDiagnosis)
                    Text(AiInsightConsentCopy.withdraw)
                    Link(destination: URL(string: AiInsightConsentCopy.privacyURL)!) {
                        Text(AiInsightConsentCopy.privacyLabel)
                            .font(.system(size: 17, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                    }
                    .accessibilityIdentifier("ai_insight_privacy_link")
                    Link(destination: URL(string: AiInsightConsentCopy.deletionURL)!) {
                        Text(AiInsightConsentCopy.deletionLabel)
                            .font(.system(size: 17, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                    }
                    .accessibilityIdentifier("ai_insight_deletion_link")
                    Button(AiInsightConsentCopy.authorize, action: onAuthorize)
                        .buttonStyle(PatientPrimaryButton())
                        .accessibilityIdentifier("btn_ai_insight_authorize")
                    Button(AiInsightConsentCopy.notNow, action: onNotNow)
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                        .foregroundStyle(palette.primary)
                        .accessibilityIdentifier("btn_ai_insight_not_now")
                }
                .font(.system(size: 17))
                .foregroundStyle(palette.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle(AiInsightConsentCopy.title)
            .navigationBarTitleDisplayMode(.large)
        }
        .accessibilityIdentifier("ai_insight_consent_dialog")
        .interactiveDismissDisabled(true)
    }
}
