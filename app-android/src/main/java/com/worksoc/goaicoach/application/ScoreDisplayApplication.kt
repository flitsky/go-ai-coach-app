package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.describe

internal data class ScoreEstimateDisplayPlan(
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val engineMessage: String,
)

internal data class ScoreEstimateFailureDisplayPlan(
    val engineMessage: String,
)

internal data class FinalScoreDisplayPlan(
    val gameState: GameState,
    val scoreText: String,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val engineMessage: String,
    val candidateText: String,
    val endgameTimingSummary: String? = null,
)

internal data class EndgameFailureDisplayPlan(
    val endgameLog: String,
    val engineMessage: String,
    val candidateText: String,
)

internal sealed class ScoreEstimateRequestPlan {
    data class ShowMessage(val message: String) : ScoreEstimateRequestPlan()
    data class ShowLocalEstimate(val display: ScoreEstimateDisplayPlan) : ScoreEstimateRequestPlan()
    data class RequestEngineEstimate(
        val state: GameState,
        val profile: EngineProfile,
        val syncFirst: Boolean,
    ) : ScoreEstimateRequestPlan()
}

internal data class ScoreEstimateOperationToken(
    val position: PositionScopedOperationToken,
)

internal fun scoreEstimateOperationToken(
    request: ScoreEstimateRequestPlan.RequestEngineEstimate,
): ScoreEstimateOperationToken =
    ScoreEstimateOperationToken(
        position = positionScopedOperationToken(
            kind = "score_estimate",
            state = request.state,
        ),
    )

internal fun evaluateScoreEstimateResultGuard(
    token: ScoreEstimateOperationToken,
    currentState: GameState,
): EngineOperationResultGuard =
    evaluatePositionScopedResultGuard(
        token = token.position,
        currentState = currentState,
    )

internal fun buildScoreEstimateFailureDisplayPlan(error: Throwable): ScoreEstimateFailureDisplayPlan =
    ScoreEstimateFailureDisplayPlan(
        engineMessage = error.message ?: "Score estimate failed.",
    )

internal fun buildScoreEstimateRequestPlan(
    state: GameState,
    previousSnapshots: List<ScoreSnapshot>,
    isEngineReady: Boolean,
    isEngineBusy: Boolean,
    matchMode: MatchMode,
    engineProfile: EngineProfile,
): ScoreEstimateRequestPlan {
    if (isEngineBusy) {
        return ScoreEstimateRequestPlan.ShowMessage("Engine is busy. Estimate after the current response.")
    }

    if (matchMode == MatchMode.LocalTwoPlayer && !isEngineReady) {
        return ScoreEstimateRequestPlan.ShowLocalEstimate(
            buildLocalScoreEstimateDisplayPlan(
                state = state,
                previousSnapshots = previousSnapshots,
                engineMessage = "Local ${state.ruleset.scoringLabel} estimate refreshed.",
            ),
        )
    }

    if (!isEngineReady) {
        return ScoreEstimateRequestPlan.ShowMessage("Engine is not ready.")
    }

    return ScoreEstimateRequestPlan.RequestEngineEstimate(
        state = state,
        profile = engineProfile,
        syncFirst = matchMode == MatchMode.LocalTwoPlayer,
    )
}

internal fun buildEngineEstimateDisplayPlan(
    state: GameState,
    estimate: ScoreEstimate,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String = estimate.status.message,
    trimAfterMove: Boolean = false,
): ScoreEstimateDisplayPlan {
    val snapshots = if (trimAfterMove) {
        ScoreTimeline.trimAfter(previousSnapshots, state.moves.size)
    } else {
        previousSnapshots
    }
    return ScoreEstimateDisplayPlan(
        scoreText = estimate.toDisplayText(),
        scoreEstimate = estimate,
        scoreSnapshots = ScoreTimeline.record(
            snapshots,
            ScoreTimeline.fromEstimate(state.moves.size, estimate),
        ),
        engineMessage = engineMessage,
    )
}

internal suspend fun EngineSessionClient.runScoreEstimateDisplayPlan(
    request: ScoreEstimateRequestPlan.RequestEngineEstimate,
    previousSnapshots: List<ScoreSnapshot>,
): ScoreEstimateDisplayPlan {
    val estimate = estimateScoreForState(
        state = request.state,
        profile = request.profile,
        syncFirst = request.syncFirst,
    )
    return buildEngineEstimateDisplayPlan(
        state = request.state,
        estimate = estimate,
        previousSnapshots = previousSnapshots,
    )
}

internal suspend fun EngineSessionClient.runScoringRuleSyncDisplayPlan(
    state: GameState,
    profile: EngineProfile,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String,
): ScoreEstimateDisplayPlan {
    val estimate = syncAndEstimateGraphScore(state, profile)
    return buildEngineEstimateDisplayPlan(
        state = state,
        estimate = estimate,
        previousSnapshots = previousSnapshots,
        engineMessage = engineMessage,
        trimAfterMove = true,
    )
}

internal suspend fun EngineSessionClient.runRestoredGameSyncDisplayPlan(
    state: GameState,
    profile: EngineProfile,
): ScoreEstimateDisplayPlan {
    val estimate = configureSyncAndEstimateGraphScore(state, profile)
    return buildEngineEstimateDisplayPlan(
        state = state,
        estimate = estimate,
        previousSnapshots = emptyList(),
        engineMessage = "Previous game restored and engine state synchronized.",
    )
}

internal fun buildLocalScoreEstimateDisplayPlan(
    state: GameState,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String,
): ScoreEstimateDisplayPlan =
    ScoreEstimateDisplayPlan(
        scoreText = BoardScorer.score(state).toDisplayText(),
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(previousSnapshots, localScoreSnapshot(state)),
        engineMessage = engineMessage,
    )

internal fun buildLocalFinalScoreDisplayPlan(
    source: String,
    state: GameState,
    finalScore: FinalScoreResult,
    previousSnapshots: List<ScoreSnapshot>,
    detail: String,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayPlan {
    val finalScoreText = finalScore.toDisplayText()
    return FinalScoreDisplayPlan(
        gameState = state,
        scoreText = finalScoreText,
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(
            previousSnapshots,
            ScoreTimeline.fromFinalScore(
                moveNumber = state.moves.size,
                finalScore = finalScore,
                source = ScoreSnapshotSource.FinalScore,
            ),
        ),
        endgameLog = buildEndgameLog(
            source = source,
            state = state,
            finalScoreText = finalScoreText,
            detail = detail,
        ),
        engineMessage = engineMessage,
        candidateText = candidateText,
    )
}

internal fun buildResolvedEndgameDisplayPlan(
    source: String,
    originalState: GameState,
    resolution: AiEndgameResolution,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessagePrefix: String? = null,
): FinalScoreDisplayPlan {
    val cleanupState = resolution.cleanup.state
    val finalScoreText = resolution.finalScore.toDisplayText()
    val resolvedMessage = resolution.toEngineMessage()
    return FinalScoreDisplayPlan(
        gameState = cleanupState,
        scoreText = finalScoreText,
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(
            previousSnapshots,
            ScoreTimeline.fromFinalScore(
                moveNumber = cleanupState.moves.size,
                finalScore = resolution.finalScore,
                source = ScoreSnapshotSource.FinalScore,
            ),
        ),
        endgameLog = buildEndgameLog(
            source = source,
            state = cleanupState,
            finalScoreText = finalScoreText,
            detail = resolution.toLogDetail(originalState),
        ),
        engineMessage = listOfNotNull(engineMessagePrefix, resolvedMessage).joinToString("\n"),
        candidateText = resolution.toCandidateText(),
        endgameTimingSummary = resolution.timings.summary(),
    )
}

internal fun buildEndgameFailureDisplayPlan(
    source: String,
    state: GameState,
    errorMessage: String,
    engineMessagePrefix: String? = null,
): EndgameFailureDisplayPlan {
    val finalScoreText = "Final score failed: $errorMessage"
    return EndgameFailureDisplayPlan(
        endgameLog = buildEndgameLog(
            source = source,
            state = state,
            finalScoreText = finalScoreText,
            detail = "lastMove=${state.moves.lastOrNull()?.describe(state.boardSize) ?: "None"}",
        ),
        engineMessage = listOfNotNull(engineMessagePrefix, finalScoreText).joinToString("\n"),
        candidateText = "Game ended after two passes, but final score failed.",
    )
}
