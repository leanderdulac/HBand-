import Foundation
import UIKit

/// Holds an iOS background task so BLE/SDK callbacks can record and upload
/// before the process is suspended. `Task { @MainActor }` without this was
/// dropped after the last reading on 2026-11-14 (phone locked).
final class BleWakeLock: @unchecked Sendable {
    static let shared = BleWakeLock()

    private let lock = NSLock()
    private var task = UIBackgroundTaskIdentifier.invalid
    private var endWork: DispatchWorkItem?

    /// Call synchronously at the start of every BLE/SDK callback, before returning to iOS.
    /// Each ping keeps the process awake a bit longer so FC can restart and the POST can leave.
    func ping() {
        lock.lock()
        defer { lock.unlock() }
        if task == .invalid {
            task = UIApplication.shared.beginBackgroundTask(withName: "next2u.ble") { [weak self] in
                self?.end()
            }
        }
        endWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            self?.end()
        }
        endWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 25, execute: work)
    }

    func end() {
        lock.lock()
        defer { lock.unlock() }
        endWork?.cancel()
        endWork = nil
        if task != .invalid {
            UIApplication.shared.endBackgroundTask(task)
            task = .invalid
        }
    }
}

enum BleMain {
    /// Extends execution, then runs `body` on the main actor without an unstructured `Task`.
    static func run(_ body: @escaping @MainActor () -> Void) {
        BleWakeLock.shared.ping()
        if Thread.isMainThread {
            MainActor.assumeIsolated(body)
        } else {
            DispatchQueue.main.async {
                MainActor.assumeIsolated(body)
            }
        }
    }
}
