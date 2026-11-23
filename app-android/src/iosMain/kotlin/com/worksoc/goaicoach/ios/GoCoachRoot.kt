package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.worksoc.goaicoach.GoCoachProcessRuntime
import com.worksoc.goaicoach.ui.settings.AppTheme
import com.worksoc.goaicoach.ui.shell.GoCoachApp
import com.worksoc.goaicoach.ui.splash.AppSplash

/**
 * iOS 앱의 뿌리(백로그 #294 — 이식 계획 S31) — 안드로이드 `MainActivity.setContent`와 같은 차례: 엔진 부트스트랩(프로세스에
 * 한 번) → 화면 모드 → 홈과 스플래시를 함께 컴포즈한다.
 * ⚠️ 글꼴 배율 1.0 고정(Dynamic Type을 따르지 않는다 — `FixedAppFontScale`)은 **한 층 위** `MainViewController`가 건다 —
 * 알림 층(`IosNoticeOverlay`)도 같은 값을 받아야 해서 옮겼다(QA-05, 2026-11-23).
 */
@Composable
internal fun GoCoachRoot(runtime: GoCoachProcessRuntime) {
    LaunchedEffect(Unit) { runtime.startEngineBootstrap() }
    AppTheme {
        Box {
            GoCoachApp(
                engineClient = runtime.engineClient,
                engineIdentity = { runtime.engineIdentity() },
                diagnosticEventLog = runtime.diagnosticEventLog,
                sessionGenerationRelay = runtime.sessionGenerationRelay,
            )
            AppSplash()
        }
    }
}
