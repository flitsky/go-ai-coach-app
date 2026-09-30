package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.runtime.contextTransitionAfter
import com.worksoc.goaicoach.application.runtime.runtimeBoardSummary
import com.worksoc.goaicoach.application.runtime.runtimeErrorText
import com.worksoc.goaicoach.application.runtime.runtimeLogSnippet
import com.worksoc.goaicoach.application.runtime.runtimeLogSummary
import com.worksoc.goaicoach.application.runtime.runtimeShortFingerprint
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting

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

/**
 * 앱 프로세스가 백그라운드로 갔다(backlog #202). 도는 AI 차례가 있었는지와 그것을 취소했는지를 적는다 — 복귀 뒤의
 * 느린 차례·시간 초과가 동결 때문인지를 리포트만으로 가를 수 있게(예전에는 logcat을 대조해야 했다).
 */
internal fun runtimeAppBackgroundLog(
    context: RuntimeLogContext,
    hadTurnInFlight: Boolean,
    cancelled: Boolean,
    isResolvingEndgame: Boolean,
): String =
    context.event(
        name = "app_background",
        phase = "app_lifecycle",
        transition = if (cancelled) "cancel_ai_turn_until_foreground" else "hold_ai_turns_until_foreground",
        detail = "aiTurnInFlight=$hadTurnInFlight cancelled=$cancelled resolvingEndgame=$isResolvingEndgame",
    )

/** 앱 프로세스가 화면으로 돌아왔다(backlog #202) — 건너뛴 AI 차례를 다시 요청한다. */
internal fun runtimeAppForegroundLog(context: RuntimeLogContext): String =
    context.event(
        name = "app_foreground",
        phase = "app_lifecycle",
        transition = "request_ai_turn",
        detail = "resume=ai_turn_request",
    )

/** 탐색이 시간 초과로 끝났다(refactor backlog #74) — 판은 그대로이고 사용자의 선택(팝업)을 기다린다. */
internal fun runtimeAiTurnTimeoutLog(
    context: RuntimeLogContext,
    turnState: GameState,
    aiPlayer: StoneColor,
    turnElapsedMs: Long,
    error: Throwable,
): String =
    context.event(
        name = "ai_turn_timeout",
        phase = "ai_turn",
        transition = "keep_current_board_await_choice",
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
