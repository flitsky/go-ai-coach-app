package com.worksoc.goaicoach.shared.engine

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EngineOperationPolicyTest {
    @Test
    fun operationRequestCarriesRemoteSafeMetadataAndDefaultId() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "E5"))

        val request = engineOperationRequest(
            kind = EngineOperationKind.RemotePositionAnalysis,
            state = state,
            sessionGeneration = 7,
            timeoutPolicy = EngineTimeoutPolicy(timeoutMillis = 2_000),
            fallbackPolicy = EngineFallbackPolicy.LocalEngine,
            backendId = "remote-readonly-analysis",
        )

        assertEquals(EngineOperationKind.RemotePositionAnalysis, request.kind)
        assertEquals(7, request.sessionGeneration)
        assertEquals(1, request.moveCount)
        assertEquals(state.analysisFingerprint(), request.boardFingerprint)
        assertEquals("cap:2000ms", request.timeoutPolicy.label)
        assertEquals(EngineFallbackPolicy.LocalEngine, request.fallbackPolicy)
        assertEquals("remote-readonly-analysis", request.backendId)
        assertTrue(request.operationId.startsWith("remote_position_analysis:g7:m1:"))
    }

    @Test
    fun resultGuardDiscardsDifferentSessionGenerationBeforePositionCheck() {
        val state = GameState.empty()
            .play(play(StoneColor.Black, "E5"))
        val request = engineOperationRequest(
            kind = EngineOperationKind.TopMoves,
            state = state,
            sessionGeneration = 3,
            operationId = "top-moves-1",
        )

        val guard = evaluateEngineOperationResultGuard(
            request = request,
            currentState = state,
            currentSessionGeneration = 4,
        )

        assertTrue(guard is EngineOperationResultGuard.Discard)
        assertEquals(EngineOperationKind.TopMoves.code, guard.operation)
        assertEquals("top-moves-1", guard.operationId)
        assertEquals(3, guard.sessionGeneration)
        assertTrue(guard.reason.contains("requested generation=3"))
    }

    @Test
    fun applyPlanDiscardsChangedPositionResult() {
        val requestedState = GameState.empty()
            .play(play(StoneColor.Black, "E5"))
        val currentState = requestedState
            .play(play(StoneColor.White, "D5"))
        val request = engineOperationRequest(
            kind = EngineOperationKind.HumanMoveSync,
            state = requestedState,
            sessionGeneration = 1,
            operationId = "sync-before-white",
        )

        val plan = buildEngineOperationApplyPlan(
            request = request,
            currentState = currentState,
            currentSessionGeneration = 1,
        )

        assertTrue(plan is EngineOperationApplyPlan.Discard)
        assertEquals(EngineOperationKind.HumanMoveSync.code, plan.discard.operation)
        assertEquals("sync-before-white", plan.discard.operationId)
        assertEquals(1, plan.discard.sessionGeneration)
        assertTrue(plan.discard.reason.contains("requested move=1"))
    }

    @Test
    fun gatePoliciesBlockUnsafeUserActions() {
        assertTrue(
            evaluateEngineBenchmarkGate(
                isEngineReady = false,
                supportsDeviceBenchmark = true,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ) is EngineOperationGate.Block,
        )
        assertTrue(evaluateSearchTimeChangeGate(isEngineBusy = true) is EngineOperationGate.Block)
        assertEquals(
            EngineOperationGate.NoOp,
            evaluateScoringRuleChangeGate(
                currentRuleset = Ruleset.Japanese,
                nextRuleset = Ruleset.Japanese,
                isEngineBusy = false,
            ),
        )
        assertTrue(
            evaluateScoringRuleChangeGate(
                currentRuleset = Ruleset.Japanese,
                nextRuleset = Ruleset.Chinese,
                isEngineBusy = true,
            ) is EngineOperationGate.Block,
        )
    }

    @Test
    fun timeoutPolicyRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> {
            EngineTimeoutPolicy(timeoutMillis = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            EngineTimeoutPolicy(timeoutMillis = 1_000, label = " ")
        }
    }

    private fun play(
        player: StoneColor,
        label: String,
    ): Move.Play = Move.Play(player, BoardCoordinate.fromLabel(label, BoardSize.Nine))
}
