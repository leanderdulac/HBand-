import BackgroundTasks
import Foundation
import shared

/// Opportunistic Core flush. iOS does not promise a 15-minute cadence.
enum IngestBackground {
    static let taskId = SigningPlaceholders.ingestTaskId

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskId, using: nil) { task in
            handle(task)
        }
    }

    static func schedule() {
        let request = BGProcessingTaskRequest(identifier: taskId)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Scheduling is best-effort; the patient can still send from Envios.
        }
    }

    private static func handle(_ task: BGTask) {
        schedule()
        task.expirationHandler = { task.setTaskCompleted(success: false) }
        Task {
            let ok: Bool
            if let flush = try? await IngestRuntime.shared.flush() {
                ok = flush.failedCount == 0
            } else {
                ok = false
            }
            task.setTaskCompleted(success: ok)
        }
    }
}
