import Foundation
import shared

/// Identificação do paciente gerada no app: "PAT-" + primeiro nome (sem acentos, A-Z0-9,
/// até 12) + "-" + 4 hex aleatórios (ex.: PAT-LEANDRO-7F3A). Gerada uma vez e mantida:
/// editar o nome depois não muda a identificação (o histórico não se divide).
enum PatientIdentity {
    static let legacyDefault = IngestPayload.shared.DEFAULT_PATIENT_ID

    static func needsGeneration(_ patientId: String) -> Bool {
        let t = patientId.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty || t == legacyDefault
    }

    /// Primeira palavra com letras/dígitos utilizáveis (como no Android 569b533); senão "PACIENTE".
    static func namePart(_ fullName: String) -> String {
        for word in fullName.split(whereSeparator: { $0.isWhitespace }) {
            let folded = String(word)
                .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR"))
                .uppercased()
            let allowed = folded.unicodeScalars.filter { ("A"..."Z").contains($0) || ("0"..."9").contains($0) }
            let part = String(String.UnicodeScalarView(allowed).prefix(12))
            if !part.isEmpty { return part }
        }
        return "PACIENTE"
    }

    static func randomHex() -> String {
        String(format: "%04X", UInt16.random(in: 0...UInt16.max))
    }

    static func generate(fullName: String, hex: String = randomHex()) -> String {
        "PAT-\(namePart(fullName))-\(hex)"
    }

    /// Mantém a identificação existente; gera só se vazia/padrão antigo e houver nome.
    static func resolved(current: String, fullName: String, hex: String = randomHex()) -> String {
        guard needsGeneration(current),
              !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return current }
        return generate(fullName: fullName, hex: hex)
    }
}
