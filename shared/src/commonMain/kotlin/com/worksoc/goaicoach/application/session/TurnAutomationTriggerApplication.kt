package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.application.undo.undoEngineInterventionRemainingDelayMillis
import com.worksoc.goaicoach.shared.GameState
import kotlinx.coroutines.delay

suspend fun runTurnAutomationTriggerEffect(
    quietUntilMillis: Long,
    topMoveTargetState: GameState,
    nowMillis: () -> Long = ::currentEpochMillis,
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
