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

    static func fromStorage(_ value: String?) -> AppThemeOption {
        AppThemeOption(rawValue: value ?? "") ?? .light
    }
}

enum ResolvedAppTheme {
    case lightAzul, darkAzul, darkIndigo, darkGrafite
}

struct PatientPalette {
    let primary: Color
    let onPrimary: Color
    let background: Color
    let surface: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let outlineVariant: Color
    let ai: Color
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
        switch resolved {
        case .lightAzul:
            return PatientPalette(
                primary: Color(hex: 0x1450A3),
                onPrimary: .white,
                background: Color(hex: 0xF5F8FC),
                surface: .white,
                onSurface: Color(hex: 0x111827),
                onSurfaceVariant: Color(hex: 0x4A5568),
                outlineVariant: Color(hex: 0xDCE3EE),
                ai: Color(hex: 0xEAF1FC)
            )
        case .darkAzul:
            return PatientPalette(
                primary: Color(hex: 0xA9C7FF),
                onPrimary: Color(hex: 0x002F68),
                background: Color(hex: 0x0E131B),
                surface: Color(hex: 0x18202C),
                onSurface: Color(hex: 0xE3E8F0),
                onSurfaceVariant: Color(hex: 0xB3BDCC),
                outlineVariant: Color(hex: 0x2A3444),
                ai: Color(hex: 0x16243A)
            )
        case .darkIndigo:
            return PatientPalette(
                primary: Color(hex: 0xC4BFFF),
                onPrimary: Color(hex: 0x241A85),
                background: Color(hex: 0x121120),
                surface: Color(hex: 0x1C1A2E),
                onSurface: Color(hex: 0xE5E3F2),
                onSurfaceVariant: Color(hex: 0xBAB7CF),
                outlineVariant: Color(hex: 0x302D46),
                ai: Color(hex: 0x242044)
            )
        case .darkGrafite:
            return PatientPalette(
                primary: Color(hex: 0x5ED8EA),
                onPrimary: Color(hex: 0x00363E),
                background: Color(hex: 0x0B0E10),
                surface: Color(hex: 0x161B1F),
                onSurface: Color(hex: 0xE4E9EC),
                onSurfaceVariant: Color(hex: 0xB0BBC1),
                outlineVariant: Color(hex: 0x283036),
                ai: Color(hex: 0x122A30)
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
