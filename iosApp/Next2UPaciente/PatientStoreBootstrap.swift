import Foundation
import shared

/// Opens the encrypted store on paths that never reach SwiftUI `.task`:
/// CoreBluetooth state restoration, a cold background launch, and BGTask.
/// A transient Keychain failure (`errSecInteractionNotAllowed`) stays retryable.
enum PatientStoreBootstrap {
    @MainActor
    @discardableResult
    static func prepare() -> Bool {
        PatientCrypto.install()
        let wasReady = PatientStore.shared.status == .ready
        let opened: StoreStatus
        switch PatientStore.shared.status {
        case .ready:
            opened = .ready
        case .failed:
            opened = PatientStore.shared.retryOpen()
        default:
            opened = PatientStore.shared.open()
        }
        guard opened == .ready else { return false }
        if !wasReady {
            PatientStore.shared.attachQueue()
            VeepooSession.shared.configureIngest()
        }
        return true
    }
}
