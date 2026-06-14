package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOperationPolicyTest {
    @Test
    fun positionScopedResultGuardAppliesOnlyToSamePosition() {
        val requestedState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = requestedState
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val token = positionScopedOperationToken(
            kind = "test_operation",
            state = requestedState,
        )

        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluatePositionScopedResultGuard(token, requestedState),
        )
        assertTrue(evaluatePositionScopedResultGuard(token, changedState) is EngineOperationResultGuard.Discard)
    }

    @Test
    fun engineOperationRequestCarriesCommonRemoteSafeMetadata() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        val request = engineOperationRequest(
            kind = EngineOperationKind.RemotePositionAnalysis,
            state = state,
            sessionGeneration = 7,
            timeoutPolicy = EngineTimeoutPolicy(timeoutMillis = 3_000, label = "remote-read"),
            fallbackPolicy = EngineFallbackPolicy.LocalEngine,
            backendId = "remote-server",
        )

        assertTrue(request.operationId.startsWith("remote_position_analysis:g7:m1:"))
        assertEquals(EngineOperationKind.RemotePositionAnalysis, request.kind)
        assertEquals(7, request.sessionGeneration)
        assertEquals(state.analysisFingerprint(), request.boardFingerprint)
        assertEquals(1, request.moveCount)
        assertEquals(3_000L, request.timeoutPolicy.timeoutMillis)
        assertEquals("remote-read", request.timeoutPolicy.label)
        assertEquals(EngineFallbackPolicy.LocalEngine, request.fallbackPolicy)
        assertEquals("remote-server", request.backendId)
    }

    @Test
    fun engineOperationGuardDiscardsDifferentSessionGenerationBeforePositionCheck() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val request = engineOperationRequest(
            kind = EngineOperationKind.TopMoves,
            state = state,
            sessionGeneration = 3,
        )

        val guard = evaluateEngineOperationResultGuard(
            request = request,
            currentState = state,
            currentSessionGeneration = 4,
        )

        assertTrue(guard is EngineOperationResultGuard.Discard)
        assertTrue((guard as EngineOperationResultGuard.Discard).reason.contains("generation=3"))
    }

    @Test
    fun engineOperationGuardAppliesOnlyToSameGenerationAndPosition() {
        val requestedState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = requestedState
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val request = engineOperationRequest(
            kind = EngineOperationKind.ScoreEstimate,
            state = requestedState,
            sessionGeneration = 1,
        )

        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluateEngineOperationResultGuard(
                request = request,
                currentState = requestedState,
                currentSessionGeneration = 1,
            ),
        )
        assertTrue(
            evaluateEngineOperationResultGuard(
                request = request,
                currentState = changedState,
                currentSessionGeneration = 1,
            ) is EngineOperationResultGuard.Discard,
        )
    }

    @Test
    fun benchmarkGateRequiresReadyLocalIdleEngine() {
        assertEquals(
            EngineOperationGate.Block("Engine benchmark requires a ready local engine."),
            evaluateEngineBenchmarkGate(
                isEngineReady = false,
                supportsDeviceBenchmark = true,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ),
        )
        assertEquals(
            EngineOperationGate.Block("Engine benchmark is available only for the local KataGo process engine."),
            evaluateEngineBenchmarkGate(
                isEngineReady = true,
                supportsDeviceBenchmark = false,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ),
        )
        assertEquals(
            EngineOperationGate.Block("Engine is busy. Run benchmark after the current response."),
            evaluateEngineBenchmarkGate(
                isEngineReady = true,
                supportsDeviceBenchmark = true,
                isEngineBusy = true,
                isBenchmarkRunning = false,
            ),
        )
        assertEquals(
            EngineOperationGate.Allow,
            evaluateEngineBenchmarkGate(
                isEngineReady = true,
                supportsDeviceBenchmark = true,
                isEngineBusy = false,
                isBenchmarkRunning = false,
            ),
        )
    }

    @Test
    fun searchTimeChangeGateBlocksOnlyWhenBusy() {
        assertEquals(
            EngineOperationGate.Block("Engine is busy. Change search time after the current action."),
            evaluateSearchTimeChangeGate(isEngineBusy = true),
        )
        assertEquals(EngineOperationGate.Allow, evaluateSearchTimeChangeGate(isEngineBusy = false))
    }

    @Test
    fun scoringRuleChangeGateDistinguishesNoOpBusyAndAllowed() {
        assertEquals(
            EngineOperationGate.NoOp,
            evaluateScoringRuleChangeGate(
                currentRuleset = Ruleset.Japanese,
                nextRuleset = Ruleset.Japanese,
                isEngineBusy = true,
            ),
        )

        val busy = evaluateScoringRuleChangeGate(
            currentRuleset = Ruleset.Japanese,
            nextRuleset = Ruleset.Chinese,
            isEngineBusy = true,
        )
        assertTrue(busy is EngineOperationGate.Block)

        assertEquals(
            EngineOperationGate.Allow,
            evaluateScoringRuleChangeGate(
                currentRuleset = Ruleset.Japanese,
                nextRuleset = Ruleset.Chinese,
                isEngineBusy = false,
            ),
        )
    }
}
