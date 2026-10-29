import Foundation

/// Signing values for the first TestFlight / Ad Hoc archive.
/// Team ID is the Next2U Apple team. The bundle ID stays as agreed.
enum SigningPlaceholders {
    static let teamId = "H87XXMXA5G"
    static let bundleId = "br.com.next2u.saude.paciente"
    static let ingestTaskId = "br.com.next2u.saude.paciente.ingest"
    static let ingestRefreshTaskId = "br.com.next2u.saude.paciente.ingest.refresh"

    static let summary = """
    Team ID: \(teamId) (Next2U).
    Bundle ID: \(bundleId).
    Signing: automático. Certificados e perfis o Xcode cria ao escolher o Team.
    TestFlight: Archive → App Store Connect → grupo interno.
    Ad Hoc: cadastrar UDID dos iPhones do piloto se alguém não puder usar TestFlight.
    """
}
