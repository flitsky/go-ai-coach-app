package com.worksoc.goaicoach.ui.foundation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties

// 옛 화면 코드가 `LocalConfiguration.current.screenWidthDp`를 그대로 읽던 것을 한곳에 모았다(#294) — 값의 뜻을 바꾸지 않으려고 린트 권고
// (`LocalWindowInfo`)를 따르지 않는다.
@Suppress("ConfigurationScreenWidthHeight")
@Composable
actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

@Suppress("ConfigurationScreenWidthHeight")
@Composable
actual fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

/**
 * 문맥을 감싼 껍질(ContextWrapper)을 따라 올라가 액티비티를 찾는다 — 없으면 null. 한 벌만 둔다(QA-19b, 2026-11-23 — 시스템 바 · 엔진 받기 배너의 사본 둘을 모았다).
 * ⚠️ 창(Dialog · AlertDialog) 안의 `LocalContext`는 액티비티가 아니라 창의 ContextThemeWrapper다 — 액티비티가 필요한 곳은 이것으로 푼다.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
