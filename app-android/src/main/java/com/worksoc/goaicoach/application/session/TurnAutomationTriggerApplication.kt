package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.undo.undoEngineInterventionRemainingDelayMillis
import com.worksoc.goaicoach.shared.GameState
import kotlinx.coroutines.delay

internal suspend fun runTurnAutomationTriggerEffect(
    quietUntilMillis: Long,
    topMoveTargetState: GameState,
    nowMillis: () -> Long = System::currentTimeMillis,
    delayMillis: suspend (Long) -> Unit = { millis -> delay(millis) },
    requestAiTurn: () -> Unit,
    requestTopMoveAnalysis: (GameState) -> Unit,
) {
    val remainingMillis = undoEngineInterventionRemainingDelayMillis(
        nowMillis = nowMillis(),
        quietUntilMillis = quietUntilMillis,
    )
    if (remainingMillis > 0L) {
        delayMillis(remainingMillis)
    }

    requestAiTurn()
    requestTopMoveAnalysis(topMoveTargetState)
}
