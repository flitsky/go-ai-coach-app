package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import platform.UIKit.UIViewController

/**
 * iOS 앱(`app-ios/`)의 SwiftUI가 이 화면을 띄운다 — Swift에서는 `MainViewControllerKt.MainViewController()`(백로그 #292).
 *
 * ⚠️ Compose Multiplatform은 `Info.plist`에 `CADisableMinimumFrameDurationOnPhone`이 없으면 시작할 때 멈춘다(엄격한 plist 검사가 기본이다) —
 *    그 키는 `app-ios/Info.plist`에 있다.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController {
    // 엔진 확인(백로그 #293 — `make ios-engine-probe`)이 요청됐으면 그 화면, 아니면 이식 카탈로그(#294 — 앱 전체가 뜨기 전까지).
    val probe = EngineProbe.requested()
    val platform = IosAppPlatform()
    return ComposeUIViewController {
        CompositionLocalProvider(LocalAppPlatform provides platform) {
            Box {
                if (probe != null) EngineProbeScreen(probe) else PortCatalog()
                IosNoticeOverlay(platform.notice)
            }
        }
    }
}
