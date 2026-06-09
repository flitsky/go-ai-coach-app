package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline

internal fun shouldRequestAiTurn(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    gameState: GameState,
): Boolean =
    !isGameEnded &&
        isEngineReady &&
        !isEngineBusy &&
        !shouldShowResumePrompt &&
        playerSetup.sideFor(gameState.nextPlayer).controller == SeatController.Ai

internal fun shouldRequestTopMoveAnalysis(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    targetState: GameState,
): Boolean =
    !isGameEnded &&
        isEngineReady &&
        !isEngineBusy &&
        !shouldShowResumePrompt &&
        playerSetup.sideFor(targetState.nextPlayer).controller == SeatController.Human

internal data class AutoAiTurnDisplayPlan(
    val playLevel: PlayLevelSetting,
    val profile: EngineProfile,
    val analysisPreset: AnalysisPreset,
    val gameState: GameState,
    val turnEngineMessage: String,
    val candidateText: String,
    val lastMoveText: String,
    val scoreDisplay: ScoreEstimateDisplayPlan,
    val shouldResolveEndgame: Boolean,
    val endgamePrePassCandidates: List<CandidateMove>,
    val nextAnalysisState: GameState?,
)

internal fun buildAutoAiTurnDisplayPlan(
    result: AutoAiTurnResult,
    previousSnapshots: List<ScoreSnapshot>,
    previousReviewCandidates: List<CandidateMove>,
): AutoAiTurnDisplayPlan {
    val outcome = result.turnOutcome
    val nextState = outcome.gameState
    val shouldResolveEndgame = nextState.hasConsecutivePasses() || nextState.isBoardFull()
    val scoreDisplay = result.scoreEstimate?.let { estimate ->
        buildEngineEstimateDisplayPlan(
            state = nextState,
            estimate = estimate,
            previousSnapshots = previousSnapshots,
        )
    } ?: ScoreEstimateDisplayPlan(
        scoreText = "Score estimate not current.",
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(
            previousSnapshots,
            localScoreSnapshot(nextState),
        ),
        engineMessage = outcome.engineMessage,
    )

    return AutoAiTurnDisplayPlan(
        playLevel = result.playLevel,
        profile = result.profile,
        analysisPreset = result.playLevel.analysisPreset,
        gameState = nextState,
        turnEngineMessage = outcome.engineMessage,
        candidateText = outcome.candidateText,
        lastMoveText = outcome.lastMoveText,
        scoreDisplay = scoreDisplay,
        shouldResolveEndgame = shouldResolveEndgame,
        endgamePrePassCandidates = if (nextState.moves.lastOrNull() is Move.Pass) {
            previousReviewCandidates
        } else {
            emptyList()
        },
        nextAnalysisState = nextState.takeUnless { shouldResolveEndgame },
    )
}
