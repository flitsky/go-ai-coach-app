package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.DebugReportCopyPlan
import com.worksoc.goaicoach.application.engine.EngineBenchmarkUiState
import com.worksoc.goaicoach.application.HumanEngineSyncRunPlan
import com.worksoc.goaicoach.application.PositionAnalysisCacheOptimizationPlan
import com.worksoc.goaicoach.application.PositionAnalysisCacheOptimizationUiState
import com.worksoc.goaicoach.application.SavedSessionUiState
import com.worksoc.goaicoach.application.autoai.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnRunPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnUiState
import com.worksoc.goaicoach.application.score.ScoreEstimateRequestPlan
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisPlan
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset

internal data class GameSessionControllerState(
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

internal fun buildGameSessionControllerState(
    gameState: GameState,
    isGameEnded: Boolean,
    analysisState: GameSessionAnalysisState,
    scoreState: GameSessionScoreState,
    runtimeState: GameSessionRuntimeState,
    moveReviewState: GameSessionMoveReviewState,
    engineMessage: String,
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
        ),
        settings = settings,
        benchmark = benchmark,
        savedSession = savedSession,
        autoAiTurn = autoAiTurn,
        positionCacheOptimization = positionCacheOptimization,
    )

internal sealed interface GameSessionEffect {
    data class RunTopMoveAnalysis(
        val plan: TopMoveAnalysisPlan,
        val deep: Boolean,
        val automatic: Boolean,
    ) : GameSessionEffect

    data class RunAutoAiTurn(
        val plan: AutoAiTurnRunPlan,
    ) : GameSessionEffect

    data class ResolveAutoAiEndgame(
        val plan: AutoAiTurnEndgamePlan.Resolve,
    ) : GameSessionEffect

    data class SyncHumanMove(
        val plan: HumanEngineSyncRunPlan,
    ) : GameSessionEffect

    data class StartEngineSession(
        val state: GameState,
        val profile: EngineProfile,
    ) : GameSessionEffect

    data class StartEngineBackedGame(
        val currentState: GameState,
        val profile: EngineProfile,
        val boardSize: BoardSize,
        val ruleset: Ruleset,
    ) : GameSessionEffect

    data class UndoEngineMoves(
        val state: GameState,
        val undoCount: Int,
    ) : GameSessionEffect {
        init {
            require(undoCount > 0) { "undoCount must be positive" }
        }
    }

    data class RunScoreEstimate(
        val request: ScoreEstimateRequestPlan.RequestEngineEstimate,
    ) : GameSessionEffect

    data object RunStartupBenchmark : GameSessionEffect

    data class RunPositionCacheOptimization(
        val plan: PositionAnalysisCacheOptimizationPlan,
    ) : GameSessionEffect

    data class SyncRestoredGame(
        val gameState: GameState,
    ) : GameSessionEffect

    data class CopyDebugReport(
        val plan: DebugReportCopyPlan,
    ) : GameSessionEffect
}
