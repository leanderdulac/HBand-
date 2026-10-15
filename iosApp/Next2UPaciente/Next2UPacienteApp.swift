import SwiftUI
import UIKit
import shared

@main
struct Next2UPacienteApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var storage = StorageSession()

    var body: some Scene {
        WindowGroup {
            Group {
                if storage.status == .ready {
                    ContentView()
                } else {
                    StorageStartupView(status: storage.status)
                }
            }
            .task {
                await storage.openIfNeeded()
            }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Restore id must be on a CBCentralManager before this method returns (TN3115).
        MainActor.assumeIsolated {
            VeepooSession.installAtLaunch()
        }
        IngestBackground.register()
        IngestBackground.schedule()
        return true
    }
}
