package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.endgame.*

import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.DeadStonesResult
import com.worksoc.goaicoach.shared.EngineProfile
import com.worksoc.goaicoach.shared.EngineStatus
import com.worksoc.goaicoach.shared.FinalScoreResult
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.ScoreEstimate
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndgameResolverTest {
    @Test
    fun resolveAiEndgameRemovesEngineMarkedDeadStonesAndBuildsLog() = runBlocking {
        val deadWhite = BoardCoordinate.fromLabel("D4", BoardSize.Nine)
        val state = GameState.empty(ruleset = Ruleset.Japanese)
            .copy(
                stones = mapOf(
                    deadWhite to StoneColor.White,
                    BoardCoordinate.fromLabel("C4", BoardSize.Nine) to StoneColor.Black,
                ),
                moves = listOf(
                    Move.Pass(StoneColor.Black),
                    Move.Pass(StoneColor.White),
                ),
            )
        val engine = FakeEndgameJudgeGateway(deadStones = listOf(deadWhite))

        val resolution = resolveAiEndgame(
            judgeGateway = engine,
            originalState = state,
            estimateLimit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
        )

        assertEquals(1, resolution.cleanup.removedCount)
        assertNull(resolution.cleanup.state.stoneAt(deadWhite))
        assertEquals(1, resolution.cleanup.state.capturedByBlack)
        assertEquals(0, resolution.cleanup.state.capturedByWhite)
        assertTrue(resolution.toCandidateText().contains("Removed 1"))
        assertTrue(resolution.toEngineMessage().contains("Dead-stone cleanup removed 1"))
        assertTrue(resolution.toLogDetail(state).contains("removedStones=D4=White"))
        assertTrue(resolution.toLogDetail(state).contains("timingSummary="))
        assertTrue(resolution.toLogDetail(state).contains("deadStonesMs="))
        assertTrue(resolution.toLogDetail(state).contains("diagnosticFinalScoreMs="))
        assertTrue(resolution.timings.resolverTotalMs >= 0L)
    }
}

private class FakeEndgameJudgeGateway(
    private val deadStones: List<BoardCoordinate>,
) : EndgameJudgeGateway {
    override suspend fun configure(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("configured")

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = -5.0,
            whiteWinRate = 0.1,
            summary = "fake estimate",
        )

    override suspend fun deadStones(): DeadStonesResult =
        DeadStonesResult(
            status = EngineStatus.ready("dead stones"),
            coordinates = deadStones,
            summary = "fake dead stones",
        )

    override suspend fun scoreFinal(): FinalScoreResult =
        FinalScoreResult(
            status = EngineStatus.ready("final"),
            rawScore = "B+5.5",
            winner = StoneColor.Black,
            margin = 5.5,
            summary = "fake final",
        )
}
