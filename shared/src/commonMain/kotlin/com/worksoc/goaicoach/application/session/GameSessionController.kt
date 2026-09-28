package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState

data class GameSessionControllerState(
    val core: GameSessionCoreState,
    val settings: GameSessionSettingsState,
    val benchmark: EngineBenchmarkUiState,
    val savedSession: SavedSessionUiState,
    val autoAiTurn: AutoAiTurnUiState,
    val positionCacheOptimization: PositionAnalysisCacheOptimizationUiState,
) {
    val gameState: GameState
        get() = core.gameState

    val isGameEnded: Boolean
        get() = core.isGameEnded

    val playerSetup: PlayerSetup
        get() = settings.playerSetup

    val matchMode: MatchMode
        get() = settings.matchMode

    val engineMessage: String
        get() = core.engineMessage

    val shouldShowResumePrompt: Boolean
        get() = savedSession.shouldShowResumePrompt

    val isAutoAiTurnPending: Boolean
        get() = autoAiTurn.isPending

    /**
     * 이 국면의 AI 탐색이 시간 초과로 끝나 사용자의 선택(「한 번 더 기다리기」/「엔진 다시 시작하기」)을 기다리는
     * 중인가(refactor backlog #74). 같은 국면에서 진짜 실패가 잇달아 나도 그렇다(#109). 세대·수순 길이가 바뀌면 저절로 `false`다.
     */
    val isAwaitingAutoAiTurnTimeoutChoice: Boolean
        get() = autoAiTurn.isAwaitingTimeoutChoice(
            sessionGeneration = core.runtimeState.sessionGeneration,
            moveCount = gameState.moves.size,
        )

    fun withCore(next: GameSessionCoreState): GameSessionControllerState =
        copy(core = next)

    fun withSettings(next: GameSessionSettingsState): GameSessionControllerState =
        copy(settings = next)

    fun withBenchmark(next: EngineBenchmarkUiState): GameSessionControllerState =
        copy(benchmark = next)

    fun withSavedSession(next: SavedSessionUiState): GameSessionControllerState =
        copy(savedSession = next)

    fun withAutoAiTurn(next: AutoAiTurnUiState): GameSessionControllerState =
        copy(autoAiTurn = next)

    fun withPositionCacheOptimization(
        next: PositionAnalysisCacheOptimizationUiState,
    ): GameSessionControllerState =
        copy(positionCacheOptimization = next)
}

fun buildGameSessionControllerState(
    gameState: GameState,
    isGameEnded: Boolean,
    analysisState: GameSessionAnalysisState,
    scoreState: GameSessionScoreState,
    runtimeState: GameSessionRuntimeState,
    moveReviewState: GameSessionMoveReviewState,
    engineMessage: String,
    turnTimeState: GameSessionTurnTimeState = GameSessionTurnTimeState.reset(gameState, 0L),
    settings: GameSessionSettingsState,
    benchmark: EngineBenchmarkUiState,
    savedSession: SavedSessionUiState,
    autoAiTurn: AutoAiTurnUiState,
    positionCacheOptimization: PositionAnalysisCacheOptimizationUiState,
): GameSessionControllerState =
    GameSessionControllerState(
        core = GameSessionCoreState(
            gameState = gameState,
            isGameEnded = isGameEnded,
            analysisState = analysisState,
            scoreState = scoreState,
            runtimeState = runtimeState,
            moveReviewState = moveReviewState,
            engineMessage = engineMessage,
            turnTimeState = turnTimeState,
        ),
        settings = settings,
        benchmark = benchmark,
        savedSession = savedSession,
        autoAiTurn = autoAiTurn,
        positionCacheOptimization = positionCacheOptimization,
    )
