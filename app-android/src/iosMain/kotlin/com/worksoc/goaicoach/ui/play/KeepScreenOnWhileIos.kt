package com.worksoc.goaicoach.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/** 켜 둔 자리 수 — 둘 이상이 켜면 마지막 것이 꺼질 때 유휴 타이머를 되돌린다(메인 스레드에서만 바뀐다). */
private var keepAwakeHolders = 0

@Composable
internal actual fun KeepScreenOnWhile(active: Boolean) {
    DisposableEffect(active) {
        if (active) {
            keepAwakeHolders++
            UIApplication.sharedApplication.idleTimerDisabled = true
        }
        onDispose {
            if (active) {
                keepAwakeHolders--
                if (keepAwakeHolders <= 0) {
                    keepAwakeHolders = 0
                    UIApplication.sharedApplication.idleTimerDisabled = false
                }
            }
        }
    }
}
