package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.AiEndgameResolution
import com.worksoc.goaicoach.application.AutoAiTurnResult
import com.worksoc.goaicoach.application.EngineSessionClient
import com.worksoc.goaicoach.application.session.GameSessionEffect
import com.worksoc.goaicoach.application.localScoreSnapshot
import com.worksoc.goaicoach.application.score.*
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreTimeline
import com.worksoc.goaicoach.shared.aiMoveAnalysisLimitWith
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

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

internal sealed class AutoAiTurnFollowUpPlan {
    data object None : AutoAiTurnFollowUpPlan()
    data class RequestTopMoveAnalysis(
        val targetState: GameState,
    ) : AutoAiTurnFollowUpPlan()
}

internal data class AutoAiTurnFollowUpRequest(
    val targetState: GameState,
    val automatic: Boolean,
    val deep: Boolean,
)

internal fun buildAutoAiTurnFollowUpPlan(display: AutoAiTurnDisplayPlan): AutoAiTurnFollowUpPlan =
    display.nextAnalysisState?.let { state ->
        AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(state)
    } ?: AutoAiTurnFollowUpPlan.None

internal fun AutoAiTurnFollowUpPlan.toAutoAiTurnFollowUpRequest(): AutoAiTurnFollowUpRequest? =
    when (this) {
        AutoAiTurnFollowUpPlan.None -> null
        is AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis ->
            AutoAiTurnFollowUpRequest(
                targetState = targetState,
                automatic = true,
                deep = false,
            )
    }

internal sealed class AutoAiTurnEndgamePlan {
    data object None : AutoAiTurnEndgamePlan()
    data class Resolve(
        val state: GameState,
        val profile: EngineProfile,
        val prePassCandidates: List<CandidateMove>,
        val engineMessagePrefix: String,
        val successSource: String = "auto-ai-engine-dead-stone-cleanup",
        val failureSource: String = "auto-ai-engine-final-score-failed",
    ) : AutoAiTurnEndgamePlan()
}

internal fun buildAutoAiTurnEndgamePlan(display: AutoAiTurnDisplayPlan): AutoAiTurnEndgamePlan =
    if (display.shouldResolveEndgame) {
        AutoAiTurnEndgamePlan.Resolve(
            state = display.gameState,
            profile = display.profile,
            prePassCandidates = display.endgamePrePassCandidates,
            engineMessagePrefix = display.turnEngineMessage,
        )
    } else {
        AutoAiTurnEndgamePlan.None
    }

internal sealed class AutoAiTurnEndgameDisplayPlan {
    data class Resolved(
        val resolution: AiEndgameResolution,
        val display: FinalScoreDisplayPlan,
    ) : AutoAiTurnEndgameDisplayPlan()

    data class Failed(
        val error: Throwable,
        val display: EndgameFailureDisplayPlan,
    ) : AutoAiTurnEndgameDisplayPlan()
}

internal data class AutoAiTurnFailureDisplayPlan(
    val engineMessage: String,
    val candidateText: String,
)

internal fun buildAutoAiTurnFailureDisplayPlan(error: Throwable): AutoAiTurnFailureDisplayPlan =
    AutoAiTurnFailureDisplayPlan(
        engineMessage = error.message ?: "AI turn failed.",
        candidateText = "AI turn failed. Current board state was not changed.",
    )

internal data class AutoAiTurnRunExecutionContext(
    val currentProfile: EngineProfile,
    val searchTimeSettings: SearchTimeSettings,
    val previousSnapshots: List<ScoreSnapshot>,
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
    searchMode: EngineSearchMode,
    isolateSearchCache: Boolean,
    previousSnapshots: List<ScoreSnapshot>,
    previousReviewCandidates: List<CandidateMove>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnDisplayPlan {
    val analysisLimit = playLevel.aiMoveAnalysisLimitWith(searchTimeSettings)
    val result = runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.AutoAiTurn,
            state = currentState,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = analysisLimit.timeMillis,
                label = "${searchMode.name}:${analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.None,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        runAutoAiTurn(
            currentState = currentState,
            playLevel = playLevel,
            currentProfile = currentProfile,
            searchTimeSettings = searchTimeSettings,
            searchMode = searchMode,
            isolateSearchCache = isolateSearchCache,
        )
    }
    return buildAutoAiTurnDisplayPlan(
        result = result,
        previousSnapshots = previousSnapshots,
        previousReviewCandidates = previousReviewCandidates,
    )
}

internal suspend fun EngineSessionClient.runAutoAiTurnEffect(
    effect: GameSessionEffect.RunAutoAiTurn,
    executionContext: AutoAiTurnRunExecutionContext,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnDisplayPlan {
    val turnContext = effect.plan.context
    return runAutoAiTurnDisplayPlan(
        currentState = turnContext.turnState,
        playLevel = turnContext.playLevel,
        currentProfile = executionContext.currentProfile,
        searchTimeSettings = executionContext.searchTimeSettings,
        searchMode = turnContext.searchMode,
        isolateSearchCache = turnContext.isolateSearchCache,
        previousSnapshots = executionContext.previousSnapshots,
        previousReviewCandidates = turnContext.previousReviewCandidates,
        operationRequest = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    )
}

internal suspend fun EngineSessionClient.runAutoAiTurnWorkflowResult(
    effect: GameSessionEffect.RunAutoAiTurn,
    executionContext: AutoAiTurnRunExecutionContext,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnWorkflowResult =
    runCatching {
        runAutoAiTurnEffect(
            effect = effect,
            executionContext = executionContext,
            operationRequest = operationRequest,
            diagnosticEventLog = diagnosticEventLog,
        )
    }.fold(
        onSuccess = { display -> AutoAiTurnWorkflowResult.Success(display) },
        onFailure = { error -> AutoAiTurnWorkflowResult.Failure(error) },
    )

internal suspend fun EngineSessionClient.runAutoAiEndgameDisplayPlan(
    plan: AutoAiTurnEndgamePlan.Resolve,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnEndgameDisplayPlan =
    runCatching {
        val resolution = runObservedEngineOperation(
            request = operationRequest ?: autoAiEndgameOperationToken(plan).operation,
            diagnosticEventLog = diagnosticEventLog,
        ) {
            resolveEndgameForState(
                state = plan.state,
                profile = plan.profile,
                prePassCandidates = plan.prePassCandidates,
            )
        }
        AutoAiTurnEndgameDisplayPlan.Resolved(
            resolution = resolution,
            display = buildResolvedEndgameDisplayPlan(
                source = plan.successSource,
                originalState = plan.state,
                resolution = resolution,
                previousSnapshots = previousSnapshots,
                engineMessagePrefix = plan.engineMessagePrefix,
            ),
        )
    }.getOrElse { error ->
        AutoAiTurnEndgameDisplayPlan.Failed(
            error = error,
            display = buildEndgameFailureDisplayPlan(
                source = plan.failureSource,
                state = plan.state,
                errorMessage = error.message ?: "Unknown error",
                engineMessagePrefix = plan.engineMessagePrefix,
            ),
        )
    }

internal suspend fun EngineSessionClient.runAutoAiEndgameEffect(
    effect: GameSessionEffect.ResolveAutoAiEndgame,
    previousSnapshots: List<ScoreSnapshot>,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnEndgameDisplayPlan =
    runAutoAiEndgameDisplayPlan(
        plan = effect.plan,
        previousSnapshots = previousSnapshots,
        operationRequest = operationRequest,
        diagnosticEventLog = diagnosticEventLog,
    )
