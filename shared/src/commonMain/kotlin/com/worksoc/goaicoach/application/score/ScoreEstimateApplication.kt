package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.shared.engine.engineOperationRequest
import com.worksoc.goaicoach.application.engine.operation.evaluateEngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.analysis.toDisplayText
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.BoardScorer
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline

fun scoreEstimateOperationToken(
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
fun evaluateScoreEstimateResultGuard(
    token: ScoreEstimateOperationToken,
    currentState: GameState,
    currentSessionGeneration: Long = 0L,
): EngineOperationResultGuard =
    evaluateEngineOperationResultGuard(
        request = token.operation,
        currentState = currentState,
        currentSessionGeneration = currentSessionGeneration,
    )
fun buildScoreEstimateFailureDisplayPlan(error: Throwable): ScoreEstimateFailureDisplayPlan =
    ScoreEstimateFailureDisplayPlan(
        engineMessage = error.message ?: "Score estimate failed.",
    )
fun buildScoreEstimateCompletionPlan(
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
fun ScoreEstimateCompletionPlan.toApplyPlan(): ScoreEstimateCompletionApplyPlan =
    when (this) {
        is ScoreEstimateCompletionPlan.ApplySuccess ->
            ScoreEstimateCompletionApplyPlan.ApplySuccess(display)

        is ScoreEstimateCompletionPlan.ApplyFailure ->
            ScoreEstimateCompletionApplyPlan.ApplyFailure(failure)

        is ScoreEstimateCompletionPlan.Discard ->
            ScoreEstimateCompletionApplyPlan.Discard(discard)
    }
fun buildScoreEstimateRequestPlan(
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
fun ScoreEstimateRequestPlan.toScoreEstimateLaunchStateUpdate(): ScoreEstimateLaunchStateUpdate =
    when (this) {
        is ScoreEstimateRequestPlan.ShowMessage ->
            ScoreEstimateLaunchStateUpdate(engineMessage = message)
        is ScoreEstimateRequestPlan.ShowLocalEstimate ->
            ScoreEstimateLaunchStateUpdate(display = display)
        is ScoreEstimateRequestPlan.RequestEngineEstimate ->
            ScoreEstimateLaunchStateUpdate(effect = GameSessionEffect.RunScoreEstimate(this))
    }
fun buildEngineScoreEstimateStateResult(
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
fun ScoreEstimateStateResult.toScoreEstimateDisplayPlan(
    scoreText: String,
    engineMessage: String,
): ScoreEstimateDisplayPlan =
    ScoreEstimateDisplayPlan(
        scoreText = scoreText,
        scoreEstimate = scoreEstimate,
        scoreSnapshots = scoreSnapshots,
        engineMessage = engineMessage,
    )

fun buildEngineEstimateDisplayPlan(
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

fun buildLocalScoreEstimateDisplayPlan(
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
