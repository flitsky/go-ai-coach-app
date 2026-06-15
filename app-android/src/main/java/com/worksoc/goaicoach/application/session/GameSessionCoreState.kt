package com.worksoc.goaicoach.application.session

import com.worksoc.goaicoach.application.GameSessionResetPlan
import com.worksoc.goaicoach.application.HumanEngineSyncFailurePlan
import com.worksoc.goaicoach.application.HumanMoveLocalResult
import com.worksoc.goaicoach.application.engine.EngineBenchmarkDisplayPlan
import com.worksoc.goaicoach.application.engine.EngineStartupDisplayPlan
import com.worksoc.goaicoach.application.PlayerSetupChangePlan
import com.worksoc.goaicoach.application.SavedGameRestorePlan
import com.worksoc.goaicoach.application.ScoringRuleChangePlan
import com.worksoc.goaicoach.application.UndoLocalStatePlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.score.ScoreEstimateFailureDisplayPlan
import com.worksoc.goaicoach.application.topmoves.TopMoveAnalysisFailureDisplayPlan
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot

internal data class GameSessionCoreState(
    val gameState: GameState,
    val isGameEnded: Boolean,
    val analysisState: GameSessionAnalysisState,
    val scoreState: GameSessionScoreState,
    val runtimeState: GameSessionRuntimeState,
    val moveReviewState: GameSessionMoveReviewState,
    val engineMessage: String,
) {
    fun applyScoreEstimateDisplayPlan(score: ScoreEstimateDisplayPlan): GameSessionCoreState =
        copy(
            scoreState = scoreState.applyScoreEstimateDisplayPlan(score),
            engineMessage = score.engineMessage,
        )

    fun applyScoreEstimateFailureDisplayPlan(
        failure: ScoreEstimateFailureDisplayPlan,
    ): GameSessionCoreState =
        copy(
            scoreState = scoreState.applyScoreEstimateFailureDisplayPlan(failure),
            engineMessage = failure.engineMessage,
        )

    fun applyTopMoveAnalysisFailureDisplayPlan(
        failure: TopMoveAnalysisFailureDisplayPlan,
    ): GameSessionCoreState =
        copy(
            analysisState = analysisState.applyTopMoveAnalysisFailureDisplayPlan(failure),
            engineMessage = failure.engineMessage,
        )

    fun applyEngineStartupDisplayPlan(startup: EngineStartupDisplayPlan): GameSessionCoreState =
        copy(
            scoreState = if (startup.scoreSnapshots.isNotEmpty()) {
                scoreState.replaceSnapshots(startup.scoreSnapshots)
            } else {
                scoreState
            },
            analysisState = startup.candidateText?.let { text ->
                analysisState.copy(candidateText = text)
            } ?: analysisState,
            engineMessage = startup.engineMessage,
        )

    fun applyEngineBenchmarkDisplayPlan(benchmark: EngineBenchmarkDisplayPlan): GameSessionCoreState =
        copy(
            analysisState = analysisState.copy(candidateText = benchmark.candidateText),
            engineMessage = benchmark.engineMessage,
        )

    fun applyFinalScoreDisplayPlan(final: FinalScoreDisplayPlan): GameSessionCoreState =
        copy(
            gameState = final.gameState,
            isGameEnded = true,
            scoreState = scoreState.applyFinalScoreDisplayPlan(final),
            analysisState = analysisState.copy(candidateText = final.candidateText),
            engineMessage = final.engineMessage,
        )

    fun applyEndgameFailureDisplayPlan(failure: EndgameFailureDisplayPlan): GameSessionCoreState =
        copy(
            scoreState = scoreState.applyEndgameFailureDisplayPlan(failure),
            analysisState = analysisState.copy(candidateText = failure.candidateText),
            engineMessage = failure.engineMessage,
        )

    fun applyGameSessionResetPlan(reset: GameSessionResetPlan): GameSessionCoreState =
        copy(
            gameState = reset.gameState,
            isGameEnded = false,
            runtimeState = runtimeState.nextSessionGeneration(),
            analysisState = GameSessionAnalysisState.reset(
                candidateText = reset.candidateText,
                reviewAnalysis = reset.reviewAnalysis,
            ),
            scoreState = GameSessionScoreState.reset(
                scoreText = reset.scoreText,
                scoreSnapshots = reset.scoreSnapshots,
                endgameLog = reset.endgameLog,
            ),
            moveReviewState = GameSessionMoveReviewState.reset(
                moveReviewText = reset.moveReviewText,
                lastMoveText = reset.lastMoveText,
            ),
            engineMessage = reset.engineMessage,
        )

    fun applySavedGameRestorePlan(restore: SavedGameRestorePlan): GameSessionCoreState =
        copy(
            gameState = restore.gameState,
            isGameEnded = false,
            analysisState = GameSessionAnalysisState.reset(
                candidateText = restore.candidateText,
                reviewAnalysis = restore.reviewAnalysis,
            ),
            scoreState = GameSessionScoreState.reset(
                scoreText = restore.scoreText,
                scoreSnapshots = restore.scoreSnapshots,
                endgameLog = restore.endgameLog,
            ),
            runtimeState = runtimeState.applySelection(restore.runtime).nextSessionGeneration(),
            moveReviewState = GameSessionMoveReviewState.reset(
                moveReviewText = restore.moveReviewText,
                lastMoveText = restore.lastMoveText,
            ),
            engineMessage = restore.engineMessage,
        )

    fun applyUndoLocalStatePlan(undo: UndoLocalStatePlan): GameSessionCoreState =
        copy(
            gameState = undo.gameState,
            isGameEnded = false,
            runtimeState = runtimeState.nextSessionGeneration(),
            analysisState = GameSessionAnalysisState.reset(
                candidateText = undo.candidateText,
                reviewAnalysis = undo.reviewAnalysis,
            ),
            scoreState = GameSessionScoreState.reset(
                scoreText = undo.scoreText,
                scoreSnapshots = undo.scoreSnapshots,
                endgameLog = undo.endgameLog,
            ),
            moveReviewState = moveReviewState.applyUndoLocalStatePlan(undo),
        )

    fun applyScoringRuleChangePlan(ruleChange: ScoringRuleChangePlan): GameSessionCoreState =
        copy(
            gameState = ruleChange.gameState,
            analysisState = GameSessionAnalysisState.reset(
                candidateText = ruleChange.candidateText,
                reviewAnalysis = ruleChange.reviewAnalysis,
            ),
            scoreState = GameSessionScoreState.reset(
                scoreText = ruleChange.scoreText,
                scoreSnapshots = ruleChange.scoreSnapshots,
                endgameLog = ruleChange.endgameLog,
            ),
            engineMessage = ruleChange.engineMessage ?: engineMessage,
        )

    fun applyPlayerSetupChangePlan(change: PlayerSetupChangePlan.Apply): GameSessionCoreState =
        copy(
            runtimeState = runtimeState.applySelection(change.runtime),
            analysisState = GameSessionAnalysisState.reset(
                candidateText = change.topMoveClearMessage,
                reviewAnalysis = change.reviewAnalysis,
            ),
        )

    fun applyHumanEngineSyncFailurePlan(failure: HumanEngineSyncFailurePlan): GameSessionCoreState =
        copy(
            scoreState = scoreState.replaceSnapshots(failure.scoreSnapshots),
            analysisState = analysisState.copy(candidateText = failure.candidateText),
            engineMessage = failure.engineMessage,
        )

    fun applyAutoAiTurnDisplayPlan(display: AutoAiTurnDisplayPlan): GameSessionCoreState =
        copy(
            gameState = display.gameState,
            runtimeState = runtimeState.applyAutoAiTurnDisplayPlan(display),
            analysisState = GameSessionAnalysisState.reset(
                candidateText = display.candidateText,
                reviewAnalysis = MoveAnalysisSnapshot.empty(display.gameState),
            ),
            scoreState = scoreState.applyScoreEstimateDisplayPlan(display.scoreDisplay),
            moveReviewState = moveReviewState.applyAutoAiTurnDisplayPlan(display),
            engineMessage = display.scoreDisplay.engineMessage,
        )

    fun applyAutoAiTurnFailureDisplayPlan(failure: AutoAiTurnFailureDisplayPlan): GameSessionCoreState =
        copy(
            analysisState = analysisState.copy(candidateText = failure.candidateText),
            engineMessage = failure.engineMessage,
        )

    fun applyHumanMoveLocalResult(result: HumanMoveLocalResult): GameSessionCoreState =
        copy(
            gameState = result.afterMove,
            analysisState = analysisState
                .clearTopMoveSpots()
                .clearReviewAnalysis(result.afterMove)
                .copy(lastAnalysisKey = null),
            scoreState = scoreState.copy(
                scoreText = "Score estimate not current.",
                scoreEstimate = null,
            ),
            moveReviewState = moveReviewState.applyHumanMoveLocalResult(result),
        )
}
