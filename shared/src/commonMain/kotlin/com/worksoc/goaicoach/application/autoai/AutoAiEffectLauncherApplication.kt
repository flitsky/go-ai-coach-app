package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.application.undo.undoEngineInterventionRemainingDelayMillis
import kotlinx.coroutines.delay

suspend fun runAutoAiTurnTriggerEffect(
    quietUntilMillis: Long,
    nowMillis: () -> Long = ::currentEpochMillis,
    delayMillis: suspend (Long) -> Unit = { millis -> delay(millis) },
    requestAiTurn: () -> Unit,
) {
    val remainingMillis = undoEngineInterventionRemainingDelayMillis(
        nowMillis = nowMillis(),
        quietUntilMillis = quietUntilMillis,
    )
    if (remainingMillis > 0L) {
        delayMillis(remainingMillis)
    }
    requestAiTurn()
}
