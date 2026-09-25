package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.contract.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

suspend fun EngineSessionClient.runScoringRuleSyncDisplayPlan(
    state: GameState,
    profile: EngineProfile,
    previousSnapshots: List<ScoreSnapshot>,
    engineMessage: String,
    operationRequest: EngineOperationRequest? = null,
    diagnosticEventLog: DiagnosticEventLogPort = NoopDiagnosticEventLog,
): ScoreEstimateDisplayPlan {
    val estimate = runObservedEngineOperation(
        request = operationRequest ?: engineOperationRequest(
            kind = EngineOperationKind.ScoringRuleSync,
            state = state,
            sessionGeneration = 0L,
            timeoutPolicy = EngineTimeoutPolicy(
                timeoutMillis = profile.analysisLimit.timeMillis,
                label = "${profile.difficulty.label}:${profile.analysisLimit.visits}v",
            ),
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        ),
        diagnosticEventLog = diagnosticEventLog,
    ) {
        syncAndEstimateGraphScore(state, profile)
    }
    return buildEngineEstimateDisplayPlan(
        state = state,
        estimate = estimate,
        previousSnapshots = previousSnapshots,
        engineMessage = engineMessage,
        trimAfterMove = true,
    )
}

/**
 * 세 score sync 러너(계가 규칙·무르기 후·복원 대국)의 흐름 한 벌(refactor backlog #39). 러너는 작업 종류·
 * [runApplyPlan]·후속 분석 시점만 정하고, 작업 요청(폴백은 늘 로컬 규칙), 블록 안에서 현재 국면 → 세대 순
 * 읽기, [runEngineWork] 안의 적용 계획, [applyCompletion]은 여기 한 곳이다.
 */
internal class ScoreSyncFlow(
    kind: EngineOperationKind,
    state: GameState,
    sessionGeneration: Long,
    timeoutPolicy: EngineTimeoutPolicy,
    private val currentState: () -> GameState,
    private val currentSessionGeneration: () -> Long,
    private val runEngineWork: suspend (suspend () -> ScoreSyncCompletionApplyPlan) -> ScoreSyncCompletionApplyPlan,
    private val applyCompletion: (ScoreSyncCompletionApplyPlan) -> GameState?,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
    private val runApplyPlan: suspend (EngineOperationRequest, GameState, Long) -> ScoreSyncCompletionApplyPlan,
) {
    private val operation = engineOperationRequest(
        kind = kind,
        state = state,
        sessionGeneration = sessionGeneration,
        timeoutPolicy = timeoutPolicy,
        fallbackPolicy = EngineFallbackPolicy.LocalRules,
    )

    /** 후속 분석을 요청할 국면 — 결과를 버렸으면 `null`. */
    private suspend fun runInOperation(): GameState? =
        applyCompletion(runEngineWork { runApplyPlan(operation, currentState(), currentSessionGeneration()) })

    /** 띄우고 바로 돌아오는 작업(`launchTracked`) — 후속 분석을 작업이 아직 추적 중일 때, 블록 **안에서** 요청한다. */
    fun launchFollowingUpInside(runEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit) {
        runEngineOperation(operation) { runInOperation()?.let(requestFollowUpAnalysis) }
    }

    /** 블록을 기다리는 작업(`runTracked`) — 후속 분석을 `runEngineOperation`이 돌아온 **뒤에** 요청한다. */
    suspend fun runFollowingUpAfter(runEngineOperation: suspend (EngineOperationRequest, suspend () -> Unit) -> Unit) {
        var followUpAnalysisState: GameState? = null
        runEngineOperation(operation) { followUpAnalysisState = runInOperation() }
        followUpAnalysisState?.let(requestFollowUpAnalysis)
    }
}
