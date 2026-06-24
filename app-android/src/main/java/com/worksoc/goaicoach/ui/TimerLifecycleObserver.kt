package com.worksoc.goaicoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState

@Composable
internal fun ObserveTimerLifecycle(
    turnTimeState: GameSessionTurnTimeState,
    onTurnTimeStateChange: (GameSessionTurnTimeState) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentTurnTimeState = rememberUpdatedState(turnTimeState)
    val currentOnTurnTimeStateChange = rememberUpdatedState(onTurnTimeStateChange)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                currentOnTurnTimeStateChange.value(
                    currentTurnTimeState.value.pause(System.currentTimeMillis()),
                )
            } else if (event == Lifecycle.Event.ON_RESUME) {
                currentOnTurnTimeStateChange.value(
                    currentTurnTimeState.value.resume(System.currentTimeMillis()),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
