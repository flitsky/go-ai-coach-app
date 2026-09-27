package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.testsupport.RecordingDiagnosticEventLog
import com.worksoc.goaicoach.testsupport.RecordingRuntimeEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * T8(refactor backlog #74) — AI 차례의 Job을 **맡기고 취소하는** 자리([EngineOperationLifecycleController]).
 * 설계 F3: 예전에는 Job이 `AutoAiTurnController`에서 버려져(`launchAutoAiEffect`의 람다가 Unit을 돌려줬다)
 * 어떤 사용자 동작도 AI 차례를 취소하지 못했다. 왜 컨트롤러가 아니라 여기인지는
 * `AutoAiTurnCancellationWiringTest`(app-android)가 재현한다.
 */
class AutoAiTurnJobTrackingTest {
    @Test
    fun cancelInFlightAutoAiTurnCancelsTheTrackedJobOnceAndLogsIt() {
        val diagnostics = RecordingDiagnosticEventLog()
        val lifecycle = lifecycleController(diagnostics)
        val turn = Job()

        lifecycle.trackAutoAiTurnJob(turn)
        lifecycle.cancelInFlightAutoAiTurn()
        lifecycle.cancelInFlightAutoAiTurn()

        assertTrue(turn.isCancelled)
        assertEquals(
            listOf("engine_operation_cancelled"),
            diagnostics.events.map { it.code },
            "끝난(취소된) 차례를 다시 취소하면 아무것도 하지 않는다",
        )
    }

    @Test
    fun aFinishedTurnIsForgottenSoALaterCancelDoesNothing() {
        val diagnostics = RecordingDiagnosticEventLog()
        val lifecycle = lifecycleController(diagnostics)
        val turn = Job()
        lifecycle.trackAutoAiTurnJob(turn)

        turn.complete()
        lifecycle.cancelInFlightAutoAiTurn()

        assertFalse(turn.isCancelled)
        assertTrue(diagnostics.events.isEmpty())
    }

    /** 새 대국(`cancelStaleOperations` = [EngineOperationLifecycleController.evictAllOperations])도 차례를 멈춘다. */
    @Test
    fun evictAllOperationsCancelsTheTrackedTurnEvenWhenNoOperationIsActive() {
        val lifecycle = lifecycleController(RecordingDiagnosticEventLog())
        val turnStillInAutoPlayDelay = Job()
        lifecycle.trackAutoAiTurnJob(turnStillInAutoPlayDelay)

        lifecycle.evictAllOperations()

        assertTrue(
            turnStillInAutoPlayDelay.isCancelled,
            "AI 대 AI의 착수 지연 중인 차례는 아직 작업 목록에 없다 — 그래도 멈춰야 한다",
        )
    }

    private fun lifecycleController(diagnostics: RecordingDiagnosticEventLog): EngineOperationLifecycleController =
        EngineOperationLifecycleController(
            scope = CoroutineScope(Job()),
            runtimeEventLog = RecordingRuntimeEventLog(),
            diagnosticEventLog = diagnostics,
            currentRuntimeLogContext = { error("이 테스트는 작업 시작·완료를 적지 않는다") },
            currentState = { GameState.empty() },
            currentSessionGeneration = { 0L },
            onBusyChanged = { _, _, _, _ -> },
        )
}
