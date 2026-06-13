package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.CandidateMove
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import com.worksoc.goaicoach.shared.analysisFingerprint
import com.worksoc.goaicoach.shared.describe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSessionTest {
    @Test
    fun syncToGameStateStartsNewGameAndReplaysAllMoves() = runBlocking {
        val engine = RecordingEngineAdapter()
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
            .play(Move.Pass(StoneColor.White))

        engine.syncToGameState(state)

        assertEquals(
            listOf(
                "newGame:9:japanese",
                "play:Black E5",
                "play:White pass",
            ),
            engine.calls,
        )
    }

    @Test
    fun startNewEngineGameConfiguresProfileAndReturnsInitialEstimateSnapshot() = runBlocking {
        val engine = RecordingEngineAdapter()
        val profile = EngineProfile(
            analysisLimit = AnalysisLimit(visits = 32, timeMillis = 500, candidateCount = 8),
        )

        val result = engine.startNewEngineGame(profile, BoardSize.Nine, Ruleset.Japanese)

        assertEquals(
            listOf(
                "stop",
                "initialize:32",
                "newGame:9:japanese",
                "estimate:1",
            ),
            engine.calls,
        )
        assertEquals("new game", result.message)
        assertEquals(0, result.scoreSnapshot?.moveNumber)
        assertTrue(result.scoreSnapshot?.whiteScoreLead == 1.5)
    }

    @Test
    fun localScoreSnapshotUsesCurrentMoveNumber() {
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        val snapshot = localScoreSnapshot(state)

        assertEquals(1, snapshot.moveNumber)
    }

    @Test
    fun runAutoAiTurnConfiguresSyncsSelectsMoveAndEstimatesScore() = runBlocking {
        val engine = RecordingEngineAdapter()
        val state = GameState.empty()
        val playLevel = PlayLevelSetting()

        val result = engine.runAutoAiTurn(
            currentState = state,
            playLevel = playLevel,
            currentProfile = EngineProfile(),
        )

        assertEquals(
            listOf(
                "configure:16",
                "newGame:9:japanese",
                "analyze:16",
                "play:Black E5",
                "estimate:1",
            ),
            engine.calls,
        )
        assertEquals(playLevel, result.playLevel)
        assertEquals(1, result.turnOutcome.gameState.moves.size)
        assertEquals("Black E5", result.turnOutcome.lastMoveText)
    }

    @Test
    fun runAutoAiTurnClearsSearchCacheOnlyWhenIsolationIsRequested() = runBlocking {
        val engine = RecordingEngineAdapter()

        engine.runAutoAiTurn(
            currentState = GameState.empty(),
            playLevel = PlayLevelSetting(),
            currentProfile = EngineProfile(),
            isolateSearchCache = true,
        )

        assertEquals(
            listOf(
                "configure:16",
                "newGame:9:japanese",
                "clearSearchCache",
                "analyze:16",
                "play:Black E5",
                "estimate:1",
            ),
            engine.calls,
        )
    }

    @Test
    fun adapterSessionClientAnalyzesExplicitPositionAfterSyncingState() = runBlocking {
        val engine = RecordingEngineAdapter()
        val client = AdapterEngineSessionClient(engine)
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        client.analyzePosition(
            state = state,
            limit = AnalysisLimit(visits = 32, timeMillis = 1_000, candidateCount = 3),
        )

        assertEquals(
            listOf(
                "newGame:9:japanese",
                "play:Black E5",
                "analyze:32",
            ),
            engine.calls,
        )
    }

    @Test
    fun adapterSessionClientReusesCompleteJsonPositionAnalysisCache() = runBlocking {
        val engine = RecordingEngineAdapter()
        val cacheStore = InMemoryPositionAnalysisCacheStore()
        val client = AdapterEngineSessionClient(
            coreApi = engine,
            positionAnalysisCacheStore = cacheStore,
        )
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val limit = AnalysisLimit(
            visits = 32,
            timeMillis = 2_000L,
            candidateCount = 16,
            includePolicy = true,
            refinePolicyMoves = 0,
            minVisitsPerCandidate = 0,
            minTimeMillis = null,
        )

        val first = client.analyzePosition(
            state = state,
            limit = limit,
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )
        val second = client.analyzePosition(
            state = state,
            limit = limit,
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )

        assertEquals(32, first.rootVisits)
        assertEquals(32, second.rootVisits)
        assertTrue(second.summary.contains("cache hit"))
        assertEquals(
            listOf(
                "newGame:9:japanese",
                "play:Black E5",
                "analyze:32",
            ),
            engine.calls,
        )
    }

    @Test
    fun adapterSessionClientReusesReusablePartialJsonPositionAnalysisCache() = runBlocking {
        val engine = RecordingEngineAdapter(analyzedRootVisits = { 20 })
        val cacheStore = InMemoryPositionAnalysisCacheStore()
        val client = AdapterEngineSessionClient(
            coreApi = engine,
            positionAnalysisCacheStore = cacheStore,
        )
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val limit = AnalysisLimit(
            visits = 32,
            timeMillis = 2_000L,
            candidateCount = 16,
            includePolicy = true,
        )

        val first = client.analyzePosition(
            state = state,
            limit = limit,
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )
        val second = client.analyzePosition(
            state = state,
            limit = limit,
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )

        assertEquals(20, first.rootVisits)
        assertEquals(20, second.rootVisits)
        assertTrue(second.summary.contains("partial root=20/32"))
        assertEquals(1, engine.calls.count { call -> call == "analyze:32" })
    }

    @Test
    fun adapterSessionClientStoresButDoesNotReuseDiagnosticOnlyJsonCache() = runBlocking {
        val engine = RecordingEngineAdapter(analyzedRootVisits = { 4 })
        val cacheStore = InMemoryPositionAnalysisCacheStore()
        val client = AdapterEngineSessionClient(
            coreApi = engine,
            positionAnalysisCacheStore = cacheStore,
        )
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val limit = AnalysisLimit(
            visits = 32,
            timeMillis = 2_000L,
            candidateCount = 16,
            includePolicy = true,
        )

        client.analyzePosition(
            state = state,
            limit = limit,
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )
        client.analyzePosition(
            state = state,
            limit = limit,
            searchMode = EngineSearchMode.JsonPositionAnalysis,
        )

        assertEquals(1, cacheStore.entryCount)
        assertEquals(2, engine.calls.count { call -> call == "analyze:32" })
    }

    @Test
    fun adapterSessionClientOptimizesCacheWithUncappedExecutionLimitUnderGameplayKey() = runBlocking {
        val engine = RecordingEngineAdapter()
        val cacheStore = InMemoryPositionAnalysisCacheStore()
        val client = AdapterEngineSessionClient(
            coreApi = engine,
            positionAnalysisCacheStore = cacheStore,
        )
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))
        val cacheLimit = AnalysisLimit(
            visits = 32,
            timeMillis = 2_000L,
            candidateCount = 16,
            includePolicy = true,
        )
        val plan = PositionAnalysisCacheOptimizationPlan(
            gameFingerprint = state.analysisFingerprint(),
            finalMoveCount = state.moves.size,
            targets = listOf(
                PositionAnalysisCacheOptimizationTarget(
                    state = state,
                    moveNumber = state.moves.size,
                    levelLabel = "초급 7단계",
                    cacheLimit = cacheLimit,
                    executionLimit = cacheLimit.copy(timeMillis = null),
                ),
            ),
        )

        val result = client.optimizePositionAnalysisCache(plan)

        assertEquals(1, result.completeTargets)
        assertEquals(1, cacheStore.entryCount)
        assertEquals(2_000L, cacheStore.entries.single().key.limit.timeMillis)
        assertTrue(engine.calls.contains("analyze:32"))
    }

    @Test
    fun estimateScoreForStateOptionallySyncsBoardBeforeEstimating() = runBlocking {
        val engine = RecordingEngineAdapter()
        val state = GameState.empty()
            .play(Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)))

        engine.estimateScoreForState(
            state = state,
            profile = EngineProfile(),
            syncFirst = true,
        )

        assertEquals(
            listOf(
                "newGame:9:japanese",
                "play:Black E5",
                "estimate:5",
            ),
            engine.calls,
        )
    }
}

private class RecordingEngineAdapter(
    private val analyzedRootVisits: (AnalysisLimit) -> Int? = { limit -> limit.visits },
) : EngineAdapter {
    val calls = mutableListOf<String>()

    override suspend fun initialize(profile: EngineProfile): EngineStatus {
        calls += "initialize:${profile.analysisLimit.visits}"
        return EngineStatus.ready("initialized")
    }

    override suspend fun configure(profile: EngineProfile): EngineStatus {
        calls += "configure:${profile.analysisLimit.visits}"
        return EngineStatus.ready("configured")
    }

    override suspend fun newGame(
        boardSize: BoardSize,
        ruleset: Ruleset,
    ): EngineStatus {
        calls += "newGame:${boardSize.value}:${ruleset.katagoName}"
        return EngineStatus.ready("new game")
    }

    override suspend fun playMove(move: Move): EngineStatus {
        calls += "play:${move.describe(BoardSize.Nine)}"
        return EngineStatus.ready("played")
    }

    override suspend fun genMove(player: StoneColor): MoveResult =
        MoveResult(
            status = EngineStatus.ready("generated"),
            move = Move.Pass(player),
            summary = "generated",
        ).also {
            calls += "genMove:${player.label}"
        }

    override suspend fun undoMove(): EngineStatus =
        EngineStatus.ready("undone")

    override suspend fun clearSearchCache(): EngineStatus =
        EngineStatus.ready("cache cleared").also {
            calls += "clearSearchCache"
        }

    override suspend fun analyze(limit: AnalysisLimit): AnalysisResult =
        AnalysisResult(
            status = EngineStatus.ready("analyzed"),
            candidates = listOf(
                CandidateMove(
                    move = Move.Play(StoneColor.Black, BoardCoordinate.fromLabel("E5", BoardSize.Nine)),
                    pointLoss = 0.0,
                    visits = analyzedRootVisits(limit),
                    engineOrder = 0,
                ),
            ),
            summary = "analyzed",
            rootVisits = analyzedRootVisits(limit),
            elapsedMillis = 10L,
        ).also {
            calls += "analyze:${limit.visits}"
        }

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate {
        calls += "estimate:${limit.candidateCount}"
        return ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = 1.5,
            whiteWinRate = 0.55,
            summary = "estimated",
        )
    }

    override suspend fun deadStones(): DeadStonesResult =
        DeadStonesResult(
            status = EngineStatus.ready("dead stones"),
            coordinates = emptyList(),
            summary = "dead stones",
        )

    override suspend fun scoreFinal(): FinalScoreResult =
        FinalScoreResult(
            status = EngineStatus.ready("final"),
            rawScore = "B+0.5",
            summary = "final",
        )

    override suspend fun stop(): EngineStatus =
        EngineStatus.stopped("stopped").also {
            calls += "stop"
        }
}

private class InMemoryPositionAnalysisCacheStore : PositionAnalysisCacheStore {
    private val entryMap = mutableMapOf<PositionAnalysisCacheKey, PositionAnalysisCacheEntry>()
    val entryCount: Int
        get() = entryMap.size

    val entries: List<PositionAnalysisCacheEntry>
        get() = entryMap.values.toList()

    override fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        entryMap[key]?.takeIf { entry -> entry.quality.isReusable }

    override fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        entryMap[key]

    override fun put(
        entry: PositionAnalysisCacheEntry,
        nowMillis: Long,
    ) {
        val existing = entryMap[entry.key]
        if (shouldReplacePositionAnalysisCacheEntry(existing, entry)) {
            entryMap[entry.key] = entry
        }
    }

    override fun statsText(nowMillis: Long): String =
        "entries=${entryMap.size}"
}
