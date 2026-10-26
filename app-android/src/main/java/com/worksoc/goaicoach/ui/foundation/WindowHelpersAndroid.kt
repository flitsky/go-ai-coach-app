package com.worksoc.goaicoach.ui.foundation

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
