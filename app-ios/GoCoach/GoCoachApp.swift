import SwiftUI
import SharedUI

// iOS 앱의 입구(백로그 #292). 화면은 전부 Kotlin 공유 UI(`:shared-ui` — Compose Multiplatform, U-83)가 그리고,
// 여기서는 그 화면을 SwiftUI 창에 올리기만 한다.
@main
struct GoCoachApp: App {
    init() {
        #if DEBUG
        KataGoProbe.runIfRequested()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
