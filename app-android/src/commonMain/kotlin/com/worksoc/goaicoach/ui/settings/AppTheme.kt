package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.worksoc.goaicoach.platform.localTimeOfDay
import com.worksoc.goaicoach.ui.designsystem.AppDarkColorScheme
import com.worksoc.goaicoach.ui.designsystem.AppLightColorScheme
import com.worksoc.goaicoach.ui.designsystem.LocalAppDarkTheme
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import kotlinx.coroutines.delay

/**
 * 앱의 맨 바깥 테마(백로그 #272 — `MainActivity`가 홈 · 스플래시를 이것으로 감싼다). 고른 화면 모드(저장소의 흐름 — 설정 패널이 쓰면 곧바로 다시 그린다) ·
 * 폰의 다크 모드 · 폰의 시각(밤 시간 자동)으로
 * 밝은 색과 어두운 색 중 하나를 고르고([ThemeMode.isDark]), 상태 표시줄 · 내비게이션 바의 아이콘 색과 창 바탕을 그에 맞춘다.
 *
 * ⚠️ 매니페스트의 `configChanges`에 `uiMode`가 있어 폰의 다크 모드를 바꿔도 액티비티가 다시 만들어지지 않는다 — 그래도
 * `isSystemInDarkTheme()`이 바뀐 설정을 읽어 다시 그린다. 밤 시간 자동은 1분마다 시각을 다시 본다.
 */
@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    val platform = LocalAppPlatform.current
    val store = remember(platform) { platform.stores.themeMode }
    // ⚠️ 첫 값은 저장값을 바로 읽는다 — 효과로 미루면 첫 화면이 한 번 기본(시스템)으로 그려졌다가 바뀐다.
    val mode by remember(store) { store.modeChanges() }.collectAsState(initial = remember(store) { store.load() })
    val hour by rememberLocalHour()
    val dark = mode.isDark(isSystemInDarkTheme(), hour)
    val colors = if (dark) AppDarkColorScheme else AppLightColorScheme
    ApplySystemBarsAppearance(dark = dark, windowBackground = colors.background)
    CompositionLocalProvider(LocalAppDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** 폰의 지역 시각(시) — 1분마다 다시 본다. 값이 같으면 다시 그리지 않는다. */
@Composable
private fun rememberLocalHour(): State<Int> = produceState(localTimeOfDay().hour) {
    while (true) {
        delay((60 - localTimeOfDay().second).coerceAtLeast(1) * 1000L)
        value = localTimeOfDay().hour
    }
}

/**
 * 상태 표시줄 · 내비게이션 바의 아이콘 색과 창 바탕을 화면 모드에 맞춘다(백로그 #294 — S23). 안드로이드는 `WindowCompat` 코드 그대로,
 * iOS는 아직 아무 일도 하지 않는다(상태 표시줄 모양은 호스트 화면 컨트롤러의 몫 — W23).
 */
@Composable
internal expect fun ApplySystemBarsAppearance(dark: Boolean, windowBackground: Color)
