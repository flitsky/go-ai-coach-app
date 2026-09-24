package com.worksoc.goaicoach.application.humanmove

import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.contextTransitionAfter
import com.worksoc.goaicoach.application.runtime.runtimeBoardSummary
import com.worksoc.goaicoach.application.runtime.runtimeLogSnippet
import com.worksoc.goaicoach.application.runtime.runtimeShortFingerprint
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.shared.domain.GameState

fun runtimeHumanMoveAcceptedLog(
    context: RuntimeLogContext,
    beforeMove: GameState,
    localMove: HumanMoveLocalResult,
    turnTimeUpdate: TurnTimeMoveUpdate? = null,
): String =
    context.event(
        name = "human_move_accepted",
        phase = "human_turn",
        transition = if (localMove.afterMove.hasConsecutivePasses() || localMove.afterMove.isBoardFull()) {
            "resolve_endgame_score_or_engine_sync"
        } else if (context.isEngineReady) {
            "sync_engine_after_human_move"
        } else {
            contextTransitionAfter(localMove.afterMove, context)
        },
        detail = "move=${localMove.lastMoveText} before=${beforeMove.runtimeBoardSummary()} " +
            "after=${localMove.afterMove.runtimeBoardSummary()} review=${localMove.moveReview.text.runtimeLogSnippet(240)} " +
            "turnTime=${turnTimeUpdate?.runtimeText()?.runtimeLogSnippet(140) ?: "not_recorded"} " +
            "captured=${localMove.capturedText.runtimeLogSnippet(120)}",
    )

fun runtimeHumanEngineSyncSuccessLog(
    context: RuntimeLogContext,
    sync: HumanEngineSyncDisplayPlan,
    elapsedMs: Long,
): String =
    context.event(
        name = "human_engine_sync_success",
        phase = "human_turn",
        transition = when (sync) {
            is HumanEngineSyncDisplayPlan.FinalScore -> "game_over_wait_for_new_game_or_undo"
            is HumanEngineSyncDisplayPlan.ScoreEstimate -> "request_top_moves_for_next_turn"
            HumanEngineSyncDisplayPlan.NoUpdate -> contextTransitionAfter(context.gameState, context)
        },
        detail = "elapsedMs=$elapsedMs result=${sync.runtimeSyncSummary()}",
    )

fun runtimeHumanEngineSyncFailureLog(
    context: RuntimeLogContext,
    failure: HumanEngineSyncFailurePlan,
    elapsedMs: Long,
): String =
    context.event(
        name = "human_engine_sync_failure",
        phase = "human_turn",
        transition = "keep_human_move_show_sync_failure",
        detail = "elapsedMs=$elapsedMs message=${failure.engineMessage.runtimeLogSnippet(220)} " +
            "candidateText=${failure.candidateText.runtimeLogSnippet(180)}",
    )

private fun HumanEngineSyncDisplayPlan.runtimeSyncSummary(): String =
    when (this) {
        is HumanEngineSyncDisplayPlan.FinalScore ->
            "final_score timings=${display.endgameTimingSummary ?: "none"} " +
                "score=${display.scoreText.runtimeLogSnippet(160)}"
        is HumanEngineSyncDisplayPlan.ScoreEstimate ->
            "score_estimate nextFp=${nextAnalysisState.runtimeShortFingerprint()} score=${display.scoreText.runtimeLogSnippet(160)}"
        HumanEngineSyncDisplayPlan.NoUpdate ->
            "no_update"
    }
