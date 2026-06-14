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

internal data class ScoreEstimateStateResult(
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
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

internal data class FinalScoreStateResult(
    val gameState: GameState,
    val scoreEstimate: ScoreEstimate?,
    val scoreSnapshots: List<ScoreSnapshot>,
    val endgameLog: String,
    val endgameTimingSummary: String? = null,
)

internal data class FinalScoreDisplayText(
    val scoreText: String,
    val engineMessage: String,
    val candidateText: String,
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

internal data class ScoreEstimateLaunchStateUpdate(
    val engineMessage: String? = null,
    val display: ScoreEstimateDisplayPlan? = null,
    val effect: GameSessionEffect.RunScoreEstimate? = null,
)

internal data class ScoreEstimateOperationToken(
    val operation: EngineOperationRequest,
)

internal sealed class ScoreEstimateWorkflowResult {
    data class Success(val display: ScoreEstimateDisplayPlan) : ScoreEstimateWorkflowResult()
    data class Failure(val error: Throwable) : ScoreEstimateWorkflowResult()
}

internal sealed class ScoreEstimateCompletionPlan {
    data class ApplySuccess(val display: ScoreEstimateDisplayPlan) : ScoreEstimateCompletionPlan()
    data class ApplyFailure(val failure: ScoreEstimateFailureDisplayPlan) : ScoreEstimateCompletionPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : ScoreEstimateCompletionPlan()
}

internal fun scoreEstimateOperationToken(
    request: ScoreEstimateRequestPlan.RequestEngineEstimate,
    sessionGeneration: Long = 0L,
): ScoreEstimateOperationToken =
    ScoreEstimateOperationToken(
        operation = engineOperationRequest(
            kind = EngineOperationKind.ScoreEstimate,
            state = request.state,
            sessionGeneration = sessionGeneration,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = request.profile.analysisLimit.timeMillis,
                label = "${request.profile.difficulty.label}:${request.profile.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        ),
    )

internal fun evaluateScoreEstimateResultGuard(
    token: ScoreEstimateOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long = 0L,
): EngineOperationResultGuard =
    evaluateEngineOperationResultGuard(
        request = token.operation,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    )

internal fun buildScoreEstimateFailureDisplayPlan(error: Throwable): ScoreEstimateFailureDisplayPlan =
    ScoreEstimateFailureDisplayPlan(
        engineMessage = error.message ?: "Score estimate failed.",
    )

internal fun buildScoreEstimateCompletionPlan(
    result: ScoreEstimateWorkflowResult,
    token: ScoreEstimateOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long,
): ScoreEstimateCompletionPlan =
    when (
        val guard = evaluateScoreEstimateResultGuard(
            token = token,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationResultGuard.Apply ->
            when (result) {
                is ScoreEstimateWorkflowResult.Success ->
                    ScoreEstimateCompletionPlan.ApplySuccess(result.display)

                is ScoreEstimateWorkflowResult.Failure ->
                    ScoreEstimateCompletionPlan.ApplyFailure(
                        buildScoreEstimateFailureDisplayPlan(result.error),
                    )
            }

        is EngineOperationResultGuard.Discard ->
            ScoreEstimateCompletionPlan.Discard(guard)
    }

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

internal fun ScoreEstimateRequestPlan.toScoreEstimateLaunchStateUpdate(): ScoreEstimateLaunchStateUpdate =
    when (this) {
        is ScoreEstimateRequestPlan.ShowMessage ->
            ScoreEstimateLaunchStateUpdate(engineMessage = message)
        is ScoreEstimateRequestPlan.ShowLocalEstimate ->
            ScoreEstimateLaunchStateUpdate(display = display)
        is ScoreEstimateRequestPlan.RequestEngineEstimate ->
            ScoreEstimateLaunchStateUpdate(effect = GameSessionEffect.RunScoreEstimate(this))
    }

internal fun buildEngineScoreEstimateStateResult(
    state: GameState,
    estimate: ScoreEstimate,
    previousSnapshots: List<ScoreSnapshot>,
    trimAfterMove: Boolean = false,
): ScoreEstimateStateResult {
    val snapshots = if (trimAfterMove) {
        ScoreTimeline.trimAfter(previousSnapshots, state.moves.size)
    } else {
        previousSnapshots
    }
    return ScoreEstimateStateResult(
        scoreEstimate = estimate,
        scoreSnapshots = ScoreTimeline.record(
            snapshots,
            ScoreTimeline.fromEstimate(state.moves.size, estimate),
        ),
    )
}

internal fun buildLocalScoreEstimateStateResult(
    state: GameState,
    previousSnapshots: List<ScoreSnapshot>,
): ScoreEstimateStateResult =
    ScoreEstimateStateResult(
        scoreEstimate = null,
        scoreSnapshots = ScoreTimeline.record(previousSnapshots, localScoreSnapshot(state)),
    )

internal fun ScoreEstimateStateResult.toScoreEstimateDisplayPlan(
    scoreText: String,
    engineMessage: String,
): ScoreEstimateDisplayPlan =
    ScoreEstimateDisplayPlan(
        scoreText = scoreText,
        scoreEstimate = scoreEstimate,
        scoreSnapshots = scoreSnapshots,
        engineMessage = engineMessage,
    )

internal fun buildEngineEstimateDisplayPlan(
    state: GameState,
    estimate: ScoreEstimate,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String = estimate.status.message,
    trimAfterMove: Boolean = false,
): ScoreEstimateDisplayPlan =
    buildEngineScoreEstimateStateResult(
        state = state,
        estimate = estimate,
        previousSnapshots = previousSnapshots,
        trimAfterMove = trimAfterMove,
    ).toScoreEstimateDisplayPlan(
        scoreText = estimate.toDisplayText(),
        engineMessage = engineMessage,
    )

internal fun buildLocalScoreEstimateDisplayPlan(
    state: GameState,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String,
): ScoreEstimateDisplayPlan =
    buildLocalScoreEstimateStateResult(
        state = state,
        previousSnapshots = previousSnapshots,
    ).toScoreEstimateDisplayPlan(
        scoreText = BoardScorer.score(state).toDisplayText(),
        engineMessage = engineMessage,
    )

internal fun FinalScoreStateResult.toFinalScoreDisplayPlan(
    text: FinalScoreDisplayText,
): FinalScoreDisplayPlan =
    toFinalScoreDisplayPlan(
        scoreText = text.scoreText,
        engineMessage = text.engineMessage,
        candidateText = text.candidateText,
    )

internal fun FinalScoreStateResult.toFinalScoreDisplayPlan(
    scoreText: String,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayPlan =
    FinalScoreDisplayPlan(
        gameState = gameState,
        scoreText = scoreText,
        scoreEstimate = scoreEstimate,
        scoreSnapshots = scoreSnapshots,
        endgameLog = endgameLog,
        engineMessage = engineMessage,
        candidateText = candidateText,
        endgameTimingSummary = endgameTimingSummary,
    )

internal fun buildLocalFinalScoreDisplayText(
    finalScore: FinalScoreResult,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayText =
    FinalScoreDisplayText(
        scoreText = finalScore.toDisplayText(),
        engineMessage = engineMessage,
        candidateText = candidateText,
    )

internal fun buildLocalFinalScoreStateResult(
    source: String,
    state: GameState,
    finalScore: FinalScoreResult,
    previousSnapshots: List<ScoreSnapshot>,
    detail: String,
): FinalScoreStateResult {
    val finalScoreText = finalScore.toDisplayText()
    return FinalScoreStateResult(
        gameState = state,
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
    )
}

internal fun buildLocalFinalScoreDisplayPlan(
    source: String,
    state: GameState,
    finalScore: FinalScoreResult,
    previousSnapshots: List<ScoreSnapshot>,
    detail: String,
    engineMessage: String,
    candidateText: String,
): FinalScoreDisplayPlan {
    val text = buildLocalFinalScoreDisplayText(
        finalScore = finalScore,
        engineMessage = engineMessage,
        candidateText = candidateText,
    )
    return buildLocalFinalScoreStateResult(
        source = source,
        state = state,
        finalScore = finalScore,
        previousSnapshots = previousSnapshots,
        detail = detail,
    ).toFinalScoreDisplayPlan(text)
}

internal fun buildResolvedEndgameDisplayText(
    resolution: AiEndgameResolution,
    engineMessagePrefix: String? = null,
): FinalScoreDisplayText =
    FinalScoreDisplayText(
        scoreText = resolution.finalScore.toDisplayText(),
        engineMessage = listOfNotNull(engineMessagePrefix, resolution.toEngineMessage()).joinToString("\n"),
        candidateText = resolution.toCandidateText(),
    )

internal fun buildResolvedEndgameStateResult(
    source: String,
    originalState: GameState,
    resolution: AiEndgameResolution,
    previousSnapshots: List<ScoreSnapshot>,
): FinalScoreStateResult {
    val cleanupState = resolution.cleanup.state
    val finalScoreText = resolution.finalScore.toDisplayText()
    return FinalScoreStateResult(
        gameState = cleanupState,
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
        endgameTimingSummary = resolution.timings.summary(),
    )
}

internal fun buildResolvedEndgameDisplayPlan(
    source: String,
    originalState: GameState,
    resolution: AiEndgameResolution,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessagePrefix: String? = null,
): FinalScoreDisplayPlan {
    val text = buildResolvedEndgameDisplayText(
        resolution = resolution,
        engineMessagePrefix = engineMessagePrefix,
    )
    return buildResolvedEndgameStateResult(
        source = source,
        originalState = originalState,
        resolution = resolution,
        previousSnapshots = previousSnapshots,
    ).toFinalScoreDisplayPlan(text)
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
