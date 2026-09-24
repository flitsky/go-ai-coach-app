package com.worksoc.goaicoach.application.contract

import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit

data class PositionAnalysisCacheOptimizationTarget(
    val state: GameState,
    val moveNumber: Int,
    val levelLabel: String,
    val cacheLimit: AnalysisLimit,
    val executionLimit: AnalysisLimit,
)

data class PositionAnalysisCacheOptimizationPlan(
    val gameFingerprint: String,
    val finalState: GameState,
    val finalMoveCount: Int,
    val targets: List<PositionAnalysisCacheOptimizationTarget>,
) {
    val isEmpty: Boolean = targets.isEmpty()
}
