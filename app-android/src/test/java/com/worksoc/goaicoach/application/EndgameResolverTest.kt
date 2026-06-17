package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.endgame.*
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort

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

    @Test
    fun resolveAiEndgameLogsDiagnosticEventOnScoreDisagreement() = runBlocking {
        val state = GameState.empty(ruleset = Ruleset.Japanese)
            .copy(
                stones = mapOf(
                    BoardCoordinate.fromLabel("C4", BoardSize.Nine) to StoneColor.Black,
                ),
                moves = listOf(
                    Move.Pass(StoneColor.Black),
                    Move.Pass(StoneColor.White),
                ),
            )
        val log = FakeDiagnosticEventLog()
        val engine = FakeEndgameJudgeGateway(finalScoreRaw = "W+100.5")

        val resolution = resolveAiEndgame(
            judgeGateway = engine,
            originalState = state,
            estimateLimit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
            diagnosticEventLog = log,
        )

        val localRaw = resolution.localFinalScore.rawScore
        val engineRaw = resolution.engineFinalScore?.rawScore
        assertTrue(localRaw != engineRaw)

        assertEquals(1, log.events.size)
        val event = log.events.first()
        assertEquals("score.final_disagreement", event.code)
        assertEquals("W+100.5", event.context["engineFinalScore"])
        assertEquals(localRaw, event.context["localScore"])
    }
}

private class FakeDiagnosticEventLog : DiagnosticEventLogPort {
    val events = mutableListOf<com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent>()
    override fun append(event: com.worksoc.goaicoach.shared.diagnostic.DiagnosticEvent, nowMillis: Long) {
        events.add(event)
    }
    override fun readText(): String = ""
    override fun clear() { events.clear() }
}

private class FakeEndgameJudgeGateway(
    private val deadStones: List<BoardCoordinate> = emptyList(),
    private val finalScoreRaw: String = "B+5.5",
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
            rawScore = finalScoreRaw,
            winner = if (finalScoreRaw.startsWith("B")) StoneColor.Black else StoneColor.White,
            margin = 5.5,
            summary = "fake final",
        )
}
