import CryptoKit
import Foundation
import Security
import shared

/// AES-256-GCM via CryptoKit; 256-bit key in the Keychain.
/// Installed into KMP [IosSecureHooks] before [PatientStore.open].
enum PatientCrypto {
    static let service = SigningPlaceholders.bundleId
    static let account = "snapshot-aes-256"
    static let fileName = "next2u-healthsync-snapshot.bin"

    static func install() {
        IosSecureHooks.shared.install(loader: Loader(), saver: Saver())
    }

    final class Loader: PlaintextLoader {
        override func read() -> PlaintextRead {
            PatientCrypto.readSnapshot()
        }
    }

    final class Saver: PlaintextSaver {
        override func write(plaintext: String) -> Bool {
            PatientCrypto.writeSnapshot(plaintext)
        }
    }

    static func snapshotURL() -> URL {
        let dirs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        return dirs[0].appendingPathComponent(fileName)
    }

    static func readSnapshot() -> PlaintextRead {
        let url = snapshotURL()
        guard FileManager.default.fileExists(atPath: url.path) else {
            return PlaintextRead(missing: true, failed: false, plaintext: "", detail: "")
        }
        guard let key = loadKey() else {
            return PlaintextRead(
                missing: false,
                failed: true,
                plaintext: "",
                detail: "Chave do Keychain ausente. O arquivo cifrado não foi tocado."
            )
        }
        do {
            let data = try Data(contentsOf: url)
            let box = try AES.GCM.SealedBox(combined: data)
            let plain = try AES.GCM.open(box, using: key)
            guard let text = String(data: plain, encoding: .utf8) else {
                return PlaintextRead(missing: false, failed: true, plaintext: "", detail: "UTF-8 inválido.")
            }
            return PlaintextRead(missing: false, failed: false, plaintext: text, detail: "")
        } catch {
            return PlaintextRead(
                missing: false,
                failed: true,
                plaintext: "",
                detail: error.localizedDescription
            )
        }
    }

    static func writeSnapshot(_ plaintext: String) -> Bool {
        do {
            let key = try loadOrCreateKey()
            let sealed = try AES.GCM.seal(Data(plaintext.utf8), using: key)
            guard let combined = sealed.combined else { return false }
            try combined.write(to: snapshotURL(), options: .atomic)
            return true
        } catch {
            return false
        }
    }

    private static func loadOrCreateKey() throws -> SymmetricKey {
        if let existing = loadKey() { return existing }
        let key = SymmetricKey(size: .bits256)
        try saveKey(key)
        return key
    }

    private static func loadKey() -> SymmetricKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return SymmetricKey(data: data)
    }

    private static func saveKey(_ key: SymmetricKey) throws {
        let data = key.withUnsafeBytes { Data($0) }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data,
        ]
        SecItemDelete(query as CFDictionary)
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }
}
