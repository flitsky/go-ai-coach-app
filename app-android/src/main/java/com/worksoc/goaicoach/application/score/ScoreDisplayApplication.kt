package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.endgame.buildEndgameLog
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.evaluateEngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.analysis.toDisplayText
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe
import kotlin.math.roundToInt

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
internal fun ScoreEstimateCompletionPlan.toApplyPlan(): ScoreEstimateCompletionApplyPlan =
    when (this) {
        is ScoreEstimateCompletionPlan.ApplySuccess ->
            ScoreEstimateCompletionApplyPlan.ApplySuccess(display)

        is ScoreEstimateCompletionPlan.ApplyFailure ->
            ScoreEstimateCompletionApplyPlan.ApplyFailure(failure)

        is ScoreEstimateCompletionPlan.Discard ->
            ScoreEstimateCompletionApplyPlan.Discard(discard)
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
        judgement = judgement,
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
        judgement = buildFinalScoreJudgement(
            state = state,
            displayScore = finalScore,
            localScore = finalScore,
            cleanup = DeadStoneCleanupResult(state = state, removedStones = emptyList()),
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
        scoreEstimate = resolution.engineScoreEstimate,
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
        judgement = buildFinalScoreJudgement(
            state = cleanupState,
            displayScore = resolution.finalScore,
            localScore = resolution.localFinalScore,
            cleanup = resolution.cleanup,
        ),
    )
}

private fun buildFinalScoreJudgement(
    state: GameState,
    displayScore: FinalScoreResult,
    localScore: FinalScoreResult,
    cleanup: DeadStoneCleanupResult,
): FinalScoreJudgement {
    val removedBlack = cleanup.removedStones.count { it.color == StoneColor.Black }
    val removedWhite = cleanup.removedStones.count { it.color == StoneColor.White }
    val isEstimatedDisplay = displayScore.rawScore.endsWith("?")
    return FinalScoreJudgement(
        winner = displayScore.winner,
        margin = displayScore.margin,
        ruleset = state.ruleset,
        isEstimatedDisplay = isEstimatedDisplay,
        removedBlack = removedBlack,
        removedWhite = removedWhite,
        blackArea = localScore.blackArea,
        whiteAreaWithKomi = localScore.whiteAreaWithKomi,
        capturedByBlack = state.capturedBy(StoneColor.Black),
        capturedByWhite = state.capturedBy(StoneColor.White),
        komi = localScore.komi,
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
    val text = buildEndgameFailureDisplayText(
        errorMessage = errorMessage,
        engineMessagePrefix = engineMessagePrefix,
    )
    return EndgameFailureDisplayPlan(
        endgameLog = buildEndgameLog(
            source = source,
            state = state,
            finalScoreText = text.finalScoreText,
            detail = "lastMove=${state.moves.lastOrNull()?.describe(state.boardSize) ?: "None"}",
        ),
        engineMessage = text.engineMessage,
        candidateText = text.candidateText,
    )
}
