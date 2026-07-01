import SwiftUI
import ChatwootSDK

struct ContentView: View {
    @State private var showChat = false

    var body: some View {
        VStack {
            Button("Open chat") { showChat = true }
        }
        .fullScreenCover(isPresented: $showChat) {
            ChatView(onFinish: { showChat = false })
                .ignoresSafeArea()
        }
    }
}

struct ChatView: UIViewControllerRepresentable {
    let onFinish: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        ChatPageViewControllerKt.ChatPageViewController(
            onFinish: onFinish,
            styleConfig: StyleConfigKt.DefaultStyle
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
