import CoreBluetooth
import Foundation
import os
import SwiftUI
import UIKit
import VeepooBleSDK
import shared

private let bleLog = Logger(subsystem: "br.com.next2u.saude.paciente", category: "ble")

/// P0 Veepoo session: scan, connect, senha 0000, FC ao vivo, SpO₂ e bateria.
///
/// VeepooBleSDK owns the real `CBCentralManager`. A dummy sidecar with a restore
/// id never saw the VE30, so iOS did not relaunch this process after suspend.
/// We replace the SDK manager (same restore id, our forwarder) at launch.
///
/// Enum cases use `rawValue` from `VPPublicDefine.h`. Swift strips ObjC prefixes
/// (`BleConnectSuccess` → `.connectSuccess`), so matching header names fails.
@MainActor
final class VeepooSession: NSObject, ObservableObject {
    static let shared = VeepooSession()
    static let defaultPassword = "0000"
    static let restoreId = "br.com.next2u.saude.paciente.ble"

    @Published var isScanning = false
    @Published var devices: [ScannedWatch] = []
    @Published var connected: ScannedWatch?
    @Published var sessionMessage = "Toque em Buscar relógio. Use só este app — não abra o H Band oficial ao mesmo tempo."
    @Published var hardwareReady = false
    @Published var heartRate: Int = 0
    @Published var spo2: Int = 0
    @Published var batteryPercent: Int?
    @Published var lastClientReadingId: String?
    @Published var lastFlushMessage = ""
    @Published var lastHttpCode: Int = 0
    @Published var pendingCount: Int = 0
    @Published var failedCount: Int = 0
    @Published var isFlushing = false
    @Published var lastFlushSyncedCount: Int = 0
    @Published var localMetricCount: Int = 0
    @Published var localSummary = ""
    @Published var ingestUrl = VeepooSession.bundledString("Next2UCoreBaseUrl")
        ?? "https://healthtech-secure-api-5794833455.us-central1.run.app/"
    @Published var ingestKey = ""
    @Published var lastBleEvent = "Nenhum evento BLE ainda"
    @Published var lastBleEventAt: Date?
    @Published var bleConnectionLabel = "Sem ligação"
    @Published var lastUploadLabel = "Nenhum envio ainda"
    @Published var profileRevision = 0
    @Published var watchAlias = UserDefaults.standard.string(forKey: "last_veepoo_name") ?? ""
    @Published var renameStatus = ""
    /// Nome da pulseira enviado ao Core (`device_label`) em todo envio. Ver `resolvedDeviceLabel`.
    @Published var deviceLabel: String = VeepooSession.initialDeviceLabel()
    @Published var watchTimeSyncing = false
    @Published var watchTimeStatus = ""
    @Published var watchTimeLastSync: Date? = UserDefaults.standard.object(forKey: "watch_time_sync_at") as? Date
    /// Capacidades de mostrador reportadas pelo firmware; usadas depois para liberar "Trocar foto do mostrador".
    @Published var watchPhotoDialCount: Int = UserDefaults.standard.integer(forKey: "watch_photo_dial_count")
    @Published var watchScreenTypes: Int = UserDefaults.standard.integer(forKey: "watch_screen_types")
    var watchSupportsPhotoDial: Bool { watchPhotoDialCount > 0 }
    private var watchTimeAttempt: UUID?
    private static var is24HourClock: Bool {
        let f = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .current) ?? "HH"
        return !f.contains("a")
    }

    private let manager = VPBleCentralManage.sharedBleManager()!
    private let forwarder = VeepooCentralForwarder()
    private var lastAddress: String? {
        get { UserDefaults.standard.string(forKey: "last_veepoo_address") }
        set { UserDefaults.standard.set(newValue, forKey: "last_veepoo_address") }
    }
    private var lastName: String? {
        get { UserDefaults.standard.string(forKey: "last_veepoo_name") }
        set {
            UserDefaults.standard.set(newValue, forKey: "last_veepoo_name")
            watchAlias = newValue ?? ""
        }
    }

    var displayWatchName: String {
        if !watchAlias.isEmpty { return watchAlias }
        return connected?.name ?? ""
    }
    private var lastPeripheralUUID: String? {
        get { UserDefaults.standard.string(forKey: "last_veepoo_uuid") }
        set { UserDefaults.standard.set(newValue, forKey: "last_veepoo_uuid") }
    }
    private var autoReconnectEnabled: Bool {
        UserDefaults.standard.object(forKey: "auto_reconnect_ble") as? Bool ?? true
    }
    private var userRequestedDisconnect = false
    /// One pending backoff retry at a time (503/429 from the Core).
    private var scheduledRetry: Task<Void, Never>?
    private var heartRunning = false
    private var spo2Running = false
    private var heartSamplesThisBurst = 0
    private var cycleGotReading = false
    private var consecutiveEmptyCycles = 0
    private var nextCycleAt = Date.distantPast
    /// Background: FC contínua mantém notificações GATT chegando, e cada uma acorda o app
    /// (bluetooth-central). Sem isso, a pausa de 5 min entre ciclos deixava o app sem tráfego
    /// BLE, o iOS suspendia e o watchdog nunca iniciava o próximo ciclo (campo 29/10).
    private var backgroundStreaming = false
    private var lastRecordAt = Date.distantPast
    private var lastFlushAt = Date.distantPast
    private var deferredFlush: Task<Void, Never>?
    private var lastHistoryPullAt = Date.distantPast
    static let minUploadInterval: TimeInterval = 60
    static let historyPullMinInterval: TimeInterval = 5 * 60
    /// Liveness of the measurement loop. The FC/SpO₂ rotation is driven only by BLE
    /// callbacks, so one missed callback used to freeze it forever (no readings, no POSTs).
    private var lastSampleAt = Date()
    private var measureStartedAt = Date()
    private var gattUpSince: Date?
    private var stallTimer: DispatchSourceTimer?
    /// Single in-flight Veepoo connect. Without it every didConnect (the SDK itself calls
    /// connect) re-entered veepooSDKConnectDevice, producing a storm of parallel sessions,
    /// "pairing fail" / "check result fail", and an FC test that never returned values.
    private var sessionAttemptAt: Date?
    /// One auto-connect to the last known watch per user scan.
    private var autoConnectAttemptedThisScan = false

    /// Services VE30/HBand-class bands commonly expose. Used only to ask iOS
    /// for peripherals already connected at the OS (H Band official / Settings).
    private static let veepooServiceUUIDs: [CBUUID] = [
        CBUUID(string: "FFF0"),
        CBUUID(string: "FFF6"),
        CBUUID(string: "FFE0"),
        CBUUID(string: "FEE7"),
        CBUUID(string: "5554"),
        CBUUID(string: "180A"),
        CBUUID(string: "180D"),
        CBUUID(string: "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"),
    ]

    private func beginSessionAttempt(_ origin: String) -> Bool {
        if let at = sessionAttemptAt, Date().timeIntervalSince(at) < Self.handshakeAttemptTimeout {
            return false
        }
        sessionAttemptAt = Date()
        return true
    }

    struct ScannedWatch: Identifiable, Equatable {
        var id: String { uuid.isEmpty ? address : uuid }
        var name: String
        var address: String
        let uuid: String
        let rssi: Int
        let isOsConnected: Bool
        let model: VPPeripheralModel

        static func == (lhs: ScannedWatch, rhs: ScannedWatch) -> Bool {
            if !lhs.uuid.isEmpty, !rhs.uuid.isEmpty { return lhs.uuid == rhs.uuid }
            return !lhs.address.isEmpty && lhs.address == rhs.address
        }
    }

    static func bundledString(_ key: String) -> String? {
        guard let v = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let t = v.trimmingCharacters(in: .whitespacesAndNewlines)
        return (t.isEmpty || t.hasPrefix("$(")) ? nil : t
    }

    /// Must run on the main thread before `didFinishLaunching` returns so iOS
    /// can deliver `willRestoreState` (TN3115).
    static func installAtLaunch() {
        _ = shared
    }

    override init() {
        super.init()
        manager.isLogEnable = true
        manager.is24HourFormat = true
        // O reconectar automático do SDK fazia, com o relógio fora de alcance, um scan e duas
        // buscas de periféricos ligados POR SEGUNDO (dreno de bateria). Fica sempre desligado:
        // a reconexão é um único connect pendente do iOS (sem timeout, baixo consumo) +
        // scan ocasional com backoff só quando o periférico é desconhecido (ver backoffScan).
        manager.automaticConnection = false
        // Default rrisLimit is -85: the SDK drops weaker ads, so a VE30 on the
        // wrist (phone in pocket / other hand) never appears. -127 is the BLE floor.
        manager.manufacturerIDFilter = false
        manager.rrisLimit = -127
        // Veepoo's 30s timer cancelled pending iOS connects. Keep the system
        // pending-connect until the VE30 advertises again.
        manager.connectionTimeout = .max
        manager.deviceConfirmTimeout = 30
        attachRestorableCentral()
        configureIngest()
        refreshLocalStore()
        note("Sessão BLE criada. Restore id \(Self.restoreId).")
        startStallWatchdog()
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.handleEnteredBackground() }
        }
        nc.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.handleWillEnterForeground() }
        }
    }

    /// Tempo máximo entre pedir a sessão Veepoo (senha 0000) e o handshake; passou disso,
    /// o watchdog repõe a sessão. Antes eram 60 s + até 30 s de intervalo do timer.
    static let handshakeAttemptTimeout: TimeInterval = 8
    /// Tentativas de repor a sessão no mesmo GATT antes de derrubar o link e religar do zero.
    static let handshakeRetriesBeforeLinkReset = 2
    static let watchdogInterval: TimeInterval = 3
    private var handshakeRetries = 0
    private var passwordSentAt: Date?

    /// Runs whenever the process is awake (foreground, or the ~10 s windows iOS grants on
    /// each GATT indication in background) and restarts a stuck handshake/measurement.
    private func startStallWatchdog() {
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now() + Self.watchdogInterval, repeating: Self.watchdogInterval, leeway: .seconds(1))
        timer.setEventHandler { [weak self] in
            MainActor.assumeIsolated { self?.checkStall() }
        }
        timer.resume()
        stallTimer = timer
    }

    private func checkStall() {
        let now = Date()
        guard autoReconnectEnabled, !userRequestedDisconnect else { return }
        if !hardwareReady {
            // GATT up but the Veepoo password handshake never finished (e.g. "bluetooth pairing
            // fail" from the SDK, or a connect issued before poweredOn that the SDK dropped).
            guard manager.centralManager.state == .poweredOn,
                  let since = gattUpSince,
                  let uuid = lastPeripheralUUID.flatMap(UUID.init(uuidString:)),
                  let peripheral = manager.centralManager.retrievePeripherals(withIdentifiers: [uuid]).first,
                  peripheral.state == .connected else { return }
            let attemptAge = now.timeIntervalSince(sessionAttemptAt ?? since)
            guard attemptAge > Self.handshakeAttemptTimeout else { return }
            handshakeRetries += 1
            if handshakeRetries > Self.handshakeRetriesBeforeLinkReset {
                note("Watchdog: handshake sem resposta após \(handshakeRetries - 1) tentativas (\(Int(now.timeIntervalSince(since))) s). Religando o link com o relógio.")
                handshakeRetries = 0
                gattUpSince = nil
                sessionAttemptAt = nil
                // didDisconnect → keepPendingConnect → didConnect → resumeSession.
                manager.centralManager.cancelPeripheralConnection(peripheral)
                return
            }
            note("Watchdog: handshake parado há \(Int(attemptAge)) s (tentativa \(handshakeRetries)). Repondo sessão.")
            sessionAttemptAt = nil
            resumeSession(peripheral)
            return
        }
        advanceMeasurementCycle(now: now)
    }

    // MARK: - Ciclo de medição pontual (FC → SpO₂ → pausa), sem medição contínua

    /// Pausa entre ciclos com leitura. Ciclos sem leitura dobram a pausa (até 30 min).
    static let cycleIdleSeconds: TimeInterval = 5 * 60
    static let maxIdleSeconds: TimeInterval = 30 * 60
    static let heartStageTimeout: TimeInterval = 45
    static let oxygenStageTimeout: TimeInterval = 40
    static let heartSamplesPerCycle = 2

    /// Primeira tentativa após ciclo vazio (fora do pulso/ocupado) em 2 min; depois 4, 8, 16, 30.
    static let emptyRetrySeconds: TimeInterval = 2 * 60

    static func idleSeconds(afterEmptyCycles empty: Int) -> TimeInterval {
        guard empty > 0 else { return cycleIdleSeconds }
        return min(maxIdleSeconds, emptyRetrySeconds * pow(2, Double(min(empty - 1, 4))))
    }

    /// Chamado pelo watchdog (3 s) e por eventos BLE: encerra estágio vencido e inicia o
    /// próximo ciclo só quando a pausa terminou.
    private func advanceMeasurementCycle(now: Date = Date()) {
        guard hardwareReady else { return }
        if heartRunning, backgroundStreaming {
            // FC contínua: só reinicia se o relógio parou de mandar amostras.
            if now.timeIntervalSince(lastSampleAt) > Self.heartStageTimeout {
                note("FC contínua sem amostras; reiniciando.")
                stopHeart()
                startHeart()
            }
            return
        }
        if heartRunning, now.timeIntervalSince(measureStartedAt) > Self.heartStageTimeout {
            note("Ciclo: FC sem resultado completo em \(Int(Self.heartStageTimeout)) s.")
            stopHeart()
            if cycleGotReading { startOxygen() } else { finishCycle(reason: "FC sem leitura") }
            return
        }
        if spo2Running, now.timeIntervalSince(measureStartedAt) > Self.oxygenStageTimeout {
            note("Ciclo: SpO₂ sem resultado em \(Int(Self.oxygenStageTimeout)) s.")
            finishCycle(reason: "SpO₂ sem resultado")
            return
        }
        if backgroundStreaming, !heartRunning, !spo2Running {
            startHeart()
            return
        }
        if !heartRunning, !spo2Running, now >= nextCycleAt {
            startCycle(reason: "pausa concluída")
        }
    }

    private func startCycle(reason: String, force: Bool = false) {
        guard hardwareReady, !heartRunning, !spo2Running else { return }
        if !force, Date() < nextCycleAt { return }
        cycleGotReading = false
        note("Ciclo de medição: início (\(reason)).")
        NSLog("[Next2U] Ciclo de medição: início (%@)", reason)
        startHeart()
    }

    private func finishCycle(reason: String) {
        stopSensors()
        consecutiveEmptyCycles = cycleGotReading ? 0 : consecutiveEmptyCycles + 1
        let idle = Self.idleSeconds(afterEmptyCycles: consecutiveEmptyCycles)
        nextCycleAt = Date().addingTimeInterval(idle)
        let msg = "Ciclo de medição: fim (\(reason)); leitura=\(cycleGotReading ? "sim" : "não"); próxima em \(Int(idle / 60)) min."
        note(msg)
        NSLog("[Next2U] %@", msg)
        if cycleGotReading {
            sessionMessage = "Medição concluída. Próxima em \(Int(idle / 60)) min."
        }
        if backgroundStreaming || Self.isInBackground {
            // Em segundo plano uma pausa sem tráfego BLE = app suspenso. Reabre a FC já.
            backgroundStreaming = true
            if hardwareReady { startHeart() }
        }
    }

    func configureIngest() {
        let key = ingestKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if !Self.loggedIngestConfig {
            Self.loggedIngestConfig = true
            // Só presença/tamanho — nunca o valor.
            let builtIn = VeepooSession.bundledString("Next2UIngestApiKey") ?? ""
            NSLog("[Next2U] Configuração de envio: URL embutida=%@ chave embutida=%@ (len %d) aparelho=%@",
                  VeepooSession.bundledString("Next2UCoreBaseUrl") == nil ? "não" : "sim",
                  builtIn.isEmpty ? "não" : "sim", builtIn.count, deviceLabel)
        }
        IngestRuntime.shared.setDeviceLabel(raw: deviceLabel)
        IngestRuntime.shared.setAppVersion(raw: Self.appVersionString)
        IngestRuntime.shared.configure(
            baseUrl: ingestUrl,
            overrideKey: key.isEmpty ? nil : key,
            builtInKey: VeepooSession.bundledString("Next2UIngestApiKey") ?? ""
        )
        pendingCount = Int(IngestRuntime.shared.pendingCount())
        failedCount = Int(IngestRuntime.shared.failedCount())
        refreshLocalStore()
    }

    private static var loggedIngestConfig = false
    /// "1.2.3+45" (CFBundleShortVersionString+CFBundleVersion) → header X-App-Version.
    static let appVersionString: String = {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "0"
        let build = info?["CFBundleVersion"] as? String ?? "0"
        return "ios-\(short)+\(build)"
    }()
    /// Nome da pulseira escolhido pelo paciente em Meu perfil (vazio = segue o nome do perfil).
    static let wristbandNameKey = "wristband_name"
    /// Último `device_label` efetivo (cache para envios antes de o perfil abrir).
    static let deviceLabelCacheKey = "device_label_effective"
    /// Chave antiga (build 6-10, ex.: "iPhone de LFM" fixo); não é mais usada.
    static let legacyDeviceLabelKey = "device_label"

    /// Sugestão a partir do nome do perfil: "Pulseira de <nome>".
    static func defaultWristbandName(profileName: String) -> String? {
        let name = profileName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return nil }
        return IngestRuntime.shared.sanitizeDeviceLabel(raw: "Pulseira de \(name)")
    }

    /// Nome salvo pelo paciente > "Pulseira de <nome do perfil>" > modelo do aparelho.
    static func resolvedDeviceLabel(profileName: String?) -> String {
        let defaults = UserDefaults.standard
        if let saved = IngestRuntime.shared.sanitizeDeviceLabel(raw: defaults.string(forKey: wristbandNameKey)) {
            return saved
        }
        if let profileName, let suggested = defaultWristbandName(profileName: profileName) {
            return suggested
        }
        return IngestRuntime.shared.sanitizeDeviceLabel(raw: UIDevice.current.model) ?? "iPhone"
    }

    static func initialDeviceLabel() -> String {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: legacyDeviceLabelKey)
        // Teste (devicectl/Xcode): `-Next2UDeviceLabel "…"` define o nome da pulseira.
        if let arg = defaults.volatileDomain(forName: UserDefaults.argumentDomain)["Next2UDeviceLabel"] as? String,
           let clean = IngestRuntime.shared.sanitizeDeviceLabel(raw: arg) {
            defaults.set(clean, forKey: wristbandNameKey)
        }
        if defaults.string(forKey: wristbandNameKey) == nil,
           let cached = IngestRuntime.shared.sanitizeDeviceLabel(raw: defaults.string(forKey: deviceLabelCacheKey)) {
            return cached
        }
        return resolvedDeviceLabel(profileName: nil)
    }

    /// Salva o nome da pulseira (vazio ou igual à sugestão = segue o nome do perfil).
    func saveWristbandName(_ proposed: String, profileName: String) {
        let clean = IngestRuntime.shared.sanitizeDeviceLabel(raw: proposed)
        if clean == nil || clean == Self.defaultWristbandName(profileName: profileName) {
            UserDefaults.standard.removeObject(forKey: Self.wristbandNameKey)
        } else {
            UserDefaults.standard.set(clean, forKey: Self.wristbandNameKey)
        }
        refreshDeviceLabel(profileName: profileName)
    }

    /// Instalações com nome salvo e identificação padrão (PAT-HBAND-001) ganham uma identificação
    /// própria uma única vez. Também aplica a identificação aos envios (inclusive à fila).
    @discardableResult
    func ensurePatientId() -> String {
        let p = PatientStore.shared.profile()
        var current = p.patientId
        let resolved = PatientIdentity.resolved(current: current, fullName: p.fullName)
        if resolved != current {
            let saved = PatientStore.shared.saveProfile(
                profile: UserProfile(
                    id: p.id, patientId: resolved, fullName: p.fullName, age: p.age, gender: p.gender,
                    heightCm: p.heightCm, weightKg: p.weightKg, dailyStepGoal: p.dailyStepGoal,
                    targetWaterMl: p.targetWaterMl, emergencyContact: p.emergencyContact,
                    medicalNotes: p.medicalNotes
                )
            )
            if saved {
                current = resolved
                note("Identificação do paciente criada: \(resolved)")
            }
        }
        IngestRuntime.shared.setPatientId(raw: current)
        return current
    }

    /// Recalcula o `device_label` (perfil aberto) e aplica nos próximos envios.
    func refreshDeviceLabel(profileName: String? = nil) {
        let name = profileName ?? PatientStore.shared.profile().fullName
        let label = Self.resolvedDeviceLabel(profileName: name)
        UserDefaults.standard.set(label, forKey: Self.deviceLabelCacheKey)
        IngestRuntime.shared.setDeviceLabel(raw: label)
        if label != deviceLabel {
            deviceLabel = label
            note("Nome da pulseira atualizado.")
        }
    }

    func refreshLocalStore() {
        localMetricCount = Int(PatientStore.shared.metricCount())
        localSummary = PatientStore.shared.localSummary()
    }

    func onStoreReady() {
        note("Store cifrado pronto.")
        ensurePatientId()
        refreshDeviceLabel()
        reconnectLastKnown(reason: "store pronto")
        flushNow()
    }

    func handleBecameActive() {
        note("App em primeiro plano.")
        configureIngest()
        if hardwareReady {
            pullWatchHistory(reason: "primeiro plano")
        }
        advanceMeasurementCycle()
        reconnectLastKnown(reason: "primeiro plano")
        flushQueueOnly()
    }

    private static var isInBackground: Bool {
        UIApplication.shared.applicationState == .background
    }

    /// Envia o que está na fila (com beginBackgroundTask) e passa a FC contínua para que
    /// as notificações do relógio continuem acordando o app com o iPhone bloqueado.
    func handleEnteredBackground() {
        note("App em segundo plano.")
        Task { await self.performFlush() }
        IngestBackground.schedule()
        guard hardwareReady else { return }
        backgroundStreaming = true
        if !heartRunning {
            stopOxygen()
            startHeart()
        }
    }

    func handleWillEnterForeground() {
        guard backgroundStreaming else { return }
        backgroundStreaming = false
        note("Primeiro plano: FC contínua encerrada, volta ao ciclo pontual.")
        if heartRunning {
            stopHeart()
            finishCycle(reason: "volta ao primeiro plano")
        }
    }

    /// Histórico gravado no relógio → fila (client_reading_id estável) → envio.
    private func pullWatchHistory(reason: String, force: Bool = false) {
        guard hardwareReady else { return }
        if !force, Date().timeIntervalSince(lastHistoryPullAt) < Self.historyPullMinInterval { return }
        lastHistoryPullAt = Date()
        let resumeHeart = heartRunning
        if resumeHeart { stopHeart() }
        stopOxygen()
        note("Lendo histórico do relógio (\(reason)).")
        manager.peripheralManage.veepooSdkStartReadDeviceAllData { [weak self] state, _, _, _ in
            Task { @MainActor in
                guard let self, state.rawValue == 2 else { return }
                if let current = self.connected { self.adoptSdkMac(from: current) }
                await self.enqueueWatchHistoryThenFlush()
                if self.backgroundStreaming || Self.isInBackground {
                    self.backgroundStreaming = true
                    if !self.heartRunning { self.startHeart() }
                } else {
                    self.advanceMeasurementCycle()
                }
            }
        }
    }

    func startScan(automatic: Bool = false) {
        if !automatic {
            userRequestedDisconnect = false
            backoffScanTask?.cancel()
            backoffScanTask = nil
        }
        devices = []
        autoConnectAttemptedThisScan = false
        isScanning = true
        sessionMessage = "Procurando o relógio…"
        note(automatic ? "Scan Veepoo automático (10 s)." : "Scan Veepoo iniciado.")
        manager.manufacturerIDFilter = false
        manager.rrisLimit = -127
        guard manager.centralManager.state == .poweredOn else {
            sessionMessage = "Ligue o Bluetooth do iPhone."
            note("Scan adiado: Bluetooth ainda não está pronto.")
            return
        }
        // Relógios já ligados ao iOS (H Band oficial, Ajustes, restore) não anunciam.
        seedKnownAndConnectedWatches()
        // Scan amplo, como no Android (`startScan(null)`): sem UUID de serviço.
        // O scan do SDK sozinho aplica rrisLimit -85 e ignora periféricos já ligados.
        // One scan only: a second `scanForPeripherals` from the SDK would replace this one.
        manager.centralManager.scanForPeripherals(
            withServices: nil,
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
    }

    func stopScan() {
        manager.veepooSDKStopScanDevice()
        manager.centralManager.stopScan()
        isScanning = false
        autoScanStopTask?.cancel()
        autoScanStopTask = nil
    }

    /// iOS does not advertise already-connected BLE devices. Pull them from CoreBluetooth.
    private func seedKnownAndConnectedWatches() {
        if let uuid = lastPeripheralUUID.flatMap(UUID.init(uuidString:)) {
            for peripheral in manager.centralManager.retrievePeripherals(withIdentifiers: [uuid]) {
                offerRetrieved(peripheral)
            }
        }
        for peripheral in manager.centralManager.retrieveConnectedPeripherals(withServices: Self.veepooServiceUUIDs) {
            offerRetrieved(peripheral)
        }
    }

    private func offerRetrieved(_ peripheral: CBPeripheral) {
        guard let model = VPPeripheralModel(peripher: peripheral) else { return }
        upsertScanned(
            model: model,
            fallbackName: peripheral.name ?? lastName,
            rssi: 0,
            uuid: peripheral.identifier.uuidString,
            isOsConnected: peripheral.state == .connected || peripheral.state == .connecting
        )
    }

    func onDidDiscover(
        peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi: NSNumber
    ) {
        guard isScanning else { return }
        let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let manufacturer = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
        let uuid = peripheral.identifier.uuidString
        let isKnown = uuid == lastPeripheralUUID
        let named = !((localName ?? peripheral.name ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        let osConnected = peripheral.state == .connected || peripheral.state == .connecting
        // Keep unnamed random beacons off the patient list; keep anything named, known, or connected.
        guard isKnown || named || manufacturer != nil || osConnected else { return }
        let advertised: VPPeripheralModel? = VPPeripheralModel(
            peripher: peripheral,
            advertisementData: advertisementData,
            rssi: rssi
        )
        guard let model = advertised ?? VPPeripheralModel(peripher: peripheral) else { return }
        let rssiValue = rssi.intValue == 127 ? 0 : rssi.intValue
        upsertScanned(
            model: model,
            fallbackName: localName ?? peripheral.name ?? lastName,
            rssi: rssiValue,
            uuid: uuid,
            isOsConnected: osConnected
        )
    }

    private func upsertScanned(
        model: VPPeripheralModel,
        fallbackName: String?,
        rssi: Int,
        uuid: String,
        isOsConnected: Bool
    ) {
        let mac = (model.deviceAddress ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let ident = uuid.isEmpty ? mac : uuid
        guard !ident.isEmpty else { return }
        let resolvedName = [model.deviceName, fallbackName, lastName]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty } ?? "Relógio"
        let watch = ScannedWatch(
            name: resolvedName,
            address: mac.isEmpty ? ident : mac,
            uuid: uuid.isEmpty ? ident : uuid,
            rssi: rssi,
            isOsConnected: isOsConnected,
            model: model
        )
        if let index = devices.firstIndex(where: {
            $0.uuid == watch.uuid || (!$0.address.isEmpty && $0.address == watch.address)
        }) {
            let previous = devices[index]
            devices[index] = ScannedWatch(
                name: watch.name,
                address: watch.address,
                uuid: watch.uuid,
                rssi: rssi != 0 ? rssi : previous.rssi,
                isOsConnected: watch.isOsConnected || previous.isOsConnected,
                model: watch.model
            )
        } else {
            devices.append(watch)
        }
        devices.sort {
            if $0.isOsConnected != $1.isOsConnected { return $0.isOsConnected && !$1.isOsConnected }
            return $0.rssi > $1.rssi
        }
        let matchesLast = (!watch.address.isEmpty && watch.address == lastAddress)
            || (!watch.uuid.isEmpty && watch.uuid == lastPeripheralUUID)
        if matchesLast, connected == nil, !userRequestedDisconnect, !autoConnectAttemptedThisScan {
            autoConnectAttemptedThisScan = true
            connect(watch)
        }
    }

    func connect(_ watch: ScannedWatch) {
        userRequestedDisconnect = false
        stopScan()
        sessionMessage = "Conectando a \(watch.name)…"
        connected = watch
        rememberLastAddress(watch.address)
        lastName = watch.name
        rememberPeripheral(watch.model.peripheral)
        keepPendingConnect(watch.model.peripheral)
        manager.deviceShowConfirm = true
        manager.automaticConnection = false
        note("Connect Veepoo \(watch.name)")
        manager.veepooSDKConnectDevice(watch.model, deviceConnect: { [weak self] state in
            Task { @MainActor in
                self?.handleConnect(state, watch: watch)
            }
        })
    }

    func notifyProfileChanged() {
        profileRevision += 1
        ensurePatientId()
        refreshDeviceLabel()
        refreshLocalStore()
    }

    /// Local alias plus Veepoo `veepooSDKSettingDeviceNameWithString:resultBlock:` (state 0 ok).
    func renameWatch(_ proposed: String) {
        let trimmed = proposed.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            renameStatus = "Digite um nome para o relógio."
            return
        }
        lastName = trimmed
        if var watch = connected {
            watch.name = trimmed
            connected = watch
        }
        guard hardwareReady else {
            renameStatus = "Nome atualizado neste iPhone. Conecte o relógio para gravar no aparelho."
            return
        }
        manager.peripheralManage.veepooSDKSettingDeviceName(with: trimmed) { [weak self] state in
            Task { @MainActor in
                switch state {
                case 0:
                    self?.renameStatus = "Nome do relógio atualizado."
                    self?.note("Nome do relógio: \(trimmed)")
                case 2:
                    self?.renameStatus = "O nome do relógio é longo demais."
                case 3:
                    self?.renameStatus = "O nome do relógio é curto demais."
                default:
                    self?.renameStatus = "Não foi possível gravar o nome no relógio. O apelido ficou neste iPhone."
                }
            }
        }
    }

    func setAutoReconnect(_ enabled: Bool) {
        manager.automaticConnection = false
        note("Reconectar automático: \(enabled ? "ligado" : "desligado")")
        if enabled { reconnectLastKnown(reason: "ajuste") }
    }

    func disconnect() {
        userRequestedDisconnect = true
        stopSensors()
        manager.automaticConnection = false
        manager.veepooSDKDisconnectDevice()
        hardwareReady = false
        connected = nil
        bleConnectionLabel = "Desligado pelo usuário"
        sessionMessage = "Desconexão solicitada."
        note("Desconexão pedida pelo usuário. Sem ligação pendente.")
    }

    func reconnectLastIfPossible() {
        reconnectLastKnown(reason: "pedido")
    }

    /// `DeviceConnectState` in VPPublicDefine.h (BlePoweredOff = 0 … BleConfirmTimeout = 7).
    private func handleConnect(_ state: DeviceConnectState, watch: ScannedWatch) {
        switch state.rawValue {
        case 0:
            bleConnectionLabel = "Bluetooth desligado"
            sessionMessage = "Ligue o Bluetooth do iPhone."
            note("Bluetooth desligado.")
        case 1:
            bleConnectionLabel = "Ligando…"
            sessionMessage = "Conectando…"
            note("Ligando ao relógio.")
        case 2:
            bleConnectionLabel = "Ligado, senha"
            sessionMessage = "Ligação feita. Confirmando a senha 0000…"
            note("GATT ligado. Senha 0000.")
            // Vários callbacks de "GATT ligado" chegam juntos; uma senha por vez basta.
            if let at = passwordSentAt, Date().timeIntervalSince(at) < 5 { return }
            passwordSentAt = Date()
            verifyPassword(watch)
        case 3, 6, 7:
            hardwareReady = false
            bleConnectionLabel = "Falha ou tempo esgotado"
            sessionMessage = "Falha ou tempo esgotado na ligação. Mantendo ligação pendente."
            note("Falha/timeout Veepoo (\(state.rawValue)). Pending connect iOS.")
            sessionAttemptAt = nil
            keepPendingConnect(watch.model.peripheral)
            reconnectLastKnown(reason: "falha \(state.rawValue)")
        case 4:
            note("Senha ok (callback de ligação).")
            onHandshake(watch)
        case 5:
            hardwareReady = false
            bleConnectionLabel = "Senha recusada"
            sessionMessage = "A senha padrão 0000 foi recusada. Confira o relógio."
            note("Senha 0000 recusada.")
        default:
            sessionMessage = "Estado de ligação desconhecido (\(state.rawValue))."
            note("Estado de ligação \(state.rawValue).")
        }
    }

    private func verifyPassword(_ watch: ScannedWatch) {
        manager.veepooSDKSynchronousPassword(
            with: SynchronousPasswordType(rawValue: 0)!,
            password: Self.defaultPassword,
            synchronizationResult: { [weak self] result in
                Task { @MainActor in
                    guard let self else { return }
                    switch result.rawValue {
                    case 1, 5, 6:
                        self.note("Senha confirmada (\(result.rawValue)).")
                        self.onHandshake(watch)
                    case 0, 4:
                        self.sessionMessage = "A senha padrão 0000 foi recusada."
                        self.hardwareReady = false
                        self.note("Senha recusada (\(result.rawValue)).")
                    default:
                        self.sessionMessage = "Confirmação de senha: código \(result.rawValue)."
                        self.note("Senha código \(result.rawValue).")
                    }
                }
            }
        )
    }

    private func onHandshake(_ watch: ScannedWatch) {
        adoptSdkMac(from: watch)
        if hardwareReady {
            advanceMeasurementCycle()
            return
        }
        hardwareReady = true
        sessionAttemptAt = nil
        handshakeRetries = 0
        if let since = gattUpSince {
            NSLog("[Next2U] Relógio pronto %.1f s após o GATT.", Date().timeIntervalSince(since))
        }
        gattUpSince = nil
        lastSampleAt = Date()
        bleConnectionLabel = "Relógio pronto"
        sessionMessage = "Relógio pronto. Lendo dados do dia e depois a frequência cardíaca."
        note("Handshake. Reinscreve serviços e inicia FC.")
        // Link (re)established, e.g. after state restoration: push what queued meanwhile.
        flushQueueOnly()
        recordDialCapabilities()
        readBattery()
        manager.peripheralManage.veepooSdkStartReadDeviceAllData { [weak self] state, _, _, _ in
            Task { @MainActor in
                guard let self else { return }
                if state.rawValue == 2 {
                    self.sessionMessage = "Histórico do relógio lido. Medindo batimentos…"
                    self.note("Histórico do band lido. FC.")
                    // Acerta a hora depois do histórico (como no Android): no handshake o VE30 ignora o comando.
                    self.syncWatchTime(manual: false)
                    if let current = self.connected {
                        self.adoptSdkMac(from: current)
                    }
                    self.lastHistoryPullAt = Date()
                    await self.enqueueWatchHistoryThenFlush()
                    self.advanceMeasurementCycle()
                }
            }
        }
        if Self.isInBackground { backgroundStreaming = true }
        startCycle(reason: "relógio conectado", force: true)
    }

    /// After password verification the SDK fills `deviceAddress` with the radio MAC
    /// (`VPPeripheralModel.h`: "password verification may change after successful verification").
    private func adoptSdkMac(from watch: ScannedWatch) {
        let uuid = watch.model.peripheral?.identifier.uuidString ?? watch.uuid
        let sources: [(String, String?)] = [
            ("peripheralManage.peripheralModel.deviceAddress", manager.peripheralManage?.peripheralModel?.deviceAddress),
            ("peripheralModel.deviceAddress", manager.peripheralModel?.deviceAddress),
            ("connected VPPeripheralModel.deviceAddress", watch.model.deviceAddress),
        ]
        var mac: String?
        var source = ""
        for (name, raw) in sources {
            if let normalized = DeviceIdentity.shared.normalizeMac(raw: raw) {
                mac = normalized
                source = name
                break
            }
        }
        let deviceId = DeviceIdentity.shared.ingestDeviceId(sdkAddress: mac, coreBluetoothUuid: uuid)
        guard !deviceId.isEmpty else { return }
        applyIngestDeviceId(deviceId, watch: watch)
        if mac != nil {
            NSLog("[Next2U] device_id MAC via %@ = %@", source, deviceId)
            note("device_id: MAC do SDK (\(source)).")
        } else {
            NSLog("[Next2U] device_id fallback UUID CoreBluetooth (SDK sem MAC)")
            note("device_id: UUID (SDK não informou MAC).")
        }
    }

    /// Keep a stored MAC; do not replace it with a CoreBluetooth UUID before handshake.
    private func rememberLastAddress(_ candidate: String) {
        if DeviceIdentity.shared.normalizeMac(raw: candidate) != nil {
            lastAddress = candidate
            return
        }
        if DeviceIdentity.shared.normalizeMac(raw: lastAddress) != nil { return }
        lastAddress = candidate
    }

    private func applyIngestDeviceId(_ deviceId: String, watch: ScannedWatch) {
        lastAddress = deviceId
        var updated = watch
        updated.address = deviceId
        connected = updated
        if let index = devices.firstIndex(where: { $0.uuid == watch.uuid && !watch.uuid.isEmpty }) {
            devices[index].address = deviceId
        }
    }

    /// MAC after handshake; CoreBluetooth UUID only if the SDK still has no address.
    private func ingestDeviceId() -> String {
        DeviceIdentity.shared.ingestDeviceId(
            sdkAddress: manager.peripheralManage?.peripheralModel?.deviceAddress
                ?? manager.peripheralModel?.deviceAddress
                ?? connected?.model.deviceAddress
                ?? lastAddress,
            coreBluetoothUuid: lastPeripheralUUID ?? connected?.uuid
        )
    }

    /// Envia a hora do iPhone ao relógio (automático a cada handshake e pelo botão em Ajustes).
    /// O SDK enfileira a escrita com os demais comandos BLE; não interrompe a medição em curso.
    func syncWatchTime(manual: Bool = true) {
        guard hardwareReady else {
            watchTimeStatus = "Conecte o relógio para sincronizar a hora."
            return
        }
        guard !watchTimeSyncing else { return }
        watchTimeSyncing = true
        note("Hora: enviando hora do iPhone ao relógio (manual=\(manual)).")
        let attempt = UUID()
        watchTimeAttempt = attempt
        // Alguns firmwares não respondem: libera o botão e registra falha após 10 s.
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            guard let self, self.watchTimeAttempt == attempt, self.watchTimeSyncing else { return }
            self.watchTimeSyncing = false
            self.watchTimeAttempt = nil
            self.watchTimeStatus = "O relógio não confirmou o acerto da hora."
            NSLog("[Next2U] Hora: sem resposta do relógio em 10 s (manual=%@)", manual ? "sim" : "não")
        }
        let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: Date())
        manager.peripheralManage.veepooSDKSettingTime(
            withYear: Int32(c.year ?? 2026), month: Int32(c.month ?? 1), day: Int32(c.day ?? 1),
            hour: Int32(c.hour ?? 0), minute: Int32(c.minute ?? 0), second: Int32(c.second ?? 0),
            timeSystem: Self.is24HourClock ? 2 : 1
        ) { [weak self] success in
            Task { @MainActor in
                guard let self, self.watchTimeAttempt == attempt else { return }
                self.watchTimeAttempt = nil
                self.watchTimeSyncing = false
                if success {
                    let now = Date()
                    self.watchTimeLastSync = now
                    UserDefaults.standard.set(now, forKey: "watch_time_sync_at")
                    self.watchTimeStatus = WatchClockText.success(now)
                    self.note("Hora do relógio sincronizada.")
                    NSLog("[Next2U] Hora do relógio sincronizada (manual=%@)", manual ? "sim" : "não")
                } else {
                    self.watchTimeStatus = "Não foi possível acertar a hora do relógio."
                    self.note("Hora: falha ao sincronizar.")
                    NSLog("[Next2U] Hora: falha ao sincronizar (manual=%@)", manual ? "sim" : "não")
                }
            }
        }
    }

    private func recordDialCapabilities() {
        guard let model = manager.peripheralModel else { return }
        let photo = Int(model.photoDialCount)
        let screens = Int(model.screenTypes)
        watchPhotoDialCount = photo
        watchScreenTypes = screens
        UserDefaults.standard.set(photo, forKey: "watch_photo_dial_count")
        UserDefaults.standard.set(screens, forKey: "watch_screen_types")
        let line = "Mostrador VE30: photoDialCount=\(photo) screenTypes=\(screens) aiDialType=\(model.aiDialType)"
        NSLog("[Next2U] %@", line)
        note(line)
    }

    private func readBattery() {
        manager.peripheralManage.veepooSDKReadDeviceBatteryInfo { [weak self] isPercent, _, battery in
            Task { @MainActor in
                if isPercent { self?.batteryPercent = Int(battery) }
            }
        }
    }

    /// Rotate FC / SpO₂ from BLE callbacks only — `asyncAfter` is frozen in background.
    private func startHeart() {
        guard hardwareReady, !heartRunning else { return }
        stopOxygen()
        heartRunning = true
        heartSamplesThisBurst = 0
        measureStartedAt = Date()
        manager.peripheralManage.veepooSDKTestHeartStart(true) { [weak self] state, value in
            Task { @MainActor in
                guard let self else { return }
                if state.rawValue == 1, value >= 30, value <= 240 {
                    self.heartRate = Int(value)
                    self.lastSampleAt = Date()
                    self.sessionMessage = "Batimentos: \(value) bpm (leitura do relógio)."
                    HeartAlertDelivery.evaluate(heartRate: Int(value))
                    guard self.heartRunning else { return }
                    self.heartSamplesThisBurst += 1
                    self.note("FC: amostra \(self.heartSamplesThisBurst) recebida.")
                    self.cycleGotReading = true
                    self.recordAndFlush()
                    if !self.backgroundStreaming, self.heartSamplesThisBurst >= Self.heartSamplesPerCycle {
                        self.stopHeart()
                        self.startOxygen()
                    }
                }
                // 2 = não está no pulso, 3 = relógio ocupado, 4 = encerrado: o teste já acabou.
                if state.rawValue == 2 {
                    self.sessionMessage = "O relógio não está no pulso."
                    self.note("Relógio fora do pulso.")
                }
                if state.rawValue >= 2, self.heartRunning {
                    self.heartRunning = false // o relógio já encerrou este teste
                    let reason = state.rawValue == 2 ? "fora do pulso" : state.rawValue == 3 ? "relógio ocupado" : "FC encerrada pelo relógio"
                    if self.cycleGotReading { self.startOxygen() } else { self.finishCycle(reason: reason) }
                }
            }
        }
    }

    private func startOxygen() {
        guard hardwareReady else { return }
        stopHeart()
        spo2Running = true
        measureStartedAt = Date()
        manager.peripheralManage.veepooSDKTestOxygenStart(true) { [weak self] state, value in
            Task { @MainActor in
                guard let self else { return }
                guard self.spo2Running else { return }
                // 2 fora do pulso, 3 ocupado, 4 encerrado, 5 sem SpO₂: o teste já acabou.
                if state.rawValue >= 2 {
                    self.spo2Running = false
                    self.finishCycle(reason: "SpO₂ encerrado pelo relógio (\(state.rawValue))")
                    return
                }
                if value >= 50, value <= 100 {
                    self.spo2 = Int(value)
                    self.lastSampleAt = Date()
                    self.cycleGotReading = true
                    self.recordAndFlush()
                    self.finishCycle(reason: "SpO₂ lido")
                }
            }
        }
    }

    private func stopHeart() {
        if heartRunning {
            manager.peripheralManage.veepooSDKTestHeartStart(false, testResult: nil)
        }
        heartRunning = false
    }

    private func stopOxygen() {
        if spo2Running {
            manager.peripheralManage.veepooSDKTestOxygenStart(false, testResult: nil)
        }
        spo2Running = false
    }

    private func stopSensors() {
        stopHeart()
        stopOxygen()
    }

    func flushNow() {
        if heartRate > 0 || spo2 > 0 {
            recordAndFlush()
            return
        }
        configureIngest()
        isFlushing = true
        let task = beginIngestTask()
        Task {
            defer {
                self.isFlushing = false
                self.endIngestTask(task)
            }
            guard let flush = try? await IngestRuntime.shared.flush() else { return }
            applyFlush(flush)
        }
    }

    /// Flush the durable queue without recording a new reading.
    func flushQueueOnly() {
        Task { await self.performFlush() }
    }

    /// Used by BGTask after `configureIngest()` / store open.
    func noteBackgroundFlush(_ flush: FlushOutcome) {
        applyFlush(flush)
    }

    /// History from the Veepoo SDK database, then one flush. Same IDs on reconnect.
    private func enqueueWatchHistoryThenFlush() async {
        guard PatientStore.shared.status == .ready else {
            note("Histórico lido; store ainda não abriu.")
            return
        }
        configureIngest()
        let watch = connected
        let deviceId = ingestDeviceId()
        let deviceModel = watch?.name ?? lastName ?? "VE30"
        let days = Int(manager.peripheralModel?.saveDays ?? 3)
        var samples = VeepooHistoryIngest.samples(deviceId: deviceId, days: days)
        if samples.isEmpty, let uuid = lastPeripheralUUID, uuid != deviceId {
            samples = VeepooHistoryIngest.samples(deviceId: uuid, days: days)
        }
        let queued = await VeepooHistoryIngest.enqueue(
            samples: samples,
            deviceId: deviceId,
            deviceModel: deviceModel,
            patientId: PatientStore.shared.profile().patientId
        )
        NSLog("[Next2U] Histórico do relógio: amostras=%d novas na fila=%d", samples.count, queued)
        note("Histórico do relógio enfileirado.")
        await performFlush()
    }

    @discardableResult
    func performFlush() async -> Bool {
        guard PatientStore.shared.status == .ready else { return false }
        configureIngest()
        let task = beginIngestTask()
        defer { endIngestTask(task) }
        do {
            let flush = try await IngestRuntime.shared.flush()
            applyFlush(flush)
            return flush.failedCount == 0
        } catch {
            return false
        }
    }

    /// Honors the shared backoff (exponential + jitter, Retry-After floor).
    private func scheduleBackoffRetry(after ms: Int64) {
        scheduledRetry?.cancel()
        scheduledRetry = nil
        guard ms > 0 else { return }
        // If iOS suspends us first, the next BLE wake-up or BGTask flushes instead.
        scheduledRetry = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000)
            guard !Task.isCancelled, let self else { return }
            self.scheduledRetry = nil
            self.flushQueueOnly()
        }
    }

    func retryFailed(_ clientReadingId: String) {
        IngestRuntime.shared.retryFailed(clientReadingId: clientReadingId)
        configureIngest()
        flushNow()
    }

    func retryAllFailed() {
        IngestRuntime.shared.retryAllFailed()
        configureIngest()
        flushNow()
    }

    func deleteQueueItem(_ clientReadingId: String) {
        IngestRuntime.shared.deleteItem(clientReadingId: clientReadingId)
        configureIngest()
    }

    func clearQueue() {
        IngestRuntime.shared.clearAll()
        configureIngest()
    }

    private func applyFlush(_ flush: FlushOutcome) {
        lastFlushMessage = flush.message
        lastHttpCode = Int(flush.lastHttpCode)
        pendingCount = Int(flush.pendingCount)
        failedCount = Int(IngestRuntime.shared.failedCount())
        lastFlushSyncedCount = Int(flush.syncedCount)
        lastUploadLabel = "HTTP \(flush.lastHttpCode) · \(flush.message)"
        refreshLocalStore()
        IngestBackground.schedule()
        scheduleBackoffRetry(after: flush.pendingCount > 0 ? flush.retryInMs : 0)
        bleLog.notice("upload http=\(flush.lastHttpCode, privacy: .public) pending=\(flush.pendingCount, privacy: .public)")
        NSLog("[Next2U] Envio ao Core: HTTP %d concluídos=%d fila=%d falhas=%d", Int(flush.lastHttpCode),
              Int(flush.syncedCount), pendingCount, failedCount)
    }

    private func recordAndFlush() {
        // FC contínua em segundo plano: no máximo uma leitura por minuto na fila.
        if backgroundStreaming, Date().timeIntervalSince(lastRecordAt) < Self.minUploadInterval { return }
        lastRecordAt = Date()
        guard PatientStore.shared.status == .ready else {
            note("Leitura recebida; store ainda não abriu.")
            NSLog("[Next2U] Leitura NÃO gravada: store %@", "\(PatientStore.shared.status)")
            return
        }
        configureIngest()
        let watch = connected
        let task = beginIngestTask()
        Task {
            defer { self.endIngestTask(task) }
            guard let recorded = try? await IngestRuntime.shared.recordLive(
                deviceId: ingestDeviceId(),
                deviceModel: watch?.name ?? "VE30",
                heartRate: Int32(heartRate),
                spo2: Int32(spo2),
                systolic: 0,
                diastolic: 0,
                temperatureCelsius: 0,
                steps: 0,
                calories: 0,
                distanceMeters: 0,
                hrvScore: 0,
                patientId: PatientStore.shared.profile().patientId
            ) else {
                NSLog("[Next2U] Leitura: recordLive lançou erro")
                return
            }
            NSLog("[Next2U] Leitura gravada: fc=%d spo2=%d enfileirada=%@ medições=%d fila=%d falhas=%d",
                  self.heartRate, self.spo2, recorded.queued ? "sim" : "não",
                  Int(PatientStore.shared.metricCount()), Int(IngestRuntime.shared.pendingCount()),
                  Int(IngestRuntime.shared.failedCount()))
            if !recorded.queued { note("Leitura não enfileirada: \(recorded.message)") }
            lastClientReadingId = recorded.clientReadingId
            pendingCount = Int(IngestRuntime.shared.pendingCount())
            failedCount = Int(IngestRuntime.shared.failedCount())
            refreshLocalStore()
            if recorded.queued { await self.throttledFlush() }
        }
    }

    /// Envia no máximo 1x/min; leituras intermediárias ficam na fila durável e saem no
    /// próximo envio (adiado ou acordado pela próxima notificação do relógio).
    private func throttledFlush() async {
        let wait = Self.minUploadInterval - Date().timeIntervalSince(lastFlushAt)
        if wait <= 0 {
            lastFlushAt = Date()
            deferredFlush?.cancel()
            deferredFlush = nil
            await performFlush()
            return
        }
        guard deferredFlush == nil else { return }
        deferredFlush = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard let self, !Task.isCancelled else { return }
            self.deferredFlush = nil
            self.lastFlushAt = Date()
            await self.performFlush()
        }
    }

    private func beginIngestTask() -> UIBackgroundTaskIdentifier {
        var id = UIBackgroundTaskIdentifier.invalid
        id = UIApplication.shared.beginBackgroundTask(withName: "next2u.ingest") { [weak self] in
            self?.endIngestTask(id)
        }
        return id
    }

    private func endIngestTask(_ id: UIBackgroundTaskIdentifier) {
        if id != .invalid {
            UIApplication.shared.endBackgroundTask(id)
        }
    }

    /// N2UBleRestore.m injeta o restore id no CBCentralManager criado pelo próprio SDK
    /// (evita o "API MISUSE: has no restore identifier" e um segundo gerenciador).
    private static func sdkCentralHasRestoreId() -> Bool {
        guard let cls = NSClassFromString("N2UBleRestore") as? NSObject.Type else { return false }
        let sel = NSSelectorFromString("injectedFlag")
        guard cls.responds(to: sel),
              let flag = cls.perform(sel)?.takeUnretainedValue() as? NSNumber else { return false }
        return flag.boolValue
    }

    private func attachRestorableCentral() {
        forwarder.sdk = manager
        forwarder.session = self
        if Self.sdkCentralHasRestoreId() {
            // Mesmo gerenciador, mesma fila principal: willRestoreState chega depois deste init.
            manager.centralManager.delegate = forwarder
            note("CBCentralManager do Veepoo já criado com restore id.")
            return
        }
        // Fallback: substitui o gerenciador do SDK e impede injeção posterior (restore id único).
        if let cls = NSClassFromString("N2UBleRestore") as? NSObject.Type,
           cls.responds(to: NSSelectorFromString("disableInjection")) {
            _ = cls.perform(NSSelectorFromString("disableInjection"))
        }
        manager.centralManager.delegate = nil
        let restored = CBCentralManager(
            delegate: forwarder,
            queue: .main,
            options: [
                CBCentralManagerOptionRestoreIdentifierKey: Self.restoreId,
                CBCentralManagerOptionShowPowerAlertKey: false,
            ]
        )
        manager.centralManager = restored
        note("CBCentralManager do Veepoo com restore id.")
    }

    func onCentralState(_ state: CBManagerState) {
        switch state {
        case .poweredOn:
            bleConnectionLabel = hardwareReady ? "Relógio pronto" : "Bluetooth ligado"
            note("Bluetooth ligado.")
            if isScanning {
                seedKnownAndConnectedWatches()
                manager.manufacturerIDFilter = false
                manager.rrisLimit = -127
                manager.centralManager.scanForPeripherals(
                    withServices: nil,
                    options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
                )
            }
            reconnectLastKnown(reason: "poweredOn")
        case .poweredOff:
            hardwareReady = false
            bleConnectionLabel = "Bluetooth desligado"
            sessionMessage = "Ligue o Bluetooth do iPhone."
            note("Bluetooth desligado. Sem pending connect.")
        case .unauthorized:
            bleConnectionLabel = "Bluetooth sem permissão"
            note("Bluetooth sem permissão.")
        case .resetting:
            note("Bluetooth a reiniciar.")
        default:
            note("Estado do Bluetooth \(state.rawValue).")
        }
    }

    func onWillRestoreState(_ dict: [String: Any]) {
        let peripherals = (dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral]) ?? []
        note("willRestoreState: \(peripherals.count) periférico(s).")
        sessionMessage = "O iOS restaurou o Bluetooth. Repondo a sessão do relógio."
        for peripheral in peripherals {
            rememberPeripheral(peripheral)
            if peripheral.state == .connected, gattUpSince == nil { gattUpSince = Date() }
        }
        // willRestoreState chega antes de poweredOn; comandos agora seriam descartados
        // ("can only accept this command while in the powered on state"). poweredOn repõe.
        guard manager.centralManager.state == .poweredOn else {
            note("willRestoreState: aguardando Bluetooth ligado para repor a sessão.")
            return
        }
        if peripherals.isEmpty {
            reconnectLastKnown(reason: "restore vazio")
            return
        }
        for peripheral in peripherals {
            lastPeripheralUUID = peripheral.identifier.uuidString
            rememberPeripheral(peripheral)
            if peripheral.state == .connected {
                resumeSession(peripheral)
            } else {
                keepPendingConnect(peripheral)
                connectRetrieved(peripheral)
            }
        }
    }

    func onDidConnect(_ peripheral: CBPeripheral) {
        lastPeripheralUUID = peripheral.identifier.uuidString
        rememberPeripheral(peripheral)
        bleConnectionLabel = "GATT ligado"
        resetBackoffScan()
        if gattUpSince == nil { gattUpSince = Date() }
        note("didConnect \(peripheral.identifier.uuidString)")
        if !hardwareReady {
            resumeSession(peripheral)
        }
    }

    func onDidDisconnect(_ peripheral: CBPeripheral, reason: String) {
        hardwareReady = false
        heartRunning = false
        spo2Running = false
        bleConnectionLabel = "Desligado"
        gattUpSince = nil
        sessionAttemptAt = nil
        handshakeRetries = 0
        passwordSentAt = nil
        note("didDisconnect \(reason)")
        stopSdkScanIfIdle()
        keepPendingConnect(peripheral)
    }

    private func resumeSession(_ peripheral: CBPeripheral) {
        guard let model = VPPeripheralModel(peripher: peripheral) else { return }
        let watch = ScannedWatch(
            name: lastName ?? peripheral.name ?? "VE30",
            address: lastAddress ?? peripheral.identifier.uuidString,
            uuid: peripheral.identifier.uuidString,
            rssi: 0,
            isOsConnected: true,
            model: model
        )
        connected = watch
        rememberLastAddress(watch.address)
        lastName = watch.name
        lastPeripheralUUID = peripheral.identifier.uuidString
        if gattUpSince == nil { gattUpSince = Date() }
        guard beginSessionAttempt("resume") else { return }
        note("Reposiciona sessão Veepoo (senha + FC).")
        manager.deviceShowConfirm = false
        manager.veepooSDKConnectDevice(model, deviceConnect: { [weak self] state in
            Task { @MainActor in
                self?.handleConnect(state, watch: watch)
            }
        })
    }

    private func connectRetrieved(_ peripheral: CBPeripheral) {
        guard peripheral.state == .connected else {
            // veepooSDKConnectDevice num relógio desligado/fora de alcance faz o SDK escanear
            // 1x/s indefinidamente (connectionTimeout = .max). Basta o connect pendente do iOS;
            // quando o relógio voltar, didConnect → resumeSession abre a sessão Veepoo.
            stopSdkScanIfIdle()
            keepPendingConnect(peripheral)
            return
        }
        guard let model = VPPeripheralModel(peripher: peripheral) else { return }
        guard beginSessionAttempt("retrieve") else { return }
        let watch = ScannedWatch(
            name: lastName ?? peripheral.name ?? "VE30",
            address: lastAddress ?? peripheral.identifier.uuidString,
            uuid: peripheral.identifier.uuidString,
            rssi: 0,
            isOsConnected: true,
            model: model
        )
        connected = watch
        rememberLastAddress(watch.address)
        lastName = watch.name
        lastPeripheralUUID = peripheral.identifier.uuidString
        manager.deviceShowConfirm = false
        manager.automaticConnection = false
        note("Reconnect UUID \(peripheral.identifier.uuidString)")
        manager.veepooSDKConnectDevice(model, deviceConnect: { [weak self] state in
            Task { @MainActor in
                self?.handleConnect(state, watch: watch)
            }
        })
    }

    private func reconnectLastKnown(reason: String) {
        guard autoReconnectEnabled, !userRequestedDisconnect else { return }
        if hardwareReady, manager.isConnected { return }
        guard manager.centralManager.state == .poweredOn else {
            // Um connect antes de poweredOn era descartado e a sessão só voltava pelo watchdog.
            note("Reconnect (\(reason)) adiado: Bluetooth ainda não está pronto.")
            return
        }
        note("Reconnect (\(reason)).")
        if let uuid = lastPeripheralUUID.flatMap(UUID.init(uuidString:)) {
            let found = manager.centralManager.retrievePeripherals(withIdentifiers: [uuid])
            if let peripheral = found.first {
                if peripheral.state == .connected {
                    // Já ligado: um novo connect fazia o SDK redescobrir serviços e repetir a
                    // senha várias vezes no meio do handshake/medição. Só repõe a sessão
                    // (beginSessionAttempt ignora se já há uma tentativa em curso).
                    if !hardwareReady { resumeSession(peripheral) }
                    return
                }
                keepPendingConnect(peripheral)
                connectRetrieved(peripheral)
                return
            }
        }
        if lastAddress != nil {
            scheduleBackoffScan(reason: reason)
        }
    }

    // MARK: - Scan ocasional (só sem periférico conhecido, só em primeiro plano)

    /// Espera antes de cada scan automático: 10 s, 30 s, 60 s, 2 min, depois 5 min.
    static let backoffScanDelays: [TimeInterval] = [10, 30, 60, 120, 300]
    static let backoffScanWindow: TimeInterval = 10
    private var backoffScanStep = 0
    private var backoffScanTask: Task<Void, Never>?
    private var autoScanStopTask: Task<Void, Never>?

    private func scheduleBackoffScan(reason: String) {
        guard backoffScanTask == nil, !isScanning else { return }
        let delay = Self.backoffScanDelays[min(backoffScanStep, Self.backoffScanDelays.count - 1)]
        backoffScanStep += 1
        note("Scan automático em \(Int(delay)) s (\(reason)).")
        backoffScanTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard let self, !Task.isCancelled else { return }
            self.backoffScanTask = nil
            guard self.autoReconnectEnabled, !self.userRequestedDisconnect,
                  self.connected == nil || !self.hardwareReady,
                  self.manager.centralManager.state == .poweredOn,
                  UIApplication.shared.applicationState == .active else { return }
            // Periférico apareceu entretanto (restore/retrieve): basta o connect pendente.
            if let uuid = self.lastPeripheralUUID.flatMap(UUID.init(uuidString:)),
               let p = self.manager.centralManager.retrievePeripherals(withIdentifiers: [uuid]).first {
                self.keepPendingConnect(p)
                return
            }
            self.startScan(automatic: true)
            self.autoScanStopTask = Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(Self.backoffScanWindow * 1_000_000_000))
                guard let self, !Task.isCancelled, self.isScanning else { return }
                self.stopScan()
                self.note("Scan automático encerrado sem achar o relógio.")
                self.scheduleBackoffScan(reason: "backoff")
            }
        }
    }

    /// Encerra qualquer scan interno do SDK quando nem o usuário nem o backoff pediram scan.
    private func stopSdkScanIfIdle() {
        guard !isScanning else { return }
        manager.veepooSDKStopScanDevice()
    }

    private func resetBackoffScan() {
        backoffScanTask?.cancel()
        backoffScanTask = nil
        autoScanStopTask?.cancel()
        autoScanStopTask = nil
        backoffScanStep = 0
        if isScanning { stopScan() }
    }

    private func rememberPeripheral(_ peripheral: CBPeripheral?) {
        guard let peripheral else { return }
        lastPeripheralUUID = peripheral.identifier.uuidString
    }

    /// iOS keeps this connect pending with no app-side timeout until the VE30
    /// advertises again. Veepoo's 30s timer is disabled (`connectionTimeout = .max`).
    private func keepPendingConnect(_ peripheral: CBPeripheral?) {
        guard let peripheral else { return }
        guard autoReconnectEnabled, !userRequestedDisconnect else { return }
        lastPeripheralUUID = peripheral.identifier.uuidString
        guard manager.centralManager.state == .poweredOn else { return }
        // connect num periférico já ligado/ligando gera didConnect extra → SDK reinicia o handshake.
        guard peripheral.state != .connected, peripheral.state != .connecting else { return }
        manager.centralManager.connect(
            peripheral,
            options: [
                CBConnectPeripheralOptionNotifyOnConnectionKey: true,
                CBConnectPeripheralOptionNotifyOnDisconnectionKey: true,
                CBConnectPeripheralOptionNotifyOnNotificationKey: true,
            ]
        )
        note("Pending connect iOS \(peripheral.identifier.uuidString)")
    }

    func note(_ message: String) {
        lastBleEvent = message
        lastBleEventAt = Date()
        bleLog.notice("\(message, privacy: .public)")
    }

    var diagnosticsLines: [String] {
        let when = lastBleEventAt.map(Self.stamp) ?? "—"
        return [
            "Último evento BLE: \(lastBleEvent) (\(when))",
            "Ligação: \(bleConnectionLabel)",
            "Último envio: \(lastUploadLabel)",
            "device_id: \(ingestDeviceId())",
        ]
    }

    private static func stamp(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt-BR")
        formatter.dateFormat = "dd/MM HH:mm:ss"
        return formatter.string(from: date)
    }
}

/// Forwards Core Bluetooth to Veepoo and intercepts restore / disconnect.
final class VeepooCentralForwarder: NSObject, CBCentralManagerDelegate {
    weak var sdk: VPBleCentralManage?
    nonisolated(unsafe) weak var session: VeepooSession?

    private var sdkDelegate: CBCentralManagerDelegate? { sdk }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        sdkDelegate?.centralManagerDidUpdateState(central)
        Task { @MainActor in
            self.session?.onCentralState(central.state)
        }
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        sdkDelegate?.centralManager?(central, willRestoreState: dict)
        Task { @MainActor in
            self.session?.onWillRestoreState(dict)
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        sdkDelegate?.centralManager?(
            central,
            didDiscover: peripheral,
            advertisementData: advertisementData,
            rssi: RSSI
        )
        Task { @MainActor in
            self.session?.onDidDiscover(
                peripheral: peripheral,
                advertisementData: advertisementData,
                rssi: RSSI
            )
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        sdkDelegate?.centralManager?(central, didConnect: peripheral)
        Task { @MainActor in
            self.session?.onDidConnect(peripheral)
        }
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        sdkDelegate?.centralManager?(central, didFailToConnect: peripheral, error: error)
        Task { @MainActor in
            self.session?.onDidDisconnect(peripheral, reason: error?.localizedDescription ?? "falha ao ligar")
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        sdkDelegate?.centralManager?(central, didDisconnectPeripheral: peripheral, error: error)
        Task { @MainActor in
            self.session?.onDidDisconnect(peripheral, reason: error?.localizedDescription ?? "desconectado")
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        connectionEventDidOccur event: CBConnectionEvent,
        for peripheral: CBPeripheral
    ) {
        sdkDelegate?.centralManager?(central, connectionEventDidOccur: event, for: peripheral)
        Task { @MainActor in
            if event == .peerConnected {
                self.session?.onDidConnect(peripheral)
            } else {
                self.session?.onDidDisconnect(peripheral, reason: "connectionEvent")
            }
        }
    }
}

#if DEBUG
struct BleDiagnosticsCard: View {
    @Environment(\.patientPalette) private var palette
    @ObservedObject var session: VeepooSession

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Diagnóstico BLE")
                .font(.system(size: 17, weight: .semibold))
            ForEach(session.diagnosticsLines, id: \.self) { line in
                Text(line).font(.system(size: 15))
            }
            Text("Force-quit e Bluetooth desligado param o envio. Isto não confirma o Core.")
                .font(.system(size: 15))
                .foregroundStyle(palette.onSurfaceVariant)
        }
        .foregroundStyle(palette.onSurface)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(palette.outlineVariant, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityIdentifier("ble_diagnostics_card")
    }
}
#endif
