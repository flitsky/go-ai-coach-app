package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.engine.*
import com.worksoc.goaicoach.application.session.*

import com.worksoc.goaicoach.application.autoai.*

import com.worksoc.goaicoach.application.topmoves.*
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisPreset
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveAnalysisSnapshot
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopMovesApplicationTest {
    @Test
    fun buildTopMoveAnalysisPlanCreatesBoundedLimitAndCacheKey() {
        val state = GameState.empty()
        val profile = EngineProfile()

        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = profile,
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        assertEquals(1, plan.candidateCount)
        assertEquals(1, plan.analysisLimit.candidateCount)
        assertEquals(AnalysisPreset.Lite, plan.analysisKey.preset)
        assertEquals(false, plan.analysisKey.deep)
    }

    @Test
    fun completedAnalysisUpdateBuildsSnapshotAndCachePayload() {
        val state = GameState.empty()
        val coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine)
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val result = AnalysisResult(
            status = EngineStatus.ready("analysis complete"),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.0,
                ),
            ),
            rootVisits = plan.analysisLimit.visits,
            summary = "raw",
        )

        val update = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = result,
            rawCandidateText = "raw candidate text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = true,
        )

        assertEquals(1, update.snapshot.scoredPlayCount)
        assertEquals(coordinate, (update.candidateMoves.single().move as Move.Play).coordinate)
        assertTrue(update.candidateText.contains("Analysis cache miss"))
        assertEquals("analysis complete", update.engineMessage)
        assertNotNull(update.cachedResult)
        assertNotNull(update.undoRestoreResult)
    }

    @Test
    fun completedAnalysisUpdateDoesNotStorePayloadWhenCacheDisabled() {
        val state = GameState.empty()
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val result = AnalysisResult(
            status = EngineStatus.ready("analysis complete"),
            candidates = emptyList(),
            rootVisits = plan.analysisLimit.visits,
            summary = "raw",
        )

        val update = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = result,
            rawCandidateText = "raw candidate text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = false,
            cacheEnabled = false,
        )

        assertTrue(update.candidateText.contains("Analysis cache disabled"))
        assertNull(update.cachedResult)
        assertNull(update.undoRestoreResult)
    }

    @Test
    fun completedAnalysisUpdateRestoresAfterUndoOnlyWhenRootVisitsAreComplete() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val complete = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = AnalysisResult(
                status = EngineStatus.ready("complete"),
                candidates = listOf(candidate),
                rootVisits = plan.analysisLimit.visits,
                summary = "complete",
            ),
            rawCandidateText = "complete text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = false,
            cacheEnabled = false,
        )
        val short = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = AnalysisResult(
                status = EngineStatus.ready("short"),
                candidates = listOf(candidate),
                rootVisits = plan.analysisLimit.visits - 1,
                summary = "short",
            ),
            rawCandidateText = "short text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = false,
            cacheEnabled = false,
        )

        assertNotNull(complete.undoRestoreResult)
        assertNull(short.undoRestoreResult)
    }

    @Test
    fun topMoveAnalysisCompletionPlanAppliesSuccessFailureOrDiscard() {
        val state = GameState.empty()
        val changedState = state
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val token = topMoveAnalysisOperationToken(
            targetState = state,
            plan = plan,
            sessionGeneration = 3L,
        )
        val update = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = AnalysisResult(
                status = EngineStatus.ready("complete"),
                candidates = listOf(candidate),
                rootVisits = plan.analysisLimit.visits,
                summary = "complete",
            ),
            rawCandidateText = "complete text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = true,
        )

        val success = buildTopMoveAnalysisSuccessCompletionPlan(
            token = token,
            currentState = state,
            currentAnalysisKey = plan.analysisKey,
            currentSessionGeneration = 3L,
            update = update,
        )
        val failure = buildTopMoveAnalysisFailureCompletionPlan(
            token = token,
            currentState = state,
            currentAnalysisKey = plan.analysisKey,
            currentSessionGeneration = 3L,
            targetState = state,
            error = IllegalStateException("analysis failed"),
            topMovesEnabled = true,
        )
        val discarded = buildTopMoveAnalysisSuccessCompletionPlan(
            token = token,
            currentState = changedState,
            currentAnalysisKey = plan.analysisKey,
            currentSessionGeneration = 3L,
            update = update,
        )

        assertTrue(success is TopMoveAnalysisCompletionPlan.ApplySuccess)
        assertEquals(update, (success as TopMoveAnalysisCompletionPlan.ApplySuccess).update)
        assertEquals(plan.analysisKey, success.analysisKey)
        assertTrue(failure is TopMoveAnalysisCompletionPlan.ApplyFailure)
        assertEquals("analysis failed", (failure as TopMoveAnalysisCompletionPlan.ApplyFailure).display.engineMessage)
        assertTrue(discarded is TopMoveAnalysisCompletionPlan.Discard)
    }

    @Test
    fun topMoveAnalysisCompletionApplyPlanCarriesCacheUpdateDisposition() {
        val state = GameState.empty()
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val token = topMoveAnalysisOperationToken(
            targetState = state,
            plan = plan,
            sessionGeneration = 3L,
        )
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            pointLoss = 0.0,
        )
        val update = buildCompletedTopMoveAnalysisUpdate(
            targetState = state,
            result = AnalysisResult(
                status = EngineStatus.ready("cached"),
                candidates = listOf(candidate),
                rootVisits = plan.analysisLimit.visits,
                summary = "cached summary",
            ),
            rawCandidateText = "cached candidate text",
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = true,
        )
        val applyPlan = buildTopMoveAnalysisSuccessCompletionPlan(
            token = token,
            currentState = state,
            currentAnalysisKey = plan.analysisKey,
            currentSessionGeneration = 3L,
            update = update,
        ).toApplyPlan()

        assertTrue(applyPlan is TopMoveAnalysisCompletionApplyPlan.ApplySuccess)
        val success = applyPlan as TopMoveAnalysisCompletionApplyPlan.ApplySuccess
        assertEquals(plan.analysisKey, success.analysisKey)
        assertEquals(update, success.update)
        assertEquals(update.cachedResult, success.update.cachedResult)
    }

    @Test
    fun runTopMoveAnalysisDelegatesExplicitStateToEngineClient() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)),
            pointLoss = 0.0,
        )
        val client = FakeTopMoveEngineSessionClient(
            result = AnalysisResult(
                status = EngineStatus.ready("analysis ready"),
                candidates = listOf(candidate),
                summary = "engine summary",
            ),
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val update = client.runTopMoveAnalysis(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            plan = plan,
            deep = false,
            topMovesEnabled = true,
            cacheEnabled = false,
        )

        assertEquals(state, client.analyzedState)
        assertEquals(plan.analysisLimit, client.analyzedLimit)
        assertEquals("analysis ready", update.engineMessage)
        assertEquals(1, update.candidateMoves.size)
        assertNull(update.cachedResult)
    }

    @Test
    fun runTopMoveAnalysisEffectUsesEffectAndExecutionContext() = runBlocking {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D4", BoardSize.Nine)),
            pointLoss = 0.0,
        )
        val client = FakeTopMoveEngineSessionClient(
            result = AnalysisResult(
                status = EngineStatus.ready("analysis ready"),
                candidates = listOf(candidate),
                summary = "engine summary",
            ),
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Balanced,
            deep = true,
        )
        val effect = GameSessionEffect.RunTopMoveAnalysis(
            plan = plan,
            deep = true,
            automatic = true,
        )

        val update = client.runTopMoveAnalysisEffect(
            effect = effect,
            context = TopMoveAnalysisExecutionContext(
                targetState = state,
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Balanced,
                topMovesEnabled = false,
                cacheEnabled = true,
            ),
        )

        assertEquals(state, client.analyzedState)
        assertEquals(plan.analysisLimit, client.analyzedLimit)
        assertTrue(update.engineMessage.startsWith("Move review analysis ready"))
        assertEquals(0, update.candidateMoves.size)
        assertNotNull(update.cachedResult)
    }

    @Test
    fun runTopMoveAnalysisWorkflowResultWrapsSuccessAndFailure() = runBlocking {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            pointLoss = 0.0,
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val effect = GameSessionEffect.RunTopMoveAnalysis(
            plan = plan,
            deep = false,
            automatic = true,
        )
        val context = TopMoveAnalysisExecutionContext(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            topMovesEnabled = true,
            cacheEnabled = true,
        )

        val success = FakeTopMoveEngineSessionClient(
            result = AnalysisResult(
                status = EngineStatus.ready("analysis ready"),
                candidates = listOf(candidate),
                summary = "summary",
            ),
        ).runTopMoveAnalysisWorkflowResult(effect, context)
        val failure = FakeTopMoveEngineSessionClient(
            failure = IllegalStateException("engine failed"),
        ).runTopMoveAnalysisWorkflowResult(effect, context)

        assertTrue(success is TopMoveAnalysisWorkflowResult.Success)
        assertEquals("analysis ready", (success as TopMoveAnalysisWorkflowResult.Success).update.engineMessage)
        assertTrue(failure is TopMoveAnalysisWorkflowResult.Failure)
        assertEquals("engine failed", (failure as TopMoveAnalysisWorkflowResult.Failure).error.message)
    }

    @Test
    fun runTopMoveAnalysisEffectApplyPlanBuildsApplyDisposition() = runBlocking {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
            pointLoss = 0.0,
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val effect = GameSessionEffect.RunTopMoveAnalysis(
            plan = plan,
            deep = false,
            automatic = true,
        )
        val context = TopMoveAnalysisExecutionContext(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            topMovesEnabled = true,
            cacheEnabled = true,
        )
        val token = topMoveAnalysisOperationToken(
            targetState = state,
            plan = plan,
            sessionGeneration = 3L,
        )
        val request = TopMoveAnalysisEffectLaunchRequest(
            effect = effect,
            context = context,
            token = token,
            currentState = state,
            currentAnalysisKey = plan.analysisKey,
            currentSessionGeneration = 3L,
            targetState = state,
            topMovesEnabled = true,
        )

        val success = FakeTopMoveEngineSessionClient(
            result = AnalysisResult(
                status = EngineStatus.ready("analysis ready"),
                candidates = listOf(candidate),
                summary = "summary",
            ),
        ).runTopMoveAnalysisEffectApplyPlan(request)
        val failure = FakeTopMoveEngineSessionClient(
            failure = IllegalStateException("engine failed"),
        ).runTopMoveAnalysisEffectApplyPlan(request)

        assertTrue(success is TopMoveAnalysisCompletionApplyPlan.ApplySuccess)
        assertEquals(
            "analysis ready",
            (success as TopMoveAnalysisCompletionApplyPlan.ApplySuccess).update.engineMessage,
        )
        assertTrue(failure is TopMoveAnalysisCompletionApplyPlan.ApplyFailure)
        assertEquals(
            "engine failed",
            (failure as TopMoveAnalysisCompletionApplyPlan.ApplyFailure).display.engineMessage,
        )
    }

    @Test
    fun cachedAnalysisUpdateKeepsDisplayMovesOnlyWhenTopMovesEnabled() {
        val state = GameState.empty()
        val snapshot = MoveAnalysisSnapshot.from(
            state = state,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(
                        player = StoneColor.Black,
                        coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    ),
                    pointLoss = 0.0,
                ),
            ),
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val cached = CachedAnalysisResult(snapshot = snapshot, candidateText = "cached text")

        val hidden = buildCachedTopMoveAnalysisUpdate(
            targetState = state,
            cacheKey = plan.analysisKey,
            cached = cached,
            topMovesEnabled = false,
        )
        val shown = buildCachedTopMoveAnalysisUpdate(
            targetState = state,
            cacheKey = plan.analysisKey,
            cached = cached,
            topMovesEnabled = true,
        )

        assertEquals(0, hidden.candidateMoves.size)
        assertEquals(1, shown.candidateMoves.size)
        assertTrue(hidden.engineMessage.startsWith("Move review analysis cache hit"))
        assertTrue(shown.engineMessage.startsWith("Top Moves cache hit"))
    }

    @Test
    fun launchPlanRestoresCurrentSnapshotForAutomaticSameKeyDisplayRefresh() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        var cacheRequested = false

        val launchPlan = buildTopMoveAnalysisLaunchPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
            automatic = true,
            topMovesEnabled = true,
            currentCandidateMoves = emptyList(),
            reviewAnalysis = snapshot,
            lastAnalysisKey = plan.analysisKey,
            cachedResultFor = {
                cacheRequested = true
                null
            },
        )

        assertTrue(launchPlan is TopMoveAnalysisLaunchPlan.RestoreCurrentSnapshot)
        assertEquals(1, (launchPlan as TopMoveAnalysisLaunchPlan.RestoreCurrentSnapshot).candidateMoves.size)
        assertFalse(cacheRequested)
    }

    @Test
    fun launchPlanSkipsAutomaticSameKeyWhenDisplayDoesNotNeedRefresh() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val launchPlan = buildTopMoveAnalysisLaunchPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
            automatic = true,
            topMovesEnabled = true,
            currentCandidateMoves = listOf(candidate),
            reviewAnalysis = snapshot,
            lastAnalysisKey = plan.analysisKey,
            cachedResultFor = { null },
        )

        assertTrue(launchPlan is TopMoveAnalysisLaunchPlan.Skip)
    }

    @Test
    fun launchPlanUsesCachedAnalysisBeforeRunningEngine() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val launchPlan = buildTopMoveAnalysisLaunchPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
            automatic = false,
            topMovesEnabled = true,
            currentCandidateMoves = emptyList(),
            reviewAnalysis = MoveAnalysisSnapshot.empty(state),
            lastAnalysisKey = null,
            cachedResultFor = { key ->
                if (key == plan.analysisKey) {
                    CachedAnalysisResult(snapshot = snapshot, candidateText = "cached text")
                } else {
                    null
                }
            },
        )

        assertTrue(launchPlan is TopMoveAnalysisLaunchPlan.UseCached)
        assertEquals(plan.analysisKey, (launchPlan as TopMoveAnalysisLaunchPlan.UseCached).analysisKey)
        assertEquals(1, launchPlan.update.candidateMoves.size)
    }

    @Test
    fun launchRequestBuildsTheSameCachedAnalysisPlanAsExpandedArguments() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val cached = CachedAnalysisResult(snapshot = snapshot, candidateText = "cached text")

        val launchPlan = buildTopMoveAnalysisLaunchPlan(
            request = TopMoveAnalysisLaunchRequest(
                targetState = state,
                engineProfile = EngineProfile(),
                analysisPreset = AnalysisPreset.Lite,
                deep = false,
                automatic = false,
                topMovesEnabled = true,
                currentCandidateMoves = emptyList(),
                reviewAnalysis = MoveAnalysisSnapshot.empty(state),
                lastAnalysisKey = null,
            ),
            cachedResultFor = { key -> cached.takeIf { key == plan.analysisKey } },
        )

        assertTrue(launchPlan is TopMoveAnalysisLaunchPlan.UseCached)
        assertEquals(plan.analysisKey, (launchPlan as TopMoveAnalysisLaunchPlan.UseCached).analysisKey)
        assertEquals(1, launchPlan.update.candidateMoves.size)
    }

    @Test
    fun undoAnalysisRestoreCacheStoresOnlyCompleteRootVisitResults() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val cache = UndoAnalysisRestoreCache(maxEntries = 4)

        cache.put(
            key = plan.analysisKey,
            result = CachedAnalysisResult(
                snapshot = snapshot,
                candidateText = "partial",
                quality = PositionAnalysisCacheQuality.from(
                    rootVisits = plan.analysisLimit.visits - 1,
                    requestedRootVisits = plan.analysisLimit.visits,
                ),
            ),
        )
        assertNull(cache.get(plan.analysisKey))

        cache.put(
            key = plan.analysisKey,
            result = CachedAnalysisResult(
                snapshot = snapshot,
                candidateText = "complete",
                quality = PositionAnalysisCacheQuality.from(
                    rootVisits = plan.analysisLimit.visits,
                    requestedRootVisits = plan.analysisLimit.visits,
                ),
            ),
        )

        assertEquals("complete", cache.get(plan.analysisKey)?.candidateText)
        assertTrue(cache.statsText().contains("undoRestoreEntries=1"))
    }

    @Test
    fun launchStateUpdateAppliesCachedAnalysisToAnalysisState() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val update = buildCachedTopMoveAnalysisUpdate(
            targetState = state,
            cacheKey = plan.analysisKey,
            cached = CachedAnalysisResult(snapshot = snapshot, candidateText = "cached text"),
            topMovesEnabled = true,
        )

        val stateUpdate = GameSessionAnalysisState.empty(state)
            .applyTopMoveAnalysisLaunchPlan(
                TopMoveAnalysisLaunchPlan.UseCached(
                    analysisKey = plan.analysisKey,
                    update = update,
                ),
            )

        assertNotNull(stateUpdate)
        assertEquals(plan.analysisKey, stateUpdate?.analysisState?.lastAnalysisKey)
        assertEquals(1, stateUpdate?.analysisState?.candidateMoves?.size)
        assertEquals(update.engineMessage, stateUpdate?.engineMessage)
        assertNull(stateUpdate?.effect)
    }

    @Test
    fun launchStateUpdateBuildsRunTopMoveEffectAndMarksPendingAnalysisKey() {
        val state = GameState.empty()
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val stateUpdate = GameSessionAnalysisState.empty(state)
            .applyTopMoveAnalysisLaunchPlan(
                TopMoveAnalysisLaunchPlan.RunEngine(
                    plan = plan,
                    deep = true,
                    automatic = true,
                ),
            )

        assertEquals(plan.analysisKey, stateUpdate?.analysisState?.lastAnalysisKey)
        assertEquals(plan, stateUpdate?.effect?.plan)
        assertEquals(true, stateUpdate?.effect?.deep)
        assertEquals(true, stateUpdate?.effect?.automatic)
        assertNull(stateUpdate?.engineMessage)
    }

    @Test
    fun launchPlanRunsEngineWhenNoCurrentOrCachedAnalysisExists() {
        val state = GameState.empty()
        val expected = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val launchPlan = buildTopMoveAnalysisLaunchPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
            automatic = false,
            topMovesEnabled = false,
            currentCandidateMoves = emptyList(),
            reviewAnalysis = MoveAnalysisSnapshot.empty(state),
            lastAnalysisKey = null,
            cachedResultFor = { null },
        )

        assertTrue(launchPlan is TopMoveAnalysisLaunchPlan.RunEngine)
        val run = launchPlan as TopMoveAnalysisLaunchPlan.RunEngine
        assertEquals(expected, run.plan)
        assertEquals(false, run.deep)
        assertEquals(false, run.automatic)
    }

    @Test
    fun controllerStateBuildsTopMoveAnalysisLaunchPlan() {
        val state = GameState.empty()
        val controller = topMoveControllerState(state = state)
        val expected = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )

        val launchPlan = controller.toTopMoveAnalysisLaunchPlan(
            targetState = state,
            deep = false,
            automatic = false,
            cachedResultFor = { null },
        )

        assertTrue(launchPlan is TopMoveAnalysisLaunchPlan.RunEngine)
        val run = launchPlan as TopMoveAnalysisLaunchPlan.RunEngine
        assertEquals(expected, run.plan)
        assertEquals(false, run.deep)
        assertEquals(false, run.automatic)
    }

    @Test
    fun topMoveAnalysisResultGuardRejectsChangedPositionOrAnalysisKey() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val changedState = state
            .play(Move.Play(StoneColor.White, BoardCoordinate.fromLabel("D5", BoardSize.Nine)))
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val deepPlan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = true,
        )
        val token = topMoveAnalysisOperationToken(
            targetState = state,
            plan = plan,
        )

        assertEquals(EngineOperationKind.TopMoves, token.operation.kind)
        assertEquals(EngineFallbackPolicy.CachedAnalysis, token.operation.fallbackPolicy)
        assertEquals(plan.analysisLimit.timeMillis, token.operation.timeoutPolicy.timeoutMillis)
        assertEquals("local-engine", token.operation.backendId)
        assertEquals(
            EngineOperationResultGuard.Apply,
            evaluateTopMoveAnalysisResultGuard(
                token = token,
                currentState = state,
                currentAnalysisKey = plan.analysisKey,
            ),
        )
        assertTrue(
            evaluateTopMoveAnalysisResultGuard(
                token = token,
                currentState = changedState,
                currentAnalysisKey = plan.analysisKey,
            ) is EngineOperationResultGuard.Discard,
        )
        assertTrue(
            evaluateTopMoveAnalysisResultGuard(
                token = token,
                currentState = state,
                currentAnalysisKey = deepPlan.analysisKey,
            ) is EngineOperationResultGuard.Discard,
        )
    }

    @Test
    fun topMoveAnalysisFailureDisplayPlanPreservesHiddenTopMovesText() {
        val state = GameState.empty()

        val hidden = buildTopMoveAnalysisFailureDisplayPlan(
            targetState = state,
            error = IllegalStateException("engine failed"),
            topMovesEnabled = false,
        )
        val shown = buildTopMoveAnalysisFailureDisplayPlan(
            targetState = state,
            error = Throwable(),
            topMovesEnabled = true,
        )

        assertEquals("engine failed", hidden.engineMessage)
        assertEquals(false, hidden.clearDisplayedTopMoves)
        assertNull(hidden.candidateText)
        assertEquals("Top Moves analysis failed.", shown.engineMessage)
        assertEquals(true, shown.clearDisplayedTopMoves)
        assertEquals("Top Moves analysis failed.", shown.candidateText)
    }

    @Test
    fun planShowTopMovesUsesCachedBestMoveWithoutDeepFallback() {
        val state = GameState.empty()
        val snapshot = MoveAnalysisSnapshot.from(
            state = state,
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(
                        player = StoneColor.Black,
                        coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
                    ),
                    pointLoss = 0.0,
                ),
            ),
        )
        val currentPlan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Deep,
            deep = false,
        )

        val showPlan = planShowTopMoves(
            reviewAnalysis = snapshot,
            lastAnalysisKey = currentPlan.analysisKey,
            currentPlan = currentPlan,
            analysisPreset = AnalysisPreset.Deep,
            isEngineBusy = false,
        )

        assertTrue(showPlan is ShowTopMovesPlan.ShowCached)
        assertEquals(1, (showPlan as ShowTopMovesPlan.ShowCached).candidateMoves.size)
    }

    @Test
    fun controllerStateBuildsShowTopMovesPlanFromCurrentAnalysis() {
        val state = GameState.empty()
        val candidate = CandidateMove(
            move = Move.Play(
                player = StoneColor.Black,
                coordinate = BoardCoordinate.fromLabel("E5", BoardSize.Nine),
            ),
            pointLoss = 0.0,
        )
        val snapshot = MoveAnalysisSnapshot.from(state = state, candidates = listOf(candidate))
        val currentPlan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
        )
        val controller = topMoveControllerState(
            state = state,
            analysisState = GameSessionAnalysisState.reset(
                candidateText = "cached analysis",
                reviewAnalysis = snapshot,
            ).copy(lastAnalysisKey = currentPlan.analysisKey),
        )

        val showPlan = controller.toShowTopMovesPlan(isEngineBusy = false)

        assertTrue(showPlan is ShowTopMovesPlan.ShowCached)
        assertEquals(1, (showPlan as ShowTopMovesPlan.ShowCached).candidateMoves.size)
    }
}

private fun topMoveControllerState(
    state: GameState,
    analysisState: GameSessionAnalysisState = GameSessionAnalysisState.empty(state),
): GameSessionControllerState =
    GameSessionControllerState(
        core = GameSessionCoreState(
            gameState = state,
            isGameEnded = false,
            analysisState = analysisState,
            scoreState = GameSessionScoreState.reset(
                scoreText = "score",
                scoreSnapshots = listOf(localScoreSnapshot(state)),
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
            playerSetup = PlayerSetup(),
            autoPlayDelaySetting = AutoPlayDelaySetting.Default,
            searchTimeSettings = SearchTimeSettings(),
            topMovesEnabled = true,
        ),
        benchmark = EngineBenchmarkUiState.initial(
            benchmarkText = "benchmark",
            profile = null,
        ),
        savedSession = SavedSessionUiState(),
        autoAiTurn = AutoAiTurnUiState(),
        positionCacheOptimization = PositionAnalysisCacheOptimizationUiState(),
    )

private class FakeTopMoveEngineSessionClient(
    private val result: AnalysisResult? = null,
    private val failure: Throwable? = null,
) : EngineSessionClient {
    var analyzedState: GameState? = null
        private set
    var analyzedLimit: AnalysisLimit? = null
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
    ): AnalysisResult {
        analyzedState = state
        analyzedLimit = limit
        failure?.let { throw it }
        return result ?: error("not used")
    }

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
        playLevel: com.worksoc.goaicoach.shared.PlayLevelSetting,
        currentProfile: EngineProfile,
        searchTimeSettings: com.worksoc.goaicoach.shared.SearchTimeSettings,
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
    ): ScoreEstimate =
        error("not used")

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
}
