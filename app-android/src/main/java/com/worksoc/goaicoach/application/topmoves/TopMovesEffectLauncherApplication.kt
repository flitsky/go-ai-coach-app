package com.worksoc.goaicoach.application.topmoves

import com.worksoc.goaicoach.application.undo.undoEngineInterventionRemainingDelayMillis
import kotlinx.coroutines.delay

internal suspend fun runTopMoveAnalysisTriggerEffect(
    quietUntilMillis: Long,
    nowMillis: () -> Long = System::currentTimeMillis,
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
