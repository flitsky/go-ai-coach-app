package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.score.*

import com.worksoc.goaicoach.match.MatchMode
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStoneCleanupResult
import com.worksoc.goaicoach.shared.EndgameScoreSource
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreDisplayApplicationTest {
    @Test
    fun scoreEstimateRequestPlanBlocksWhileEngineIsBusy() {
        val plan = buildScoreEstimateRequestPlan(
            state = GameState.empty(),
            previousSnapshots = emptyList(),
            isEngineReady = true,
            isEngineBusy = true,
            matchMode = MatchMode.HumanVsAi,
            engineProfile = EngineProfile(),
        )

        assertEquals(
            ScoreEstimateRequestPlan.ShowMessage("Engine is busy. Estimate after the current response."),
            plan,
        )
    }

    @Test
    fun scoreEstimateRequestPlanUsesLocalEstimateForOfflineTwoPlayerMode() {
        val plan = buildScoreEstimateRequestPlan(
            state = GameState.empty(),
            previousSnapshots = emptyList(),
            isEngineReady = false,
            isEngineBusy = false,
            matchMode = MatchMode.LocalTwoPlayer,
            engineProfile = EngineProfile(),
        )

        assertTrue(plan is ScoreEstimateRequestPlan.ShowLocalEstimate)
        val local = plan as ScoreEstimateRequestPlan.ShowLocalEstimate
        assertNull(local.display.scoreEstimate)
        assertTrue(local.display.engineMessage.contains("Local"))
    }

    @Test
    fun scoreEstimateRequestPlanReportsEngineNotReadyForAiModes() {
        val plan = buildScoreEstimateRequestPlan(
            state = GameState.empty(),
            previousSnapshots = emptyList(),
            isEngineReady = false,
            isEngineBusy = false,
            matchMode = MatchMode.HumanVsAi,
            engineProfile = EngineProfile(),
        )

        assertEquals(ScoreEstimateRequestPlan.ShowMessage("Engine is not ready."), plan)
    }

    @Test
    fun scoreEstimateRequestPlanRequestsEngineAndSyncsTwoPlayerModeWhenReady() {
        val state = GameState.empty()
        val profile = EngineProfile()
        val plan = buildScoreEstimateRequestPlan(
            state = state,
            previousSnapshots = emptyList(),
            isEngineReady = true,
            isEngineBusy = false,
            matchMode = MatchMode.LocalTwoPlayer,
            engineProfile = profile,
        )

        assertTrue(plan is ScoreEstimateRequestPlan.RequestEngineEstimate)
        val request = plan as ScoreEstimateRequestPlan.RequestEngineEstimate
        assertEquals(state, request.state)
        assertEquals(profile, request.profile)
        assertTrue(request.syncFirst)
    }

    @Test
    fun scoreEstimateLaunchStateUpdateWrapsRequestAsDisplayMessageOrEffect() {
        val state = GameState.empty()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = false,
        )
        val display = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = emptyList(),
            engineMessage = "local",
        )

        val messageUpdate = ScoreEstimateRequestPlan.ShowMessage("busy")
            .toScoreEstimateLaunchStateUpdate()
        val displayUpdate = ScoreEstimateRequestPlan.ShowLocalEstimate(display)
            .toScoreEstimateLaunchStateUpdate()
        val effectUpdate = request.toScoreEstimateLaunchStateUpdate()

        assertEquals("busy", messageUpdate.engineMessage)
        assertEquals(display, displayUpdate.display)
        assertEquals(request, effectUpdate.effect?.request)
    }

    @Test
    fun scoreEstimateResultGuardRejectsChangedPosition() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = state
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = false,
        )
        val token = scoreEstimateOperationToken(request)

        assertEquals(EngineOperationKind.ScoreEstimate, token.operation.kind)
        assertEquals(EngineFallbackPolicy.LocalRules, token.operation.fallbackPolicy)
        assertEquals(request.profile.analysisLimit.timeMillis, token.operation.timeoutPolicy.timeoutMillis)
        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluateScoreEstimateResultGuard(token, state),
        )
        assertTrue(evaluateScoreEstimateResultGuard(token, changedState) is EngineOperationResultGuard.Discard)
    }

    @Test
    fun scoreEstimateCompletionPlanAppliesSuccessFailureOrDiscard() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = state
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = false,
        )
        val token = scoreEstimateOperationToken(
            request = request,
            sessionGeneration = 7L,
        )
        val display = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = emptyList(),
            engineMessage = "estimated",
        )

        val success = buildScoreEstimateCompletionPlan(
            result = ScoreEstimateWorkflowResult.Success(display),
            token = token,
            currentState = state,
            currentSessionGeneration = 7L,
        )
        val failure = buildScoreEstimateCompletionPlan(
            result = ScoreEstimateWorkflowResult.Failure(IllegalStateException("estimate failed")),
            token = token,
            currentState = state,
            currentSessionGeneration = 7L,
        )
        val discard = buildScoreEstimateCompletionPlan(
            result = ScoreEstimateWorkflowResult.Success(display),
            token = token,
            currentState = changedState,
            currentSessionGeneration = 7L,
        )

        assertTrue(success is ScoreEstimateCompletionPlan.ApplySuccess)
        assertEquals(display, (success as ScoreEstimateCompletionPlan.ApplySuccess).display)
        assertTrue(failure is ScoreEstimateCompletionPlan.ApplyFailure)
        assertEquals("estimate failed", (failure as ScoreEstimateCompletionPlan.ApplyFailure).failure.engineMessage)
        assertTrue(discard is ScoreEstimateCompletionPlan.Discard)
    }

    @Test
    fun scoreEstimateCompletionApplyPlanCarriesDisposition() {
        val state = GameState.empty()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = false,
        )
        val token = scoreEstimateOperationToken(
            request = request,
            sessionGeneration = 7L,
        )
        val display = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = emptyList(),
            engineMessage = "estimated",
        )

        val success = buildScoreEstimateCompletionPlan(
            result = ScoreEstimateWorkflowResult.Success(display),
            token = token,
            currentState = state,
            currentSessionGeneration = 7L,
        ).toApplyPlan()
        val failure = buildScoreEstimateCompletionPlan(
            result = ScoreEstimateWorkflowResult.Failure(IllegalStateException("estimate failed")),
            token = token,
            currentState = state,
            currentSessionGeneration = 7L,
        ).toApplyPlan()

        assertTrue(success is ScoreEstimateCompletionApplyPlan.ApplySuccess)
        assertEquals(display, (success as ScoreEstimateCompletionApplyPlan.ApplySuccess).display)
        assertTrue(failure is ScoreEstimateCompletionApplyPlan.ApplyFailure)
        assertEquals(
            "estimate failed",
            (failure as ScoreEstimateCompletionApplyPlan.ApplyFailure).failure.engineMessage,
        )
    }

    @Test
    fun scoreEstimateFailureDisplayPlanUsesErrorMessageOrDefault() {
        val withMessage = buildScoreEstimateFailureDisplayPlan(IllegalStateException("engine stalled"))
        val withoutMessage = buildScoreEstimateFailureDisplayPlan(Throwable())

        assertEquals("engine stalled", withMessage.engineMessage)
        assertEquals("Score estimate failed.", withoutMessage.engineMessage)
    }

    @Test
    fun scoreSyncCompletionPlanAppliesOnlyForCurrentOperationState() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = state
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.PostUndoSync,
            state = state,
            sessionGeneration = 4L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val display = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = emptyList(),
            engineMessage = "synced",
        )
        val request = ScoreSyncCompletionRequest(
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
        )

        val success = buildScoreSyncSuccessCompletionPlan(
            request = request,
            display = display,
        )
        val failure = buildScoreSyncFailureCompletionPlan(
            request = request,
            error = IllegalStateException("sync failed"),
            fallbackMessage = "fallback",
        )
        val changedPosition = buildScoreSyncSuccessCompletionPlan(
            operation = operation,
            currentState = changedState,
            currentSessionGeneration = 4L,
            display = display,
            followUpAnalysisState = state,
        )
        val changedGeneration = buildScoreSyncFailureCompletionPlan(
            operation = operation,
            currentState = state,
            currentSessionGeneration = 5L,
            error = Throwable(),
            fallbackMessage = "fallback",
            followUpAnalysisState = state,
        )

        assertTrue(success is ScoreSyncCompletionPlan.ApplySuccess)
        assertEquals(display, (success as ScoreSyncCompletionPlan.ApplySuccess).display)
        assertEquals(state, success.followUpAnalysisState)
        assertTrue(failure is ScoreSyncCompletionPlan.ApplyFailure)
        assertEquals("sync failed", (failure as ScoreSyncCompletionPlan.ApplyFailure).engineMessage)
        assertTrue(changedPosition is ScoreSyncCompletionPlan.Discard)
        assertTrue(changedGeneration is ScoreSyncCompletionPlan.Discard)
    }

    @Test
    fun scoreSyncCompletionApplyPlanCarriesDisposition() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.PostUndoSync,
            state = state,
            sessionGeneration = 4L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val display = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = emptyList(),
            engineMessage = "synced",
        )
        val request = ScoreSyncCompletionRequest(
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
        )

        val success = buildScoreSyncSuccessCompletionPlan(
            request = request,
            display = display,
        ).toApplyPlan()
        val failure = buildScoreSyncFailureCompletionPlan(
            request = request,
            error = IllegalStateException("sync failed"),
            fallbackMessage = "fallback",
        ).toApplyPlan()

        assertTrue(success is ScoreSyncCompletionApplyPlan.ApplySuccess)
        assertEquals(display, (success as ScoreSyncCompletionApplyPlan.ApplySuccess).display)
        assertEquals(state, success.followUpAnalysisState)
        assertTrue(failure is ScoreSyncCompletionApplyPlan.ApplyFailure)
        assertEquals("sync failed", (failure as ScoreSyncCompletionApplyPlan.ApplyFailure).engineMessage)
        assertEquals(state, failure.followUpAnalysisState)
    }

    @Test
    fun scoreSyncWorkflowCompletionPlanWrapsRunnerResult() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.PostUndoSync,
            state = state,
            sessionGeneration = 4L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val display = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = emptyList(),
            engineMessage = "synced",
        )

        val success = runScoreSyncWorkflowCompletionPlan(
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
            fallbackMessage = "fallback",
        ) {
            display
        }
        val failure = runScoreSyncWorkflowCompletionPlan(
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
            fallbackMessage = "fallback",
        ) {
            throw IllegalStateException("sync failed")
        }

        assertTrue(success is ScoreSyncCompletionPlan.ApplySuccess)
        assertEquals(display, (success as ScoreSyncCompletionPlan.ApplySuccess).display)
        assertTrue(failure is ScoreSyncCompletionPlan.ApplyFailure)
        assertEquals("sync failed", (failure as ScoreSyncCompletionPlan.ApplyFailure).engineMessage)
    }

    @Test
    fun engineEstimateDisplayPlanRecordsScoreSnapshotAndMessage() {
        val state = GameState.empty()
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -3.5,
            whiteWinRate = 0.25,
            summary = "KataGo estimate",
        )

        val plan = buildEngineEstimateDisplayPlan(
            state = state,
            estimate = estimate,
            previousSnapshots = emptyList(),
        )

        assertEquals(estimate, plan.scoreEstimate)
        assertEquals("estimated", plan.engineMessage)
        assertTrue(plan.scoreText.contains("Black win: 75%"))
        assertEquals(0, plan.scoreSnapshots.single().moveNumber)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun scoreEstimateStateResultSeparatesDomainDataFromDisplayText() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val estimate = ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -2.5,
            whiteWinRate = 0.2,
            summary = "estimate",
        )

        val result = buildEngineScoreEstimateStateResult(
            state = state,
            estimate = estimate,
            previousSnapshots = emptyList(),
        )
        val display = result.toScoreEstimateDisplayPlan(
            scoreText = "display text",
            engineMessage = "display message",
        )

        assertEquals(estimate, result.scoreEstimate)
        assertEquals(ScoreSnapshotSource.EngineEstimate, result.scoreSnapshots.single().source)
        assertEquals("display text", display.scoreText)
        assertEquals("display message", display.engineMessage)
        assertEquals(result.scoreSnapshots, display.scoreSnapshots)
    }

    @Test
    fun finalScoreStateResultSeparatesDomainDataFromDisplayText() {
        val state = GameState.empty()
            .play(Move.Pass(StoneColor.Black))
            .play(Move.Pass(StoneColor.White))
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("B+0.5"),
            rawScore = "B+0.5",
            winner = StoneColor.Black,
            margin = 0.5,
            summary = "final",
        )

        val result = buildLocalFinalScoreStateResult(
            source = "test-final",
            state = state,
            finalScore = finalScore,
            previousSnapshots = emptyList(),
            detail = "test",
        )
        val display = result.toFinalScoreDisplayPlan(
            scoreText = "display score",
            engineMessage = "display message",
            candidateText = "display candidate",
        )

        assertEquals(state, result.gameState)
        assertEquals(ScoreSnapshotSource.FinalScore, result.scoreSnapshots.single().source)
        assertTrue(result.endgameLog.contains("source=test-final"))
        assertEquals("display score", display.scoreText)
        assertEquals("display message", display.engineMessage)
        assertEquals("display candidate", display.candidateText)
        assertEquals(result.endgameLog, display.endgameLog)
    }

    @Test
    fun finalScoreDisplayTextWrapsScoreMessageAndCandidateText() {
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("W+6.5"),
            rawScore = "W+6.5",
            winner = StoneColor.White,
            margin = 6.5,
            summary = "final",
        )

        val text = buildLocalFinalScoreDisplayText(
            finalScore = finalScore,
            engineMessage = "local final",
            candidateText = "ended",
        )

        assertEquals(finalScore.toDisplayText(), text.scoreText)
        assertEquals("local final", text.engineMessage)
        assertEquals("ended", text.candidateText)
    }

    @Test
    fun endgameFailureDisplayTextWrapsFailureMessageAndCandidateText() {
        val text = buildEndgameFailureDisplayText(
            errorMessage = "timeout",
            engineMessagePrefix = "Assistant judge failed.",
        )

        assertEquals("Final score failed: timeout", text.finalScoreText)
        assertEquals(
            "Assistant judge failed.\nFinal score failed: timeout",
            text.engineMessage,
        )
        assertEquals("Game ended after two passes, but final score failed.", text.candidateText)
    }

    @Test
    fun scoreEstimateDisplayRunnerRequestsEngineAndBuildsPlan() = runBlocking {
        val state = GameState.empty()
        val profile = EngineProfile()
        val client = FakeScoreEngineSessionClient()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = profile,
            syncFirst = true,
        )

        val plan = client.runScoreEstimateDisplayPlan(
            request = request,
            previousSnapshots = emptyList(),
        )

        assertEquals(state, client.estimatedState)
        assertEquals(profile, client.estimatedProfile)
        assertEquals(true, client.estimatedSyncFirst)
        assertEquals("estimated", plan.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun scoreEstimateEffectRunnerRequestsEngineAndBuildsPlan() = runBlocking {
        val state = GameState.empty()
        val profile = EngineProfile()
        val client = FakeScoreEngineSessionClient()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = profile,
            syncFirst = true,
        )

        val plan = client.runScoreEstimateEffect(
            effect = GameSessionEffect.RunScoreEstimate(request),
            previousSnapshots = emptyList(),
        )

        assertEquals(state, client.estimatedState)
        assertEquals(profile, client.estimatedProfile)
        assertEquals(true, client.estimatedSyncFirst)
        assertEquals("estimated", plan.engineMessage)
        assertEquals(ScoreSnapshotSource.EngineEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun scoreEstimateWorkflowResultWrapsSuccessAndFailure() = runBlocking {
        val state = GameState.empty()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = true,
        )

        val success = FakeScoreEngineSessionClient()
            .runScoreEstimateWorkflowResult(
                effect = GameSessionEffect.RunScoreEstimate(request),
                previousSnapshots = emptyList(),
            )
        val failure = FakeScoreEngineSessionClient(
            estimateError = IllegalStateException("estimate failed"),
        ).runScoreEstimateWorkflowResult(
            effect = GameSessionEffect.RunScoreEstimate(request),
            previousSnapshots = emptyList(),
        )

        assertTrue(success is ScoreEstimateWorkflowResult.Success)
        assertEquals("estimated", (success as ScoreEstimateWorkflowResult.Success).display.engineMessage)
        assertTrue(failure is ScoreEstimateWorkflowResult.Failure)
        assertEquals("estimate failed", (failure as ScoreEstimateWorkflowResult.Failure).error.message)
    }

    @Test
    fun scoreEstimateEffectCompletionRunnerBuildsCompletionPlan() = runBlocking {
        val state = GameState.empty()
        val changedState = state
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = true,
        )
        val token = scoreEstimateOperationToken(
            request = request,
            sessionGeneration = 3L,
        )
        val launchRequest = ScoreEstimateEffectLaunchRequest(
            effect = GameSessionEffect.RunScoreEstimate(request),
            previousSnapshots = emptyList(),
            token = token,
            currentState = state,
            currentSessionGeneration = 3L,
        )

        val success = FakeScoreEngineSessionClient()
            .runScoreEstimateEffectCompletionPlan(launchRequest)
        val failure = FakeScoreEngineSessionClient(
            estimateError = IllegalStateException("estimate failed"),
        ).runScoreEstimateEffectCompletionPlan(launchRequest)
        val discard = FakeScoreEngineSessionClient()
            .runScoreEstimateEffectCompletionPlan(
                launchRequest.copy(currentState = changedState),
            )

        assertTrue(success is ScoreEstimateCompletionPlan.ApplySuccess)
        assertTrue(failure is ScoreEstimateCompletionPlan.ApplyFailure)
        assertEquals("estimate failed", (failure as ScoreEstimateCompletionPlan.ApplyFailure).failure.engineMessage)
        assertTrue(discard is ScoreEstimateCompletionPlan.Discard)
    }

    @Test
    fun scoreEstimateEffectApplyRunnerBuildsApplyPlan() = runBlocking {
        val state = GameState.empty()
        val request = ScoreEstimateRequestPlan.RequestEngineEstimate(
            state = state,
            profile = EngineProfile(),
            syncFirst = true,
        )
        val launchRequest = ScoreEstimateEffectLaunchRequest(
            effect = GameSessionEffect.RunScoreEstimate(request),
            previousSnapshots = emptyList(),
            token = scoreEstimateOperationToken(
                request = request,
                sessionGeneration = 3L,
            ),
            currentState = state,
            currentSessionGeneration = 3L,
        )

        val success = FakeScoreEngineSessionClient()
            .runScoreEstimateEffectApplyPlan(launchRequest)
        val failure = FakeScoreEngineSessionClient(
            estimateError = IllegalStateException("estimate failed"),
        ).runScoreEstimateEffectApplyPlan(launchRequest)

        assertTrue(success is ScoreEstimateCompletionApplyPlan.ApplySuccess)
        assertTrue(failure is ScoreEstimateCompletionApplyPlan.ApplyFailure)
        assertEquals(
            "estimate failed",
            (failure as ScoreEstimateCompletionApplyPlan.ApplyFailure).failure.engineMessage,
        )
    }

    @Test
    fun scoringRuleSyncRunnerBuildsTrimmedEngineEstimatePlan() = runBlocking {
        val state = GameState.empty()
        val client = FakeScoreEngineSessionClient()
        val previous = listOf(
            ScoreSnapshot(moveNumber = 5, source = ScoreSnapshotSource.EngineEstimate),
        )

        val plan = client.runScoringRuleSyncDisplayPlan(
            state = state,
            profile = EngineProfile(),
            previousSnapshots = previous,
            engineMessage = "rules synced",
        )

        assertEquals(state, client.syncedState)
        assertEquals("rules synced", plan.engineMessage)
        assertEquals(0, plan.scoreSnapshots.single().moveNumber)
    }

    @Test
    fun scoringRuleSyncCompletionRunnerBuildsCompletionPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.ScoringRuleSync,
            state = state,
            sessionGeneration = 4L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val request = ScoringRuleSyncEffectLaunchRequest(
            state = state,
            profile = EngineProfile(),
            previousSnapshots = emptyList(),
            engineMessage = "rules synced",
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
            fallbackMessage = "rules failed",
        )

        val success = FakeScoreEngineSessionClient()
            .runScoringRuleSyncCompletionPlan(request)
        val failure = FakeScoreEngineSessionClient(
            syncError = IllegalStateException("sync failed"),
        ).runScoringRuleSyncCompletionPlan(request)

        assertTrue(success is ScoreSyncCompletionPlan.ApplySuccess)
        assertEquals("rules synced", (success as ScoreSyncCompletionPlan.ApplySuccess).display.engineMessage)
        assertTrue(failure is ScoreSyncCompletionPlan.ApplyFailure)
        assertEquals("sync failed", (failure as ScoreSyncCompletionPlan.ApplyFailure).engineMessage)
    }

    @Test
    fun scoringRuleSyncApplyRunnerBuildsApplyPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.ScoringRuleSync,
            state = state,
            sessionGeneration = 4L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val request = ScoringRuleSyncEffectLaunchRequest(
            state = state,
            profile = EngineProfile(),
            previousSnapshots = emptyList(),
            engineMessage = "rules synced",
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
            fallbackMessage = "rules failed",
        )

        val success = FakeScoreEngineSessionClient()
            .runScoringRuleSyncApplyPlan(request)

        assertTrue(success is ScoreSyncCompletionApplyPlan.ApplySuccess)
        assertEquals(
            "rules synced",
            (success as ScoreSyncCompletionApplyPlan.ApplySuccess).display.engineMessage,
        )
    }

    @Test
    fun postUndoScoreSyncCompletionRunnerBuildsCompletionPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val operation = engineOperationRequest(
            kind = EngineOperationKind.PostUndoSync,
            state = state,
            sessionGeneration = 4L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val request = PostUndoScoreSyncEffectLaunchRequest(
            state = state,
            profile = EngineProfile(),
            previousSnapshots = emptyList(),
            engineMessage = "undo synced",
            operation = operation,
            currentState = state,
            currentSessionGeneration = 4L,
            followUpAnalysisState = state,
            fallbackMessage = "undo sync failed",
        )

        val success = FakeScoreEngineSessionClient()
            .runPostUndoScoreSyncCompletionPlan(request)
        val failure = FakeScoreEngineSessionClient(
            syncError = IllegalStateException("post undo failed"),
        ).runPostUndoScoreSyncCompletionPlan(request)

        assertTrue(success is ScoreSyncCompletionPlan.ApplySuccess)
        assertEquals("undo synced", (success as ScoreSyncCompletionPlan.ApplySuccess).display.engineMessage)
        assertTrue(failure is ScoreSyncCompletionPlan.ApplyFailure)
        assertEquals("post undo failed", (failure as ScoreSyncCompletionPlan.ApplyFailure).engineMessage)
    }

    @Test
    fun restoredGameSyncRunnerUsesRestoreMessageAndFreshTimeline() = runBlocking {
        val state = GameState.empty()
        val client = FakeScoreEngineSessionClient()

        val plan = client.runRestoredGameSyncDisplayPlan(
            state = state,
            profile = EngineProfile(),
        )

        assertEquals(state, client.configuredSyncState)
        assertEquals("Previous game restored and engine state synchronized.", plan.engineMessage)
        assertEquals(1, plan.scoreSnapshots.size)
    }

    @Test
    fun restoredGameSyncCompletionRunnerBuildsCompletionPlan() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val profile = EngineProfile(name = "restored")
        val operation = engineOperationRequest(
            kind = EngineOperationKind.RestoredGameSync,
            state = state,
            sessionGeneration = 5L,
            fallbackPolicy = EngineFallbackPolicy.LocalRules,
        )
        val request = RestoredGameSyncEffectLaunchRequest(
            effect = GameSessionEffect.SyncRestoredGame(state),
            context = RestoredGameSyncExecutionContext(profile = profile),
            operation = operation,
            currentState = state,
            currentSessionGeneration = 5L,
            followUpAnalysisState = state,
            fallbackMessage = "restore failed",
        )

        val success = FakeScoreEngineSessionClient()
            .runRestoredGameSyncCompletionPlan(request)
        val failure = FakeScoreEngineSessionClient(
            configuredSyncError = IllegalStateException("restore sync failed"),
        ).runRestoredGameSyncCompletionPlan(request)

        assertTrue(success is ScoreSyncCompletionPlan.ApplySuccess)
        assertEquals(
            "Previous game restored and engine state synchronized.",
            (success as ScoreSyncCompletionPlan.ApplySuccess).display.engineMessage,
        )
        assertTrue(failure is ScoreSyncCompletionPlan.ApplyFailure)
        assertEquals("restore sync failed", (failure as ScoreSyncCompletionPlan.ApplyFailure).engineMessage)
    }

    @Test
    fun restoredGameSyncEffectRunnerUsesEffectStateAndContextProfile() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val profile = EngineProfile(name = "restored")
        val client = FakeScoreEngineSessionClient()

        val plan = client.runRestoredGameSyncEffect(
            effect = GameSessionEffect.SyncRestoredGame(state),
            context = RestoredGameSyncExecutionContext(profile = profile),
        )

        assertEquals(state, client.configuredSyncState)
        assertEquals(profile, client.configuredSyncProfile)
        assertEquals("Previous game restored and engine state synchronized.", plan.engineMessage)
    }

    @Test
    fun localScoreEstimateDisplayPlanClearsEngineEstimateAndRecordsLocalSnapshot() {
        val state = GameState.empty()

        val plan = buildLocalScoreEstimateDisplayPlan(
            state = state,
            previousSnapshots = listOf(ScoreSnapshot(moveNumber = 0, source = ScoreSnapshotSource.EngineEstimate)),
            engineMessage = "local refreshed",
        )

        assertNull(plan.scoreEstimate)
        assertEquals("local refreshed", plan.engineMessage)
        assertTrue(plan.scoreText.contains("Final:"))
        assertEquals(ScoreSnapshotSource.LocalAreaEstimate, plan.scoreSnapshots.single().source)
    }

    @Test
    fun resolvedEndgameDisplayPlanBuildsFinalScoreLogAndCandidateText() {
        val state = GameState.empty()
        val finalScore = FinalScoreResult(
            status = EngineStatus.ready("final complete"),
            rawScore = "B+0.5",
            winner = StoneColor.Black,
            margin = 0.5,
            summary = "Final score",
        )
        val resolution = AiEndgameResolution(
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

        val plan = buildResolvedEndgameDisplayPlan(
            source = "test-endgame",
            originalState = state,
            resolution = resolution,
            previousSnapshots = emptyList(),
            engineMessagePrefix = "prefix",
        )

        assertEquals(state, plan.gameState)
        assertNull(plan.scoreEstimate)
        assertTrue(plan.scoreText.contains("Final: B+0.5"))
        assertTrue(plan.endgameLog.contains("source=test-endgame"))
        assertTrue(plan.engineMessage.startsWith("prefix\n"))
        assertTrue(plan.candidateText.contains("Game ended after pass/pass"))
        assertEquals(ScoreSnapshotSource.FinalScore, plan.scoreSnapshots.single().source)
    }
}

private class FakeScoreEngineSessionClient(
    private val estimateError: Throwable? = null,
    private val syncError: Throwable? = null,
    private val configuredSyncError: Throwable? = null,
) : EngineSessionClient {
    var estimatedState: GameState? = null
        private set
    var estimatedProfile: EngineProfile? = null
        private set
    var estimatedSyncFirst: Boolean? = null
        private set
    var syncedState: GameState? = null
        private set
    var configuredSyncState: GameState? = null
        private set
    var configuredSyncProfile: EngineProfile? = null
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
    ): ScoreEstimate {
        syncedState = state
        syncError?.let { throw it }
        return testEstimate()
    }

    override suspend fun configureSyncAndEstimateGraphScore(
        state: GameState,
        profile: EngineProfile,
    ): ScoreEstimate {
        configuredSyncState = state
        configuredSyncProfile = profile
        configuredSyncError?.let { throw it }
        return testEstimate()
    }

    override suspend fun runAutoAiTurn(
        currentState: GameState,
        playLevel: PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: SearchTimeSettings,
        searchMode: EngineSearchMode,
        isolateSearchCache: Boolean,
    ): AutoAiTurnResult =
        error("not used")

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
    ): ScoreEstimate {
        estimatedState = state
        estimatedProfile = profile
        estimatedSyncFirst = syncFirst
        estimateError?.let { throw it }
        return testEstimate()
    }

    override suspend fun resolveEndgameForState(
        state: GameState,
        profile: EngineProfile,
        prePassCandidates: List<CandidateMove>,
    ): AiEndgameResolution =
        error("not used")

    override suspend fun undoMove(): EngineStatus =
        error("not used")

    override suspend fun runStartupBenchmark(
        restoreState: GameState,
        nowMillis: Long,
        onProgress: suspend (EngineBenchmarkProgress) -> Unit,
    ): EngineBenchmarkProfile =
        error("not used")

    private fun testEstimate(): ScoreEstimate =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 0.5,
            whiteWinRate = 0.5,
            summary = "estimate",
        )
}
