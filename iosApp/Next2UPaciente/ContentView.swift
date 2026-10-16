import SwiftUI

/// Five destinations match the Android patient app. Início / Histórico follow PR #17.
struct ContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("app_theme_option") private var themeStorage = AppThemeOption.light.rawValue
    @ObservedObject private var session = VeepooSession.shared
    @State private var tab = 0
    @State private var showProfile = false

    private var option: AppThemeOption { AppThemeOption.fromStorage(themeStorage) }
    private var resolved: ResolvedAppTheme { PatientTheme.resolve(option, systemDark: colorScheme == .dark) }
    private var palette: PatientPalette { PatientTheme.palette(resolved) }

    var body: some View {
        TabView(selection: $tab) {
            HomeScreen(
                session: session,
                onShowHistory: { tab = 1 },
                onShowProfile: { showProfile = true },
                onConnectWatch: {
                    tab = 2
                    session.startScan()
                }
            )
            .tabItem { Label("Início", systemImage: "heart.fill") }
            .tag(0)

            HistoryScreen()
                .tabItem { Label("Histórico", systemImage: "chart.xyaxis.line") }
                .tag(1)

            WatchScreen(session: session)
                .tabItem { Label("Relógio", systemImage: "applewatch") }
                .tag(2)

            QueueScreen(session: session)
                .tabItem { Label("Envios", systemImage: "icloud.and.arrow.up") }
                .badge(session.pendingCount)
                .tag(3)

            SettingsScreen(themeStorage: $themeStorage, session: session)
                .tabItem { Label("Ajustes", systemImage: "gearshape.fill") }
                .tag(4)
        }
        .tint(palette.primary)
        .environment(\.patientPalette, palette)
        .environment(\.patientColorScheme, resolved.colorScheme)
        .preferredColorScheme(resolved.colorScheme)
        .patientChrome()
        .sheet(isPresented: $showProfile) {
            ProfileEditorSheet(onClose: {
                showProfile = false
                session.notifyProfileChanged()
            })
            .environment(\.patientPalette, palette)
            .environment(\.patientColorScheme, resolved.colorScheme)
            .preferredColorScheme(resolved.colorScheme)
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { session.handleBecameActive() }
        }
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
                    Next2uBrandLogo()
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
            .patientChrome()
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
                    Next2uBrandLogo()
                    Text("Aparência")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                    Text("O tema padrão é claro. Os temas escuros usam as paletas da proposta visual.")
                        .font(.system(size: 15))
                        .foregroundStyle(palette.onSurfaceVariant)
                    ForEach(AppThemeOption.allCases) { option in
                        Button {
                            themeStorage = option.rawValue
                        } label: {
                            HStack(alignment: .center, spacing: 12) {
                                Circle()
                                    .fill(themeStorage == option.rawValue ? palette.primary : palette.outlineVariant)
                                    .frame(width: 22, height: 22)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(option.label)
                                        .font(.system(size: 17, weight: .semibold))
                                    Text(option.hint)
                                        .font(.system(size: 15))
                                        .foregroundStyle(palette.onSurfaceVariant)
                                }
                                Spacer()
                            }
                            .foregroundStyle(palette.onSurface)
                            .frame(minHeight: PatientTheme.primaryButton)
                            .padding(.horizontal, 12)
                            .background(palette.surface)
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(option.label)
                        .accessibilityIdentifier("theme_option_\(option.rawValue)")
                        .accessibilityAddTraits(themeStorage == option.rawValue ? [.isSelected] : [])
                    }
                    Text("Assinatura do piloto")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .padding(.top, PatientTheme.margin)
                    Text(SigningPlaceholders.summary)
                        .font(.system(size: 17))
                        .foregroundStyle(palette.onSurfaceVariant)
                }
                .padding(PatientTheme.margin)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Ajustes")
            .patientChrome()
        }
        .accessibilityIdentifier("theme_selector")
    }
}

#Preview {
    ContentView()
}
