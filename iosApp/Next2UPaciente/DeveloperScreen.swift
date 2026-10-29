#if DEBUG
import SwiftUI
import shared

/// Hidden DEBUG-only tools. Testers on TestFlight/Release never see this.
struct DeveloperScreen: View {
    @Environment(\.patientPalette) private var palette
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var session: VeepooSession

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Desenvolvimento")
                        .font(.system(size: 28, weight: .bold))
                    Text("Só neste build Debug. Release e TestFlight não incluem esta tela.")
                        .font(.system(size: 15))
                        .foregroundStyle(palette.onSurfaceVariant)
                    ingestCard
                    QueueScreen(session: session)
                }
                .foregroundStyle(palette.onSurface)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .patientChrome()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fechar") { dismiss() }
                }
            }
        }
        .accessibilityIdentifier("developer_screen")
    }

    private var ingestCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Envio ao Core")
                .font(.system(size: 22, weight: .semibold))
            Text("URL e chave vêm do xcconfig / Info.plist. Override só neste ecrã Debug.")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
            TextField("URL HTTPS do Core", text: $session.ingestUrl)
                .textInputAutocapitalization(.never)
                .font(.system(size: 17))
                .patientFieldInk(palette)
                .frame(minHeight: PatientTheme.minTouch)
            SecureField("Chave X-API-Key (override)", text: $session.ingestKey)
                .font(.system(size: 17))
                .patientFieldInk(palette)
                .frame(minHeight: PatientTheme.minTouch)
            Button("Guardar chave e URL") { session.configureIngest() }
                .buttonStyle(PatientPrimaryButton())
            Text("Último client_reading_id: \(session.lastClientReadingId ?? "—")")
                .font(.system(size: 15))
            Text("HTTP do último flush: \(session.lastHttpCode == 0 ? "—" : "\(session.lastHttpCode)")")
                .font(.system(size: 17, weight: .semibold))
            Text(session.lastFlushMessage)
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
            Text("Na fila: \(session.pendingCount) · falhas: \(session.failedCount)")
                .font(.system(size: 15))
            Text("BGAppRefresh: \(SigningPlaceholders.ingestRefreshTaskId)")
                .font(.system(size: 13))
            Text("BGProcessing: \(SigningPlaceholders.ingestTaskId)")
                .font(.system(size: 13))
            Text(SigningPlaceholders.summary)
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .padding(PatientTheme.margin)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.ai)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
#endif
