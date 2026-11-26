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
        protectContainers()
        IosSecureHooks.shared.install(loader: Loader(), saver: Saver())
    }

    /// Data Protection class requested for health files. Background upload still
    /// works after the first unlock of the device.
    static func protectContainers() {
        let manager = FileManager.default
        guard let docs = manager.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let extras = [
            docs,
            docs.appendingPathComponent(fileName),
            docs.appendingPathComponent("next2u-healthsync-queue.json"),
            docs.appendingPathComponent(fileName).appendingPathExtension("r8migrating"),
        ]
        for url in extras where manager.fileExists(atPath: url.path) {
            try? manager.setAttributes(
                [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
                ofItemAtPath: url.path
            )
        }
        try? manager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: docs.path
        )
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
            // No Keychain item yet: a legacy JSON file can be encrypted now.
            // Anything else is left byte-for-byte (likely ciphertext without its key).
            if let data = try? Data(contentsOf: url),
               let text = String(data: data, encoding: .utf8),
               SnapshotAtRest.shared.shouldMigratePlaintext(utf8: text),
               migratePlaintext(text, url: url) {
                return PlaintextRead(missing: false, failed: false, plaintext: text, detail: "")
            }
            return PlaintextRead(
                missing: false,
                failed: true,
                plaintext: "",
                detail: "Chave do Keychain ausente. O arquivo cifrado não foi tocado."
            )
        }
        let data: Data
        do {
            data = try Data(contentsOf: url)
        } catch {
            return PlaintextRead(
                missing: false,
                failed: true,
                plaintext: "",
                detail: error.localizedDescription
            )
        }
        if let text = decrypt(data, key: key) {
            protect(url)
            return PlaintextRead(missing: false, failed: false, plaintext: text, detail: "")
        }
        // Legacy plaintext JSON (queue, profile, readings). Re-encrypt in place
        // only after a round-trip matches; otherwise the original file stays.
        if let text = String(data: data, encoding: .utf8),
           SnapshotAtRest.shared.shouldMigratePlaintext(utf8: text),
           migratePlaintext(text, url: url) {
            return PlaintextRead(missing: false, failed: false, plaintext: text, detail: "")
        }
        return PlaintextRead(
            missing: false,
            failed: true,
            plaintext: "",
            detail: "Arquivo ilegível. O conteúdo original não foi substituído."
        )
    }

    private static func decrypt(_ data: Data, key: SymmetricKey) -> String? {
        guard let box = try? AES.GCM.SealedBox(combined: data),
              let plain = try? AES.GCM.open(box, using: key),
              let text = String(data: plain, encoding: .utf8) else { return nil }
        return text
    }

    private static func migratePlaintext(_ text: String, url: URL) -> Bool {
        let temp = url.appendingPathExtension("r8migrating")
        do {
            let key = try loadOrCreateKey()
            let sealed = try AES.GCM.seal(Data(text.utf8), using: key)
            guard let combined = sealed.combined else { return false }
            try? FileManager.default.removeItem(at: temp)
            try combined.write(to: temp, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            guard decrypt(try Data(contentsOf: temp), key: key) == text else {
                try? FileManager.default.removeItem(at: temp)
                return false
            }
            _ = try FileManager.default.replaceItemAt(url, withItemAt: temp)
            protect(url)
            return true
        } catch {
            try? FileManager.default.removeItem(at: temp)
            return false
        }
    }

    private static func protect(_ url: URL) {
        try? FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
    }

    static func writeSnapshot(_ plaintext: String) -> Bool {
        do {
            let key = try loadOrCreateKey()
            let sealed = try AES.GCM.seal(Data(plaintext.utf8), using: key)
            guard let combined = sealed.combined else { return false }
            try combined.write(to: snapshotURL(), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
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
