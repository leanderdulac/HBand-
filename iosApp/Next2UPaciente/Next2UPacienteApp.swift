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
        // bluetooth-central + CBCentralManager restore id live on VeepooSession.
        return true
    }
}
