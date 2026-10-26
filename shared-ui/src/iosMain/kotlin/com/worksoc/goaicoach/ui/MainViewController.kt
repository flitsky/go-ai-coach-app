package com.worksoc.goaicoach.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.worksoc.goaicoach.ui.skeleton.SkeletonApp
import platform.UIKit.UIViewController

/**
 * iOS 앱(`app-ios/`)의 SwiftUI가 이 화면을 띄운다 — Swift에서는 `MainViewControllerKt.MainViewController()`(백로그 #292).
 *
 * ⚠️ Compose Multiplatform은 `Info.plist`에 `CADisableMinimumFrameDurationOnPhone`이 없으면 시작할 때 멈춘다(엄격한 plist 검사가 기본이다) —
 *    그 키는 `app-ios/Info.plist`에 있다.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { SkeletonApp() }
