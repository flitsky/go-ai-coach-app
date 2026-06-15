package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.diagnostic.NoopDiagnosticEventLog
import com.worksoc.goaicoach.application.diagnostic.runObservedEngineOperation
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.engineOperationRequest

internal suspend fun EngineSessionClient.runScoringRuleSyncDisplayPlan(
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
