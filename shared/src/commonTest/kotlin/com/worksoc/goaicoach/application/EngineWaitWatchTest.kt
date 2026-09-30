package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.engine.operation.EngineWaitPauseMeasurement
import com.worksoc.goaicoach.application.engine.operation.EngineWaitPauseMeter
import com.worksoc.goaicoach.application.engine.operation.EngineWaitProcessPauseThresholdMillis
import com.worksoc.goaicoach.application.engine.operation.HeartbeatEngineWaitPauseProbe
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.testsupport.RecordingDiagnosticEventLog
import com.worksoc.goaicoach.testsupport.RecordingRuntimeEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job

/**
 * backlog #204 — AI 차례가 엔진을 기다리는 사이 **앱이 멈춰 있었는지**를 재는 두 신호.
 * - 포그라운드 세대: 앱 프로세스가 화면을 떠나고 돌아올 때마다 오른다(#202가 수명을 보는 자리).
 * - 멈춤 박동: 수명 콜백이 없거나 늦는 멈춤(동결이 `ON_STOP`보다 먼저, VM 정지)을 마감과 같은 단조 시계의 틈으로 잡는다.
 * 숫자는 손으로 적는다(박동 간격 1초).
 */
class EngineWaitWatchTest {
    @Test
    fun heartbeatsOnTimeMeasureNoPause() {
        var now = 0L
        val meter = EngineWaitPauseMeter(clockMillis = { now }, intervalMillis = 1_000L)
        repeat(30) {
            now += 1_000L
            meter.beat()
        }
        now += 400L

        assertEquals(0L, meter.finish(), "제때 온 박동은 멈춤이 아니다")
    }

    @Test
    fun aLateHeartbeatMeasuresHowLongTheProcessDidNotRun() {
        var now = 0L
        val meter = EngineWaitPauseMeter(clockMillis = { now }, intervalMillis = 1_000L)
        now += 1_000L
        meter.beat()
        // 다음 박동이 70초 뒤에야 왔다 — 69초 동안 이 프로세스는 돌지 못했다(동결).
        now += 70_000L
        meter.beat()
        now += 1_000L
        meter.beat()

        assertEquals(69_000L, meter.finish())
    }

    /**
     * 해동 순간에는 밀린 박동보다 시간 초과의 처리가 **먼저** 올 수 있다. 그래도 끝낼 때 마지막 박동부터 지금까지를 재므로
     * 틈을 놓치지 않는다 — 판정이 박동과 판정의 순서에 기대지 않는다.
     */
    @Test
    fun aPauseNotYetSeenByAnyHeartbeatIsMeasuredWhenTheWaitEnds() {
        var now = 0L
        val meter = EngineWaitPauseMeter(clockMillis = { now }, intervalMillis = 1_000L)
        now += 1_000L
        meter.beat()
        now += 36L * 60_000L // 36분 동결 뒤 복귀 — 박동은 아직 한 번도 못 쳤다.

        assertTrue(meter.finish() >= EngineWaitProcessPauseThresholdMillis)
    }

    @Test
    fun finishingFreezesTheMeasurementAndLaterHeartbeatsAreIgnored() {
        var now = 0L
        val meter = EngineWaitPauseMeter(clockMillis = { now }, intervalMillis = 1_000L)
        now += 800L
        val finished = meter.finish()
        now += 60_000L
        meter.beat()

        assertEquals(0L, finished)
        assertEquals(finished, meter.finish(), "끝낸 뒤의 박동·시간은 그 기다림의 일이 아니다")
    }

    /** 앱의 박동 — 끝내면 박동을 끄고, 단조 시계의 틈을 돌려준다. */
    @Test
    fun theHeartbeatProbeStopsBeatingWhenTheWaitEnds() {
        var now = 0L
        val scope = CoroutineScope(Job())
        val probe = HeartbeatEngineWaitPauseProbe(scope = scope, clockMillis = { now })

        val quick = probe.start()
        assertEquals(1, scope.coroutineContext.job.children.count { it.isActive }, "기다리는 동안 박동이 돈다")
        assertEquals(0L, quick.finish())
        assertEquals(0, scope.coroutineContext.job.children.count { it.isActive }, "기다림이 끝나면 박동을 끈다")

        val frozen = probe.start()
        now += 69_000L
        assertTrue(frozen.finish() >= EngineWaitProcessPauseThresholdMillis, "69초 멈춘 기다림")
        scope.coroutineContext.job.cancel()
    }

    /**
     * 포그라운드 세대는 앱이 화면을 떠나고 돌아오는 **전환마다** 오르고, 기다림은 시작할 때의 세대와 끝낼 때의 세대를
     * 맞댄다. 끝낸 뒤의 전환은 그 기다림의 일이 아니다.
     */
    @Test
    fun theForegroundGenerationRisesOnEveryTransitionAndTheWatchComparesItAcrossTheWait() {
        val controller = lifecycleController()
        assertEquals(0L, controller.foregroundGeneration)

        val untouched = controller.startEngineWaitWatch()
        assertFalse(untouched.finish().isInterrupted, "앱이 화면에 그대로였다")

        val spanning = controller.startEngineWaitWatch()
        controller.markAppInForeground(false)
        controller.markAppInForeground(true)
        assertEquals(2L, controller.foregroundGeneration, "떠날 때 한 번, 돌아올 때 한 번")
        val interruption = spanning.finish()
        assertTrue(interruption.backgroundedDuringWait)
        assertTrue(interruption.isInterrupted)

        val finishedBefore = controller.startEngineWaitWatch()
        assertFalse(finishedBefore.finish().backgroundedDuringWait)
        controller.markAppInForeground(false)
        assertFalse(finishedBefore.finish().backgroundedDuringWait, "굳힌 판정은 뒤의 전환으로 바뀌지 않는다")
    }

    private fun lifecycleController(): EngineOperationLifecycleController =
        EngineOperationLifecycleController(
            scope = CoroutineScope(Job()),
            runtimeEventLog = RecordingRuntimeEventLog(),
            diagnosticEventLog = RecordingDiagnosticEventLog(),
            currentRuntimeLogContext = { error("이 테스트는 작업 시작·완료를 적지 않는다") },
            currentState = { GameState.empty() },
            currentSessionGeneration = { 0L },
            onBusyChanged = { _, _, _, _ -> },
            engineWaitPauseProbe = { EngineWaitPauseMeasurement { 0L } },
        )
}
