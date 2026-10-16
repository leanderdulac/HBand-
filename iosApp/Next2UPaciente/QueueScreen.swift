import SwiftUI
import shared

struct QueueScreen: View {
    @Environment(\.patientPalette) private var palette
    @Environment(\.patientColorScheme) private var paletteScheme
    @ObservedObject var session: VeepooSession
    @State private var inspect: StoredQueueItem?
    @State private var removal: QueueRemoval?

    private var items: [StoredQueueItem] {
        PatientReadings.queueItems(IngestRuntime.shared.visibleItems())
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Next2uBrandLogo()
                    BleDiagnosticsCard(session: session)
                    Text("Envios")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityAddTraits(.isHeader)
                    syncCard
                    if session.lastFlushSyncedCount > 0 {
                        Text("Concluídos no aplicativo: \(session.lastFlushSyncedCount)")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(palette.onSurface)
                    }
                    Text("Esta tela mostra a fila do aplicativo. Ela não confirma que os dados chegaram à equipe de saúde.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                    Text("Ver registros da fila")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    if items.isEmpty {
                        Text("A lista exibida está vazia.")
                            .font(.system(size: 17))
                            .foregroundStyle(palette.onSurface)
                    } else {
                        Text("Até 30 registros, com pendências primeiro.")
                            .font(.system(size: 17))
                            .foregroundStyle(palette.onSurface)
                    }
                    ForEach(items, id: \.clientReadingId) { item in
                        queueCard(item)
                    }
                    if !items.isEmpty {
                        Button("Apagar toda a fila") { removal = .all }
                            .buttonStyle(PatientPrimaryButton())
                            .accessibilityIdentifier("clear_queue_button")
                    }
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .patientChrome()
            .sheet(item: $inspect) { item in
                JsonPayloadSheet(item: item, onClose: { inspect = nil })
                    .environment(\.patientPalette, palette)
                    .environment(\.patientColorScheme, paletteScheme)
                    .preferredColorScheme(paletteScheme)
            }
            .alert("Apagar registros da fila?", isPresented: Binding(
                get: { removal != nil },
                set: { if !$0 { removal = nil } }
            )) {
                Button("Apagar", role: .destructive) { confirmRemoval() }
                    .accessibilityIdentifier("confirm_queue_removal")
                Button("Cancelar", role: .cancel) { removal = nil }
            } message: {
                Text(removalCopy)
            }
        }
    }

    private var syncCard: some View {
        let pending = session.pendingCount
        let failed = session.failedCount
        let syncing = session.isFlushing
        let title: String
        let body: String
        if syncing {
            title = "Enviando registros"
            body = "Aguarde enquanto o aplicativo tenta enviar os registros."
        } else if IngestRuntime.shared.configurationError() != nil {
            title = "Envios pausados: acesso não autorizado"
            body = IngestRuntime.shared.configurationError() ?? ""
        } else if failed > 0 {
            title = "Não foi possível enviar alguns registros"
            body = "Há registros que não puderam ser enviados. Confira os detalhes da falha antes de tentar novamente."
        } else if pending > 0 {
            title = "Há registros aguardando envio"
            body = "Os registros continuam na fila do aplicativo. Você pode tentar enviá-los agora."
        } else {
            title = "Envio de dados"
            body = "A fila exibida não tem envios pendentes. Isso não confirma o recebimento pela equipe de saúde."
        }
        return VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.system(size: 17, weight: .bold))
            Text(body).font(.system(size: 17))
            if pending > 0 {
                Text("Aguardando envio: \(pending)").font(.system(size: 17))
            }
            if failed > 0 {
                Text("Registros com falha no envio: \(failed)").font(.system(size: 17))
            }
            if pending > 0 || failed > 0 || IngestRuntime.shared.configurationError() != nil {
                Button(syncing ? "Enviando…" : (failed > 0 ? "Tentar enviar novamente" : "Enviar registros")) {
                    if failed > 0 { session.retryAllFailed() } else { session.flushNow() }
                }
                .buttonStyle(PatientPrimaryButton())
                .disabled(syncing)
                .accessibilityIdentifier("trigger_workmanager_sync_button")
            }
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityIdentifier("workmanager_sync_status_card")
    }

    private func queueCard(_ item: StoredQueueItem) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(IngestRuntime.shared.statusLabel(status: item.status))
                .font(.system(size: 22, weight: .semibold))
            Text(queuedAtLabel(item.queuedAtMillis))
                .font(.system(size: 17))
            if item.status == "FAILED" {
                Text("O aplicativo não conseguiu concluir este envio.")
                    .font(.system(size: 17))
                Button("Tentar enviar novamente") { session.retryFailed(item.clientReadingId) }
                    .buttonStyle(PatientPrimaryButton())
                    .accessibilityIdentifier("retry_queue_\(item.clientReadingId)")
            }
            Button("Ver dados técnicos do registro") { inspect = item }
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
            Button("Apagar registro da fila") { removal = .one(item.clientReadingId) }
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity, minHeight: PatientTheme.primaryButton)
                .accessibilityIdentifier("delete_queue_\(item.clientReadingId)")
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func queuedAtLabel(_ millis: Int64) -> String {
        guard millis > 0 else { return "Data de entrada na fila indisponível" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt-BR")
        formatter.dateFormat = "dd/MM/yyyy 'às' HH:mm"
        return "Entrou na fila em " + formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    private var removalCopy: String {
        let suffix = " A exclusão será recusada se a fila estiver ocupada. Ela não desfaz envios anteriores."
        switch removal {
        case .all:
            return "Todos os registros da fila serão apagados, inclusive os que ainda não foram enviados. Essa ação não pode ser desfeita pelo aplicativo." + suffix
        case .one:
            return "O registro será apagado da fila. Se ainda não foi enviado, ele deixará de ser enviado por esta fila." + suffix
        case .none:
            return ""
        }
    }

    private func confirmRemoval() {
        switch removal {
        case .all: session.clearQueue()
        case .one(let id): session.deleteQueueItem(id)
        case .none: break
        }
        removal = nil
    }
}

enum QueueRemoval {
    case all
    case one(String)
}

extension StoredQueueItem: Identifiable {
    public var id: String { clientReadingId }
}

struct JsonPayloadSheet: View {
    @Environment(\.patientPalette) private var palette
    let item: StoredQueueItem
    var onClose: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("JSON HBand SDK -> HealthTech")
                        .font(.system(size: 17, weight: .bold))
                    Text("Item da Fila (\(item.status))")
                        .font(.system(size: 15))
                        .foregroundStyle(palette.primary)
                    Text(item.payloadJson)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(palette.onSurface)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(palette.ai)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .accessibilityIdentifier("json_payload_text")
                }
                .foregroundStyle(palette.onSurface)
                .padding(20)
            }
            .background(palette.surface.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .patientChrome()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fechar", action: onClose)
                        .foregroundStyle(palette.primary)
                        .accessibilityIdentifier("close_json_inspector")
                }
            }
        }
        .accessibilityIdentifier("json_payload_modal")
    }
}
