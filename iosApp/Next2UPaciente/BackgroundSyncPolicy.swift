import Foundation

/// Decisions for iOS background sync that must stay testable without the
/// Veepoo SDK, CoreBluetooth, or the encrypted store.
///
/// Build 19 stopped live optical tests in the background so the VE30 PPG
/// would not stay on until the battery died. That also stops GATT notifies.
/// After `BleWakeLock` ends (~25s) iOS suspends the process and the 3-second
/// `DispatchSourceTimer` does not fire, so a 30-minute history poll scheduled
/// only on that timer never runs until the patient opens the app.
enum BackgroundSyncPolicy {
    /// Matches `BleWakeLock`'s background task. Not a promise from iOS.
    static let backgroundExecutionBudget: TimeInterval = 25
    /// Battery-policy writes must not consume the whole wake. Foreground can wait.
    static let backgroundPolicyWait: TimeInterval = 3
    static let foregroundPolicyFallback: TimeInterval = 25
    /// One password attempt on a weak RSSI often needs more than the old 8s.
    static let handshakeTimeout: TimeInterval = 20
    /// Foreground only. Background never cancels the link from this watchdog:
    /// `cancelPeripheralConnection` plus a slow password was a reconnect loop.
    static let foregroundLinkResetAfterRetries = 2
    /// A stall check this late means the process was suspended, not that the
    /// watch ignored the password. Wall time during suspension is not a stall.
    static let suspensionGap: TimeInterval = 8
    /// How long a BGTask may wait for the Veepoo password before reading history.
    static let backgroundConnectWait: TimeInterval = 22
    /// Debounce lock/unlock. Each pull is a history read, not a live PPG test.
    static let backgroundEntryMinGap: TimeInterval = 2 * 60

    /// Same sentence as `PatientStore.TRANSIENT_UNAVAILABLE`. A locked Keychain
    /// item is not a missing key and must not mint a new one.
    static let transientStoreDetail =
        "Armazenamento temporariamente indisponível. Tente de novo sem apagar os registros."

    /// Security framework constants. Duplicated so this file builds without
    /// linking Security in the host-side unit test.
    static let errSecSuccess: Int32 = 0
    static let errSecItemNotFound: Int32 = -25300
    static let errSecInteractionNotAllowed: Int32 = -25308

    enum KeyRead: Equatable {
        case success
        case notFound
        case interactionNotAllowed
        case other
    }

    struct HandshakeAction: Equatable {
        var resetClock: Bool
        var retryPassword: Bool
        var resetLink: Bool
    }

    struct BackgroundEntryPlan: Equatable {
        var stopLiveSensors: Bool
        var pullHistory: Bool
    }

    static func classifyKeychain(_ status: Int32) -> KeyRead {
        switch status {
        case errSecSuccess: return .success
        case errSecItemNotFound: return .notFound
        case errSecInteractionNotAllowed: return .interactionNotAllowed
        default: return .other
        }
    }

    /// Create a key only when the item is absent. `errSecInteractionNotAllowed`
    /// means the existing key cannot be read yet (locked); replacing it would
    /// orphan the ciphertext.
    static func mayCreateKey(_ read: KeyRead) -> Bool {
        read == .notFound
    }

    static func handshakeAction(
        attemptAge: TimeInterval,
        retriesSoFar: Int,
        sinceLastCheck: TimeInterval,
        inBackground: Bool
    ) -> HandshakeAction {
        if sinceLastCheck >= suspensionGap {
            return HandshakeAction(resetClock: true, retryPassword: false, resetLink: false)
        }
        guard attemptAge >= handshakeTimeout else {
            return HandshakeAction(resetClock: false, retryPassword: false, resetLink: false)
        }
        let nextRetry = retriesSoFar + 1
        if !inBackground && nextRetry > foregroundLinkResetAfterRetries {
            return HandshakeAction(resetClock: false, retryPassword: false, resetLink: true)
        }
        return HandshakeAction(resetClock: false, retryPassword: true, resetLink: false)
    }

    static func historyFallbackDelay(inBackground: Bool) -> TimeInterval {
        inBackground ? backgroundPolicyWait : foregroundPolicyFallback
    }

    static func batteryPolicyCovers(storedUuid: String?, currentUuid: String) -> Bool {
        let stored = storedUuid?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let current = currentUuid.trimmingCharacters(in: .whitespacesAndNewlines)
        return !stored.isEmpty && !current.isEmpty && stored == current
    }

    /// Foreground still rewrites the battery policy. Background skips it when
    /// this watch already accepted the policy, so the wake is spent on history.
    static func shouldSkipBatteryRewrite(inBackground: Bool, policyCoversThisWatch: Bool) -> Bool {
        inBackground && policyCoversThisWatch
    }

    static func onEnteredBackground(lastHistoryPullAt: Date, now: Date) -> BackgroundEntryPlan {
        BackgroundEntryPlan(
            stopLiveSensors: true,
            pullHistory: shouldPullHistoryOnBackgroundEntry(lastPullAt: lastHistoryPullAt, now: now)
        )
    }

    static func shouldPullHistoryOnBackgroundEntry(lastPullAt: Date, now: Date) -> Bool {
        now.timeIntervalSince(lastPullAt) >= backgroundEntryMinGap
    }

    /// Pending connect is enough when CoreBluetooth can still see the peripheral.
    /// A service-UUID scan is the background wake when the UUID cannot be retrieved.
    /// A scan with `withServices: nil` is stopped as soon as the app suspends.
    static func shouldStartBackgroundServiceScan(
        inBackground: Bool,
        hardwareReady: Bool,
        canRetrievePeripheral: Bool
    ) -> Bool {
        inBackground && !hardwareReady && !canRetrievePeripheral
    }

    static func matchesKnownWatch(
        uuid: String,
        address: String,
        knownUuids: [String],
        knownAddresses: [String]
    ) -> Bool {
        func hit(_ value: String, _ known: [String]) -> Bool {
            let needle = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if needle.isEmpty { return false }
            return known.contains { candidate in
                let trimmed = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                return !trimmed.isEmpty && trimmed.compare(needle, options: .caseInsensitive) == .orderedSame
            }
        }
        return hit(uuid, knownUuids) || hit(address, knownAddresses)
    }
}
