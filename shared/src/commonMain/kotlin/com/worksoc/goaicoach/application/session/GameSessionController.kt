package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.savedgame.SavedSessionUiState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
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
