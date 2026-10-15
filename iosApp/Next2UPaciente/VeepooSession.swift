import CoreBluetooth
import Foundation
import VeepooBleSDK
import shared

/// P0 Veepoo session: scan, connect, senha 0000, FC ao vivo, SpO₂ e bateria.
/// Comandos do SDK não são concorrentes — FC e SpO₂ revezam, como no Android.
///
/// Enum cases use `rawValue` from `VPPublicDefine.h`. Swift strips ObjC prefixes
/// (`BleConnectSuccess` → `.connectSuccess`, `VPTestHeartStateTesting` → `.testing`),
/// so matching on the header names does not compile.
@MainActor
final class VeepooSession: NSObject, ObservableObject {
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
    @Published var localMetricCount: Int = 0
    @Published var localSummary = ""
    @Published var ingestUrl = VeepooSession.bundledString("Next2UCoreBaseUrl")
        ?? "https://healthtech-secure-api-5794833455.us-central1.run.app/"
    @Published var ingestKey = ""

    private let manager = VPBleCentralManage.sharedBleManager()!
    private var lastAddress: String? {
        get { UserDefaults.standard.string(forKey: "last_veepoo_address") }
        set { UserDefaults.standard.set(newValue, forKey: "last_veepoo_address") }
    }
    private var lastName: String? {
        get { UserDefaults.standard.string(forKey: "last_veepoo_name") }
        set { UserDefaults.standard.set(newValue, forKey: "last_veepoo_name") }
    }
    private var heartRunning = false
    private var spo2Running = false
    private var restoreCentral: CBCentralManager?

    struct ScannedWatch: Identifiable, Equatable {
        var id: String { address }
        let name: String
        let address: String
        let rssi: Int
        let model: VPPeripheralModel

        static func == (lhs: ScannedWatch, rhs: ScannedWatch) -> Bool {
            lhs.address == rhs.address
        }
    }

    /// Build-time value from Secrets.local.xcconfig via Info.plist; nil if unset.
    static func bundledString(_ key: String) -> String? {
        guard let v = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let t = v.trimmingCharacters(in: .whitespacesAndNewlines)
        return (t.isEmpty || t.hasPrefix("$(")) ? nil : t
    }

    override init() {
        super.init()
        manager.isLogEnable = true
        manager.is24HourFormat = true
        manager.automaticConnection = true
        manager.connectionTimeout = 30
        manager.deviceConfirmTimeout = 30
        startRestorationSidecar()
        configureIngest()
        refreshLocalStore()
    }

    func configureIngest() {
        let key = ingestKey.trimmingCharacters(in: .whitespacesAndNewlines)
        IngestRuntime.shared.configure(
            baseUrl: ingestUrl,
            overrideKey: key.isEmpty ? nil : key,
            builtInKey: VeepooSession.bundledString("Next2UIngestApiKey") ?? ""
        )
        pendingCount = Int(IngestRuntime.shared.pendingCount())
        refreshLocalStore()
    }

    func refreshLocalStore() {
        localMetricCount = Int(PatientStore.shared.metricCount())
        localSummary = PatientStore.shared.localSummary()
    }

    func startScan() {
        devices = []
        isScanning = true
        sessionMessage = "Procurando o relógio…"
        manager.veepooSDKStartScanDeviceAndReceiveScanningDevice { [weak self] model in
            guard let self, let model else { return }
            Task { @MainActor in
                let watch = ScannedWatch(
                    name: model.deviceName ?? "HBand",
                    address: model.deviceAddress ?? "",
                    rssi: model.rssi?.intValue ?? 0,
                    model: model
                )
                guard !watch.address.isEmpty else { return }
                if let index = self.devices.firstIndex(where: { $0.address == watch.address }) {
                    self.devices[index] = watch
                } else {
                    self.devices.append(watch)
                }
                if watch.address == self.lastAddress, self.connected == nil {
                    self.connect(watch)
                }
            }
        }
    }

    func stopScan() {
        manager.veepooSDKStopScanDevice()
        isScanning = false
    }

    func connect(_ watch: ScannedWatch) {
        stopScan()
        sessionMessage = "Conectando a \(watch.name)…"
        connected = watch
        lastAddress = watch.address
        lastName = watch.name
        manager.veepooSDKConnectDevice(watch.model, deviceConnect: { [weak self] state in
            Task { @MainActor in
                self?.handleConnect(state, watch: watch)
            }
        })
    }

    func disconnect() {
        stopSensors()
        manager.automaticConnection = false
        manager.veepooSDKDisconnectDevice()
        hardwareReady = false
        connected = nil
        sessionMessage = "Desconexão solicitada."
    }

    func reconnectLastIfPossible() {
        guard connected == nil, lastAddress != nil else { return }
        sessionMessage = "Restaurando a sessão do relógio…"
        startScan()
    }

    /// `DeviceConnectState` in VPPublicDefine.h (BlePoweredOff = 0 … BleConfirmTimeout = 7).
    private func handleConnect(_ state: DeviceConnectState, watch: ScannedWatch) {
        switch state.rawValue {
        case 0:
            sessionMessage = "Ligue o Bluetooth do iPhone."
        case 1:
            sessionMessage = "Conectando…"
        case 2:
            sessionMessage = "Ligação feita. Confirmando a senha 0000…"
            verifyPassword(watch)
        case 3, 6, 7:
            sessionMessage = "Falha ou tempo esgotado na ligação. Toque de novo em conectar."
            hardwareReady = false
        case 4:
            onHandshake(watch)
        case 5:
            sessionMessage = "A senha padrão 0000 foi recusada. Confira o relógio."
            hardwareReady = false
        default:
            sessionMessage = "Estado de ligação desconhecido (\(state.rawValue))."
        }
    }

    /// `veepooSDKSynchronousPasswordWithType:password:SynchronizationResult:`
    /// `SynchronousPasswordType.VerifyPasswordType = 0`.
    /// `PasswordSynchronTpye`: 1 success, 5 read success, 6 success+time (usual).
    private func verifyPassword(_ watch: ScannedWatch) {
        manager.veepooSDKSynchronousPassword(
            with: SynchronousPasswordType(rawValue: 0)!,
            password: Self.defaultPassword,
            synchronizationResult: { [weak self] result in
                Task { @MainActor in
                    guard let self else { return }
                    switch result.rawValue {
                    case 1, 5, 6:
                        self.onHandshake(watch)
                    case 0, 4:
                        self.sessionMessage = "A senha padrão 0000 foi recusada."
                        self.hardwareReady = false
                    default:
                        self.sessionMessage = "Confirmação de senha: código \(result.rawValue)."
                    }
                }
            }
        )
    }

    private func onHandshake(_ watch: ScannedWatch) {
        guard !hardwareReady else { return }
        hardwareReady = true
        sessionMessage = "Relógio pronto. Lendo dados do dia e depois a frequência cardíaca."
        readBattery()
        // veepooSdkStartReadDeviceAllDataWithReadStateChangeBlock:
        // VPReadDeviceBaseDataComplete = 2
        manager.peripheralManage.veepooSdkStartReadDeviceAllData { [weak self] state, _, _, _ in
            Task { @MainActor in
                if state.rawValue == 2 {
                    self?.sessionMessage = "Histórico do relógio lido. Medindo batimentos…"
                    self?.startHeart()
                }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in
            if self?.heartRunning == false { self?.startHeart() }
        }
    }

    private func readBattery() {
        manager.peripheralManage.veepooSDKReadDeviceBatteryInfo { [weak self] isPercent, _, battery in
            Task { @MainActor in
                if isPercent { self?.batteryPercent = Int(battery) }
            }
        }
    }

    private func startHeart() {
        guard hardwareReady, !heartRunning else { return }
        stopOxygen()
        heartRunning = true
        manager.peripheralManage.veepooSDKTestHeartStart(true) { [weak self] state, value in
            Task { @MainActor in
                guard let self else { return }
                // VPTestHeartStateTesting = 1, VPTestHeartStateNotWear = 2
                if state.rawValue == 1, value >= 30, value <= 240 {
                    self.heartRate = Int(value)
                    self.sessionMessage = "Batimentos: \(value) bpm (leitura do relógio)."
                    self.recordAndFlush()
                }
                if state.rawValue == 2 {
                    self.sessionMessage = "O relógio não está no pulso."
                }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 20) { [weak self] in
            self?.startOxygen()
        }
    }

    private func startOxygen() {
        guard hardwareReady else { return }
        stopHeart()
        spo2Running = true
        manager.peripheralManage.veepooSDKTestOxygenStart(true) { [weak self] _, value in
            Task { @MainActor in
                guard let self else { return }
                if value >= 50, value <= 100 {
                    self.spo2 = Int(value)
                    self.recordAndFlush()
                }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self] in
            self?.stopOxygen()
            self?.startHeart()
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

    /// Flush queued rows. If a live reading is on screen, reuse the existing record path.
    func flushNow() {
        if heartRate > 0 || spo2 > 0 {
            recordAndFlush()
            return
        }
        configureIngest()
        Task {
            guard let flush = try? await IngestRuntime.shared.flush() else { return }
            lastFlushMessage = flush.message
            lastHttpCode = Int(flush.lastHttpCode)
            pendingCount = Int(flush.pendingCount)
            refreshLocalStore()
        }
    }

    private func recordAndFlush() {
        configureIngest()
        let watch = connected
        Task {
            guard let recorded = try? await IngestRuntime.shared.recordLive(
                deviceId: watch?.address ?? "",
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
                patientId: "PAT-HBAND-001"
            ) else { return }
            lastClientReadingId = recorded.clientReadingId
            pendingCount = Int(IngestRuntime.shared.pendingCount())
            refreshLocalStore()
            if recorded.queued {
                guard let flush = try? await IngestRuntime.shared.flush() else { return }
                lastFlushMessage = flush.message
                lastHttpCode = Int(flush.lastHttpCode)
                pendingCount = Int(flush.pendingCount)
            }
        }
    }

    private func startRestorationSidecar() {
        restoreCentral = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [
                CBCentralManagerOptionRestoreIdentifierKey: Self.restoreId,
                CBCentralManagerOptionShowPowerAlertKey: false,
            ]
        )
    }
}

extension VeepooSession: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {}

    nonisolated func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        Task { @MainActor in
            sessionMessage = "O iOS restaurou o Bluetooth. Tentando o último relógio."
            reconnectLastIfPossible()
        }
    }
}
