import SwiftUI
import ChatwootSDK

@main
struct iOSApp: App {
    init() {
        // Or set ChatwootBaseUrl / ChatwootWebsiteToken in Info.plist instead.
        Chatwoot.shared.configure(
            baseUrl: "https://app.chatwoot.com",
            websiteToken: "YOUR_WEBSITE_TOKEN"
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
