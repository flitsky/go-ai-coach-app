package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * 앱 **프로세스**가 화면을 떠나고 돌아오는 전환을 알린다(backlog #202).
 *
 * 액티비티가 아니라 프로세스 수명([ProcessLifecycleOwner])인 이유: 안드로이드가 앱을 동결하는 조건이 곧 "프로세스에
 * 보이는 화면이 없다"이다. 전면 광고·결제 시트처럼 **우리 프로세스 안의** 화면이 대국 화면을 가릴 때는 동결되지
 * 않으므로 대국도 지금처럼 계속된다. 화면 회전(액티비티 재생성)도 프로세스 수명은 흔들지 않는다(ON_STOP이 700ms 늦게 온다).
 *
 * 전환에서만 부른다 — 옵저버를 붙이면 수명이 지금 상태까지 ON_CREATE·ON_START를 다시 흘려보내는데, 처음은 화면에
 * 있는 것으로 보므로 그 ON_START는 삼킨다.
 */
@Composable
internal fun ObserveAppForegroundLifecycle(
    onBackgrounded: () -> Unit,
    onForegrounded: () -> Unit,
    processLifecycle: Lifecycle = remember { ProcessLifecycleOwner.get().lifecycle },
) {
    val currentOnBackgrounded = rememberUpdatedState(onBackgrounded)
    val currentOnForegrounded = rememberUpdatedState(onForegrounded)

    DisposableEffect(processLifecycle) {
        var isInForeground = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (isInForeground) {
                    isInForeground = false
                    currentOnBackgrounded.value()
                }
                Lifecycle.Event.ON_START -> if (!isInForeground) {
                    isInForeground = true
                    currentOnForegrounded.value()
                }
                else -> Unit
            }
        }
        processLifecycle.addObserver(observer)
        onDispose {
            processLifecycle.removeObserver(observer)
        }
    }
}
