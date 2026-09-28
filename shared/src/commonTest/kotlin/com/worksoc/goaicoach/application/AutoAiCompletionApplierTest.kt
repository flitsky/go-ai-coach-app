package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.autoai.AutoAiTurnCompletionApplyRunRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnCompletionPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnCompletionApplication
import com.worksoc.goaicoach.application.contract.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.contract.AutoAiTurnExecutionContext
import com.worksoc.goaicoach.application.contract.GameSessionRuntimeState
import com.worksoc.goaicoach.application.contract.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.AnalysisPreset
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineSearchMode
import com.worksoc.goaicoach.shared.policy.EngineOperationResultGuard
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import com.worksoc.goaicoach.shared.policy.SearchTimeSettings
import com.worksoc.goaicoach.testsupport.RecordingRuntimeEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AutoAiCompletionApplierTest {
    @Test
    fun successLogsTurnTimeAppliesDisplayAndReturnsFollowUp() {
        val before = GameState.empty()
        val after = before.play(
            Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
        )
        val display = autoAiDisplay(
            state = after,
            shouldResolveEndgame = false,
        )
        val runtimeLog = RecordingRuntimeEventLog()
        var appliedDisplay: AutoAiTurnDisplayPlan? = null
        var appliedTurnTime: TurnTimeMoveUpdate? = null

        val followUp = runBlocking {
            applyAutoAiTurnCompletionApplication(
                baseRequest(
                    before = before,
                    completion = AutoAiTurnCompletionPlan.ApplySuccess(display),
                    runtimeLog = runtimeLog,
                    applyTurnTimeUpdate = { appliedTurnTime = it },
                    applyTurnDisplay = {
                        appliedDisplay = it
                        AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(it.gameState)
                    },
                ),
            )
        }

        assertSame(display, appliedDisplay)
        assertEquals(StoneColor.Black, appliedTurnTime?.player)
        assertEquals(500L, appliedTurnTime?.elapsedMillis)
        assertEquals(AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(after), followUp)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_success") })
    }

    @Test
    fun successRunsEndgameResolverWhenDisplayRequiresEndgame() {
        val before = GameState.empty()
        val after = before
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val display = autoAiDisplay(
            state = after,
            shouldResolveEndgame = true,
        )
        var resolved: AutoAiTurnEndgamePlan.Resolve? = null

        runBlocking {
            applyAutoAiTurnCompletionApplication(
                baseRequest(
                    before = before,
                    completion = AutoAiTurnCompletionPlan.ApplySuccess(display),
                    resolveEndgame = { resolved = it },
                ),
            )
        }

        assertEquals(after, resolved?.state)
        assertEquals(display.profile, resolved?.profile)
    }

    /** 진짜 실패는 실패 문구를 띄우고, 그 국면의 실패를 센다(refactor backlog #109 — 잇달아 나면 선택 팝업). */
    @Test
    fun failureLogsAppliesFailureDisplayAndCountsTheFailure() {
        val failure = IllegalStateException("AI failed")
        val runtimeLog = RecordingRuntimeEventLog()
        var appliedFailure: Throwable? = null
        var failureMarks = 0
        var timeoutMarks = 0

        val followUp = runBlocking {
            applyAutoAiTurnCompletionApplication(
                baseRequest(
                    completion = AutoAiTurnCompletionPlan.ApplyFailure(failure),
                    runtimeLog = runtimeLog,
                    applyTurnFailureDisplay = { appliedFailure = it },
                    markTurnTimedOut = { timeoutMarks += 1 },
                    markTurnFailed = { failureMarks += 1 },
                ),
            )
        }

        assertSame(failure, appliedFailure)
        assertEquals(1, failureMarks)
        assertEquals(0, timeoutMarks, "진짜 실패는 시간 초과가 아니다")
        assertEquals(AutoAiTurnFollowUpPlan.None, followUp)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_failure") })
    }

    /** 시간 초과(refactor backlog #74)는 실패 문구 없이 표시를 남기고(조용한 재시도 차단) 로그 한 줄을 남긴다. */
    @Test
    fun timeoutMarksTheTurnLogsItAndShowsNoFailureText() {
        val runtimeLog = RecordingRuntimeEventLog()
        var appliedFailure: Throwable? = null
        var marks = 0
        var failureMarks = 0

        val followUp = runBlocking {
            applyAutoAiTurnCompletionApplication(
                baseRequest(
                    completion = AutoAiTurnCompletionPlan.ApplyTimedOut(IllegalStateException("timed out")),
                    runtimeLog = runtimeLog,
                    applyTurnFailureDisplay = { appliedFailure = it },
                    markTurnTimedOut = { marks += 1 },
                    markTurnFailed = { failureMarks += 1 },
                ),
            )
        }

        assertEquals(null, appliedFailure)
        assertEquals(1, marks)
        assertEquals(0, failureMarks, "시간 초과는 진짜 실패로 세지 않는다")
        assertEquals(AutoAiTurnFollowUpPlan.None, followUp)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_timeout") })
    }

    @Test
    fun discardOnlyAppendsDiscardLog() {
        val discard = EngineOperationResultGuard.Discard(reason = "stale")
        var appendedDiscard: EngineOperationResultGuard.Discard? = null

        val followUp = runBlocking {
            applyAutoAiTurnCompletionApplication(
                baseRequest(
                    completion = AutoAiTurnCompletionPlan.Discard(discard),
                    appendEngineOperationDiscardLog = { appendedDiscard = it },
                ),
            )
        }

        assertSame(discard, appendedDiscard)
        assertEquals(AutoAiTurnFollowUpPlan.None, followUp)
    }

    private fun baseRequest(
        before: GameState = GameState.empty(),
        completion: AutoAiTurnCompletionPlan,
        runtimeLog: RecordingRuntimeEventLog = RecordingRuntimeEventLog(),
        applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit = {},
        applyTurnDisplay: (AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan = { AutoAiTurnFollowUpPlan.None },
        resolveEndgame: suspend (AutoAiTurnEndgamePlan.Resolve) -> Unit = {},
        applyTurnFailureDisplay: (Throwable) -> Unit = {},
        markTurnTimedOut: () -> Unit = {},
        markTurnFailed: () -> Unit = {},
        appendEngineOperationDiscardLog: (EngineOperationResultGuard.Discard) -> Unit = {},
    ): AutoAiTurnCompletionApplyRunRequest =
        AutoAiTurnCompletionApplyRunRequest(
            completion = completion,
            turnContext = AutoAiTurnExecutionContext(
                turnState = before,
                aiPlayer = before.nextPlayer,
                playLevel = PlayLevelSetting(),
                analysisLimit = AnalysisLimit(),
                searchMode = EngineSearchMode.GtpStatefulFast,
                isolateSearchCache = false,
                previousReviewCandidates = emptyList(),
            ),
            turnStartMillis = 1_000L,
            runtimeContextProvider = { runtimeContext(before) },
            runtimeEventLog = runtimeLog,
            nowMillis = { 1_500L },
            recordTurnMove = { player, nowMillis, nextPlayer ->
                GameSessionTurnTimeState.reset(before, 1_000L)
                    .recordMove(player, nowMillis, nextPlayer)
            },
            applyTurnTimeUpdate = applyTurnTimeUpdate,
            applyTurnDisplay = applyTurnDisplay,
            resolveEndgame = resolveEndgame,
            applyTurnFailureDisplay = applyTurnFailureDisplay,
            markTurnTimedOut = markTurnTimedOut,
            markTurnFailed = markTurnFailed,
            appendEngineOperationDiscardLog = appendEngineOperationDiscardLog,
        )

    private fun runtimeContext(state: GameState): RuntimeLogContext =
        RuntimeLogContext(
            engineName = "KataGo",
            engineDiagnostic = "diagnostic",
            playerSetup = PlayerSetup(),
            gameState = state,
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
            ),
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

    private fun autoAiDisplay(
        state: GameState,
        shouldResolveEndgame: Boolean,
    ): AutoAiTurnDisplayPlan =
        AutoAiTurnDisplayPlan(
            playLevel = PlayLevelSetting(),
            profile = EngineProfile(name = "test"),
            analysisPreset = AnalysisPreset.Lite,
            gameState = state,
            turnEngineMessage = "AI played",
            candidateText = "candidate",
            lastMoveText = "Black E5",
            scoreDisplay = ScoreEstimateDisplayPlan(
                scoreText = "score",
                scoreEstimate = null,
                scoreSnapshots = emptyList(),
                engineMessage = "engine",
            ),
            shouldResolveEndgame = shouldResolveEndgame,
            endgamePrePassCandidates = emptyList(),
            nextAnalysisState = state.takeUnless { shouldResolveEndgame },
        )
}
