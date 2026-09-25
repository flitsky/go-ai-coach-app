package com.worksoc.goaicoach.application

import com.worksoc.goaicoach.application.analysis.*
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.endgame.*
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.DeadStonesResult
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.FinalScoreResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.policy.EndgameScoreSource
import com.worksoc.goaicoach.shared.scoring.chineseHandicapProbeState
import com.worksoc.goaicoach.testsupport.RecordingDiagnosticEventLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

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
        val log = RecordingDiagnosticEventLog()
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

    /**
     * ⭐ **#89 재현 국면에서 `score.final_disagreement`가 더는 나지 않는다.**
     *
     * 면적계가 접바둑(9x9 2점, 덤 6.5)을 두 번 통과로 끝내면 KataGo `final_score`는 W+1.5인데
     * (조사 실측, `chinese`의 `whiteHandicapBonus:"N"`), 고치기 전 로컬 계가는 보정 2점을 빠뜨려
     * B+0.5였다 — 결과는 로컬이 정하므로 **승자가 뒤집혔고**, 이런 판마다 Critical 진단 이벤트가 났다.
     * 엔진 쪽 가짜 값은 조사가 잰 그대로다(`final_score` W+1.5, raw NN `whiteLead` +2.206).
     */
    @Test
    fun resolveAiEndgameAgreesWithKataGoFinalScoreOnAChineseHandicapGame() = runBlocking {
        val state = chineseHandicapProbeState(Ruleset.Chinese, komi = 6.5)
        val log = RecordingDiagnosticEventLog()
        val engine = FakeEndgameJudgeGateway(finalScoreRaw = "W+1.5", finalScoreMargin = 1.5, estimateWhiteLead = 2.206)

        val resolution = resolveAiEndgame(
            judgeGateway = engine,
            originalState = state,
            estimateLimit = AnalysisLimit(visits = 16, timeMillis = 250, candidateCount = 8),
            diagnosticEventLog = log,
        )

        assertEquals("W+1.5", resolution.localFinalScore.rawScore)
        assertEquals(2.0, resolution.localFinalScore.whiteHandicapBonus)
        assertEquals(EndgameScoreSource.CleanedLocalArea, resolution.scoreSource)
        assertEquals("W+1.5", resolution.finalScore.rawScore)
        assertEquals(StoneColor.White, resolution.finalScore.winner)
        assertEquals(emptyList(), log.events.map { it.code }, "로컬 계가와 KataGo final_score가 같으면 진단 이벤트가 없어야 한다(#89).")
    }
}

private class FakeEndgameJudgeGateway(
    private val deadStones: List<BoardCoordinate> = emptyList(),
    private val finalScoreRaw: String = "B+5.5",
    private val finalScoreMargin: Double = 5.5,
    private val estimateWhiteLead: Double = -5.0,
) : EndgameJudgeGateway {
    override suspend fun configure(profile: EngineProfile): EngineStatus =
        EngineStatus.ready("configured")

    override suspend fun estimateScore(limit: AnalysisLimit): ScoreEstimate =
        ScoreEstimate(
            status = EngineStatus.ready("estimated"),
            whiteScoreLead = estimateWhiteLead,
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
            margin = finalScoreMargin,
            summary = "fake final",
        )
}
