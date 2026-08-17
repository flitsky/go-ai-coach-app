package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.*
import com.worksoc.goaicoach.shared.engine.EngineOperationRequest
import com.worksoc.goaicoach.shared.engine.EngineOperationKind
import com.worksoc.goaicoach.shared.engine.EngineTimeoutPolicy
import com.worksoc.goaicoach.shared.engine.EngineFallbackPolicy
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class EngineOperationLifecycleTest {

    private fun createRequest(operationId: String, kind: EngineOperationKind = EngineOperationKind.TopMoves): EngineOperationRequest {
        return EngineOperationRequest(
            operationId = operationId,
            kind = kind,
            sessionGeneration = 1L,
            boardFingerprint = "dummy",
            moveCount = 0,
            timeoutPolicy = EngineTimeoutPolicy(),
            fallbackPolicy = EngineFallbackPolicy.None,
            backendId = "local-engine"
        )
    }

    @Test
    fun startedTransitionTracksOperationAndMarksEngineBusy() {
        val request = createRequest("top_moves:g1")
        val next = applyEngineOperationLifecycleTransition(
            state = EngineOperationLifecycleState(),
            transition = EngineOperationLifecycleTransition.Started(request),
        )

        assertTrue(
            next.isEngineBusy(1L),
        )
        assertTrue("top_moves:g1" in next.activeOperations.keys)
    }

    @Test
    fun completedTransitionOnlyClearsMatchingOperation() {
        val req1 = createRequest("top_moves:g1")
        val req2 = createRequest("score:g1", EngineOperationKind.ScoreEstimate)
        val state = EngineOperationLifecycleState(
            activeOperations = mapOf("top_moves:g1" to req1, "score:g1" to req2),
        )

        val next = applyEngineOperationLifecycleTransition(
            state = state,
            transition = EngineOperationLifecycleTransition.Completed("top_moves:g1"),
        )

        assertTrue(next.isEngineBusy(1L))
        assertFalse("top_moves:g1" in next.activeOperations.keys)
        assertTrue("score:g1" in next.activeOperations.keys)
    }

    @Test
    fun resetClearsAllOperations() {
        val req1 = createRequest("a")
        val req2 = createRequest("b")
        val state = EngineOperationLifecycleState(
            activeOperations = mapOf("a" to req1, "b" to req2)
        )

        val next = applyEngineOperationLifecycleTransition(
            state = state,
            transition = EngineOperationLifecycleTransition.Reset,
        )

        assertFalse(next.isEngineBusy(1L))
        assertTrue(next.activeOperations.isEmpty())
    }

    @Test
    fun engineTurnWaitCompletionSeqIncrementsForTurnWaitKindsOnly() {
        fun completionSeqAfter(kind: EngineOperationKind): Int {
            val operationId = "${kind.code}:g1"
            val started = applyEngineOperationLifecycleTransition(
                state = EngineOperationLifecycleState(),
                transition = EngineOperationLifecycleTransition.Started(createRequest(operationId, kind)),
            )
            return applyEngineOperationLifecycleTransition(
                state = started,
                transition = EngineOperationLifecycleTransition.Completed(operationId),
            ).engineTurnWaitCompletionSeq
        }

        // 엔진 응답 지연 팝업이 "이번 차례 대기 종료"로 취급해야 하는 작업들 — 양패스로 계가에
        // 들어가는 경우도 HumanMoveSync로 처리되므로 반드시 포함되어야 한다.
        assertEquals(1, completionSeqAfter(EngineOperationKind.AutoAiTurn))
        assertEquals(1, completionSeqAfter(EngineOperationKind.HumanMoveSync))
        assertEquals(1, completionSeqAfter(EngineOperationKind.AutoAiEndgame))

        // 무관한 백그라운드 작업 완료로는 팝업을 오탐 종료시키면 안 된다.
        assertEquals(0, completionSeqAfter(EngineOperationKind.TopMoves))
        assertEquals(0, completionSeqAfter(EngineOperationKind.ScoreEstimate))
        assertEquals(0, completionSeqAfter(EngineOperationKind.PositionCacheOptimization))
    }

    @Test
    fun activityIndicatorDistinguishesPreparingRecommendingThinkingAndOptimizing() {
        fun indicatorFor(kind: EngineOperationKind) =
            EngineOperationLifecycleState(
                activeOperations = mapOf("${kind.code}:g1" to createRequest("${kind.code}:g1", kind)),
            ).activityIndicator(1L)

        assertEquals(EngineActivityIndicator.Preparing, indicatorFor(EngineOperationKind.EngineStartup))
        assertEquals(EngineActivityIndicator.Recommending, indicatorFor(EngineOperationKind.TopMoves))
        assertEquals(EngineActivityIndicator.Thinking, indicatorFor(EngineOperationKind.AutoAiTurn))
        assertEquals(EngineActivityIndicator.Optimizing, indicatorFor(EngineOperationKind.PositionCacheOptimization))
    }

    @Test
    fun busyFlagsAndIndicatorIgnoreOperationsFromOtherSessionGenerations() {
        // 실제 발생 사례 재현: 기권 직후 새 대국을 시작하면 이전 세대(예: 1)의 엔진 작업이
        // 아직 남아 있을 수 있다. 새 대국(세대 2) 입장에서는 이 작업이 "내 세대"가 아니므로
        // busy/activityIndicator에 잡히면 안 된다.
        val staleRequest = createRequest("auto_ai_turn:g1", EngineOperationKind.AutoAiTurn) // sessionGeneration = 1L
        val state = EngineOperationLifecycleState(
            activeOperations = mapOf("auto_ai_turn:g1" to staleRequest),
        )

        assertTrue(state.isEngineBusy(1L))
        assertTrue(state.isBlockingBusy(1L))
        assertEquals(EngineActivityIndicator.Thinking, state.activityIndicator(1L))

        assertFalse(state.isEngineBusy(2L))
        assertFalse(state.isBlockingBusy(2L))
        assertEquals(null, state.activityIndicator(2L))
    }
}
