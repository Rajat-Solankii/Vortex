import SwiftUI
import WebKit // Vortex embed player (WKWebView) — links WebKit for the static ComposeApp framework
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(OrientationLockAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    AppUrlBridgeKt.handleAppUrl(url: url.absoluteString)
                }
        }
    }
}
