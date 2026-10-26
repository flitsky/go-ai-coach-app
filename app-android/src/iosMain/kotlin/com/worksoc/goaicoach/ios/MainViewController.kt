package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.settings.IosStatusBarAppearance
import com.worksoc.goaicoach.ui.settings.LocalIosStatusBarAppearance
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController

/**
 * iOS 앱(`app-ios/`)의 창이 이 화면을 루트로 띄운다 — Swift에서는 `MainViewControllerKt.MainViewController()`(백로그 #292).
 * 보통은 앱 전체([GoCoachRoot])다. 환경 변수로 다른 화면을 연다: 엔진 확인(`make ios-engine-probe`) · 이식 카탈로그(`make ios-catalog`).
 * 프로세스에 한 번 부른다 — 시작 처리([runIosAppStartup])와 엔진 묶음([iosProcessRuntime])이 여기서 하나씩 생긴다.
 *
 * ⚠️ Compose Multiplatform은 `Info.plist`에 `CADisableMinimumFrameDurationOnPhone`이 없으면 시작할 때 멈춘다(엄격한 plist 검사가 기본이다) —
 *    그 키는 `app-ios/Info.plist`에 있다.
 * ⚠️ 상태 표시줄 모양은 **창의 루트** 화면 컨트롤러가 정한다 — 그래서 Swift 쪽이 SwiftUI 호스트가 아니라 이 컨트롤러를 루트로 둔다.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController {
    val probe = EngineProbe.requested()
    val catalog = NSProcessInfo.processInfo.environment["GOCOACH_PORT_CATALOG_PAGE"] != null
    val platform = IosAppPlatform()
    runIosAppStartup(platform)
    val runtime = iosProcessRuntime(platform.diagnosticEventLog)
    val statusBar = IosStatusBarAppearance()
    return ComposeUIViewController(
        configure = {
            delegate = object : ComposeUIViewControllerDelegate {
                override val preferredStatusBarStyle: UIStatusBarStyle?
                    get() = if (statusBar.dark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
            }
        },
    ) {
        CompositionLocalProvider(LocalAppPlatform provides platform, LocalIosStatusBarAppearance provides statusBar) {
            Box {
                when {
                    probe != null -> EngineProbeScreen(probe)
                    catalog -> PortCatalog()
                    else -> GoCoachRoot(runtime)
                }
                IosNoticeOverlay(platform.notice)
            }
        }
    }
}
