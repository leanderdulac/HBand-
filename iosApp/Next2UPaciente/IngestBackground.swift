import BackgroundTasks
import Foundation
import shared

/// Opportunistic Core flush. iOS does not promise a 15-minute cadence.
/// BGAppRefresh is the preferred slot; BGProcessing stays as a longer backup.
enum IngestBackground {
    static let processingTaskId = SigningPlaceholders.ingestTaskId
    static let refreshTaskId = SigningPlaceholders.ingestRefreshTaskId
    static let earliestBegin: TimeInterval = 15 * 60

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskId, using: nil) { task in
            handle(task)
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: processingTaskId, using: nil) { task in
            handle(task)
        }
    }

    static func schedule() {
        scheduleRefresh()
        scheduleProcessing()
    }

    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: earliestBegin)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Scheduling is best-effort; BLE callbacks and Envios still send.
        }
    }

    static func scheduleProcessing() {
        let request = BGProcessingTaskRequest(identifier: processingTaskId)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: earliestBegin)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Scheduling is best-effort; the patient can still send from Envios.
        }
    }

    private static func handle(_ task: BGTask) {
        schedule()
        let completer = TaskCompleter()
        let work = Task {
            let ok = await runFlush()
            completer.finish(task, success: ok)
        }
        task.expirationHandler = {
            work.cancel()
            completer.finish(task, success: false)
        }
    }

    @MainActor
    private static func runFlush() async -> Bool {
        if Task.isCancelled { return false }
        guard prepareStoreAndIngest() else { return false }
        if Task.isCancelled { return false }
        do {
            let flush = try await IngestRuntime.shared.flush()
            if Task.isCancelled { return false }
            VeepooSession.shared.noteBackgroundFlush(flush)
            return flush.failedCount == 0
        } catch is CancellationError {
            return false
        } catch {
            return false
        }
    }

    /// BG launch can skip the SwiftUI `.task` that normally opens the store.
    @MainActor
    private static func prepareStoreAndIngest() -> Bool {
        PatientCrypto.install()
        switch PatientStore.shared.status {
        case .ready:
            break
        case .failed:
            return false
        default:
            let opened = PatientStore.shared.open()
            guard opened == .ready else { return false }
            PatientStore.shared.attachQueue()
        }
        VeepooSession.shared.configureIngest()
        return PatientStore.shared.status == .ready
    }
}

/// `setTaskCompleted` must run exactly once (expiration vs. work finishing).
private final class TaskCompleter: @unchecked Sendable {
    private let lock = NSLock()
    private var finished = false

    func finish(_ task: BGTask, success: Bool) {
        lock.lock()
        defer { lock.unlock() }
        guard !finished else { return }
        finished = true
        task.setTaskCompleted(success: success)
    }
}
