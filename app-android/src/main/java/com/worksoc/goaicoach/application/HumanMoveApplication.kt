package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.describe

internal data class HumanMoveLocalResult(
    val afterMove: GameState,
    val moveReview: MoveReviewResult,
    val moveReviews: List<MoveReviewMarker>,
    val lastMoveText: String,
    val capturedText: String,
    val localScoreSnapshot: ScoreSnapshot,
    val localFinalScore: FinalScoreResult?,
)

internal sealed class HumanEngineSyncDisplayPlan {
    data class FinalScore(val display: FinalScoreDisplayPlan) : HumanEngineSyncDisplayPlan()
    data class ScoreEstimate(
        val display: ScoreEstimateDisplayPlan,
        val candidateText: String,
        val nextAnalysisState: GameState,
    ) : HumanEngineSyncDisplayPlan()
    data object NoUpdate : HumanEngineSyncDisplayPlan()
}

internal data class HumanEngineSyncFailurePlan(
    val scoreSnapshots: List<ScoreSnapshot>,
    val candidateText: String,
    val engineMessage: String,
)

internal data class HumanEngineSyncRunPlan(
    val afterMove: GameState,
    val profile: EngineProfile,
    val move: Move,
    val previousReviewCandidates: List<CandidateMove>,
)

internal data class HumanEngineSyncEffectLaunchRequest(
    val effect: GameSessionEffect.SyncHumanMove,
    val operation: EngineOperationRequest,
)

internal data class HumanEngineSyncCompletionRequest(
    val result: HumanEngineSyncWorkflowResult,
    val operation: EngineOperationRequest,
    val currentState: GameState,
    val currentSessionGeneration: Long,
    val afterMove: GameState,
    val moveDescription: String,
    val localMove: HumanMoveLocalResult,
    val previousSnapshots: List<ScoreSnapshot>,
)

internal sealed class HumanEngineSyncCompletionPlan {
    data class ApplySuccess(val display: HumanEngineSyncDisplayPlan) : HumanEngineSyncCompletionPlan()
    data class ApplyFailure(val failure: HumanEngineSyncFailurePlan) : HumanEngineSyncCompletionPlan()
    data class Discard(val discard: EngineOperationResultGuard.Discard) : HumanEngineSyncCompletionPlan()
}

internal sealed class HumanEngineSyncRuntimeLogPlan {
    data class Success(val display: HumanEngineSyncDisplayPlan) : HumanEngineSyncRuntimeLogPlan()
    data class Failure(val failure: HumanEngineSyncFailurePlan) : HumanEngineSyncRuntimeLogPlan()
    data object None : HumanEngineSyncRuntimeLogPlan()
}

internal sealed class HumanEngineSyncCompletionApplyPlan {
    abstract val runtimeLogPlan: HumanEngineSyncRuntimeLogPlan

    data class ApplySuccess(
        val display: HumanEngineSyncDisplayPlan,
        override val runtimeLogPlan: HumanEngineSyncRuntimeLogPlan,
    ) : HumanEngineSyncCompletionApplyPlan()

    data class ApplyFailure(
        val failure: HumanEngineSyncFailurePlan,
        override val runtimeLogPlan: HumanEngineSyncRuntimeLogPlan,
    ) : HumanEngineSyncCompletionApplyPlan()

    data class Discard(
        val discard: EngineOperationResultGuard.Discard,
        override val runtimeLogPlan: HumanEngineSyncRuntimeLogPlan,
    ) : HumanEngineSyncCompletionApplyPlan()
}

internal sealed class HumanEngineSyncWorkflowResult {
    data class Success(val result: LocalEngineMoveResult) : HumanEngineSyncWorkflowResult()
    data class Failure(val error: Throwable) : HumanEngineSyncWorkflowResult()
}

internal fun applyHumanMoveLocally(
    beforeMove: GameState,
    move: Move,
    reviewAnalysis: MoveAnalysisSnapshot,
    previousMoveReviews: List<MoveReviewMarker>,
): Result<HumanMoveLocalResult> =
    runCatching {
        val afterMove = MatchReferee.playOrThrow(beforeMove, move)
        val moveReview = buildMoveReview(
            move = move,
            analysis = reviewAnalysis,
            boardSize = beforeMove.boardSize,
            moveNumber = afterMove.moves.size,
        )
        HumanMoveLocalResult(
            afterMove = afterMove,
            moveReview = moveReview,
            moveReviews = previousMoveReviews.withReviewMarker(moveReview.marker),
            lastMoveText = move.describe(beforeMove.boardSize),
            capturedText = "Captured: Black ${afterMove.capturedBy(StoneColor.Black)} / White ${afterMove.capturedBy(StoneColor.White)}",
            localScoreSnapshot = localScoreSnapshot(afterMove),
            localFinalScore = MatchReferee.localFinalScoreIfGameEndedByPasses(afterMove),
        )
    }

internal fun buildHumanEngineSyncSuccessPlan(
    afterMove: GameState,
    moveDescription: String,
    result: LocalEngineMoveResult,
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
): HumanEngineSyncDisplayPlan {
    result.endgame?.let { endgame ->
        return HumanEngineSyncDisplayPlan.FinalScore(
            buildResolvedEndgameDisplayPlan(
                source = "human-engine-dead-stone-cleanup",
                originalState = afterMove,
                resolution = endgame,
                previousSnapshots = previousSnapshots,
                engineMessagePrefix = "Game ended after two passes.",
            ),
        )
    }

    result.estimate?.let { estimate ->
        return HumanEngineSyncDisplayPlan.ScoreEstimate(
            display = buildEngineEstimateDisplayPlan(
                state = afterMove,
                estimate = estimate,
                previousSnapshots = previousSnapshots,
                engineMessage = "Human move accepted and engine analysis synced: $moveDescription.",
            ),
            candidateText = localMove.capturedText,
            nextAnalysisState = afterMove,
        )
    }

    return HumanEngineSyncDisplayPlan.NoUpdate
}

internal fun buildHumanEngineSyncFailurePlan(
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
    errorMessage: String?,
): HumanEngineSyncFailurePlan =
    HumanEngineSyncFailurePlan(
        scoreSnapshots = ScoreTimeline.record(previousSnapshots, localMove.localScoreSnapshot),
        candidateText = localMove.capturedText,
        engineMessage = errorMessage ?: "Human move accepted, but engine sync failed.",
    )

internal fun buildHumanEngineSyncSuccessCompletionPlan(
    operation: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
    afterMove: GameState,
    moveDescription: String,
    result: LocalEngineMoveResult,
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
): HumanEngineSyncCompletionPlan =
    when (
        val applyPlan = buildEngineOperationApplyPlan(
            request = operation,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationApplyPlan.Apply ->
            HumanEngineSyncCompletionPlan.ApplySuccess(
                buildHumanEngineSyncSuccessPlan(
                    afterMove = afterMove,
                    moveDescription = moveDescription,
                    result = result,
                    localMove = localMove,
                    previousSnapshots = previousSnapshots,
                ),
            )

        is EngineOperationApplyPlan.Discard ->
            HumanEngineSyncCompletionPlan.Discard(applyPlan.discard)
    }

internal fun buildHumanEngineSyncFailureCompletionPlan(
    operation: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
    errorMessage: String?,
): HumanEngineSyncCompletionPlan =
    when (
        val applyPlan = buildEngineOperationApplyPlan(
            request = operation,
            currentState = currentState,
            currentSessionGeneration = currentSessionGeneration,
        )
    ) {
        EngineOperationApplyPlan.Apply ->
            HumanEngineSyncCompletionPlan.ApplyFailure(
                buildHumanEngineSyncFailurePlan(
                    localMove = localMove,
                    previousSnapshots = previousSnapshots,
                    errorMessage = errorMessage,
                ),
            )

        is EngineOperationApplyPlan.Discard ->
            HumanEngineSyncCompletionPlan.Discard(applyPlan.discard)
    }

internal fun buildHumanEngineSyncCompletionPlan(
    result: HumanEngineSyncWorkflowResult,
    operation: EngineOperationRequest,
    currentState: GameState,
    currentSessionGeneration: Long,
    afterMove: GameState,
    moveDescription: String,
    localMove: HumanMoveLocalResult,
    previousSnapshots: List<ScoreSnapshot>,
): HumanEngineSyncCompletionPlan =
    when (result) {
        is HumanEngineSyncWorkflowResult.Success ->
            buildHumanEngineSyncSuccessCompletionPlan(
                operation = operation,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                afterMove = afterMove,
                moveDescription = moveDescription,
                result = result.result,
                localMove = localMove,
                previousSnapshots = previousSnapshots,
            )

        is HumanEngineSyncWorkflowResult.Failure ->
            buildHumanEngineSyncFailureCompletionPlan(
                operation = operation,
                currentState = currentState,
                currentSessionGeneration = currentSessionGeneration,
                localMove = localMove,
                previousSnapshots = previousSnapshots,
                errorMessage = result.error.message,
            )
    }

internal fun buildHumanEngineSyncCompletionPlan(
    request: HumanEngineSyncCompletionRequest,
): HumanEngineSyncCompletionPlan =
    buildHumanEngineSyncCompletionPlan(
        result = request.result,
        operation = request.operation,
        currentState = request.currentState,
        currentSessionGeneration = request.currentSessionGeneration,
        afterMove = request.afterMove,
        moveDescription = request.moveDescription,
        localMove = request.localMove,
        previousSnapshots = request.previousSnapshots,
    )

internal fun HumanEngineSyncCompletionPlan.toRuntimeLogPlan(): HumanEngineSyncRuntimeLogPlan =
    when (this) {
        is HumanEngineSyncCompletionPlan.ApplySuccess ->
            HumanEngineSyncRuntimeLogPlan.Success(display)

        is HumanEngineSyncCompletionPlan.ApplyFailure ->
            HumanEngineSyncRuntimeLogPlan.Failure(failure)

        is HumanEngineSyncCompletionPlan.Discard ->
            HumanEngineSyncRuntimeLogPlan.None
    }

internal fun HumanEngineSyncCompletionPlan.toApplyPlan(): HumanEngineSyncCompletionApplyPlan =
    when (this) {
        is HumanEngineSyncCompletionPlan.ApplySuccess ->
            HumanEngineSyncCompletionApplyPlan.ApplySuccess(
                display = display,
                runtimeLogPlan = toRuntimeLogPlan(),
            )

        is HumanEngineSyncCompletionPlan.ApplyFailure ->
            HumanEngineSyncCompletionApplyPlan.ApplyFailure(
                failure = failure,
                runtimeLogPlan = toRuntimeLogPlan(),
            )

        is HumanEngineSyncCompletionPlan.Discard ->
            HumanEngineSyncCompletionApplyPlan.Discard(
                discard = discard,
                runtimeLogPlan = toRuntimeLogPlan(),
            )
    }

internal suspend fun EngineSessionClient.runHumanEngineSyncEffect(
    effect: GameSessionEffect.SyncHumanMove,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): LocalEngineMoveResult {
    val plan = effect.plan
    return runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.HumanMoveSync,
            state = plan.afterMove,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = plan.profile.analysisLimit.timeMillis,
                label = "${plan.profile.difficulty.label}:${plan.profile.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        syncAfterHumanMove(
            afterMove = plan.afterMove,
            profile = plan.profile,
            move = plan.move,
            previousReviewCandidates = plan.previousReviewCandidates,
        )
    }
}

internal suspend fun EngineSessionClient.runHumanEngineSyncWorkflowResult(
    effect: GameSessionEffect.SyncHumanMove,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): HumanEngineSyncWorkflowResult =
    runCatching {
        runHumanEngineSyncEffect(
            effect = effect,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { result -> HumanEngineSyncWorkflowResult.Success(result) },
        onFailure = { error -> HumanEngineSyncWorkflowResult.Failure(error) },
    )

internal suspend fun EngineSessionClient.runHumanEngineSyncWorkflowResult(
    request: HumanEngineSyncEffectLaunchRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): HumanEngineSyncWorkflowResult =
    runHumanEngineSyncWorkflowResult(
        effect = request.effect,
        operationRequest = request.operation,
        diagnosticEventLog = diagnosticEventLog,
    )
