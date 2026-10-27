import UIKit
import SharedUI

// iOS 앱의 입구(백로그 #292). 화면은 전부 Kotlin(`:app-android`의 commonMain — Compose Multiplatform, U-83 · #294)이 그리고,
// 여기서는 그 화면을 창의 루트에 올리기만 한다.
// SwiftUI가 아니라 UIKit 창인 이유(#294): 상태 표시줄의 글자 색(밝은 · 어두운 화면 모드)은 창의 **루트** 화면 컨트롤러가 정한다 —
// SwiftUI 호스트 안에 넣으면 Kotlin 화면이 그것을 정할 수 없다.
// 창을 여는 장면 델리게이트는 `Info.plist`의 `UISceneDelegateClassName`이 고른다(지난 실행에서 저장된 장면도 같은 델리게이트로 다시 붙는다).
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    /// 프로세스에 하나 — 플랫폼 · 시작 처리 · 엔진 묶음(안드로이드 `GoAiCoachApplication`의 짝). 장면이 다시 붙어도 같은 것을 쓴다.
    let root = IosAppRoot(modelSource: AppleHostedModelSource.makeIfAvailable(), storeSource: AppStoreSubscriptionSource())
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        // 실행 화면이 내려간 뒤 첫 프레임까지도 앱 바탕색으로(번쩍임 방지 — Info.plist UILaunchScreen과 같은 색).
        window.backgroundColor = UIColor(named: "LaunchBackground")
        window.rootViewController = (UIApplication.shared.delegate as! AppDelegate).root.makeViewController()
        window.makeKeyAndVisible()
        self.window = window
    }
}
