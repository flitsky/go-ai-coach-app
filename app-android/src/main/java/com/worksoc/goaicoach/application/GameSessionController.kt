package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.GameState

internal data class GameSessionControllerState(
    val core: GameSessionCoreState,
    val settings: GameSessionSettingsState,
    val benchmark: EngineBenchmarkUiState,
    val savedSession: SavedSessionUiState,
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

    fun withCore(next: GameSessionCoreState): GameSessionControllerState =
        copy(core = next)

    fun withSettings(next: GameSessionSettingsState): GameSessionControllerState =
        copy(settings = next)

    fun withBenchmark(next: EngineBenchmarkUiState): GameSessionControllerState =
        copy(benchmark = next)

    fun withSavedSession(next: SavedSessionUiState): GameSessionControllerState =
        copy(savedSession = next)

    fun withPositionCacheOptimization(
        next: PositionAnalysisCacheOptimizationUiState,
    ): GameSessionControllerState =
        copy(positionCacheOptimization = next)
}

internal sealed interface GameSessionEffect {
    data class RunTopMoveAnalysis(
        val plan: TopMoveAnalysisPlan,
        val deep: Boolean,
        val automatic: Boolean,
    ) : GameSessionEffect

    data class RunAutoAiTurn(
        val context: AutoAiTurnExecutionContext,
    ) : GameSessionEffect

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
}
