package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.worksoc.goaicoach.GoCoachProcessRuntime
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.settings.AppFontScaleState
import com.worksoc.goaicoach.ui.settings.AppTheme
import com.worksoc.goaicoach.ui.shell.GoCoachApp
import com.worksoc.goaicoach.ui.splash.AppSplash

/**
 * iOS 앱의 뿌리(백로그 #294 — 이식 계획 S31) — 안드로이드 `MainActivity.setContent`와 같은 차례: 앱 글꼴 배율 → 엔진 부트스트랩(프로세스에
 * 한 번) → 화면 모드 → 홈과 스플래시를 함께 컴포즈한다. 시스템 글꼴 크기는 따르지 않는다(안드로이드와 같은 결정 — `DefaultAppFontScale`).
 */
@Composable
internal fun GoCoachRoot(runtime: GoCoachProcessRuntime) {
    val platform = LocalAppPlatform.current
    val preferencesStore = remember(platform) { platform.stores.userPreferences }
    LaunchedEffect(preferencesStore) { AppFontScaleState.load(preferencesStore) }
    val baseDensity = LocalDensity.current
    val density = Density(density = baseDensity.density, fontScale = AppFontScaleState.scale)
    CompositionLocalProvider(LocalDensity provides density) {
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
}
