import Foundation

/// Values the first TestFlight/Ad Hoc archive still needs from Leandro.
/// The Xcode project builds with these placeholders; they are not secrets.
enum SigningPlaceholders {
    static let teamId = "TEAMIDXXXX"
    static let bundleId = "br.com.next2u.saude.paciente"
    static let ingestTaskId = "br.com.next2u.saude.paciente.ingest"

    static let summary = """
    Team ID: \(teamId) (substituir no Xcode pelo time Next2U).
    Bundle ID: \(bundleId) (confirmar se é o ID oficial).
    Signing: automático. Certificados e perfis o Xcode cria ao escolher o Team.
    TestFlight: Archive → App Store Connect → grupo interno.
    Ad Hoc: cadastrar UDID dos iPhones do piloto se alguém não puder usar TestFlight.
    HealthKit e o framework Veepoo ficam para fases seguintes.
    """
}
