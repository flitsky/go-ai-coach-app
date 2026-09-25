package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile

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
