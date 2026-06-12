package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
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
        playerSetup.seatFor(gameState.nextPlayer).isAi

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
        playerSetup.seatFor(targetState.nextPlayer).isHuman

internal fun autoAiTurnDelayMillis(
    playerSetup: PlayerSetup,
    setting: AutoPlayDelaySetting,
): Long =
    if (playerSetup.isAutoPlay()) setting.millis else 0L

internal sealed class AutoAiTurnRequestPlan {
    data object Skip : AutoAiTurnRequestPlan()
    data class Schedule(
        val delayMillis: Long,
    ) : AutoAiTurnRequestPlan()
}

internal fun buildAutoAiTurnRequestPlan(
    isGameEnded: Boolean,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    isAutoAiTurnPending: Boolean,
    shouldShowResumePrompt: Boolean,
    playerSetup: PlayerSetup,
    gameState: GameState,
    autoPlayDelaySetting: AutoPlayDelaySetting,
): AutoAiTurnRequestPlan {
    if (isAutoAiTurnPending) {
        return AutoAiTurnRequestPlan.Skip
    }
    if (
        !shouldRequestAiTurn(
            isGameEnded = isGameEnded,
            isEngineReady = isEngineReady,
            isEngineBusy = isEngineBusy,
            shouldShowResumePrompt = shouldShowResumePrompt,
            playerSetup = playerSetup,
            gameState = gameState,
        )
    ) {
        return AutoAiTurnRequestPlan.Skip
    }

    return AutoAiTurnRequestPlan.Schedule(
        delayMillis = autoAiTurnDelayMillis(playerSetup, autoPlayDelaySetting),
    )
}

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
    val shouldResolveEndgame = MatchReferee.shouldResolveEndgame(nextState)
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

internal suspend fun EngineSessionClient.runAutoAiTurnDisplayPlan(
    currentState: GameState,
    playLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
    searchTimeSettings: SearchTimeSettings,
    isolateSearchCache: Boolean,
    previousSnapshots: List<ScoreSnapshot>,
    previousReviewCandidates: List<CandidateMove>,
): AutoAiTurnDisplayPlan {
    val result = runAutoAiTurn(
        currentState = currentState,
        playLevel = playLevel,
        currentProfile = currentProfile,
        searchTimeSettings = searchTimeSettings,
        isolateSearchCache = isolateSearchCache,
    )
    return buildAutoAiTurnDisplayPlan(
        result = result,
        previousSnapshots = previousSnapshots,
        previousReviewCandidates = previousReviewCandidates,
    )
}
