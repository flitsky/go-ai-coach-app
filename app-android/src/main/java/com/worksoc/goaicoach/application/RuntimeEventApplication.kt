package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.describe

private const val RuntimeAppName = "Go AI Coach"
private const val RuntimeAppPurpose =
    "Android-first local AI Go coaching app for 9x9 play, Top Moves, scoring, and endgame cleanup."

internal data class RuntimeLogContext(
    val engineName: String,
    val engineDiagnostic: String,
    val playerSetup: PlayerSetup,
    val gameState: GameState,
    val runtimeState: GameSessionRuntimeState,
    val autoPlayDelaySetting: AutoPlayDelaySetting,
    val searchTimeSettings: SearchTimeSettings,
    val topMovesEnabled: Boolean,
    val isEngineReady: Boolean,
    val isEngineBusy: Boolean,
    val isGameEnded: Boolean,
    val isAutoAiTurnPending: Boolean,
    val shouldShowResumePrompt: Boolean,
    val analysisCacheStats: String,
    val moveAnalysisCoverage: String,
    val scoreText: String,
    val turnTimeText: String = "Time B 0.0s / W 0.0s",
) {
    fun event(
        name: String,
        phase: String,
        transition: String = predictedNextState(),
        detail: String,
    ): String =
        listOf(
            "event=$name",
            "phase=$phase",
            "app=${RuntimeAppName.runtimeLogValue(40)}",
            "purpose=${RuntimeAppPurpose.runtimeLogValue(120)}",
            "mode=${playerSetup.matchMode()}",
            "setup=${playerSetup.summary(engineName).runtimeLogValue(180)}",
            "board=${gameState.runtimeBoardSummary().runtimeLogValue(180)}",
            "engine=${engineName.runtimeLogValue(60)}",
            "engineReady=$isEngineReady",
            "engineBusy=$isEngineBusy",
            "runtime=${runtimeState.runtimeSummary().runtimeLogValue(180)}",
            "analysis=${analysisSummary().runtimeLogValue(180)}",
            "score=${scoreText.runtimeLogValue(180)}",
            "turnTime=${turnTimeText.runtimeLogValue(80)}",
            "flags=${flagsSummary().runtimeLogValue(160)}",
            "transition=${transition.runtimeLogValue(120)}",
            "detail=${detail.runtimeLogValue(1_200)}",
        ).joinToString(" ")

    private fun predictedNextState(): String =
        when {
            isGameEnded -> "game_over_wait_for_new_game_or_undo"
            gameState.hasConsecutivePasses() || gameState.isBoardFull() -> "resolve_endgame_score"
            shouldShowResumePrompt -> "await_resume_decision"
            isEngineBusy -> "engine_busy_keep_current_state"
            isAutoAiTurnPending -> "ai_turn_scheduled"
            playerSetup.seatFor(gameState.nextPlayer).isAi && isEngineReady -> "schedule_ai_turn"
            playerSetup.seatFor(gameState.nextPlayer).isHuman -> "await_human_move"
            else -> "await_engine_ready_or_manual_action"
        }

    private fun analysisSummary(): String =
        "topMoves=$topMovesEnabled cache=$analysisCacheStats coverage=$moveAnalysisCoverage"

    private fun flagsSummary(): String =
        "gameEnded=$isGameEnded autoAiPending=$isAutoAiTurnPending resumePrompt=$shouldShowResumePrompt " +
            "autoDelay=${autoPlayDelaySetting.label}/${autoPlayDelaySetting.millis}ms " +
            "search=${searchTimeSettings.normalized().summaryText()} diagnostic=${engineDiagnostic.runtimeLogSnippet(140)}"
}

internal fun runtimeAppStartLog(context: RuntimeLogContext): String =
    context.event(
        name = "app_start",
        phase = "startup",
        transition = "engine_startup_then_saved_game_check",
        detail = "App process started. Engine bootstrap will run, then saved-session resume check may appear.",
    )

internal fun runtimeGameResetLog(
    context: RuntimeLogContext,
    reset: GameSessionResetPlan,
): String =
    context.event(
        name = "game_reset",
        phase = "game_setup",
        transition = contextTransitionAfter(reset.gameState, context),
        detail = "New local board prepared. message=${reset.engineMessage.runtimeLogSnippet(220)}",
    )

internal fun runtimeEngineGameStartRequestLog(
    context: RuntimeLogContext,
    ruleset: Ruleset,
    runtime: RuntimePlayLevelSelection,
): String =
    context.event(
        name = "engine_game_start_request",
        phase = "engine_game_setup",
        transition = "start_engine_new_game_then_reset_local_board",
        detail = "ruleset=$ruleset runtimeLevel=${runtime.playLevel.displayLabel} " +
            "limit=${runtime.engineProfile.analysisLimit.runtimeLogSummary()}",
    )

internal fun runtimeEngineGameStartSuccessLog(
    context: RuntimeLogContext,
    elapsedMs: Long,
    message: String,
): String =
    context.event(
        name = "engine_game_start_success",
        phase = "engine_game_setup",
        transition = "reset_local_board_then_request_top_moves",
        detail = "elapsedMs=$elapsedMs message=${message.runtimeLogSnippet(220)}",
    )

internal fun runtimeEngineGameStartFailureLog(
    context: RuntimeLogContext,
    elapsedMs: Long,
    error: Throwable,
): String =
    context.event(
        name = "engine_game_start_failure",
        phase = "engine_game_setup",
        transition = "reset_local_board_with_failure_message",
        detail = "elapsedMs=$elapsedMs error=${error.runtimeErrorText(220)}",
    )

internal fun runtimeAutoPlayDelayChangeLog(
    context: RuntimeLogContext,
    from: AutoPlayDelaySetting,
    to: AutoPlayDelaySetting,
): String =
    context.event(
        name = "auto_play_delay_change",
        phase = "settings",
        transition = contextTransitionAfter(context.gameState, context),
        detail = "from=${from.label}/${from.millis}ms to=${to.label}/${to.millis}ms",
    )

internal fun runtimeAiTurnScheduleLog(
    context: RuntimeLogContext,
    gameState: GameState,
    delayMillis: Long,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    isEngineBusy: Boolean,
): String =
    context.event(
        name = "ai_turn_schedule",
        phase = "ai_turn",
        transition = "wait_delay_then_validate_ai_turn",
        detail = "nextMove=${gameState.moves.size + 1} player=${gameState.nextPlayer.label} " +
            "delayMs=$delayMillis autoDelaySetting=${autoPlayDelaySetting.label} " +
            "engineBusy=$isEngineBusy fp=${gameState.runtimeShortFingerprint()}",
    )

internal fun runtimeAiTurnScheduleCancelledLog(
    context: RuntimeLogContext,
    gameState: GameState,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    shouldShowResumePrompt: Boolean,
): String =
    context.event(
        name = "ai_turn_schedule_cancelled",
        phase = "ai_turn",
        transition = contextTransitionAfter(gameState, context),
        detail = "nextMove=${gameState.moves.size + 1} player=${gameState.nextPlayer.label} " +
            "engineReady=$isEngineReady engineBusy=$isEngineBusy gameEnded=$isGameEnded " +
            "resumePrompt=$shouldShowResumePrompt fp=${gameState.runtimeShortFingerprint()}",
    )

internal fun runtimeAiTurnBeginLog(
    context: RuntimeLogContext,
    turnState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    analysisLimit: AnalysisLimit,
    searchMode: EngineSearchMode = EngineSearchMode.GtpStatefulFast,
    delayMillis: Long,
    isolateSearchCache: Boolean,
): String =
    context.event(
        name = "ai_turn_begin",
        phase = "ai_turn",
        transition = "engine_select_move",
        detail = "move=${turnState.moves.size + 1} player=${aiPlayer.label} " +
            "level=${playLevel.displayLabel} policy=${playLevel.selectionPolicy.description.runtimeLogSnippet(100)} " +
            "limit=${analysisLimit.runtimeLogSummary()} delayMs=$delayMillis " +
            "searchMode=${searchMode.name} " +
            "searchCache=${if (isolateSearchCache) "clear" else "reuse"} fp=${turnState.runtimeShortFingerprint()}",
    )

internal fun runtimeAiTurnSuccessLog(
    context: RuntimeLogContext,
    turnState: GameState,
    aiPlayer: StoneColor,
    display: AutoAiTurnDisplayPlan,
    turnElapsedMs: Long,
    turnTimeUpdate: TurnTimeMoveUpdate? = null,
): String =
    context.event(
        name = "ai_turn_success",
        phase = "ai_turn",
        transition = if (display.shouldResolveEndgame) {
            "resolve_endgame_score"
        } else {
            contextTransitionAfter(display.gameState, context)
        },
        detail = "move=${turnState.moves.size + 1} player=${aiPlayer.label} " +
            "selected=${display.lastMoveText.runtimeLogSnippet(80)} turnElapsedMs=$turnElapsedMs " +
            "turnTime=${turnTimeUpdate?.runtimeText()?.runtimeLogSnippet(140) ?: "not_recorded"} " +
            "before=${turnState.runtimeBoardSummary()} after=${display.gameState.runtimeBoardSummary()} " +
            "summary=${display.candidateText.runtimeLogSnippet(900)}",
    )

internal fun runtimeAiTurnEndgameDetectedLog(
    context: RuntimeLogContext,
    state: GameState,
): String =
    context.event(
        name = "ai_turn_endgame_detected",
        phase = "endgame",
        transition = "resolve_dead_stones_then_final_score",
        detail = "move=${state.moves.size} consecutivePasses=${state.hasConsecutivePasses()} " +
            "boardFull=${state.isBoardFull()} fp=${state.runtimeShortFingerprint()}",
    )

internal fun runtimeAiTurnEndgameSuccessLog(
    context: RuntimeLogContext,
    state: GameState,
    endgame: AiEndgameResolution,
): String =
    context.event(
        name = "ai_turn_endgame_success",
        phase = "endgame",
        transition = "game_over_wait_for_new_game_or_undo",
        detail = "move=${state.moves.size} removed=${endgame.cleanup.removedCount} " +
            "score=${endgame.finalScore.toString().runtimeLogSnippet(180)}",
    )

internal fun runtimeAiTurnEndgameFailureLog(
    context: RuntimeLogContext,
    state: GameState,
    error: Throwable,
): String =
    context.event(
        name = "ai_turn_endgame_failure",
        phase = "endgame",
        transition = "show_uncertain_endgame_failure",
        detail = "move=${state.moves.size} error=${error.runtimeErrorText(220)}",
    )

internal fun runtimeAiTurnFailureLog(
    context: RuntimeLogContext,
    turnState: GameState,
    aiPlayer: StoneColor,
    turnElapsedMs: Long,
    error: Throwable,
): String =
    context.event(
        name = "ai_turn_failure",
        phase = "ai_turn",
        transition = "keep_current_board_show_error",
        detail = "move=${turnState.moves.size + 1} player=${aiPlayer.label} " +
            "turnElapsedMs=$turnElapsedMs fp=${turnState.runtimeShortFingerprint()} error=${error.runtimeErrorText(300)}",
    )

internal fun runtimeAiTurnCompleteLog(
    context: RuntimeLogContext,
    gameState: GameState,
    isEngineBusy: Boolean,
    isAutoAiTurnPending: Boolean,
): String =
    context.event(
        name = "ai_turn_complete",
        phase = "ai_turn",
        transition = contextTransitionAfter(gameState, context),
        detail = "currentMoves=${gameState.moves.size} next=${gameState.nextPlayer.label} " +
            "engineBusy=$isEngineBusy pending=$isAutoAiTurnPending fp=${gameState.runtimeShortFingerprint()}",
    )

internal fun runtimeHumanMoveAcceptedLog(
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

internal fun runtimeHumanEngineSyncSuccessLog(
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

internal fun runtimeHumanEngineSyncFailureLog(
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
            "final_score score=${display.scoreText.runtimeLogSnippet(160)}"
        is HumanEngineSyncDisplayPlan.ScoreEstimate ->
            "score_estimate nextFp=${nextAnalysisState.runtimeShortFingerprint()} score=${display.scoreText.runtimeLogSnippet(160)}"
        HumanEngineSyncDisplayPlan.NoUpdate ->
            "no_update"
    }

private fun contextTransitionAfter(
    state: GameState,
    context: RuntimeLogContext,
): String =
    when {
        context.isGameEnded -> "game_over_wait_for_new_game_or_undo"
        state.hasConsecutivePasses() || state.isBoardFull() -> "resolve_endgame_score"
        context.playerSetup.seatFor(state.nextPlayer).isAi && context.isEngineReady -> "schedule_ai_turn"
        context.playerSetup.seatFor(state.nextPlayer).isHuman -> "await_human_move"
        else -> "await_engine_ready_or_manual_action"
    }

internal fun GameState.runtimeShortFingerprint(): String =
    analysisFingerprint()
        .hashCode()
        .toUInt()
        .toString(16)

internal fun GameState.runtimeBoardSummary(): String =
    "size=${boardSize.value} ruleset=$ruleset moves=${moves.size} next=${nextPlayer.label} " +
        "stones=${stones.size}/${boardSize.value * boardSize.value} captures=B${capturedBy(StoneColor.Black)}/W${capturedBy(StoneColor.White)} " +
        "pass2=${hasConsecutivePasses()} full=${isBoardFull()} fp=${runtimeShortFingerprint()}"

internal fun GameSessionRuntimeState.runtimeSummary(): String =
    "level=${playLevel.displayLabel} preset=$analysisPreset limit=${engineProfile.analysisLimit.runtimeLogSummary()}"

internal fun AnalysisLimit.runtimeLogSummary(): String =
    "visits=$visits,timeMs=${timeMillis ?: "none"},candidates=$candidateCount"

internal fun String.runtimeLogSnippet(maxChars: Int): String =
    replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { value ->
            if (value.length <= maxChars) {
                value
            } else {
                value.take(maxChars) + "..."
            }
        }

internal fun String.runtimeLogValue(maxChars: Int): String =
    "\"${runtimeLogSnippet(maxChars).replace('"', '\'')}\""

internal fun Throwable.runtimeErrorText(maxChars: Int): String =
    (message ?: this::class.simpleName ?: "unknown").runtimeLogSnippet(maxChars)
