package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.operation.EngineOperationGate
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.engine.operation.evaluateScoringRuleChangeGate
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy

/**
 * Owns the full lifecycle of a scoring-rule change:
 *   1. Gate check (no-op / block / allow)
 *   2. Local state plan build + apply
 *   3. Optional engine sync launch (only when [ScoringRuleChangePlan.requiresEngineSync])
 *
 * GoCoachApp delegates [changeScoringRule] here and supplies only callbacks.
 * All engine-interaction policy stays inside the application layer.
 */
internal class ScoringRuleController(
    private val engineClient: EngineSessionClient,
    private val diagnosticEventLog: DiagnosticEventLogPort,
    private val currentGameState: () -> GameState,
    private val currentMatchMode: () -> MatchMode,
    private val isEngineReady: () -> Boolean,
    private val isEngineBusy: () -> Boolean,
    private val currentScoreSnapshots: () -> List<ScoreSnapshot>,
    private val currentEngineProfile: () -> EngineProfile,
    private val currentSessionGeneration: () -> Long,
    private val timeoutPolicy: (EngineProfile) -> EngineTimeoutPolicy,
    private val onEngineMessage: (String) -> Unit,
    private val applyScoringRuleChangePlan: (ScoringRuleChangePlan) -> Unit,
    private val applyScoreSyncCompletionApplyPlan: (ScoreSyncCompletionApplyPlan) -> GameState?,
    private val requestFollowUpAnalysis: (GameState) -> Unit,
    private val launchEngineOperation: (EngineOperationRequest, suspend () -> Unit) -> Unit,
    private val appendDiscardLog: (EngineOperationResultGuard.Discard) -> Unit,
) {
    fun change(nextRuleset: Ruleset) {
        val gameState = currentGameState()
        when (
            val gate = evaluateScoringRuleChangeGate(
                currentRuleset = gameState.ruleset,
                nextRuleset = nextRuleset,
                isEngineBusy = isEngineBusy(),
            )
        ) {
            EngineOperationGate.Allow -> Unit
            EngineOperationGate.NoOp -> return
            is EngineOperationGate.Block -> {
                onEngineMessage(gate.message)
                return
            }
        }

        val profile = currentEngineProfile()
        val ruleChange = buildScoringRuleChangePlan(
            currentState = gameState,
            nextRuleset = nextRuleset,
            isGameEnded = false, // gate already blocks if game ended
            matchMode = currentMatchMode(),
            isEngineReady = isEngineReady(),
            previousSnapshots = currentScoreSnapshots(),
        )
        val nextState = ruleChange.gameState
        applyScoringRuleChangePlan(ruleChange)

        if (!ruleChange.requiresEngineSync) {
            return
        }

        runScoringRuleSyncApplication(
            ScoringRuleSyncRunRequest(
                engineClient = engineClient,
                state = nextState,
                profile = profile,
                previousSnapshots = currentScoreSnapshots(),
                sessionGeneration = currentSessionGeneration(),
                timeoutPolicy = timeoutPolicy(profile),
                diagnosticEventLog = diagnosticEventLog,
                engineMessage = "Scoring rule changed to ${nextRuleset.scoringLabel}; engine rules synchronized.",
                currentState = currentGameState,
                currentSessionGeneration = currentSessionGeneration,
                runEngineOperation = { operation, block ->
                    launchEngineOperation(operation) { block() }
                },
                applyCompletion = ::applyCompletion,
                requestFollowUpAnalysis = { state ->
                    requestFollowUpAnalysis(state)
                },
            ),
        )
    }

    private fun applyCompletion(applyPlan: ScoreSyncCompletionApplyPlan): GameState? =
        applyScoreSyncCompletionApplyPlan(applyPlan)
}
