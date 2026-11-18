import CryptoKit
import Foundation
import shared

/// Background `URLSession` for Core uploads. Ktor's Darwin engine in
/// `shared.framework` is a foreground session: a POST started on a BLE wake
/// dies when iOS suspends the process. This session keeps the transfer alive
/// (file upload task) and relaunches the app via
/// `handleEventsForBackgroundURLSession` to reconcile the durable queue.
/// Core idempotency (`client_reading_id` + `Idempotency-Key`) makes overlap
/// with `IngestRuntime.flush()` safe.
final class IngestURLSession: NSObject, URLSessionDelegate, URLSessionDataDelegate {
    static let shared = IngestURLSession()
    static let identifier = SigningPlaceholders.ingestHttpSessionId

    private var session: URLSession!
    private var eventsCompletion: (() -> Void)?
    private var buffers: [Int: Data] = [:]
    private var inflight = false
    private let lock = NSLock()

    private override init() {
        super.init()
        let config = URLSessionConfiguration.background(withIdentifier: Self.identifier)
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        config.allowsCellularAccess = true
        config.allowsConstrainedNetworkAccess = true
        config.allowsExpensiveNetworkAccess = true
        config.waitsForConnectivity = true
        config.shouldUseExtendedBackgroundIdleMode = true
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 15 * 60
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
    }

    /// Recreate the session at launch so iOS can deliver pending task events.
    func prepare() {
        _ = session
    }

    func handleEvents(identifier: String, completionHandler: @escaping () -> Void) {
        guard identifier == Self.identifier else {
            completionHandler()
            return
        }
        eventsCompletion = completionHandler
        session.getAllTasks { [weak self] tasks in
            guard let self, tasks.isEmpty else { return }
            let handler = self.eventsCompletion
            self.eventsCompletion = nil
            handler?()
        }
    }

    func uploadPending(baseUrl: String, apiKey: String, appVersion: String, patientId: String) {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        lock.lock()
        if inflight {
            lock.unlock()
            return
        }
        inflight = true
        lock.unlock()

        let pending = PatientReadings.queueItems(IngestRuntime.shared.items())
            .filter { $0.status == "PENDING" }
            .prefix(10)
        guard !pending.isEmpty else {
            lock.lock(); inflight = false; lock.unlock()
            return
        }

        var readings: [[String: Any]] = []
        var patient = patientId.trimmingCharacters(in: .whitespacesAndNewlines)
        for item in pending {
            guard var obj = Self.jsonObject(item.payloadJson) else { continue }
            obj["client_reading_id"] = item.clientReadingId
            if patient.isEmpty, let fromPayload = obj["patient_id"] as? String, !fromPayload.isEmpty {
                patient = fromPayload
            }
            if let p = obj["patient_id"] as? String, p.isEmpty || p == "PAT-HBAND-001", !patient.isEmpty {
                obj["patient_id"] = patient
            }
            // Send-time keys (app_state, screen_locked, watch_battery_pct). The Ktor
            // flush stamps the same snapshot; this path builds the body itself.
            if let data = try? JSONSerialization.data(withJSONObject: obj),
               let text = String(data: data, encoding: .utf8) {
                readings.append(Self.jsonObject(IngestRuntime.shared.stampUploadContext(json: text)) ?? obj)
            } else {
                readings.append(obj)
            }
        }
        guard !readings.isEmpty, !patient.isEmpty else {
            lock.lock(); inflight = false; lock.unlock()
            return
        }

        let body: [String: Any] = ["patient_id": patient, "readings": readings]
        guard let data = try? JSONSerialization.data(withJSONObject: body) else {
            lock.lock(); inflight = false; lock.unlock()
            return
        }

        let root = baseUrl.hasSuffix("/") ? baseUrl : baseUrl + "/"
        guard let url = URL(string: root + "api/v1/wearables/batch-ingest") else {
            lock.lock(); inflight = false; lock.unlock()
            return
        }

        let file = FileManager.default.temporaryDirectory
            .appendingPathComponent("n2u-ingest-\(UUID().uuidString).json")
        do {
            try data.write(to: file, options: .atomic)
        } catch {
            lock.lock(); inflight = false; lock.unlock()
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(key, forHTTPHeaderField: "X-API-Key")
        if !appVersion.isEmpty {
            request.setValue(appVersion, forHTTPHeaderField: "X-App-Version")
        }
        request.setValue(Self.idempotencyKey(patient: patient, ids: pending.map(\.clientReadingId)),
                         forHTTPHeaderField: "Idempotency-Key")

        let task = session.uploadTask(with: request, fromFile: file)
        task.taskDescription = pending.map(\.clientReadingId).joined(separator: ",")
        task.resume()
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        let id = dataTask.taskIdentifier
        buffers[id, default: Data()].append(data)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        let id = task.taskIdentifier
        let body = buffers.removeValue(forKey: id)
        lock.lock(); inflight = false; lock.unlock()
        let code = (task.response as? HTTPURLResponse)?.statusCode ?? 0
        if let error {
            NSLog("[Next2U] URLSession background: erro %@", error.localizedDescription)
        } else {
            NSLog("[Next2U] URLSession background: HTTP %d bytes=%d", code, body?.count ?? 0)
        }
        // Reconcile via the official Ktor flush: duplicates are accepted by the Core.
        Task { @MainActor in
            _ = await VeepooSession.shared.performFlush()
        }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        let handler = eventsCompletion
        eventsCompletion = nil
        handler?()
    }

    private static func jsonObject(_ raw: String) -> [String: Any]? {
        guard let data = raw.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return obj
    }

    /// Same material as `IngestReconciler.flushIdempotencyKey` (patient + ids, SHA-256).
    private static func idempotencyKey(patient: String, ids: [String]) -> String {
        let material = (["\"" + patient + "\""] + ids.map { "\"" + $0 + "\"" }).joined(separator: ",")
        let digest = SHA256.hash(data: Data("[\(material)]".utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
