package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.application.undo.undoEngineInterventionRemainingDelayMillis
import kotlinx.coroutines.delay

suspend fun runTopMoveAnalysisTriggerEffect(
    quietUntilMillis: Long,
    nowMillis: () -> Long = ::currentEpochMillis,
    delayMillis: suspend (Long) -> Unit = { millis -> delay(millis) },
    requestTopMoveAnalysis: () -> Unit,
) {
    val remainingMillis = undoEngineInterventionRemainingDelayMillis(
        nowMillis = nowMillis(),
        quietUntilMillis = quietUntilMillis,
    )
    if (remainingMillis > 0L) {
        delayMillis(remainingMillis)
    }
    requestTopMoveAnalysis()
}
