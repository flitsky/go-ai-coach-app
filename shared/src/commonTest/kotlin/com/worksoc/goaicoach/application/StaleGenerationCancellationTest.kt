package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.engine.operation.EngineOperationLifecycleController
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.policy.EngineOperationKind
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.shared.policy.engineOperationRequest
import com.worksoc.goaicoach.testsupport.RecordingDiagnosticEventLog
import com.worksoc.goaicoach.testsupport.RecordingRuntimeEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation

/**
 * refactor backlog #15 — 무르기 직후의 [EngineOperationLifecycleController.cancelStaleGenerationOperations]는 **떠난 국면의**
 * 추천 수·형세·착수 동기화만 취소한다. 결과는 어차피 세대 가드가 버린다. 그 밖의 종류(이어하기·계가 규칙 재동기화·새 대국·
 * 벤치마크·캐시 최적화…)는 결과 적용 말고도 끝에 하는 일이 있어 건드리지 않고, 지금 세대의 작업도 건드리지 않는다.
 * 실제 배선에서 무르기가 이것을 부르는지는 app-android의 `UndoCancelsStaleEngineWorkWiringTest`가 잰다.
 */
class StaleGenerationCancellationTest {
    @Test
    fun onlyTheLeftPositionsTopMovesScoreEstimateAndMoveSyncAreCancelled() {
        var generation = 1L
        val lifecycle = EngineOperationLifecycleController(
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            runtimeEventLog = RecordingRuntimeEventLog(),
            diagnosticEventLog = RecordingDiagnosticEventLog(),
            currentRuntimeLogContext = { runtimeContext() },
            currentState = { GameState.empty() },
            currentSessionGeneration = { generation },
            onBusyChanged = { _, _, _, _ -> },
        )
        val leftBehind = EngineOperationKind.entries.associateWith { kind ->
            lifecycle.launchTracked(engineOperationRequest(kind, GameState.empty(), sessionGeneration = 1L)) { awaitCancellation() }
        }
        generation = 2L // 무르기가 판을 되돌려 세대가 바뀌었다
        val current = lifecycle.launchTracked(engineOperationRequest(EngineOperationKind.TopMoves, GameState.empty(), sessionGeneration = 2L)) {
            awaitCancellation()
        }

        lifecycle.cancelStaleGenerationOperations()

        assertEquals(
            setOf(EngineOperationKind.TopMoves, EngineOperationKind.ScoreEstimate, EngineOperationKind.HumanMoveSync),
            leftBehind.filterValues { job -> job.isCancelled }.keys,
        )
        assertFalse(current.isCancelled, "the current position's work is not stale")
    }

    private fun runtimeContext(): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic",
            playerSetup = PlayerSetup(),
            gameState = GameState.empty(),
            runtimeState = GameSessionRuntimeState(playLevel = PlayLevelSetting(), engineProfile = EngineProfile(), analysisPreset = AnalysisPreset.Lite),
            autoPlayDelaySetting = AutoPlayDelaySetting.None,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = true,
            isEngineReady = true,
            isEngineBusy = false,
            isGameEnded = false,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            analysisCacheStats = "entries=0",
            moveAnalysisCoverage = "coverage",
            scoreText = "score",
        )
}
