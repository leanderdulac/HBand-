import SwiftUI

/// Tokens from PR #17 (Cuidado claro / Azul confiança). Default is Claro.
/// Hex values match `shared` ThemeTokens and Android `Color.kt`.
enum AppThemeOption: String, CaseIterable, Identifiable {
    case light = "light"
    case darkBlue = "dark_blue"
    case darkIndigo = "dark_indigo"
    case darkGraphite = "dark_graphite"
    case followSystem = "follow_system"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .light: return "Claro"
        case .darkBlue: return "Escuro azul"
        case .darkIndigo: return "Escuro índigo"
        case .darkGraphite: return "Escuro grafite + ciano"
        case .followSystem: return "Seguir o sistema"
        }
    }

    var hint: String {
        switch self {
        case .light: return "Branco com azul confiança. Padrão do aplicativo."
        case .darkBlue: return "Fundo escuro com azul confiança."
        case .darkIndigo: return "Fundo escuro com índigo."
        case .darkGraphite: return "Fundo grafite com acento ciano."
        case .followSystem: return "Claro ou escuro azul, conforme o celular."
        }
    }

    static func fromStorage(_ value: String?) -> AppThemeOption {
        AppThemeOption(rawValue: value ?? "") ?? .light
    }
}

/// Named separately so files that `import shared` do not collide with the
/// Kotlin `AppThemeOption` class (no `allCases` on the Native export).
enum PatientThemeChoices {
    static var all: [AppThemeOption] { AppThemeOption.allCases }
}

enum ResolvedAppTheme {
    case lightAzul, darkAzul, darkIndigo, darkGrafite

    var isDark: Bool { self != .lightAzul }
}

struct PatientPalette {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let background: Color
    let surface: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let outline: Color
    let outlineVariant: Color
    let ai: Color
    let heart: Color
    let spo2: Color
    let pressure: Color
    let temperature: Color
    let statusNormalContainer: Color
    let statusNormalForeground: Color
    let statusAttentionContainer: Color
    let statusAttentionForeground: Color
    let statusAlertContainer: Color
    let statusAlertForeground: Color
    let statusNoDataContainer: Color
    let statusNoDataForeground: Color
}

enum PatientTheme {
    static func resolve(_ option: AppThemeOption, systemDark: Bool) -> ResolvedAppTheme {
        switch option {
        case .light: return .lightAzul
        case .darkBlue: return .darkAzul
        case .darkIndigo: return .darkIndigo
        case .darkGraphite: return .darkGrafite
        case .followSystem: return systemDark ? .darkAzul : .lightAzul
        }
    }

    static func palette(_ resolved: ResolvedAppTheme) -> PatientPalette {
        let dark = resolved.isDark
        let vitals = dark
            ? (heart: Color(hex: 0xFF8A9A), spo2: Color(hex: 0x4DD0E1), pressure: Color(hex: 0xB4B0FF), temp: Color(hex: 0xFFB68A))
            : (heart: Color(hex: 0xC2334A), spo2: Color(hex: 0x007A86), pressure: Color(hex: 0x4F46B5), temp: Color(hex: 0xB04A00))
        let status = dark
            ? (
                normalC: Color(hex: 0x1F4E3A), normalF: Color(hex: 0xB5F0D2),
                attC: Color(hex: 0x4A3000), attF: Color(hex: 0xFFDDB0),
                alertC: Color(hex: 0x5C0F0B), alertF: Color(hex: 0xFFB4AB),
                noneC: Color(hex: 0x2A3444), noneF: Color(hex: 0xB3BDCC)
            )
            : (
                normalC: Color(hex: 0xD6F2E4), normalF: Color(hex: 0x0B5A3A),
                attC: Color(hex: 0xFFE8C2), attF: Color(hex: 0x6B3A00),
                alertC: Color(hex: 0xFFDAD6), alertF: Color(hex: 0x8C1D18),
                noneC: Color(hex: 0xE8EDF3), noneF: Color(hex: 0x4A5568)
            )
        switch resolved {
        case .lightAzul:
            return PatientPalette(
                primary: Color(hex: 0x1450A3), onPrimary: .white,
                primaryContainer: Color(hex: 0xD8E6FF), onPrimaryContainer: Color(hex: 0x0B3B7C),
                background: Color(hex: 0xF5F8FC), surface: .white,
                onSurface: Color(hex: 0x111827), onSurfaceVariant: Color(hex: 0x4A5568),
                outline: Color(hex: 0x5A6A80), outlineVariant: Color(hex: 0xDCE3EE),
                ai: Color(hex: 0xEAF1FC),
                heart: vitals.heart, spo2: vitals.spo2, pressure: vitals.pressure, temperature: vitals.temp,
                statusNormalContainer: status.normalC, statusNormalForeground: status.normalF,
                statusAttentionContainer: status.attC, statusAttentionForeground: status.attF,
                statusAlertContainer: status.alertC, statusAlertForeground: status.alertF,
                statusNoDataContainer: status.noneC, statusNoDataForeground: status.noneF
            )
        case .darkAzul:
            return PatientPalette(
                primary: Color(hex: 0xA9C7FF), onPrimary: Color(hex: 0x002F68),
                primaryContainer: Color(hex: 0x1D3A66), onPrimaryContainer: Color(hex: 0xD8E6FF),
                background: Color(hex: 0x0E131B), surface: Color(hex: 0x18202C),
                onSurface: Color(hex: 0xE3E8F0), onSurfaceVariant: Color(hex: 0xB3BDCC),
                outline: Color(hex: 0xB3BDCC), outlineVariant: Color(hex: 0x2A3444),
                ai: Color(hex: 0x16243A),
                heart: vitals.heart, spo2: vitals.spo2, pressure: vitals.pressure, temperature: vitals.temp,
                statusNormalContainer: status.normalC, statusNormalForeground: status.normalF,
                statusAttentionContainer: status.attC, statusAttentionForeground: status.attF,
                statusAlertContainer: status.alertC, statusAlertForeground: status.alertF,
                statusNoDataContainer: status.noneC, statusNoDataForeground: status.noneF
            )
        case .darkIndigo:
            return PatientPalette(
                primary: Color(hex: 0xC4BFFF), onPrimary: Color(hex: 0x241A85),
                primaryContainer: Color(hex: 0x352C80), onPrimaryContainer: Color(hex: 0xE3DFFF),
                background: Color(hex: 0x121120), surface: Color(hex: 0x1C1A2E),
                onSurface: Color(hex: 0xE5E3F2), onSurfaceVariant: Color(hex: 0xBAB7CF),
                outline: Color(hex: 0xBAB7CF), outlineVariant: Color(hex: 0x302D46),
                ai: Color(hex: 0x242044),
                heart: vitals.heart, spo2: vitals.spo2, pressure: vitals.pressure, temperature: vitals.temp,
                statusNormalContainer: status.normalC, statusNormalForeground: status.normalF,
                statusAttentionContainer: status.attC, statusAttentionForeground: status.attF,
                statusAlertContainer: status.alertC, statusAlertForeground: status.alertF,
                statusNoDataContainer: status.noneC, statusNoDataForeground: status.noneF
            )
        case .darkGrafite:
            return PatientPalette(
                primary: Color(hex: 0x5ED8EA), onPrimary: Color(hex: 0x00363E),
                primaryContainer: Color(hex: 0x0D3F48), onPrimaryContainer: Color(hex: 0xB8F1FA),
                background: Color(hex: 0x0B0E10), surface: Color(hex: 0x161B1F),
                onSurface: Color(hex: 0xE4E9EC), onSurfaceVariant: Color(hex: 0xB0BBC1),
                outline: Color(hex: 0xB0BBC1), outlineVariant: Color(hex: 0x283036),
                ai: Color(hex: 0x122A30),
                heart: vitals.heart, spo2: vitals.spo2, pressure: vitals.pressure, temperature: vitals.temp,
                statusNormalContainer: status.normalC, statusNormalForeground: status.normalF,
                statusAttentionContainer: status.attC, statusAttentionForeground: status.attF,
                statusAlertContainer: status.alertC, statusAlertForeground: status.alertF,
                statusNoDataContainer: status.noneC, statusNoDataForeground: status.noneF
            )
        }
    }

    static let minTouch: CGFloat = 48
    static let primaryButton: CGFloat = 56
    static let margin: CGFloat = 16
    static let cardGap: CGFloat = 12
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
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
