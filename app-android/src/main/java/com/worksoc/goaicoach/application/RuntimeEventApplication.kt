package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.summary
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint

internal fun runtimeAppStartLog(
    engineName: String,
    engineDiagnostic: String,
): String =
    "app_start engine=$engineName diagnostic=${engineDiagnostic.runtimeLogSnippet(180)}"

internal fun runtimeGameResetLog(
    reset: GameSessionResetPlan,
    playerSetup: PlayerSetup,
    engineName: String,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    searchTimeSettings: SearchTimeSettings,
): String =
    "game_reset moves=${reset.gameState.moves.size} next=${reset.gameState.nextPlayer.label} " +
        "ruleset=${reset.gameState.ruleset} mode=${playerSetup.matchMode()} " +
        "setup=${playerSetup.summary(engineName).runtimeLogSnippet(160)} autoDelayMs=${autoPlayDelaySetting.millis} " +
        "search=${searchTimeSettings.normalized().summaryText()} fp=${reset.gameState.runtimeShortFingerprint()} " +
        "message=${reset.engineMessage.runtimeLogSnippet(220)}"

internal fun runtimeEngineGameStartRequestLog(
    ruleset: Ruleset,
    playerSetup: PlayerSetup,
    engineName: String,
    runtime: RuntimePlayLevelSelection,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    searchTimeSettings: SearchTimeSettings,
): String =
    "engine_game_start_request ruleset=$ruleset mode=${playerSetup.matchMode()} " +
        "setup=${playerSetup.summary(engineName).runtimeLogSnippet(160)} runtimeLevel=${runtime.playLevel.displayLabel} " +
        "limit=${runtime.engineProfile.analysisLimit.runtimeLogSummary()} autoDelayMs=${autoPlayDelaySetting.millis} " +
        "search=${searchTimeSettings.normalized().summaryText()}"

internal fun runtimeEngineGameStartSuccessLog(
    elapsedMs: Long,
    message: String,
): String =
    "engine_game_start_success elapsedMs=$elapsedMs message=${message.runtimeLogSnippet(220)}"

internal fun runtimeEngineGameStartFailureLog(
    elapsedMs: Long,
    error: Throwable,
): String =
    "engine_game_start_failure elapsedMs=$elapsedMs error=${error.runtimeErrorText(220)}"

internal fun runtimeAutoPlayDelayChangeLog(
    from: AutoPlayDelaySetting,
    to: AutoPlayDelaySetting,
): String =
    "auto_play_delay_change from=${from.label}/${from.millis}ms to=${to.label}/${to.millis}ms"

internal fun runtimeAiTurnScheduleLog(
    gameState: GameState,
    delayMillis: Long,
    autoPlayDelaySetting: AutoPlayDelaySetting,
    isEngineBusy: Boolean,
): String =
    "ai_turn_schedule nextMove=${gameState.moves.size + 1} player=${gameState.nextPlayer.label} " +
        "delayMs=$delayMillis autoDelaySetting=${autoPlayDelaySetting.label} " +
        "engineBusy=$isEngineBusy fp=${gameState.runtimeShortFingerprint()}"

internal fun runtimeAiTurnScheduleCancelledLog(
    gameState: GameState,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isGameEnded: Boolean,
    shouldShowResumePrompt: Boolean,
): String =
    "ai_turn_schedule_cancelled nextMove=${gameState.moves.size + 1} " +
        "player=${gameState.nextPlayer.label} engineReady=$isEngineReady engineBusy=$isEngineBusy " +
        "gameEnded=$isGameEnded resumePrompt=$shouldShowResumePrompt fp=${gameState.runtimeShortFingerprint()}"

internal fun runtimeAiTurnBeginLog(
    turnState: GameState,
    aiPlayer: StoneColor,
    playLevel: PlayLevelSetting,
    analysisLimit: AnalysisLimit,
    delayMillis: Long,
    isolateSearchCache: Boolean,
): String =
    "ai_turn_begin move=${turnState.moves.size + 1} player=${aiPlayer.label} " +
        "level=${playLevel.displayLabel} policy=${playLevel.selectionPolicy.description.runtimeLogSnippet(80)} " +
        "limit=${analysisLimit.runtimeLogSummary()} delayMs=$delayMillis " +
        "searchCache=${if (isolateSearchCache) "clear" else "reuse"} fp=${turnState.runtimeShortFingerprint()}"

internal fun runtimeAiTurnSuccessLog(
    turnState: GameState,
    aiPlayer: StoneColor,
    display: AutoAiTurnDisplayPlan,
    turnElapsedMs: Long,
): String =
    "ai_turn_success move=${turnState.moves.size + 1} player=${aiPlayer.label} " +
        "selected=${display.lastMoveText.runtimeLogSnippet(80)} turnElapsedMs=$turnElapsedMs " +
        "beforeFp=${turnState.runtimeShortFingerprint()} afterFp=${display.gameState.runtimeShortFingerprint()} " +
        "summary=${display.candidateText.runtimeLogSnippet(900)}"

internal fun runtimeAiTurnEndgameDetectedLog(state: GameState): String =
    "ai_turn_endgame_detected move=${state.moves.size} " +
        "consecutivePasses=${state.hasConsecutivePasses()} boardFull=${state.isBoardFull()} " +
        "fp=${state.runtimeShortFingerprint()}"

internal fun runtimeAiTurnEndgameSuccessLog(
    state: GameState,
    endgame: AiEndgameResolution,
): String =
    "ai_turn_endgame_success move=${state.moves.size} " +
        "removed=${endgame.cleanup.removedCount} " +
        "score=${endgame.finalScore.toString().runtimeLogSnippet(180)}"

internal fun runtimeAiTurnEndgameFailureLog(
    state: GameState,
    error: Throwable,
): String =
    "ai_turn_endgame_failure move=${state.moves.size} error=${error.runtimeErrorText(220)}"

internal fun runtimeAiTurnFailureLog(
    turnState: GameState,
    aiPlayer: StoneColor,
    turnElapsedMs: Long,
    error: Throwable,
): String =
    "ai_turn_failure move=${turnState.moves.size + 1} player=${aiPlayer.label} " +
        "turnElapsedMs=$turnElapsedMs fp=${turnState.runtimeShortFingerprint()} error=${error.runtimeErrorText(300)}"

internal fun runtimeAiTurnCompleteLog(
    gameState: GameState,
    isEngineBusy: Boolean,
    isAutoAiTurnPending: Boolean,
): String =
    "ai_turn_complete currentMoves=${gameState.moves.size} next=${gameState.nextPlayer.label} " +
        "engineBusy=$isEngineBusy pending=$isAutoAiTurnPending fp=${gameState.runtimeShortFingerprint()}"

internal fun GameState.runtimeShortFingerprint(): String =
    analysisFingerprint()
        .hashCode()
        .toUInt()
        .toString(16)

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

internal fun Throwable.runtimeErrorText(maxChars: Int): String =
    (message ?: this::class.simpleName ?: "unknown").runtimeLogSnippet(maxChars)
