package com.worksoc.goaicoach.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun KeepScreenOnWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (active) view.keepScreenOn = true
        onDispose {
            if (active) view.keepScreenOn = false
        }
    }
}
