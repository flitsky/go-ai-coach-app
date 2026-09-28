package com.worksoc.goaicoach.application.score

import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineOperationGate
import com.worksoc.goaicoach.shared.policy.EngineOperationRequest
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.policy.evaluateScoringRuleChangeGate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

/**
 * Owns the full lifecycle of a scoring-rule change:
 *   1. Gate check (no-op / block / allow)
 *   2. Local state plan build + apply
 *   3. Optional engine sync launch (only when [ScoringRuleChangePlan.requiresEngineSync])
 *
 * GoCoachApp delegates [changeScoringRule] here and supplies only callbacks.
 * All engine-interaction policy stays inside the application layer.
 */
class ScoringRuleController(
    private val engineClient: EngineScoringClient,
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
    /** 다음 대국의 룰(설정 상태)을 바꾼다 — 로비·새 대국·자동저장이 읽는 칸이다(refactor backlog #22). */
    private val applySettingsRuleset: (Ruleset) -> Unit,
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
            // ⚠️ 지금 판이 이미 그 룰이어도 **설정에는 적는다**(refactor backlog #22) — 끝난 이어하기 판(일본)이 화면에
            // 남은 채 로비에서 일본을 고르면 이 게이트는 NoOp인데, 설정(다음 대국)은 중국일 수 있다.
            EngineOperationGate.NoOp -> {
                applySettingsRuleset(nextRuleset)
                return
            }
            is EngineOperationGate.Block -> {
                onEngineMessage(gate.message)
                return
            }
        }
        // 덤처럼(`GameSettingsController.changeKomi`) 고른 룰은 다음 대국의 룰도 된다 — 로비에서 고르든 대국 중 메뉴에서
        // 고르든 같다. 엔진이 바빠 거절된(Block) 변경만 아무 데도 적지 않는다.
        applySettingsRuleset(nextRuleset)

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
