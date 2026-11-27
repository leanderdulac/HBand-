import BackgroundTasks
import Foundation

/// Fallback Core sync when BLE notifies have been quiet.
/// iOS does not promise a 15-minute cadence — the live path is GATT notifies
/// (`bluetooth-central`) plus a background URLSession. These tasks reconnect,
/// pull watch history, restart FC streaming, then flush.
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
        guard PatientStoreBootstrap.prepare() else { return false }
        VeepooSession.shared.configureIngest()
        if Task.isCancelled { return false }
        return await VeepooSession.shared.performBackgroundOpportunity(reason: "BGTask")
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
