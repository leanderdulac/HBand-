import Foundation

/// Lista do paciente: só relógios Veepoo/VE30/HBand.
///
/// Os sinais são os mesmos do SDK (VPBleCentralManage / VPPeripheralModel) e do
/// Android (`VeepooDeviceSupport`):
/// - serviços de anúncio FEE7, FFFF e 0001 (o scan do SDK) e o Nordic UART que o projeto já aceita
/// - fabricante `f8f8` (filtro `manufacturerIDFilter`) ou `f8f9` (o mesmo parser, MAC noutro offset),
///   só quando o anúncio de fabricante tem pelo menos 8 bytes
/// - nome com veepoo, hband ou ve30, sem diferenciar maiúsculas, espaços ou hífens
/// - relógio já gravado neste iPhone (endereço ou UUID), para o aparelho renomeado continuar a aparecer
enum WatchScanFilter {
    static let patientHint = "A lista mostra só relógios compatíveis com este aplicativo."
    static let unnamedWatchLabel = "Relógio"

    static let scanServiceUuids: Set<String> = [
        "0000fee7-0000-1000-8000-00805f9b34fb",
        "0000ffff-0000-1000-8000-00805f9b34fb",
        "00000001-0000-1000-8000-00805f9b34fb",
        "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
    ]

    static func shouldList(
        name: String? = nil,
        serviceUuids: [String] = [],
        manufacturerData: Data? = nil,
        address: String? = nil,
        knownAddresses: [String] = [],
        uuid: String? = nil,
        knownUuids: [String] = [],
        alreadyConnectedOnVeepooService: Bool = false
    ) -> Bool {
        if matchesKnown(address, knownAddresses) || matchesKnown(uuid, knownUuids) {
            return true
        }
        if alreadyConnectedOnVeepooService { return true }
        if hasVeepooService(serviceUuids) { return true }
        if hasVeepooManufacturer(manufacturerData) { return true }
        return looksLikeVeepooName(name)
    }

    static func looksLikeVeepooName(_ name: String?) -> Bool {
        let compact = compactName(name)
        if compact.isEmpty { return false }
        return compact.contains("ve30") || compact.contains("hband") || compact.contains("veepoo")
    }

    static func hasVeepooService(_ serviceUuids: [String]) -> Bool {
        serviceUuids.contains { scanServiceUuids.contains(normalizeUuid($0)) }
    }

    /// Manufacturer ID the SDK compares with `isEqualToString:@"f8f8"`.
    /// `f8f9` is the other layout recognized while parsing the MAC.
    static func hasVeepooManufacturer(_ data: Data?) -> Bool {
        guard let data else { return false }
        let bytes = [UInt8](data)
        guard bytes.count >= 8 else { return false }
        let id = String(format: "%02x%02x", Int(bytes[0]), Int(bytes[1]))
        return id == "f8f8" || id == "f8f9"
    }

    static func normalizeUuid(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let hex = trimmed.filter { isHex($0) }
        if hex.count == 4 {
            return "0000\(hex)-0000-1000-8000-00805f9b34fb"
        }
        if hex.count == 32 {
            let chars = Array(hex)
            func slice(_ from: Int, _ to: Int) -> String {
                String(chars[from..<to])
            }
            return "\(slice(0, 8))-\(slice(8, 12))-\(slice(12, 16))-\(slice(16, 20))-\(slice(20, 32))"
        }
        return trimmed
    }

    private static func isHex(_ c: Character) -> Bool {
        ("0"..."9").contains(c) || ("a"..."f").contains(c)
    }

    private static func compactName(_ name: String?) -> String {
        (name ?? "").lowercased().filter { $0.isLetter || $0.isNumber }
    }

    private static func matchesKnown(_ value: String?, _ known: [String]) -> Bool {
        let needle = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if needle.isEmpty { return false }
        return known.contains { candidate in
            let trimmed = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            return !trimmed.isEmpty && trimmed.compare(needle, options: .caseInsensitive) == .orderedSame
        }
    }
}
