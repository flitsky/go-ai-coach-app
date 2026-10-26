package com.worksoc.goaicoach.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS 앱(`app-ios/`)의 SwiftUI가 이 화면을 띄운다 — Swift에서는 `MainViewControllerKt.MainViewController()`(백로그 #292).
 *
 * ⚠️ Compose Multiplatform은 `Info.plist`에 `CADisableMinimumFrameDurationOnPhone`이 없으면 시작할 때 멈춘다(엄격한 plist 검사가 기본이다) —
 *    그 키는 `app-ios/Info.plist`에 있다.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController {
    // 엔진 확인(백로그 #293 — `make ios-engine-probe`)이 요청됐으면 그 화면, 아니면 뼈대 첫 화면.
    val probe = EngineProbe.requested()
    return ComposeUIViewController { if (probe != null) EngineProbeScreen(probe) else SkeletonApp() }
}
