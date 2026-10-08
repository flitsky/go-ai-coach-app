package com.worksoc.goaicoach.application.autoai

import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.GameSessionEffect
import com.worksoc.goaicoach.application.contract.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.endgame.AiEndgameResolution
import com.worksoc.goaicoach.application.engine.AutoAiTurnResult
import com.worksoc.goaicoach.application.engine.EngineGamePlayClient
import com.worksoc.goaicoach.application.engine.localScoreSnapshot
import com.worksoc.goaicoach.application.score.EndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.FinalScoreDisplayPlan
import com.worksoc.goaicoach.application.score.buildEndgameFailureDisplayPlan
import com.worksoc.goaicoach.application.score.buildEngineEstimateDisplayPlan
import com.worksoc.goaicoach.application.score.buildResolvedEndgameDisplayPlan
import com.worksoc.goaicoach.application.session.AutoAiTurnFailureDisplayPlan
import com.worksoc.goaicoach.match.AiMoveSearchTimedOut
import com.worksoc.goaicoach.match.MatchReferee
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.enginecontract.CandidateMove
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreTimeline
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

sealed class AutoAiTurnFollowUpPlan {
    data object None : AutoAiTurnFollowUpPlan()
    data class RequestTopMoveAnalysis(
        val targetState: GameState,
    ) : AutoAiTurnFollowUpPlan()
}

data class AutoAiTurnFollowUpRequest(
    val targetState: GameState,
    val automatic: Boolean,
    val deep: Boolean,
)

fun buildAutoAiTurnFollowUpPlan(display: AutoAiTurnDisplayPlan): AutoAiTurnFollowUpPlan =
    display.nextAnalysisState?.let { state ->
        AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(state)
    } ?: AutoAiTurnFollowUpPlan.None

fun AutoAiTurnFollowUpPlan.toAutoAiTurnFollowUpRequest(): AutoAiTurnFollowUpRequest? =
    when (this) {
        AutoAiTurnFollowUpPlan.None -> null
        is AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis ->
            AutoAiTurnFollowUpRequest(
                targetState = targetState,
                automatic = true,
                deep = false,
            )
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

sealed class AutoAiTurnEndgameDisplayPlan {
    data class Resolved(
        val resolution: AiEndgameResolution,
        val display: FinalScoreDisplayPlan,
    ) : AutoAiTurnEndgameDisplayPlan()

    data class Failed(
        val error: Throwable,
        val display: EndgameFailureDisplayPlan,
    ) : AutoAiTurnEndgameDisplayPlan()
}

fun buildAutoAiTurnFailureDisplayPlan(error: Throwable): AutoAiTurnFailureDisplayPlan =
    AutoAiTurnFailureDisplayPlan(
        engineMessage = error.message ?: "AI turn failed.",
        candidateText = "AI turn failed. Current board state was not changed.",
    )

internal data class AutoAiTurnRunExecutionContext(
    val currentProfile: EngineProfile,
    val searchTimeSettings: SearchTimeSettings,
    val previousSnapshots: List<ScoreSnapshot>,
)

fun buildAutoAiTurnDisplayPlan(
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

suspend fun EngineGamePlayClient.runAutoAiTurnDisplayPlan(
    currentState: GameState,
    playLevel: PlayLevelSetting,
    currentProfile: EngineProfile,
    searchTimeSettings: SearchTimeSettings,
    searchMode: EngineSearchMode,
    isolateSearchCache: Boolean,
    previousSnapshots: List<ScoreSnapshot>,
    previousReviewCandidates: List<CandidateMove>,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnDisplayPlan {
    val result = runObservedEngineOperation(
        request = operationRequest,
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

internal suspend fun EngineGamePlayClient.runAutoAiTurnEffect(
    effect: GameSessionEffect.RunAutoAiTurn,
    executionContext: AutoAiTurnRunExecutionContext,
    operationRequest: EngineOperationRequest,
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

/**
 * AI 차례 하나를 돌려 결과를 셋으로 가른다(refactor backlog #74).
 * - 성공 → [AutoAiTurnWorkflowResult.Success]
 * - **시간 초과** — 탐색의 [AiMoveSearchTimedOut], 또는 호출자가 살아 있는데 올라온 `CancellationException`
 *   (`configure`·`playMove` 같은 다른 엔진 명령의 `withTimeout`) → [AutoAiTurnWorkflowResult.TimedOut].
 *   실패 문구를 띄우지 않고 선택 팝업이 설명한다.
 * - 그 밖의 예외 → [AutoAiTurnWorkflowResult.Failure](지금처럼 「AI turn failed…」).
 *
 * ⚠️ **진짜 취소는 여기서 삼키지 않고 올린다.** 예전의 `runCatching`은 사용자의 취소까지 `Failure`로 바꿨고,
 * 그 직후 `runEngineIo`의 복귀 경계에서 즉시 취소가 다시 던져져 호출부의 정리(busy·예약 해제)를 건너뛰었다
 * (설계 F3 (b)). 정리는 이제 호출부의 `finally`가 한다.
 */
internal suspend fun EngineGamePlayClient.runAutoAiTurnWorkflowResult(
    effect: GameSessionEffect.RunAutoAiTurn,
    executionContext: AutoAiTurnRunExecutionContext,
    operationRequest: EngineOperationRequest,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): AutoAiTurnWorkflowResult =
    try {
        AutoAiTurnWorkflowResult.Success(
            runAutoAiTurnEffect(
                effect = effect,
                executionContext = executionContext,
                operationRequest = operationRequest,
                diagnosticEventLog = diagnosticEventLog,
            ),
        )
    } catch (timeout: AiMoveSearchTimedOut) {
        AutoAiTurnWorkflowResult.TimedOut(timeout)
    } catch (cancellation: CancellationException) {
        if (!currentCoroutineContext().isActive) throw cancellation
        AutoAiTurnWorkflowResult.TimedOut(cancellation)
    } catch (failure: Throwable) {
        // 취소된 뒤 막혔던 읽기가 일반 예외로 풀린 것이면(forceReset → ISE) 결과가 아니다 — 취소로 올린다.
        currentCoroutineContext().ensureActive()
        AutoAiTurnWorkflowResult.Failure(failure)
    }

suspend fun EngineGamePlayClient.runAutoAiEndgameDisplayPlan(
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

internal suspend fun EngineGamePlayClient.runAutoAiEndgameEffect(
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
