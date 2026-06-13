package com.worksoc.goaicoach.application

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
import com.worksoc.goaicoach.shared.Ruleset
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
        val result = AnalysisResult(
            status = EngineStatus.ready("analysis complete"),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, coordinate),
                    pointLoss = 0.0,
                ),
            ),
            summary = "raw",
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
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
    }

    @Test
    fun completedAnalysisUpdateDoesNotStorePayloadWhenCacheDisabled() {
        val state = GameState.empty()
        val result = AnalysisResult(
            status = EngineStatus.ready("analysis complete"),
            candidates = emptyList(),
            summary = "raw",
        )
        val plan = buildTopMoveAnalysisPlan(
            targetState = state,
            engineProfile = EngineProfile(),
            analysisPreset = AnalysisPreset.Lite,
            deep = false,
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
        assertEquals(expected, (launchPlan as TopMoveAnalysisLaunchPlan.RunEngine).plan)
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
}

private class FakeTopMoveEngineSessionClient(
    private val result: AnalysisResult,
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
        return result
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
