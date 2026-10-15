import SwiftUI

/// Five destinations match the Android patient app. This phase is a shell:
/// no BLE, no Core upload, no Gemini. Senior-friendly type and 56 pt actions.
struct ContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("app_theme_option") private var themeStorage = AppThemeOption.light.rawValue
    @StateObject private var session = VeepooSession()
    @State private var tab = 0

    private var palette: PatientPalette {
        let option = AppThemeOption.fromStorage(themeStorage)
        return PatientTheme.palette(PatientTheme.resolve(option, systemDark: colorScheme == .dark))
    }

    var body: some View {
        TabView(selection: $tab) {
            PlaceholderScreen(
                title: "Início",
                bodyText: "Aqui vão aparecer seus sinais e o resumo do relógio. Nesta versão ainda não há medição neste iPhone."
            )
            .tabItem { Label("Início", systemImage: "heart.fill") }
            .tag(0)

            PlaceholderScreen(
                title: "Histórico",
                bodyText: "Gráficos de tendência (7 dias, 30 dias, 3 meses) entram na fase de UI. A faixa esperada só aparece se estiver configurada — não é limiar clínico."
            )
            .tabItem { Label("Histórico", systemImage: "chart.xyaxis.line") }
            .tag(1)

            WatchScreen(session: session)
            .tabItem { Label("Relógio", systemImage: "applewatch") }
            .tag(2)

            PlaceholderScreen(
                title: "Envios",
                bodyText: session.lastFlushMessage.isEmpty
                    ? "A fila (client_reading_id e lote) vive no módulo Kotlin shared. O último HTTP aparece também em Relógio."
                    : "Último envio: HTTP \(session.lastHttpCode). \(session.lastFlushMessage)"
            )
            .tabItem { Label("Envios", systemImage: "icloud.and.arrow.up") }
            .tag(3)

            SettingsPlaceholder(themeStorage: $themeStorage)
                .tabItem { Label("Ajustes", systemImage: "gearshape.fill") }
                .tag(4)
        }
        .tint(palette.primary)
        .environment(\.patientPalette, palette)
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = PatientTheme.palette(.lightAzul)
}

extension EnvironmentValues {
    var patientPalette: PatientPalette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

struct PlaceholderScreen: View {
    @Environment(\.patientPalette) private var palette
    let title: String
    let bodyText: String

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PatientTheme.cardGap) {
                    Text(title)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(palette.onSurface)
                        .accessibilityAddTraits(.isHeader)
                    Text(bodyText)
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct SettingsPlaceholder: View {
    @Environment(\.patientPalette) private var palette
    @Binding var themeStorage: String

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PatientTheme.cardGap) {
                    Text("Aparência")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    Text("Padrão Claro, iguais ao Android (PR #17).")
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                    ForEach(AppThemeOption.allCases) { option in
                        Button {
                            themeStorage = option.rawValue
                        } label: {
                            HStack {
                                Text(option.label)
                                    .font(.system(size: 17, weight: .semibold))
                                Spacer()
                                if themeStorage == option.rawValue {
                                    Image(systemName: "checkmark.circle.fill")
                                }
                            }
                            .foregroundStyle(palette.onSurface)
                            .frame(minHeight: PatientTheme.primaryButton)
                            .padding(.horizontal, PatientTheme.margin)
                            .background(palette.surface)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(option.label)
                        .accessibilityAddTraits(themeStorage == option.rawValue ? [.isSelected] : [])
                    }
                    Text("Assinatura do piloto")
                        .font(.system(size: 22, weight: .semibold))
                        .padding(.top, PatientTheme.margin)
                    Text(SigningPlaceholders.summary)
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Ajustes")
        }
    }
}

#Preview {
    ContentView()
}
