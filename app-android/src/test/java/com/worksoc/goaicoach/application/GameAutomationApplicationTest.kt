package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.operation.*

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.savedgame.*

import com.worksoc.goaicoach.application.endgame.*
import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*
import com.worksoc.goaicoach.application.topmoves.*

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.match.TurnOutcome
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.EndgameScoreSource
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.aiMoveAnalysisLimitWith
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAutomationApplicationTest {
    @Test
    fun aiTurnRunsOnlyForReadyIdleAiSeat() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        assertTrue(
            shouldRequestAiTurn(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                gameState = GameState.empty(),
            ),
        )
        assertFalse(
            shouldRequestAiTurn(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = true,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                gameState = GameState.empty(),
            ),
        )
    }

    @Test
    fun topMoveAnalysisRunsOnlyForReadyIdleHumanSeatWithoutResumePrompt() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        assertTrue(
            shouldRequestTopMoveAnalysis(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = false,
                playerSetup = setup,
                targetState = GameState.empty(),
            ),
        )
        assertFalse(
            shouldRequestTopMoveAnalysis(
                isGameEnded = false,
                isEngineReady = true,
                isEngineBusy = false,
                shouldShowResumePrompt = true,
                playerSetup = setup,
                targetState = GameState.empty(),
            ),
        )
    }

    @Test
    fun autoAiTurnDelayAppliesOnlyWhenBothSeatsAreAi() {
        val autoSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val humanVsAiSetup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        assertEquals(
            AutoPlayDelaySetting.Slow.millis,
            autoAiTurnDelayMillis(autoSetup, AutoPlayDelaySetting.Slow),
        )
        assertEquals(
            0L,
            autoAiTurnDelayMillis(humanVsAiSetup, AutoPlayDelaySetting.Slow),
        )
    }

    @Test
    fun autoAiTurnUiStateTracksPendingSchedule() {
        val scheduled = AutoAiTurnUiState().markScheduled()
        val cleared = scheduled.clearPending()

        assertTrue(scheduled.isPending)
        assertFalse(cleared.isPending)
    }

    @Test
    fun autoAiTurnUiStateAppliesRequestValidationAndCompletionPlans() {
        val initial = AutoAiTurnUiState()
        val scheduled = initial.applyAutoAiTurnRequestPlan(
            AutoAiTurnRequestPlan.Schedule(delayMillis = 500L),
        )
        val runPlan = AutoAiTurnRunPlan(
            delayMillis = 500L,
            context = buildAutoAiTurnExecutionContext(
                gameState = GameState.empty(),
                playerSetup = PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Ai),
                    white = SidePlayerSetup(controller = SeatController.Human),
                ),
                searchTimeSettings = SearchTimeSettings(),
                reviewCandidateMoves = emptyList(),
            ),
        )

        assertFalse(initial.applyAutoAiTurnRequestPlan(AutoAiTurnRequestPlan.Skip).isPending)
        assertTrue(scheduled.isPending)
        assertTrue(
            scheduled
                .applyAutoAiTurnScheduleValidationPlan(AutoAiTurnScheduleValidationPlan.Continue(runPlan))
                .isPending,
        )
        assertFalse(
            scheduled
                .applyAutoAiTurnScheduleValidationPlan(AutoAiTurnScheduleValidationPlan.Cancel)
                .isPending,
        )
        assertFalse(scheduled.completeAutoAiTurnRun().isPending)
    }

    @Test
    fun autoAiTurnRequestPlanSkipsWhenAlreadyPending() {
        val plan = buildAutoAiTurnRequestPlan(
            isGameEnded = false,
            isEngineReady = true,
            isEngineBusy = false,
            isAutoAiTurnPending = true,
            shouldShowResumePrompt = false,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            gameState = GameState.empty(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
        )

        assertEquals(AutoAiTurnRequestPlan.Skip, plan)
    }

    @Test
    fun autoAiTurnRequestPlanSchedulesWithAutoPlayDelay() {
        val plan = buildAutoAiTurnRequestPlan(
            isGameEnded = false,
            isEngineReady = true,
            isEngineBusy = false,
            isAutoAiTurnPending = false,
            shouldShowResumePrompt = false,
            playerSetup = PlayerSetup(
                black = SidePlayerSetup(controller = SeatController.Ai),
                white = SidePlayerSetup(controller = SeatController.Ai),
            ),
            gameState = GameState.empty(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
        )

        assertEquals(
            AutoAiTurnRequestPlan.Schedule(AutoPlayDelaySetting.Slow.millis),
            plan,
        )
    }

    @Test
    fun controllerStateBuildsAutoAiTurnRequestPlan() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )
        val controller = automationControllerState(
            playerSetup = setup,
            autoPlayDelaySetting = AutoPlayDelaySetting.Slow,
        )

        val plan = controller.toAutoAiTurnRequestPlan(
            isEngineReady = true,
            isEngineBusy = false,
        )

        assertEquals(
            AutoAiTurnRequestPlan.Schedule(AutoPlayDelaySetting.Slow.millis),
            plan,
        )
    }

    @Test
    fun autoAiTriggerEffectWaitsForUndoQuietWindowBeforeRequestingTurn() = runBlocking {
        val delays = mutableListOf<Long>()
        val calls = mutableListOf<String>()

        runAutoAiTurnTriggerEffect(
            quietUntilMillis = 1_500L,
            nowMillis = { 1_000L },
            delayMillis = { millis -> delays += millis },
            requestAiTurn = { calls += "request-ai" },
        )

        assertEquals(listOf(500L), delays)
        assertEquals(listOf("request-ai"), calls)
    }

    @Test
    fun topMovesTriggerEffectRunsImmediatelyAfterQuietWindow() = runBlocking {
        val delays = mutableListOf<Long>()
        val calls = mutableListOf<String>()

        runTopMoveAnalysisTriggerEffect(
            quietUntilMillis = 1_000L,
            nowMillis = { 1_500L },
            delayMillis = { millis -> delays += millis },
            requestTopMoveAnalysis = { calls += "request-top-moves" },
        )

        assertTrue(delays.isEmpty())
        assertEquals(listOf("request-top-moves"), calls)
    }

    @Test
    fun turnAutomationTriggerWaitsOnceAndRunsAiBeforeTopMovesForCapturedState() = runBlocking {
        val delays = mutableListOf<Long>()
        val calls = mutableListOf<String>()
        val targetState = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        runTurnAutomationTriggerEffect(
            quietUntilMillis = 2_000L,
            topMoveTargetState = targetState,
            nowMillis = { 1_250L },
            delayMillis = { millis -> delays += millis },
            requestAiTurn = { calls += "request-ai" },
            requestTopMoveAnalysis = { state -> calls += "top-moves:${state.moves.size}" },
        )

        assertEquals(listOf(750L), delays)
        assertEquals(listOf("request-ai", "top-moves:1"), calls)
    }

    @Test
    fun controllerStateBuildsAutoAiTurnExecutionContext() {
        val whiteLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = whiteLevel,
            ),
        )
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val reviewCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)
        val controller = automationControllerState(
            gameState = state,
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
            reviewCandidateMoves = listOf(reviewCandidate),
        )

        val context = controller.toAutoAiTurnExecutionContext()

        assertEquals(state, context.turnState)
        assertEquals(StoneColor.White, context.aiPlayer)
        assertEquals(whiteLevel, context.playLevel)
        assertEquals(32, context.analysisLimit.visits)
        assertEquals(4_000L, context.analysisLimit.timeMillis)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, context.searchMode)
        assertEquals(listOf(reviewCandidate), context.previousReviewCandidates)
    }

    @Test
    fun autoAiTurnResultGuardAppliesOnlyToRequestedPosition() {
        val state = GameState.empty()
        val changedState = state
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val runPlan = AutoAiTurnRunPlan(
            delayMillis = 0L,
            context = buildAutoAiTurnExecutionContext(
                gameState = state,
                playerSetup = PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Ai),
                    white = SidePlayerSetup(controller = SeatController.Human),
                ),
                searchTimeSettings = SearchTimeSettings(),
                reviewCandidateMoves = emptyList(),
            ),
        )
        val token = autoAiTurnOperationToken(runPlan)

        assertEquals(EngineOperationKind.AutoAiTurn, token.operation.kind)
        assertEquals(EngineFallbackPolicy.None, token.operation.fallbackPolicy)
        assertEquals(runPlan.context.analysisLimit.timeMillis, token.operation.timeoutPolicy.timeoutMillis)
        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluateAutoAiTurnResultGuard(token, state),
        )
        assertTrue(evaluateAutoAiTurnResultGuard(token, changedState) is EngineOperationResultGuard.Discard)
    }

    @Test
    fun autoAiEndgameResultGuardAppliesOnlyToRequestedPosition() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val changedState = GameState.empty()
        val plan = AutoAiTurnEndgamePlan.Resolve(
            state = state,
            profile = EngineProfile(),
            prePassCandidates = emptyList(),
            engineMessagePrefix = "pass/pass",
        )
        val token = autoAiEndgameOperationToken(plan)

        assertEquals(EngineOperationKind.AutoAiEndgame, token.operation.kind)
        assertEquals(EngineFallbackPolicy.LocalRules, token.operation.fallbackPolicy)
        assertEquals(plan.profile.analysisLimit.timeMillis, token.operation.timeoutPolicy.timeoutMillis)
        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluateAutoAiEndgameResultGuard(token, state),
        )
        assertTrue(evaluateAutoAiEndgameResultGuard(token, changedState) is EngineOperationResultGuard.Discard)
    }

    @Test
    fun autoAiTurnCompletionPlanAppliesOnlyForCurrentOperationState() {
        val state = GameState.empty()
        val changedState = state
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val runPlan = AutoAiTurnRunPlan(
            delayMillis = 0L,
            context = buildAutoAiTurnExecutionContext(
                gameState = state,
                playerSetup = PlayerSetup(
                    black = SidePlayerSetup(controller = SeatController.Ai),
                    white = SidePlayerSetup(controller = SeatController.Human),
                ),
                searchTimeSettings = SearchTimeSettings(),
                reviewCandidateMoves = emptyList(),
            ),
        )
        val token = autoAiTurnOperationToken(runPlan, sessionGeneration = 2L)
        val display = buildAutoAiTurnDisplayPlan(
            result = autoAiTurnResult(
                state = changedState,
                estimate = null,
            ),
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        val success = buildAutoAiTurnSuccessCompletionPlan(
            token = token,
            currentState = state,
            currentSessionGeneration = 2L,
            display = display,
        )
        val failure = buildAutoAiTurnFailureCompletionPlan(
            token = token,
            currentState = state,
            currentSessionGeneration = 2L,
            error = IllegalStateException("turn failed"),
        )
        val stalePosition = buildAutoAiTurnSuccessCompletionPlan(
            token = token,
            currentState = changedState,
            currentSessionGeneration = 2L,
            display = display,
        )
        val staleGeneration = buildAutoAiTurnFailureCompletionPlan(
            token = token,
            currentState = state,
            currentSessionGeneration = 3L,
            error = IllegalStateException("turn failed"),
        )

        assertTrue(success is AutoAiTurnCompletionPlan.ApplySuccess)
        assertEquals(display, (success as AutoAiTurnCompletionPlan.ApplySuccess).display)
        assertTrue(failure is AutoAiTurnCompletionPlan.ApplyFailure)
        assertEquals("turn failed", (failure as AutoAiTurnCompletionPlan.ApplyFailure).error.message)
        assertTrue(stalePosition is AutoAiTurnCompletionPlan.Discard)
        assertTrue(staleGeneration is AutoAiTurnCompletionPlan.Discard)
    }

    @Test
    fun autoAiEndgameCompletionPlanAppliesResolvedFailedOrDiscardedResult() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val changedState = GameState.empty()
        val plan = AutoAiTurnEndgamePlan.Resolve(
            state = state,
            profile = EngineProfile(),
            prePassCandidates = emptyList(),
            engineMessagePrefix = "pass/pass",
        )
        val token = autoAiEndgameOperationToken(plan, sessionGeneration = 2L)
        val resolved = AutoAiTurnEndgameDisplayPlan.Resolved(
            resolution = aiEndgameResolution(state),
            display = buildResolvedEndgameDisplayPlan(
                source = plan.successSource,
                originalState = state,
                resolution = aiEndgameResolution(state),
                previousSnapshots = emptyList(),
                engineMessagePrefix = plan.engineMessagePrefix,
            ),
        )
        val failed = AutoAiTurnEndgameDisplayPlan.Failed(
            error = IllegalStateException("final failed"),
            display = buildEndgameFailureDisplayPlan(
                source = plan.failureSource,
                state = state,
                errorMessage = "final failed",
                engineMessagePrefix = plan.engineMessagePrefix,
            ),
        )

        val resolvedCompletion = buildAutoAiEndgameCompletionPlan(
            token = token,
            currentState = state,
            currentSessionGeneration = 2L,
            display = resolved,
        )
        val failedCompletion = buildAutoAiEndgameCompletionPlan(
            token = token,
            currentState = state,
            currentSessionGeneration = 2L,
            display = failed,
        )
        val discarded = buildAutoAiEndgameCompletionPlan(
            token = token,
            currentState = changedState,
            currentSessionGeneration = 2L,
            display = resolved,
        )

        assertTrue(resolvedCompletion is AutoAiEndgameCompletionPlan.ApplyResolved)
        assertTrue(failedCompletion is AutoAiEndgameCompletionPlan.ApplyFailed)
        assertTrue(discarded is AutoAiEndgameCompletionPlan.Discard)
    }

    @Test
    fun controllerStateValidatesScheduledAutoAiTurnAndBuildsContext() {
        val whiteLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = whiteLevel,
            ),
        )
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val reviewCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)
        val controller = automationControllerState(
            gameState = state,
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
            reviewCandidateMoves = listOf(reviewCandidate),
        )

        val plan = controller.toAutoAiTurnScheduleValidationPlan(
            isEngineReady = true,
            isEngineBusy = false,
            scheduledDelayMillis = 750L,
        )

        assertTrue(plan is AutoAiTurnScheduleValidationPlan.Continue)
        val runPlan = (plan as AutoAiTurnScheduleValidationPlan.Continue).runPlan
        val context = runPlan.context
        assertEquals(750L, runPlan.delayMillis)
        assertEquals(state, context.turnState)
        assertEquals(StoneColor.White, context.aiPlayer)
        assertEquals(whiteLevel, context.playLevel)
        assertEquals(32, context.analysisLimit.visits)
        assertEquals(4_000L, context.analysisLimit.timeMillis)
        assertEquals(listOf(reviewCandidate), context.previousReviewCandidates)
    }

    @Test
    fun controllerStateCancelsScheduledAutoAiTurnWhenGateChanges() {
        val controller = automationControllerState()

        val engineBusy = controller.toAutoAiTurnScheduleValidationPlan(
            isEngineReady = true,
            isEngineBusy = true,
        )
        val resumePrompt = controller
            .withSavedSession(
                SavedSessionUiState(
                    shouldShowResumePrompt = true,
                    hasCheckedSavedSession = true,
                ),
            )
            .toAutoAiTurnScheduleValidationPlan(
                isEngineReady = true,
                isEngineBusy = false,
            )

        assertEquals(AutoAiTurnScheduleValidationPlan.Cancel, engineBusy)
        assertEquals(AutoAiTurnScheduleValidationPlan.Cancel, resumePrompt)
    }

    @Test
    fun autoAiTurnExecutionContextUsesCurrentAiSeatLevelAndSearchTime() {
        val whiteLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 7)
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Human),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = whiteLevel,
            ),
        )
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val reviewCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)

        val context = buildAutoAiTurnExecutionContext(
            gameState = state,
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b32Millis = 4_000L),
            reviewCandidateMoves = listOf(reviewCandidate),
        )

        assertEquals(state, context.turnState)
        assertEquals(StoneColor.White, context.aiPlayer)
        assertEquals(whiteLevel, context.playLevel)
        assertEquals(32, context.analysisLimit.visits)
        assertEquals(4_000L, context.analysisLimit.timeMillis)
        assertEquals(true, context.analysisLimit.includePolicy)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, context.searchMode)
        assertFalse(context.isolateSearchCache)
        assertEquals(listOf(reviewCandidate), context.previousReviewCandidates)
    }

    @Test
    fun fastBeginnerAutoAiTurnExecutionContextUsesGtpBestOnly() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.FastBeginner, level = 2),
            ),
            white = SidePlayerSetup(controller = SeatController.Human),
        )

        val context = buildAutoAiTurnExecutionContext(
            gameState = GameState.empty(),
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(b16Millis = 1_500L),
            reviewCandidateMoves = emptyList(),
        )

        assertEquals(EngineSearchMode.GtpStatefulFast, context.searchMode)
        assertEquals(16, context.analysisLimit.visits)
        assertEquals(1_500L, context.analysisLimit.timeMillis)
        assertEquals(1, context.analysisLimit.candidateCount)
        assertEquals(false, context.analysisLimit.includePolicy)
    }

    @Test
    fun autoAiTurnExecutionContextIsolatesSearchCacheOnlyForAiVsAi() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(controller = SeatController.Ai),
            white = SidePlayerSetup(controller = SeatController.Ai),
        )

        val context = buildAutoAiTurnExecutionContext(
            gameState = GameState.empty(),
            playerSetup = setup,
            searchTimeSettings = SearchTimeSettings(),
            reviewCandidateMoves = emptyList(),
        )

        assertTrue(context.isolateSearchCache)
    }

    @Test
    fun autoAiTurnDisplayPlanUsesEngineEstimateWhenAvailable() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimate ready"),
            whiteScoreLead = 1.5,
            whiteWinRate = 0.6,
            summary = "estimate",
        )
        val result = autoAiTurnResult(state, estimate)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertEquals(state, plan.gameState)
        assertEquals(result.playLevel.analysisPreset, plan.analysisPreset)
        assertEquals("candidate text", plan.candidateText)
        assertEquals(estimate, plan.scoreDisplay.scoreEstimate)
        assertEquals("estimate ready", plan.scoreDisplay.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreDisplay.scoreSnapshots.single().source)
        assertFalse(plan.shouldResolveEndgame)
        assertEquals(state, plan.nextAnalysisState)
    }

    @Test
    fun autoAiTurnDisplayPlanFallsBackToLocalSnapshotWhenEstimateIsMissing() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val result = autoAiTurnResult(state, estimate = null)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertNull(plan.scoreDisplay.scoreEstimate)
        assertEquals("Score estimate not current.", plan.scoreDisplay.scoreText)
        assertEquals("engine text", plan.scoreDisplay.engineMessage)
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreDisplay.scoreSnapshots.single().source)
    }

    @Test
    fun autoAiTurnDisplayPlanCarriesPrePassCandidatesForEndgameResolution() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val passCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)
        val result = autoAiTurnResult(state, estimate = null)

        val plan = buildAutoAiTurnDisplayPlan(
            result = result,
            previousSnapshots = emptyList(),
            previousReviewCandidates = listOf(passCandidate),
        )

        assertTrue(plan.shouldResolveEndgame)
        assertNull(plan.nextAnalysisState)
        assertEquals(listOf(passCandidate), plan.endgamePrePassCandidates)
    }

    @Test
    fun autoAiTurnFollowUpPlanRequestsTopMoveAnalysisOnlyForContinuingGame() {
        val continuingState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val endedState = continuingState
            .play(Move.Pass(StoneColor.White))

        val continuing = buildAutoAiTurnFollowUpPlan(
            buildAutoAiTurnDisplayPlan(
                result = autoAiTurnResult(continuingState, estimate = null),
                previousSnapshots = emptyList(),
                previousReviewCandidates = emptyList(),
            ),
        )
        val ended = buildAutoAiTurnFollowUpPlan(
            buildAutoAiTurnDisplayPlan(
                result = autoAiTurnResult(endedState, estimate = null),
                previousSnapshots = emptyList(),
                previousReviewCandidates = emptyList(),
            ),
        )

        assertEquals(
            AutoAiTurnFollowUpPlan.RequestTopMoveAnalysis(continuingState),
            continuing,
        )
        assertEquals(AutoAiTurnFollowUpPlan.None, ended)
    }

    @Test
    fun autoAiTurnFollowUpPlanBuildsNullableTopMoveRequestForUiRunner() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val request = AutoAiTurnFollowUpPlan
            .RequestTopMoveAnalysis(state)
            .toAutoAiTurnFollowUpRequest()

        assertEquals(state, request?.targetState)
        assertEquals(true, request?.automatic)
        assertEquals(false, request?.deep)
        assertNull(AutoAiTurnFollowUpPlan.None.toAutoAiTurnFollowUpRequest())
    }

    @Test
    fun autoAiTurnEndgamePlanResolvesOnlyForConsecutivePasses() {
        val continuingState = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
        val endedState = continuingState
            .play(Move.Pass(StoneColor.White))
        val passCandidate = CandidateMove(Move.Pass(StoneColor.White), pointLoss = 0.0)

        val continuing = buildAutoAiTurnEndgamePlan(
            buildAutoAiTurnDisplayPlan(
                result = autoAiTurnResult(continuingState, estimate = null),
                previousSnapshots = emptyList(),
                previousReviewCandidates = emptyList(),
            ),
        )
        val ended = buildAutoAiTurnEndgamePlan(
            buildAutoAiTurnDisplayPlan(
                result = autoAiTurnResult(endedState, estimate = null),
                previousSnapshots = emptyList(),
                previousReviewCandidates = listOf(passCandidate),
            ),
        )

        assertEquals(AutoAiTurnEndgamePlan.None, continuing)
        assertTrue(ended is AutoAiTurnEndgamePlan.Resolve)
        val resolve = ended as AutoAiTurnEndgamePlan.Resolve
        assertEquals(endedState, resolve.state)
        assertEquals(listOf(passCandidate), resolve.prePassCandidates)
        assertEquals("engine text", resolve.engineMessagePrefix)
        assertEquals("auto-ai-engine-dead-stone-cleanup", resolve.successSource)
        assertEquals("auto-ai-engine-final-score-failed", resolve.failureSource)
    }

    @Test
    fun autoAiTurnDisplayRunnerDelegatesToEngineSessionAndBuildsDisplayPlan() = runBlocking {
        val initialState = GameState.empty()
        val nextState = initialState.play(Move.Pass(StoneColor.Black))
        val playLevel = PlayLevelSetting()
        val client = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(
                state = nextState,
                estimate = ScoreEstimate(
                    status = EngineStatus.ready("estimate ready"),
                    whiteScoreLead = 0.0,
                    whiteWinRate = 0.5,
                    summary = "estimate",
                ),
            ),
        )

        val display = client.runAutoAiTurnDisplayPlan(
            currentState = initialState,
            playLevel = playLevel,
            currentProfile = EngineProfile(),
            searchTimeSettings = SearchTimeSettings(),
            searchMode = EngineSearchMode.GtpStatefulFast,
            isolateSearchCache = true,
            previousSnapshots = emptyList(),
            previousReviewCandidates = emptyList(),
        )

        assertEquals(initialState, client.currentState)
        assertEquals(playLevel, client.playLevel)
        assertEquals(EngineSearchMode.GtpStatefulFast, client.searchMode)
        assertEquals(true, client.isolateSearchCache)
        assertEquals(nextState, display.gameState)
        assertEquals("candidate text", display.candidateText)
        assertEquals("estimate ready", display.scoreDisplay.engineMessage)
    }

    @Test
    fun autoAiTurnEffectRunnerDelegatesEffectPlanAndExecutionContext() = runBlocking {
        val initialState = GameState.empty()
        val nextState = initialState.play(Move.Pass(StoneColor.Black))
        val previousCandidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            pointLoss = 0.0,
        )
        val playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 7)
        val searchTimeSettings = SearchTimeSettings(
            b16Millis = 1_000,
            b32Millis = 2_000,
            b64Millis = 3_000,
        )
        val profile = EngineProfile(name = "effect-profile")
        val client = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(state = nextState, estimate = null),
        )
        val runPlan = AutoAiTurnRunPlan(
            delayMillis = 250L,
            context = AutoAiTurnExecutionContext(
                turnState = initialState,
                aiPlayer = StoneColor.Black,
                playLevel = playLevel,
                analysisLimit = playLevel.aiMoveAnalysisLimitWith(searchTimeSettings),
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                isolateSearchCache = true,
                previousReviewCandidates = listOf(previousCandidate),
            ),
        )

        val display = client.runAutoAiTurnEffect(
            effect = GameSessionEffect.RunAutoAiTurn(runPlan),
            executionContext = AutoAiTurnRunExecutionContext(
                currentProfile = profile,
                searchTimeSettings = searchTimeSettings,
                previousSnapshots = emptyList(),
            ),
        )

        assertEquals(nextState, display.gameState)
        assertEquals(initialState, client.currentState)
        assertEquals(playLevel, client.playLevel)
        assertEquals(profile, client.currentProfile)
        assertEquals(searchTimeSettings, client.searchTimeSettings)
        assertEquals(EngineSearchMode.JsonPositionAnalysis, client.searchMode)
        assertEquals(true, client.isolateSearchCache)
    }

    @Test
    fun autoAiTurnWorkflowResultWrapsSuccessAndFailure() = runBlocking {
        val initialState = GameState.empty()
        val nextState = initialState.play(Move.Pass(StoneColor.Black))
        val playLevel = PlayLevelSetting(group = PlayLevelGroup.Beginner, level = 7)
        val searchTimeSettings = SearchTimeSettings()
        val runPlan = AutoAiTurnRunPlan(
            delayMillis = 0L,
            context = AutoAiTurnExecutionContext(
                turnState = initialState,
                aiPlayer = StoneColor.Black,
                playLevel = playLevel,
                analysisLimit = playLevel.aiMoveAnalysisLimitWith(searchTimeSettings),
                searchMode = EngineSearchMode.GtpStatefulFast,
                isolateSearchCache = false,
                previousReviewCandidates = emptyList(),
            ),
        )
        val effect = GameSessionEffect.RunAutoAiTurn(runPlan)
        val executionContext = AutoAiTurnRunExecutionContext(
            currentProfile = EngineProfile(),
            searchTimeSettings = searchTimeSettings,
            previousSnapshots = emptyList(),
        )

        val success = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(state = nextState, estimate = null),
        ).runAutoAiTurnWorkflowResult(
            effect = effect,
            executionContext = executionContext,
        )
        val failure = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(state = nextState, estimate = null),
            turnError = IllegalStateException("turn failed"),
        ).runAutoAiTurnWorkflowResult(
            effect = effect,
            executionContext = executionContext,
        )
        val completion = buildAutoAiTurnCompletionPlan(
            result = success,
            token = autoAiTurnOperationToken(runPlan, sessionGeneration = 2L),
            currentState = initialState,
            currentSessionGeneration = 2L,
        )

        assertTrue(success is AutoAiTurnWorkflowResult.Success)
        assertTrue(completion is AutoAiTurnCompletionPlan.ApplySuccess)
        assertTrue(failure is AutoAiTurnWorkflowResult.Failure)
        assertEquals("turn failed", (failure as AutoAiTurnWorkflowResult.Failure).error.message)
    }

    @Test
    fun autoAiEndgameDisplayRunnerBuildsResolvedDisplayPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val resolution = aiEndgameResolution(state)
        val client = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(state, estimate = null),
            endgameResolution = resolution,
        )
        val plan = AutoAiTurnEndgamePlan.Resolve(
            state = state,
            profile = EngineProfile(name = "KataGo"),
            prePassCandidates = emptyList(),
            engineMessagePrefix = "pass/pass",
        )

        val display = client.runAutoAiEndgameDisplayPlan(
            plan = plan,
            previousSnapshots = emptyList(),
        )

        assertTrue(display is AutoAiTurnEndgameDisplayPlan.Resolved)
        val resolved = display as AutoAiTurnEndgameDisplayPlan.Resolved
        assertEquals(resolution, resolved.resolution)
        assertEquals(state, client.resolvedEndgameState)
        assertEquals("KataGo", client.resolvedEndgameProfile?.name)
        assertTrue(resolved.display.scoreText.contains("Final: B+0.5"))
        assertTrue(resolved.display.endgameLog.contains("source=auto-ai-engine-dead-stone-cleanup"))
        assertTrue(resolved.display.engineMessage.startsWith("pass/pass\n"))
    }

    @Test
    fun autoAiEndgameEffectRunnerUsesEffectPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val resolution = aiEndgameResolution(state)
        val client = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(state, estimate = null),
            endgameResolution = resolution,
        )
        val plan = AutoAiTurnEndgamePlan.Resolve(
            state = state,
            profile = EngineProfile(name = "EffectProfile"),
            prePassCandidates = emptyList(),
            engineMessagePrefix = "effect pass/pass",
        )

        val display = client.runAutoAiEndgameEffect(
            effect = GameSessionEffect.ResolveAutoAiEndgame(plan),
            previousSnapshots = emptyList(),
        )

        assertTrue(display is AutoAiTurnEndgameDisplayPlan.Resolved)
        assertEquals(state, client.resolvedEndgameState)
        assertEquals("EffectProfile", client.resolvedEndgameProfile?.name)
    }

    @Test
    fun autoAiEndgameDisplayRunnerBuildsFailureDisplayPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val client = FakeAutoAiEngineSessionClient(
            result = autoAiTurnResult(state, estimate = null),
            endgameError = IllegalStateException("engine timeout"),
        )
        val plan = AutoAiTurnEndgamePlan.Resolve(
            state = state,
            profile = EngineProfile(),
            prePassCandidates = emptyList(),
            engineMessagePrefix = "pass/pass",
        )

        val display = client.runAutoAiEndgameDisplayPlan(
            plan = plan,
            previousSnapshots = emptyList(),
        )

        assertTrue(display is AutoAiTurnEndgameDisplayPlan.Failed)
        val failed = display as AutoAiTurnEndgameDisplayPlan.Failed
        assertEquals("engine timeout", failed.error.message)
        assertTrue(failed.display.endgameLog.contains("source=auto-ai-engine-final-score-failed"))
        assertTrue(failed.display.engineMessage.contains("Final score failed: engine timeout"))
        assertTrue(failed.display.candidateText.contains("final score failed"))
    }

    private fun autoAiTurnResult(
        state: GameState,
        estimate: ScoreEstimate?,
    ): AutoAiTurnResult =
        AutoAiTurnResult(
            turnOutcome = TurnOutcome(
                gameState = state,
                engineMessage = "engine text",
                candidateText = "candidate text",
                lastMoveText = "last move",
            ),
            scoreEstimate = estimate,
            profile = EngineProfile(),
            playLevel = PlayLevelSetting(),
        )

    private fun aiEndgameResolution(state: GameState): AiEndgameResolution {
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("Final score"),
            rawScore = "B+0.5",
            winner = StoneColor.Black,
            margin = 0.5,
            summary = "Final score",
        )
        return AiEndgameResolution(
            cleanup = DeadStoneCleanupResult(state = state, removedStones = emptyList()),
            finalScore = finalScore,
            scoreSource = EndgameScoreSource.CleanedLocalArea,
            localFinalScore = finalScore,
            deadStonesResult = null,
            deadStonesError = null,
            locallyInferredDeadStones = emptyList(),
            engineScoreEstimate = null,
            engineScoreEstimateError = null,
            engineFinalScore = null,
            engineFinalScoreError = null,
            prePassCandidates = emptyList(),
        )
    }
}

private fun automationControllerState(
    gameState: GameState = GameState.empty(),
    playerSetup: PlayerSetup = PlayerSetup(
        black = SidePlayerSetup(controller = SeatController.Ai),
        white = SidePlayerSetup(controller = SeatController.Human),
    ),
    autoPlayDelaySetting: AutoPlayDelaySetting = AutoPlayDelaySetting.Default,
    searchTimeSettings: SearchTimeSettings = SearchTimeSettings(),
    reviewCandidateMoves: List<CandidateMove> = emptyList(),
): GameSessionControllerState =
    GameSessionControllerState(
        core = GameSessionCoreState(
            gameState = gameState,
            isGameEnded = false,
            analysisState = GameSessionAnalysisState.empty(gameState)
                .copy(reviewCandidateMoves = reviewCandidateMoves),
            scoreState = GameSessionScoreState.reset(
                scoreText = "score",
                scoreSnapshots = listOf(localScoreSnapshot(gameState)),
                endgameLog = "endgame",
            ),
            runtimeState = GameSessionRuntimeState(
                playLevel = PlayLevelSetting(),
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
            ),
            moveReviewState = GameSessionMoveReviewState.reset(
                moveReviewText = "review",
                lastMoveText = "none",
            ),
            engineMessage = "engine",
        ),
        settings = GameSessionSettingsState(
            playerSetup = playerSetup,
            autoPlayDelaySetting = autoPlayDelaySetting,
            searchTimeSettings = searchTimeSettings,
            topMovesEnabled = false,
        ),
        benchmark = EngineBenchmarkUiState.initial(
            benchmarkText = "benchmark",
            profile = null,
        ),
        savedSession = SavedSessionUiState(),
        autoAiTurn = AutoAiTurnUiState(),
        positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
    )

private class FakeAutoAiEngineSessionClient(
    private val result: AutoAiTurnResult,
    private val endgameResolution: AiEndgameResolution? = null,
    private val endgameError: Throwable? = null,
    private val turnError: Throwable? = null,
) : EngineSessionClient {
    var currentState: GameState? = null
        private set
    var playLevel: PlayLevelSetting? = null
        private set
    var currentProfile: EngineProfile? = null
        private set
    var searchTimeSettings: SearchTimeSettings? = null
        private set
    var isolateSearchCache: Boolean? = null
        private set
    var searchMode: EngineSearchMode? = null
        private set
    var resolvedEndgameState: GameState? = null
        private set
    var resolvedEndgameProfile: EngineProfile? = null
        private set
    var resolvedEndgamePrePassCandidates: List<CandidateMove>? = null
        private set

    override val capabilities: EngineSessionCapabilities =
        EngineSessionCapabilities(supportsDeviceBenchmark = false)

    override fun positionAnalysisCacheStatsText(nowMillis: Long): String =
        "disabled"

    override fun positionAnalysisCacheQualityFor(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
        nowMillis: Long,
    ): PositionAnalysisCacheQuality? = null

    override suspend fun startSession(
        profile: EngineProfile,
        state: GameState,
    ): EngineStartupResult =
        error("not used")

    override suspend fun startNewGame(
        profile: EngineProfile,
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStartupResult =
        error("not used")

    override suspend fun analyzePosition(
        state: GameState,
        limit: AnalysisLimit,
        searchMode: EngineSearchMode,
    ): AnalysisResult =
        error("not used")

    override suspend fun optimizePositionAnalysisCache(
        plan: PositionAnalysisCacheOptimizationPlan,
    ): PositionAnalysisCacheOptimizationResult =
        error("not used")

    override suspend fun syncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("not used")

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate =
        error("not used")

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult {
        this.currentState = currentState
        this.playLevel = playLevel
        this.currentProfile = currentProfile
        this.searchTimeSettings = searchTimeSettings
        this.searchMode = searchMode
        this.isolateSearchCache = isolateSearchCache
        turnError?.let { throw it }
        return result
    }

    override suspend fun syncAfterHumanMove(
        afterMove: GameState,
        profile: EngineProfile,
        move: Move,
        previousReviewCandidates: List<CandidateMove>,
    ): LocalEngineMoveResult =
        error("not used")

    override suspend fun estimateScoreForState(
        state: GameState,
        profile: EngineProfile,
        syncFirst: Boolean,
    ): ScoreEstimate =
        error("not used")

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution {
        resolvedEndgameState = state
        resolvedEndgameProfile = profile
        resolvedEndgamePrePassCandidates = prePassCandidates
        endgameError?.let { throw it }
        return endgameResolution ?: error("not used")
    }

    override suspend fun undoMove(): EngineStatus =
        error("not used")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        error("not used")
}
