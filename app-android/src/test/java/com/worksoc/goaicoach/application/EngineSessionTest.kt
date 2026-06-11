package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineAdapter
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.MoveResult
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
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
                "genMove:Black",
                "estimate:1",
            ),
            engine.calls,
        )
        assertEquals(playLevel, result.playLevel)
        assertEquals(1, result.turnOutcome.gameState.moves.size)
        assertEquals("Black pass", result.turnOutcome.lastMoveText)
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
                "genMove:Black",
                "estimate:1",
            ),
            engine.calls,
        )
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

private class RecordingEngineAdapter : EngineAdapter {
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
            candidates = emptyList(),
            summary = "analyzed",
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
