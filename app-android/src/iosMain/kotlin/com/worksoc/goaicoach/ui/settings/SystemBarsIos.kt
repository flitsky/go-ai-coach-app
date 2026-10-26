package com.worksoc.goaicoach.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController

/**
 * 상태 표시줄 글자 색의 자리(백로그 #294 — S23). `MainViewController`가 하나 만들어 컴포지션에 넣고, 그 화면 컨트롤러의 델리게이트가
 * 상태 표시줄 모양을 물을 때 이것을 읽는다. 컴포즈 상태가 아니다 — 바뀌면 [ApplySystemBarsAppearance]가 컨트롤러에 다시 물으라고 알린다.
 */
internal class IosStatusBarAppearance {
    var dark: Boolean = false
}

internal val LocalIosStatusBarAppearance = staticCompositionLocalOf<IosStatusBarAppearance?> { null }

/**
 * iOS: 상태 표시줄 글자 색만 화면 모드에 맞춘다(어두운 화면 → 밝은 글자). 창 바탕은 컴포즈가 늘 덮으므로 따로 칠하지 않는다.
 * ⚠️ 창의 `overrideUserInterfaceStyle`로 맞추지 않는다 — 그러면 `isSystemInDarkTheme()`이 폰의 설정 대신 그 값을 읽어,
 * 「시스템 따라가기」로 돌아왔을 때 폰의 다크 모드를 못 본다.
 */
@Composable
internal actual fun ApplySystemBarsAppearance(dark: Boolean, windowBackground: Color) {
    val appearance = LocalIosStatusBarAppearance.current ?: return
    val controller = LocalUIViewController.current
    SideEffect {
        if (appearance.dark != dark) {
            appearance.dark = dark
            controller.setNeedsStatusBarAppearanceUpdate()
        }
    }
}
