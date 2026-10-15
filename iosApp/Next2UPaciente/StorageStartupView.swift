import SwiftUI
import shared

/// Literal copy of Android `StorageStartupScreen`. Operational UI is never
/// composed before the encrypted store is READY.
struct StorageStartupView: View {
    @Environment(\.colorScheme) private var colorScheme
    let status: StoreStatus

    private var palette: PatientPalette {
        PatientTheme.palette(PatientTheme.resolve(.light, systemDark: colorScheme == .dark))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Next2uBrandLogo()
                if status == .opening {
                    Text("Abrindo seus registros")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityAddTraits(.isHeader)
                    ProgressView()
                        .tint(palette.primary)
                    Text("Aguarde a verificação do armazenamento antes de usar o aplicativo.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    Text("Não foi possível abrir seus registros")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityAddTraits(.isHeader)
                    Text("A coleta pelo aplicativo e os envios não foram iniciados nesta abertura.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                    Text("Não limpe os dados, não desinstale o aplicativo e não tente substituir a chave do banco.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                    Text("Peça ajuda à equipe responsável pela instalação para verificar o armazenamento e recuperar o acesso.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                    Text("Esta tela não confirma a integridade dos registros nem recupera dados automaticamente.")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurface)
                }
            }
            .padding(24)
            .frame(maxWidth: 720, alignment: .leading)
        }
        .background(palette.background.ignoresSafeArea())
        .accessibilityIdentifier("storage_startup_screen")
    }
}

@MainActor
final class StorageSession: ObservableObject {
    @Published var status: StoreStatus = .opening

    func openIfNeeded() async {
        guard status == .opening else { return }
        PatientCrypto.install()
        let opened = await Task.detached(priority: .userInitiated) {
            PatientStore.shared.open()
        }.value
        if opened == .ready {
            PatientStore.shared.attachQueue()
            VeepooSession.shared.onStoreReady()
        }
        status = opened
    }
}
