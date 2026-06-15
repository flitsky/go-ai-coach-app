package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.autoai.AutoAiTurnCompletionApplyRunRequest
import com.worksoc.goaicoach.application.autoai.AutoAiTurnCompletionPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnDisplayPlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnEndgamePlan
import com.worksoc.goaicoach.application.autoai.AutoAiTurnExecutionContext
import com.worksoc.goaicoach.application.autoai.AutoAiTurnFollowUpPlan
import com.worksoc.goaicoach.application.autoai.applyAutoAiTurnCompletionApplication
import com.worksoc.goaicoach.application.engine.operation.EngineOperationResultGuard
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.application.runtime.RuntimeLogContext
import com.worksoc.goaicoach.application.score.ScoreEstimateDisplayPlan
import com.worksoc.goaicoach.application.session.GameSessionRuntimeState
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.application.session.TurnTimeMoveUpdate
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val runtimeLog = CompletionRuntimeLog()
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

    @Test
    fun failureLogsAndAppliesFailureDisplay() {
        val failure = IllegalStateException("AI failed")
        val runtimeLog = CompletionRuntimeLog()
        var appliedFailure: Throwable? = null

        val followUp = runBlocking {
            applyAutoAiTurnCompletionApplication(
                baseRequest(
                    completion = AutoAiTurnCompletionPlan.ApplyFailure(failure),
                    runtimeLog = runtimeLog,
                    applyTurnFailureDisplay = { appliedFailure = it },
                ),
            )
        }

        assertSame(failure, appliedFailure)
        assertEquals(AutoAiTurnFollowUpPlan.None, followUp)
        assertTrue(runtimeLog.events.any { it.contains("event=ai_turn_failure") })
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
        runtimeLog: CompletionRuntimeLog = CompletionRuntimeLog(),
        applyTurnTimeUpdate: (TurnTimeMoveUpdate) -> Unit = {},
        applyTurnDisplay: (AutoAiTurnDisplayPlan) -> AutoAiTurnFollowUpPlan = { AutoAiTurnFollowUpPlan.None },
        resolveEndgame: suspend (AutoAiTurnEndgamePlan.Resolve) -> Unit = {},
        applyTurnFailureDisplay: (Throwable) -> Unit = {},
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

private class CompletionRuntimeLog : RuntimeEventLogPort {
    val events = mutableListOf<String>()

    override fun append(
        event: String,
        nowMillis: Long,
    ) {
        events += event
    }

    override fun readText(): String = events.joinToString("\n")

    override fun clear() {
        events.clear()
    }
}
